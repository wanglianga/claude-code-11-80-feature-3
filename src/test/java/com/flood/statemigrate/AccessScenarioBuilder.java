package com.flood.statemigrate;

import com.flood.statemigrate.domain.CityBlock;
import com.flood.statemigrate.domain.DepthObservation;
import com.flood.statemigrate.domain.DirectionType;
import com.flood.statemigrate.domain.FacilityType;
import com.flood.statemigrate.domain.RoadKind;
import com.flood.statemigrate.domain.RoadLink;
import com.flood.statemigrate.domain.RoadNetwork;
import com.flood.statemigrate.domain.ServiceFacility;
import com.flood.statemigrate.repo.CityBlockRepo;
import com.flood.statemigrate.repo.DepthObservationRepo;
import com.flood.statemigrate.repo.RoadLinkRepo;
import com.flood.statemigrate.repo.RoadNetworkRepo;
import com.flood.statemigrate.repo.ServiceFacilityRepo;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 路网可达性测试场景：与 AccessDemoSeeder 相同的拓扑（桥+双替代通道+医院急诊阈值+缺测支路），
 * 6 个逐时时间片 T0..T5，水深有效期 90 分钟；L-BRANCH 默认不写观测（缺测）。
 */
class AccessScenarioBuilder {

    final Instant t0 = Instant.now().truncatedTo(ChronoUnit.HOURS);

    private final RoadNetworkRepo networkRepo;
    private final RoadLinkRepo linkRepo;
    private final DepthObservationRepo obsRepo;
    private final CityBlockRepo blockRepo;
    private final ServiceFacilityRepo facilityRepo;

    AccessScenarioBuilder(RoadNetworkRepo networkRepo, RoadLinkRepo linkRepo, DepthObservationRepo obsRepo,
                          CityBlockRepo blockRepo, ServiceFacilityRepo facilityRepo) {
        this.networkRepo = networkRepo;
        this.linkRepo = linkRepo;
        this.obsRepo = obsRepo;
        this.blockRepo = blockRepo;
        this.facilityRepo = facilityRepo;
    }

    RoadNetwork demoNetwork() {
        RoadNetwork network = new RoadNetwork();
        network.setNetworkCode("NET-TEST");
        network.setName("测试路网");
        network.setHorizonStart(t0);
        network.setHorizonEnd(t0.plus(5, ChronoUnit.HOURS));
        network.setSlotMinutes(60);
        network.setDepthValidityMinutes(90);
        networkRepo.save(network);
        Long nid = network.getId();

        Map<String, Integer> pop = new LinkedHashMap<>();
        pop.put("N0", 800);
        pop.put("N1", 2000);
        pop.put("N2", 1500);
        pop.put("N3", 600);
        pop.put("N4", 600);
        pop.put("N5", 1800);
        pop.put("N6", 2500);
        pop.put("N6H", 20);
        pop.put("N7", 1200);
        pop.put("N8", 900);
        pop.put("N9", 400);
        pop.put("N10", 400);
        pop.forEach((node, p) -> {
            CityBlock b = new CityBlock();
            b.setNetworkId(nid);
            b.setNodeCode(node);
            b.setName("街区-" + node);
            b.setResidentPopulation(p);
            blockRepo.save(b);
        });

        link(nid, "L-N0-N1", RoadKind.ROAD, "N0", "N1", 300, 0.30, null, false);
        link(nid, "L-N2-N1", RoadKind.ROAD, "N2", "N1", 350, 0.30, null, false);
        link(nid, "L-W-MAIN", RoadKind.ROAD, "N1", "N3", 500, 0.30, "G1", false);
        link(nid, "L-W-ALT1", RoadKind.ROAD, "N1", "N9", 300, 0.30, "G1", true);
        link(nid, "L-W-ALT2", RoadKind.ROAD, "N9", "N3", 300, 0.30, "G1", true);
        link(nid, "L-BRIDGE", RoadKind.BRIDGE, "N3", "N4", 200, 0.35, null, false);
        link(nid, "L-N4-N5", RoadKind.ROAD, "N4", "N5", 200, 0.30, null, false);
        link(nid, "L-E-MAIN", RoadKind.ROAD, "N5", "N6", 400, 0.30, "G2", false);
        link(nid, "L-E-ALT1", RoadKind.ROAD, "N5", "N10", 300, 0.30, "G2", true);
        link(nid, "L-E-ALT2", RoadKind.ROAD, "N10", "N6", 300, 0.30, "G2", true);
        link(nid, "L-N6-N7", RoadKind.ROAD, "N6", "N7", 250, 0.30, null, false);
        link(nid, "L-BRANCH", RoadKind.ROAD, "N7", "N8", 200, 0.30, null, false);
        link(nid, "L-H-ENTRY", RoadKind.ROAD, "N6", "N6H", 100, null, null, false);

        facility(nid, "FIRE-01", FacilityType.FIRE_STATION, "N0", null, null);
        facility(nid, "HOSP-01", FacilityType.HOSPITAL, "N6", "L-H-ENTRY", 0.40);
        facility(nid, "SHELTER-01", FacilityType.SHELTER, "N2", null, null);

        Map<String, double[]> profiles = new LinkedHashMap<>();
        profiles.put("L-BRIDGE", new double[]{0.10, 0.20, 0.40, 0.45, 0.30, 0.15});
        profiles.put("L-W-MAIN", new double[]{0.05, 0.15, 0.35, 0.38, 0.20, 0.10});
        profiles.put("L-E-MAIN", new double[]{0.05, 0.12, 0.33, 0.36, 0.18, 0.08});
        profiles.put("L-H-ENTRY", new double[]{0.10, 0.25, 0.38, 0.45, 0.30, 0.20});
        for (RoadLink l : linkRepo.findByNetworkIdOrderByLinkCodeAsc(nid)) {
            if ("L-BRANCH".equals(l.getLinkCode())) {
                continue;
            }
            double[] series = profiles.getOrDefault(l.getLinkCode(),
                    new double[]{0.10, 0.10, 0.10, 0.10, 0.10, 0.10});
            for (int i = 0; i < series.length; i++) {
                DepthObservation o = new DepthObservation();
                o.setNetworkId(nid);
                o.setLinkCode(l.getLinkCode());
                o.setObservedAt(t0.plus(i, ChronoUnit.HOURS));
                o.setDepthM(series[i]);
                o.setSource("SENSOR");
                obsRepo.save(o);
            }
        }
        return network;
    }

    DepthObservation depth(Long nid, String linkCode, int slot, double depthM) {
        DepthObservation o = new DepthObservation();
        o.setNetworkId(nid);
        o.setLinkCode(linkCode);
        o.setObservedAt(t0.plus(slot, ChronoUnit.HOURS));
        o.setDepthM(depthM);
        o.setSource("BACKFILL");
        return obsRepo.save(o);
    }

    private void link(Long nid, String code, RoadKind kind, String from, String to, double length,
                      Double threshold, String altGroup, boolean alternative) {
        RoadLink l = new RoadLink();
        l.setNetworkId(nid);
        l.setLinkCode(code);
        l.setName(code);
        l.setKind(kind);
        l.setDirection(DirectionType.BIDIRECTIONAL);
        l.setFromNode(from);
        l.setToNode(to);
        l.setLengthM(length);
        l.setDepthCloseThresholdM(threshold);
        l.setAltGroup(altGroup);
        l.setAlternative(alternative);
        linkRepo.save(l);
    }

    private void facility(Long nid, String code, FacilityType type, String node,
                          String entryLink, Double entryThreshold) {
        ServiceFacility f = new ServiceFacility();
        f.setNetworkId(nid);
        f.setFacilityCode(code);
        f.setName(code);
        f.setType(type);
        f.setNodeCode(node);
        f.setEntryLinkCode(entryLink);
        f.setEntryThresholdM(entryThreshold);
        facilityRepo.save(f);
    }
}
