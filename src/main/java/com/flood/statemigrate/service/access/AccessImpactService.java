package com.flood.statemigrate.service.access;

import com.flood.statemigrate.domain.AccessImpactSnapshot;
import com.flood.statemigrate.domain.CityBlock;
import com.flood.statemigrate.domain.ClosureOverride;
import com.flood.statemigrate.domain.DepthObservation;
import com.flood.statemigrate.domain.FacilityType;
import com.flood.statemigrate.domain.ImpactTriggerType;
import com.flood.statemigrate.domain.LinkTimeState;
import com.flood.statemigrate.domain.RoadLink;
import com.flood.statemigrate.domain.RoadNetwork;
import com.flood.statemigrate.domain.ServiceFacility;
import com.flood.statemigrate.domain.SnapshotStatus;
import com.flood.statemigrate.repo.AccessImpactSnapshotRepo;
import com.flood.statemigrate.repo.CityBlockRepo;
import com.flood.statemigrate.repo.ClosureOverrideRepo;
import com.flood.statemigrate.repo.DepthObservationRepo;
import com.flood.statemigrate.repo.RoadLinkRepo;
import com.flood.statemigrate.repo.ServiceFacilityRepo;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 路网可达性影响计算与版本账。
 *
 * 计算口径：逐时间片精确最短路——每个时间片按当时通行状态独立重算 Dijkstra，
 * 可如实表达短时开闭与绕行；非 OPEN（封闭/缺测）边一律阻断，缺测支路绝不可能成为畅通或绕行证据。
 * 服务区分别给出消防站/医院/避难点的可达街区、绕行距离变化与被孤立人口下界（仅登记常住人口）。
 *
 * 版本账：每次水深补测或封路更正生成新快照，旧版本置 SUPERSEDED，并标出受影响服务点。
 * 本服务只读写路网可达性自身的表，不触碰流定义/检查点/闸门等作业迁移状态（影响图不回写迁移状态）。
 */
@Service
public class AccessImpactService {

    private final RoadLinkRepo linkRepo;
    private final DepthObservationRepo obsRepo;
    private final ClosureOverrideRepo overrideRepo;
    private final CityBlockRepo blockRepo;
    private final ServiceFacilityRepo facilityRepo;
    private final AccessImpactSnapshotRepo snapshotRepo;
    private final TimeVaryingGraphBuilder graphBuilder;
    private final ShortestPathEngine pathEngine;
    private final ObjectMapper om;

    public AccessImpactService(RoadLinkRepo linkRepo, DepthObservationRepo obsRepo,
                               ClosureOverrideRepo overrideRepo, CityBlockRepo blockRepo,
                               ServiceFacilityRepo facilityRepo, AccessImpactSnapshotRepo snapshotRepo,
                               TimeVaryingGraphBuilder graphBuilder, ShortestPathEngine pathEngine,
                               ObjectMapper om) {
        this.linkRepo = linkRepo;
        this.obsRepo = obsRepo;
        this.overrideRepo = overrideRepo;
        this.blockRepo = blockRepo;
        this.facilityRepo = facilityRepo;
        this.snapshotRepo = snapshotRepo;
        this.graphBuilder = graphBuilder;
        this.pathEngine = pathEngine;
        this.om = om;
    }

    /** 计算并落账一版新快照。triggerRef 记录触发输入（补测观测/封路更正 ID）。 */
    @Transactional
    public AccessImpactSnapshot computeSnapshot(RoadNetwork network, ImpactTriggerType trigger, String triggerRef) {
        List<RoadLink> links = linkRepo.findByNetworkIdOrderByLinkCodeAsc(network.getId());
        List<DepthObservation> observations = obsRepo.findByNetworkIdOrderByObservedAtAsc(network.getId());
        List<ClosureOverride> overrides = overrideRepo.findByNetworkIdOrderByIdAsc(network.getId());
        List<CityBlock> blocks = blockRepo.findByNetworkIdOrderByNodeCodeAsc(network.getId());
        List<ServiceFacility> facilities = facilityRepo.findByNetworkIdOrderByFacilityCodeAsc(network.getId());

        TimeVaryingGraphBuilder.TimeVaryingGraph graph =
                graphBuilder.build(network, links, observations, overrides);
        NetworkTopology topology = new NetworkTopology(links);
        Map<String, Integer> population = new HashMap<>();
        for (CityBlock b : blocks) {
            population.put(b.getNodeCode(), b.getResidentPopulation());
        }

        Map<String, Object> graphJson = buildGraphJson(graph, links);
        Map<String, Object> impactJson = buildImpactJson(graph, topology, facilities, population);

        AccessImpactSnapshot previous = snapshotRepo
                .findTopByNetworkIdOrderByVersionNoDesc(network.getId()).orElse(null);
        List<String> affected = diffAffectedFacilities(previous, impactJson);
        List<String> unknownLinks = collectUnknownLinks(graph);

        if (previous != null) {
            previous.setStatus(SnapshotStatus.SUPERSEDED);
            snapshotRepo.save(previous);
        }
        AccessImpactSnapshot snapshot = new AccessImpactSnapshot();
        snapshot.setNetworkId(network.getId());
        snapshot.setVersionNo(previous == null ? 1 : previous.getVersionNo() + 1);
        snapshot.setStatus(SnapshotStatus.CURRENT);
        snapshot.setTriggerType(trigger);
        snapshot.setTriggerRef(triggerRef);
        snapshot.setBaselineVersionId(previous == null ? null : previous.getId());
        snapshot.setHorizonStart(network.getHorizonStart());
        snapshot.setHorizonEnd(network.getHorizonEnd());
        snapshot.setSlotMinutes(network.getSlotMinutes());
        snapshot.setTimeVaryingGraphJson(writeJson(graphJson));
        snapshot.setServiceImpactJson(writeJson(impactJson));
        snapshot.setAffectedFacilityCodesJson(writeJson(affected));
        snapshot.setUnknownLinkCodesJson(writeJson(unknownLinks));
        snapshot.setInputRefsJson(writeJson(Map.of(
                "depthObservationIds", graph.observationIds(),
                "closureOverrideIds", graph.overrideIds())));
        return snapshotRepo.save(snapshot);
    }

    private Map<String, Object> buildGraphJson(TimeVaryingGraphBuilder.TimeVaryingGraph graph,
                                               List<RoadLink> links) {
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("slotMinutes", graph.network().getSlotMinutes());
        root.put("slots", graph.slotTimes().stream().map(Instant::toString).toList());
        List<Map<String, Object>> linkArr = new ArrayList<>();
        for (RoadLink link : links) {
            Map<String, Object> l = new LinkedHashMap<>();
            l.put("linkCode", link.getLinkCode());
            l.put("name", link.getName());
            l.put("kind", link.getKind().name());
            l.put("direction", link.getDirection().name());
            l.put("fromNode", link.getFromNode());
            l.put("toNode", link.getToNode());
            l.put("lengthM", link.getLengthM());
            l.put("heightLimitM", link.getHeightLimitM());
            l.put("weightLimitT", link.getWeightLimitT());
            l.put("depthCloseThresholdM", link.getDepthCloseThresholdM());
            l.put("altGroup", link.getAltGroup());
            l.put("alternative", link.isAlternative());
            l.put("slots", graph.linkStates().get(link.getLinkCode()).stream().map(s -> {
                Map<String, Object> slot = new LinkedHashMap<>();
                slot.put("slot", s.slotIndex());
                slot.put("state", s.state().name());
                slot.put("reason", s.reason());
                slot.put("depthM", s.depthM());
                slot.put("observationId", s.observationId());
                return slot;
            }).toList());
            linkArr.add(l);
        }
        root.put("links", linkArr);
        return root;
    }

    private Map<String, Object> buildImpactJson(TimeVaryingGraphBuilder.TimeVaryingGraph graph,
                                                NetworkTopology topology, List<ServiceFacility> facilities,
                                                Map<String, Integer> population) {
        int slotCount = graph.slotTimes().size();
        List<Map<String, Object>> facilityArr = new ArrayList<>();
        Map<FacilityType, int[]> typeIsolationMax = new HashMap<>();
        Map<FacilityType, Integer> typeFacilityCount = new HashMap<>();

        for (ServiceFacility f : facilities) {
            typeFacilityCount.merge(f.getType(), 1, Integer::sum);
            ShortestPathEngine.ShortestTree baseline = pathEngine.baselineTree(topology, f.getNodeCode());
            Set<String> baselineBlocks = new LinkedHashSet<>(baseline.reachableNodes());

            Map<String, Object> fj = new LinkedHashMap<>();
            fj.put("facilityCode", f.getFacilityCode());
            fj.put("name", f.getName());
            fj.put("type", f.getType().name());
            fj.put("nodeCode", f.getNodeCode());
            fj.put("entryLinkCode", f.getEntryLinkCode());
            fj.put("entryThresholdM", f.getEntryThresholdM());

            List<Map<String, Object>> entryClosures = new ArrayList<>();
            List<Map<String, Object>> slotArr = new ArrayList<>();
            Set<String> everIsolated = new LinkedHashSet<>();
            int worstIsolated = 0;
            int affectedSlots = 0;

            for (int slot = 0; slot < slotCount; slot++) {
                Set<String> blocked = blockedEdgeIds(graph, topology, slot);
                String entryReason = entryUnavailableReason(f, graph, slot);
                if (entryReason != null) {
                    entryClosures.add(Map.of("slot", slot, "reason", entryReason));
                }

                ShortestPathEngine.ShortestTree current =
                        entryReason == null ? pathEngine.shortestTree(topology, f.getNodeCode(), blocked) : null;
                // 入口封闭时服务区无法对外服务，但仍位于自身节点：可达集退化为仅自身
                Set<String> reachable = current == null ? Set.of(f.getNodeCode()) : current.reachableNodes();

                List<String> isolatedBlocks = new ArrayList<>();
                int isolatedPop = 0;
                List<Map<String, Object>> isolatedDetail = new ArrayList<>();
                for (String node : baselineBlocks) {
                    if (!reachable.contains(node)) {
                        isolatedBlocks.add(node);
                        everIsolated.add(node);
                        isolatedPop += population.getOrDefault(node, 0);
                        Map<String, Object> d = new LinkedHashMap<>();
                        d.put("block", node);
                        d.put("baselineDistanceM", baseline.distanceTo(node));
                        isolatedDetail.add(d);
                    }
                }

                List<Map<String, Object>> detours = new ArrayList<>();
                double totalDelta = 0;
                double maxDelta = 0;
                if (current != null) {
                    for (String node : baselineBlocks) {
                        if (!reachable.contains(node)) {
                            continue;
                        }
                        double baseD = baseline.distanceTo(node);
                        double curD = current.distanceTo(node);
                        double delta = Math.round((curD - baseD) * 100.0) / 100.0;
                        if (delta > 0.01) {
                            ShortestPathEngine.PathResult path = current.pathTo(node);
                            Map<String, Object> d = new LinkedHashMap<>();
                            d.put("block", node);
                            d.put("baselineDistanceM", baseD);
                            d.put("currentDistanceM", curD);
                            d.put("deltaM", delta);
                            d.put("path", path.nodePath());
                            detours.add(d);
                            totalDelta += delta;
                            maxDelta = Math.max(maxDelta, delta);
                        }
                    }
                }
                detours.sort(Comparator.comparing(a -> (String) a.get("block")));

                if (!isolatedBlocks.isEmpty() || entryReason != null) {
                    affectedSlots++;
                }
                worstIsolated = Math.max(worstIsolated, isolatedPop);

                Map<String, Object> sj = new LinkedHashMap<>();
                sj.put("slot", slot);
                sj.put("time", graph.slotTimes().get(slot).toString());
                sj.put("entryAvailable", entryReason == null);
                sj.put("reachableBlocks", new ArrayList<>(reachable));
                sj.put("reachableCount", reachable.size());
                sj.put("isolatedBlocks", isolatedDetail);
                sj.put("isolatedPopulationLowerBound", isolatedPop);
                sj.put("detours", detours);
                sj.put("totalDetourDeltaM", Math.round(totalDelta * 100.0) / 100.0);
                sj.put("maxDetourDeltaM", Math.round(maxDelta * 100.0) / 100.0);
                slotArr.add(sj);
            }

            fj.put("entryClosures", entryClosures);
            fj.put("slots", slotArr);
            fj.put("everIsolatedBlocks", new ArrayList<>(everIsolated));
            fj.put("worstIsolatedPopulationLowerBound", worstIsolated);
            fj.put("affectedSlotCount", affectedSlots);
            facilityArr.add(fj);
            typeIsolationMax.computeIfAbsent(f.getType(), k -> new int[1])[0] =
                    Math.max(typeIsolationMax.getOrDefault(f.getType(), new int[1])[0], worstIsolated);
        }

        List<Map<String, Object>> byType = new ArrayList<>();
        for (FacilityType t : FacilityType.values()) {
            if (!typeFacilityCount.containsKey(t)) {
                continue;
            }
            Map<String, Object> tj = new LinkedHashMap<>();
            tj.put("type", t.name());
            tj.put("facilityCount", typeFacilityCount.get(t));
            tj.put("maxIsolatedPopulationLowerBound",
                    typeIsolationMax.getOrDefault(t, new int[1])[0]);
            byType.add(tj);
        }

        Map<String, Object> root = new LinkedHashMap<>();
        root.put("facilities", facilityArr);
        root.put("byType", byType);
        return root;
    }

    /** 某时间片所有非 OPEN 路段对应的有向边 ID（含双向） */
    private Set<String> blockedEdgeIds(TimeVaryingGraphBuilder.TimeVaryingGraph graph,
                                       NetworkTopology topology, int slot) {
        Set<String> blocked = new LinkedHashSet<>();
        for (NetworkTopology.DirectedEdge e : topology.edges()) {
            LinkSlotState s = graph.state(e.link().getLinkCode(), slot);
            if (s.state() != LinkTimeState.OPEN) {
                blocked.add(e.edgeId());
            }
        }
        return blocked;
    }

    /** 服务区入口在该时间片不可用的原因；可用返回 null。入口缺测同样判为不可用。 */
    private String entryUnavailableReason(ServiceFacility f,
                                          TimeVaryingGraphBuilder.TimeVaryingGraph graph, int slot) {
        if (f.getEntryLinkCode() == null) {
            return null;
        }
        List<LinkSlotState> states = graph.linkStates().get(f.getEntryLinkCode());
        if (states == null) {
            return "ENTRY_LINK_UNKNOWN";
        }
        LinkSlotState s = states.get(slot);
        if (s.state() != LinkTimeState.OPEN) {
            return "ENTRY_" + s.state() + ":" + s.reason();
        }
        if (f.getEntryThresholdM() != null && s.depthM() != null && s.depthM() >= f.getEntryThresholdM()) {
            return "ENTRY_DEPTH_THRESHOLD:" + s.depthM() + ">=" + f.getEntryThresholdM() + "m";
        }
        return null;
    }

    private List<String> collectUnknownLinks(TimeVaryingGraphBuilder.TimeVaryingGraph graph) {
        Set<String> unknown = new LinkedHashSet<>();
        graph.linkStates().forEach((code, states) -> {
            if (states.stream().anyMatch(s -> s.state() == LinkTimeState.UNKNOWN)) {
                unknown.add(code);
            }
        });
        return new ArrayList<>(unknown);
    }

    /** 与上一版本逐服务点比对：可达街区、孤立人口或绕行距离发生变化即标记为受影响。 */
    @SuppressWarnings("unchecked")
    private List<String> diffAffectedFacilities(AccessImpactSnapshot previous, Map<String, Object> newImpact) {
        List<String> affected = new ArrayList<>();
        Map<String, Object> newByCode = new LinkedHashMap<>();
        for (Map<String, Object> f : (List<Map<String, Object>>) newImpact.get("facilities")) {
            newByCode.put((String) f.get("facilityCode"), f);
        }
        if (previous == null) {
            affected.addAll(newByCode.keySet());
            return affected;
        }
        Map<String, Object> oldImpact = readJson(previous.getServiceImpactJson(), Map.class);
        Map<String, Object> oldByCode = new LinkedHashMap<>();
        for (Map<String, Object> f : (List<Map<String, Object>>) oldImpact.get("facilities")) {
            oldByCode.put((String) f.get("facilityCode"), f);
        }
        for (Map.Entry<String, Object> e : newByCode.entrySet()) {
            Map<String, Object> nf = (Map<String, Object>) e.getValue();
            Map<String, Object> of = (Map<String, Object>) oldByCode.get(e.getKey());
            if (of == null || !of.get("slots").equals(nf.get("slots"))
                    || !of.get("entryClosures").equals(nf.get("entryClosures"))
                    || !of.get("worstIsolatedPopulationLowerBound")
                            .equals(nf.get("worstIsolatedPopulationLowerBound"))) {
                affected.add(e.getKey());
            }
        }
        return affected;
    }

    private String writeJson(Object o) {
        try {
            return om.writeValueAsString(o);
        } catch (Exception e) {
            throw new IllegalStateException("序列化影响结果失败: " + e.getMessage(), e);
        }
    }

    private <T> T readJson(String json, Class<T> type) {
        try {
            return om.readValue(json, type);
        } catch (Exception e) {
            throw new IllegalStateException("解析历史快照失败: " + e.getMessage(), e);
        }
    }
}
