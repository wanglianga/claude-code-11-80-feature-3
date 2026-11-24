package com.flood.statemigrate;

import com.flood.statemigrate.domain.AccessImpactSnapshot;
import com.flood.statemigrate.domain.ClosureOverride;
import com.flood.statemigrate.domain.DepthObservation;
import com.flood.statemigrate.domain.ImpactTriggerType;
import com.flood.statemigrate.domain.LinkTimeState;
import com.flood.statemigrate.domain.RoadNetwork;
import com.flood.statemigrate.domain.SnapshotStatus;
import com.flood.statemigrate.repo.AccessImpactSnapshotRepo;
import com.flood.statemigrate.repo.CityBlockRepo;
import com.flood.statemigrate.repo.ClosureOverrideRepo;
import com.flood.statemigrate.repo.DepthObservationRepo;
import com.flood.statemigrate.repo.RoadLinkRepo;
import com.flood.statemigrate.repo.RoadNetworkRepo;
import com.flood.statemigrate.repo.ServiceFacilityRepo;
import com.flood.statemigrate.service.access.AccessImpactService;
import com.flood.statemigrate.service.access.AccessNetworkService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 路网可达性影响集成测试：
 * 桥关闭前后孤立片区与绕行距离、缺测支路不得当畅通、医院急诊 0.4m 入口阈值、
 * 水深补测/封路更正的版本账，以及影响图不回写作业迁移状态。
 */
class AccessImpactIntegrationTest extends BaseIntegrationTest {

    @Autowired private RoadNetworkRepo networkRepo;
    @Autowired private RoadLinkRepo linkRepo;
    @Autowired private DepthObservationRepo obsRepo;
    @Autowired private CityBlockRepo blockRepo;
    @Autowired private ServiceFacilityRepo facilityRepo;
    @Autowired private ClosureOverrideRepo overrideRepo;
    @Autowired private AccessImpactSnapshotRepo snapshotRepo;
    @Autowired private AccessImpactService impactService;
    @Autowired private AccessNetworkService networkService;
    @Autowired private ObjectMapper om;

    private AccessScenarioBuilder builder;
    private RoadNetwork network;

    @BeforeEach
    void initScenario() {
        builder = new AccessScenarioBuilder(networkRepo, linkRepo, obsRepo, blockRepo, facilityRepo);
        network = builder.demoNetwork();
    }

    private JsonNode tree(String json) {
        try {
            return om.readTree(json);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private JsonNode facility(JsonNode impact, String code) {
        for (JsonNode f : impact.path("facilities")) {
            if (code.equals(f.path("facilityCode").asText())) {
                return f;
            }
        }
        fail("找不到服务区: " + code);
        return null;
    }

    private JsonNode slot(JsonNode facilityNode, int slot) {
        for (JsonNode s : facilityNode.path("slots")) {
            if (s.path("slot").asInt() == slot) {
                return s;
            }
        }
        fail("找不到时间片: " + slot);
        return null;
    }

    private boolean reachable(JsonNode slotNode, String block) {
        for (JsonNode b : slotNode.path("reachableBlocks")) {
            if (block.equals(b.asText())) {
                return true;
            }
        }
        return false;
    }

    private JsonNode detour(JsonNode slotNode, String block) {
        for (JsonNode d : slotNode.path("detours")) {
            if (block.equals(d.path("block").asText())) {
                return d;
            }
        }
        return null;
    }

    @Test
    void bridgeClosureIsolatesEastDistrictAndForcesWestDetour() {
        AccessImpactSnapshot v1 = impactService.computeSnapshot(network, ImpactTriggerType.INITIAL, "test");
        JsonNode impact = tree(v1.getServiceImpactJson());
        JsonNode fire = facility(impact, "FIRE-01");

        // T1：桥畅通（0.20m < 0.35m），河东街区全部可达，N8 因支路缺测始终不可达
        JsonNode s1 = slot(fire, 1);
        for (String east : List.of("N4", "N5", "N6", "N7", "N10", "N6H")) {
            assertTrue(reachable(s1, east), "T1 桥畅通时 " + east + " 应可达");
        }
        assertFalse(reachable(s1, "N8"), "缺测支路 N8 不得当作可达");
        assertEquals(900, s1.path("isolatedPopulationLowerBound").asInt(), "T1 仅 N8（900 人）孤立");

        // T2：桥 0.40m ≥ 0.35m 封闭，河东整体被孤立
        JsonNode s2 = slot(fire, 2);
        for (String east : List.of("N4", "N5", "N6", "N7", "N10", "N6H")) {
            assertFalse(reachable(s2, east), "T2 桥封闭后 " + east + " 应被孤立");
        }
        int eastPopulation = 600 + 1800 + 2500 + 1200 + 400 + 20; // N4,N5,N6,N7,N10,N6H
        assertEquals(eastPopulation + 900, s2.path("isolatedPopulationLowerBound").asInt(),
                "被孤立人口下界=河东常住人口+缺测支路 N8");

        // T2：河西主街封闭，一号街道改走 N9 替代环路，N3 仍可达但绕行 +100m
        assertTrue(reachable(s2, "N3"), "替代环路保证 N3 仍可达");
        JsonNode n3Detour = detour(s2, "N3");
        assertNotNull(n3Detour, "N3 应出现绕行距离变化");
        assertEquals(100.0, n3Detour.path("deltaM").asDouble(), 0.01);
        boolean viaN9 = false;
        for (JsonNode n : n3Detour.path("path")) {
            if ("N9".equals(n.asText())) {
                viaN9 = true;
            }
        }
        assertTrue(viaN9, "绕行路径必须经过替代环路节点 N9");

        // T4 退水（桥 0.30m < 0.35m），河东恢复可达，孤立回到仅 N8
        JsonNode s4 = slot(fire, 4);
        assertTrue(reachable(s4, "N4"), "T4 退水后河东恢复可达");
        assertEquals(900, s4.path("isolatedPopulationLowerBound").asInt());
    }

    @Test
    void secondAlternativeGroupDetoursAroundEastMainStreet() {
        AccessImpactSnapshot v1 = impactService.computeSnapshot(network, ImpactTriggerType.INITIAL, "test");
        JsonNode hospital = facility(tree(v1.getServiceImpactJson()), "HOSP-01");

        // T2 河东主街封闭：医院到 N5 改走 N10 替代环路，基线 400m → 600m，绕行 +200m
        JsonNode s2 = slot(hospital, 2);
        JsonNode n5 = detour(s2, "N5");
        assertNotNull(n5);
        assertEquals(200.0, n5.path("deltaM").asDouble(), 0.01);
        boolean viaN10 = false;
        for (JsonNode n : n5.path("path")) {
            if ("N10".equals(n.asText())) {
                viaN10 = true;
            }
        }
        assertTrue(viaN10, "二号街道绕行必须经过 N10");
    }

    @Test
    void missingDepthBranchIsUnknownAndNeverTreatedAsOpen() {
        AccessImpactSnapshot v1 = impactService.computeSnapshot(network, ImpactTriggerType.INITIAL, "test");
        JsonNode graph = tree(v1.getTimeVaryingGraphJson());

        // 时变通行图中 L-BRANCH 六个时间片全部 UNKNOWN
        for (JsonNode link : graph.path("links")) {
            if ("L-BRANCH".equals(link.path("linkCode").asText())) {
                for (JsonNode s : link.path("slots")) {
                    assertEquals(LinkTimeState.UNKNOWN.name(), s.path("state").asText());
                }
            }
        }
        JsonNode unknown = tree(v1.getUnknownLinkCodesJson());
        boolean branchListed = false;
        for (JsonNode c : unknown) {
            if ("L-BRANCH".equals(c.asText())) {
                branchListed = true;
            }
        }
        assertTrue(branchListed, "缺测路段必须进入 unknownLinkCodes");

        // 所有服务区、所有时间片，N8 都不可达
        JsonNode impact = tree(v1.getServiceImpactJson());
        for (JsonNode f : impact.path("facilities")) {
            for (JsonNode s : f.path("slots")) {
                assertFalse(reachable(s, "N8"), f.path("facilityCode").asText() + " 不得经缺测支路到达 N8");
            }
        }
    }

    @Test
    void hospitalEmergencyEntryHasIndependent04Threshold() {
        AccessImpactSnapshot v1 = impactService.computeSnapshot(network, ImpactTriggerType.INITIAL, "test");
        JsonNode hospital = facility(tree(v1.getServiceImpactJson()), "HOSP-01");

        List<Integer> closedSlots = new ArrayList<>();
        for (JsonNode e : hospital.path("entryClosures")) {
            closedSlots.add(e.path("slot").asInt());
            assertTrue(e.path("reason").asText().contains("ENTRY_DEPTH_THRESHOLD"),
                    "入口封闭原因应指向急诊 0.4m 阈值: " + e.path("reason").asText());
        }
        assertEquals(List.of(3), closedSlots, "仅 T3 入口水深 0.45m ≥ 0.4m；T2 的 0.38m 仍低于阈值");

        // T3 入口封闭，医院对外服务中断（只剩自身节点）
        assertEquals(1, slot(hospital, 3).path("reachableCount").asInt());
        assertFalse(slot(hospital, 3).path("entryAvailable").asBoolean());
        // T2 入口水深 0.38m < 0.4m，入口仍可用（可经替代环路对外服务）
        assertTrue(slot(hospital, 2).path("entryAvailable").asBoolean());
        // T1 水深 0.25m < 0.4m，入口可用
        assertTrue(slot(hospital, 1).path("entryAvailable").asBoolean());
    }

    @Test
    void depthBackfillCreatesNewSnapshotVersionAndMarksAffectedFacilities() {
        AccessImpactSnapshot v1 = impactService.computeSnapshot(network, ImpactTriggerType.INITIAL, "test");

        // 补测缺测支路 L-BRANCH 六个时间片的水深（0.05m，低于 0.30m 阈值）
        for (int i = 0; i < 6; i++) {
            DepthObservation o = new DepthObservation();
            o.setNetworkId(network.getId());
            o.setLinkCode("L-BRANCH");
            o.setObservedAt(builder.t0.plus(i, ChronoUnit.HOURS));
            o.setDepthM(0.05);
            o.setSource("BACKFILL");
            obsRepo.save(o);
        }
        AccessImpactSnapshot v2 = impactService.computeSnapshot(network,
                ImpactTriggerType.DEPTH_BACKFILL, "backfill:L-BRANCH");

        assertEquals(2, v2.getVersionNo());
        assertEquals(SnapshotStatus.CURRENT, v2.getStatus());
        assertEquals(SnapshotStatus.SUPERSEDED, snapshotRepo.findById(v1.getId()).orElseThrow().getStatus());
        assertEquals(ImpactTriggerType.DEPTH_BACKFILL, v2.getTriggerType());
        assertEquals(v1.getId(), v2.getBaselineVersionId());

        // 补测后 L-BRANCH 不再缺测，N8 变为可达 → 消防站与避难点受影响
        JsonNode affected = tree(v2.getAffectedFacilityCodesJson());
        List<String> codes = new ArrayList<>();
        affected.forEach(c -> codes.add(c.asText()));
        assertTrue(codes.contains("FIRE-01"));
        JsonNode unknown = tree(v2.getUnknownLinkCodesJson());
        assertEquals(0, unknown.size(), "补测后不应再有缺测路段");

        JsonNode fire = facility(tree(v2.getServiceImpactJson()), "FIRE-01");
        assertTrue(reachable(slot(fire, 1), "N8"), "补测畅通后 N8 应可达");
    }

    @Test
    void depthBackfillBatchProducesExactlyOneNewVersion() {
        impactService.computeSnapshot(network, ImpactTriggerType.INITIAL, "test");
        List<DepthObservation> batch = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            DepthObservation o = new DepthObservation();
            o.setLinkCode("L-BRANCH");
            o.setObservedAt(builder.t0.plus(i, ChronoUnit.HOURS));
            o.setDepthM(0.05);
            batch.add(o);
        }
        AccessImpactSnapshot v2 = networkService.backfillDepthBatch(network.getId(), batch);
        assertEquals(2, v2.getVersionNo(), "整批补测只生成一版新快照");
        assertEquals(2, snapshotRepo.countByNetworkId(network.getId()), "历史版本保留：v1+SUPERSEDED 与 v2+CURRENT");
        assertEquals(1, snapshotRepo.findByNetworkIdAndStatus(network.getId(), SnapshotStatus.CURRENT).stream().count());
        assertEquals(0, tree(v2.getUnknownLinkCodesJson()).size());
    }

    @Test
    void closureCorrectionCreatesVersionAndCannotMaskMissingDepth() {
        impactService.computeSnapshot(network, ImpactTriggerType.INITIAL, "test");

        // 封路更正：T2..T3 强制封闭 L-N0-N1（消防站出门唯一通道）
        ClosureOverride c = new ClosureOverride();
        c.setNetworkId(network.getId());
        c.setLinkCode("L-N0-N1");
        c.setClosed(true);
        c.setEffectiveFrom(builder.t0.plus(2, ChronoUnit.HOURS));
        c.setEffectiveTo(builder.t0.plus(3, ChronoUnit.HOURS));
        c.setReason("权威渠道封路更正");
        AccessImpactSnapshot v2 = networkService.addClosureOverride(network.getId(), c);

        assertEquals(ImpactTriggerType.CLOSURE_CORRECTION, v2.getTriggerType());
        JsonNode affected = tree(v2.getAffectedFacilityCodesJson());
        List<String> codes = new ArrayList<>();
        affected.forEach(x -> codes.add(x.asText()));
        assertTrue(codes.contains("FIRE-01"), "封路更正应标出受影响消防站");

        JsonNode fire = facility(tree(v2.getServiceImpactJson()), "FIRE-01");
        assertEquals(1, slot(fire, 2).path("reachableCount").asInt(),
                "唯一出门通道封闭后消防站只能到达自身节点，无法服务任何街区");

        // 即使对缺测支路下发“人工放行”，缺测仍按 UNKNOWN，N8 依旧不可达
        ClosureOverride forceOpen = new ClosureOverride();
        forceOpen.setNetworkId(network.getId());
        forceOpen.setLinkCode("L-BRANCH");
        forceOpen.setClosed(false);
        forceOpen.setEffectiveFrom(builder.t0);
        forceOpen.setEffectiveTo(builder.t0.plus(5, ChronoUnit.HOURS));
        AccessImpactSnapshot v3 = networkService.addClosureOverride(network.getId(), forceOpen);
        JsonNode graph = tree(v3.getTimeVaryingGraphJson());
        for (JsonNode link : graph.path("links")) {
            if ("L-BRANCH".equals(link.path("linkCode").asText())) {
                for (JsonNode s : link.path("slots")) {
                    assertEquals(LinkTimeState.UNKNOWN.name(), s.path("state").asText(),
                            "人工放行不得掩盖水深缺测");
                }
            }
        }
    }

    @Test
    void expiredObservationBecomesUnknownBoundary() {
        // 自定义小网络：只有 T0 一条观测，有效期 60 分钟；T2 最近观测已 120 分钟 → UNKNOWN
        RoadNetwork small = new RoadNetwork();
        small.setNetworkCode("NET-EXPIRY");
        small.setName("有效期边界测试");
        small.setHorizonStart(builder.t0);
        small.setHorizonEnd(builder.t0.plus(3, ChronoUnit.HOURS));
        small.setSlotMinutes(60);
        small.setDepthValidityMinutes(60);
        networkRepo.save(small);

        saveLink(small, "L1", "A", "B", 0.30);
        DepthObservation o = new DepthObservation();
        o.setNetworkId(small.getId());
        o.setLinkCode("L1");
        o.setObservedAt(builder.t0);
        o.setDepthM(0.10);
        o.setSource("SENSOR");
        obsRepo.save(o);

        AccessImpactSnapshot snap = impactService.computeSnapshot(small, ImpactTriggerType.INITIAL, "expiry");
        JsonNode graph = tree(snap.getTimeVaryingGraphJson());
        JsonNode l1 = graph.path("links").get(0);
        assertEquals(LinkTimeState.OPEN.name(), l1.path("slots").get(0).path("state").asText());
        assertEquals(LinkTimeState.OPEN.name(), l1.path("slots").get(1).path("state").asText(), "60 分钟仍在有效期内");
        assertEquals(LinkTimeState.UNKNOWN.name(), l1.path("slots").get(2).path("state").asText(),
                "120 分钟超出有效期，按缺测处理");
        assertTrue(l1.path("slots").get(2).path("reason").asText().startsWith("DEPTH_EXPIRED"));
    }

    @Test
    void impactGraphDoesNotWriteBackMigrationState() {
        impactService.computeSnapshot(network, ImpactTriggerType.INITIAL, "test");
        // 全程未登记任何作业迁移流：影响计算不得创建或修改流/检查点/闸门状态
        assertEquals(0, streamRepo.count(), "影响图不得回写作业迁移流状态");
        assertEquals(0, checkpointRepo.count());
        assertEquals(0, gateRepo.count());
        assertEquals(0, evidenceRepo.count());
        assertTrue(snapshotRepo.countByNetworkId(network.getId()) >= 1, "影响快照应独立落账");
    }

    private void saveLink(RoadNetwork net, String code, String from, String to, double threshold) {
        com.flood.statemigrate.domain.RoadLink l = new com.flood.statemigrate.domain.RoadLink();
        l.setNetworkId(net.getId());
        l.setLinkCode(code);
        l.setName(code);
        l.setKind(com.flood.statemigrate.domain.RoadKind.ROAD);
        l.setDirection(com.flood.statemigrate.domain.DirectionType.BIDIRECTIONAL);
        l.setFromNode(from);
        l.setToNode(to);
        l.setLengthM(100);
        l.setDepthCloseThresholdM(threshold);
        l.setAlternative(false);
        linkRepo.save(l);
    }
}
