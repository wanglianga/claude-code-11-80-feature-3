package com.flood.statemigrate.service.access;

import com.flood.statemigrate.domain.ClosureOverride;
import com.flood.statemigrate.domain.DepthObservation;
import com.flood.statemigrate.domain.LinkTimeState;
import com.flood.statemigrate.domain.RoadLink;
import com.flood.statemigrate.domain.RoadNetwork;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 时变通行图构建器：把积水窗口（逐时间片水深）与各路段的封闭阈值、状态有效期、封路更正相交。
 *
 * 缺测边界规则（强约束）：时间片 t 上，若某路段没有 t 之前的水深观测、或最近观测已超出
 * depthValidityMinutes 有效期，则该路段状态为 UNKNOWN——后续最短路按不可通行处理，
 * 任何输出都不得把缺测支路当成畅通证据。
 */
@Component
public class TimeVaryingGraphBuilder {

    /** 一个时间片的完整路网状态 */
    public record TimeVaryingGraph(
            RoadNetwork network,
            List<Instant> slotTimes,
            Map<String, List<LinkSlotState>> linkStates,
            List<Long> observationIds,
            List<Long> overrideIds
    ) {
        public LinkSlotState state(String linkCode, int slotIndex) {
            return linkStates.get(linkCode).get(slotIndex);
        }
    }

    public TimeVaryingGraph build(RoadNetwork network, List<RoadLink> links,
                                  List<DepthObservation> observations, List<ClosureOverride> overrides) {
        long spanMin = Duration.between(network.getHorizonStart(), network.getHorizonEnd()).toMinutes();
        if (spanMin % network.getSlotMinutes() != 0) {
            throw new IllegalArgumentException("分析窗口长度必须是时间片长度的整数倍");
        }
        int slotCount = (int) (spanMin / network.getSlotMinutes()) + 1;
        List<Instant> slotTimes = new ArrayList<>(slotCount);
        for (int i = 0; i < slotCount; i++) {
            slotTimes.add(network.getHorizonStart().plus(Duration.ofMinutes((long) i * network.getSlotMinutes())));
        }

        Map<String, List<DepthObservation>> obsByLink = new HashMap<>();
        List<Long> observationIds = new ArrayList<>();
        for (DepthObservation o : observations) {
            obsByLink.computeIfAbsent(o.getLinkCode(), k -> new ArrayList<>()).add(o);
            observationIds.add(o.getId());
        }
        Map<String, List<ClosureOverride>> overrideByLink = new HashMap<>();
        List<Long> overrideIds = new ArrayList<>();
        for (ClosureOverride c : overrides) {
            overrideByLink.computeIfAbsent(c.getLinkCode(), k -> new ArrayList<>()).add(c);
            overrideIds.add(c.getId());
        }

        Map<String, List<LinkSlotState>> result = new LinkedHashMap<>();
        for (RoadLink link : links) {
            List<DepthObservation> linkObs = obsByLink.getOrDefault(link.getLinkCode(), List.of());
            List<ClosureOverride> linkOverrides = overrideByLink.getOrDefault(link.getLinkCode(), List.of());
            List<LinkSlotState> states = new ArrayList<>(slotCount);
            for (int i = 0; i < slotCount; i++) {
                states.add(evaluateSlot(link, slotTimes.get(i), network.getDepthValidityMinutes(),
                        linkObs, linkOverrides, i));
            }
            result.put(link.getLinkCode(), states);
        }
        return new TimeVaryingGraph(network, slotTimes, result, observationIds, overrideIds);
    }

    private LinkSlotState evaluateSlot(RoadLink link, Instant t, int validityMinutes,
                                       List<DepthObservation> obs, List<ClosureOverride> overrides, int slotIndex) {
        // 1) 路段自身状态有效期
        if (link.getValidFrom() != null && t.isBefore(link.getValidFrom())
                || link.getValidTo() != null && t.isAfter(link.getValidTo())) {
            return new LinkSlotState(link.getLinkCode(), slotIndex, LinkTimeState.CLOSED,
                    "OUTSIDE_VALIDITY", null, null);
        }

        // 2) 封路更正：强制封闭优先；强制放行记录下来但不能掩盖缺测
        boolean forceOpen = false;
        for (ClosureOverride c : overrides) {
            if (!t.isBefore(c.getEffectiveFrom()) && !t.isAfter(c.getEffectiveTo())) {
                if (c.isClosed()) {
                    return new LinkSlotState(link.getLinkCode(), slotIndex, LinkTimeState.CLOSED,
                            "CLOSURE_OVERRIDE", null, null);
                }
                forceOpen = true;
            }
        }

        // 3) 水深取值：t 时刻（含）之前最近一条观测，并校验有效期
        DepthObservation latest = null;
        for (DepthObservation o : obs) {
            if (!o.getObservedAt().isAfter(t) && (latest == null || o.getObservedAt().isAfter(latest.getObservedAt()))) {
                latest = o;
            }
        }
        if (latest == null) {
            return new LinkSlotState(link.getLinkCode(), slotIndex, LinkTimeState.UNKNOWN,
                    "DEPTH_MISSING", null, null);
        }
        long ageMin = Duration.between(latest.getObservedAt(), t).toMinutes();
        if (ageMin > validityMinutes) {
            return new LinkSlotState(link.getLinkCode(), slotIndex, LinkTimeState.UNKNOWN,
                    "DEPTH_EXPIRED:" + ageMin + "min>" + validityMinutes + "min",
                    latest.getDepthM(), latest.getId());
        }

        double depth = latest.getDepthM();
        // 4) 封闭阈值相交
        if (link.getDepthCloseThresholdM() != null && depth >= link.getDepthCloseThresholdM()) {
            return new LinkSlotState(link.getLinkCode(), slotIndex, LinkTimeState.CLOSED,
                    "DEPTH_THRESHOLD:" + depth + ">=" + link.getDepthCloseThresholdM() + "m",
                    depth, latest.getId());
        }
        return new LinkSlotState(link.getLinkCode(), slotIndex, LinkTimeState.OPEN,
                forceOpen ? "FORCED_OPEN" : "OK", depth, latest.getId());
    }
}
