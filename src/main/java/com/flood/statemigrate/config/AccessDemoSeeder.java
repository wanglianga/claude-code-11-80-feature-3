package com.flood.statemigrate.config;

import com.flood.statemigrate.domain.AccessImpactSnapshot;
import com.flood.statemigrate.domain.CityBlock;
import com.flood.statemigrate.domain.DepthObservation;
import com.flood.statemigrate.domain.DirectionType;
import com.flood.statemigrate.domain.FacilityType;
import com.flood.statemigrate.domain.ImpactTriggerType;
import com.flood.statemigrate.domain.RoadKind;
import com.flood.statemigrate.domain.RoadLink;
import com.flood.statemigrate.domain.RoadNetwork;
import com.flood.statemigrate.domain.ServiceFacility;
import com.flood.statemigrate.repo.CityBlockRepo;
import com.flood.statemigrate.repo.DepthObservationRepo;
import com.flood.statemigrate.repo.RoadLinkRepo;
import com.flood.statemigrate.repo.RoadNetworkRepo;
import com.flood.statemigrate.repo.ServiceFacilityRepo;
import com.flood.statemigrate.service.access.AccessImpactService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 路网可达性演示数据（网络 NET-DEMO，逐时 6 个时间片 T0..T5，水深有效期 90 分钟）。
 *
 * 拓扑：河西（N0..N3,N9）与河东（N4..N7,N10,N6H）之间唯一通道是桥梁 L-BRIDGE（0.35m 封闭）；
 * 两条主街各有一条替代环路（G1：N1-N3 主街与 N9 环路；G2：N5-N6 主街与 N10 环路）；
 * 医院急诊入口 L-H-ENTRY 另有 0.4m 阈值（路段本身不按水深封闭）；
 * 末端支路 L-BRANCH（N7-N8）全程没有水深观测，所有时间片 UNKNOWN，不得当作畅通。
 *
 * 水深过程：T2/T3 为洪峰——桥、两条主街封闭；医院急诊入口 T3 不可用（0.45m ≥ 0.4m），
 * T2 入口水深 0.38m 仍低于 0.4m 阈值、入口保持可用；T0/T1 畅通，T4/T5 退水。
 */
@Component
public class AccessDemoSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AccessDemoSeeder.class);

    private final RoadNetworkRepo networkRepo;
    private final RoadLinkRepo linkRepo;
    private final DepthObservationRepo obsRepo;
    private final CityBlockRepo blockRepo;
    private final ServiceFacilityRepo facilityRepo;
    private final AccessImpactService impactService;
    private final boolean seedEnabled;

    public AccessDemoSeeder(RoadNetworkRepo networkRepo, RoadLinkRepo linkRepo,
                            DepthObservationRepo obsRepo, CityBlockRepo blockRepo,
                            ServiceFacilityRepo facilityRepo, AccessImpactService impactService,
                            @Value("${app.seed-demo-data:true}") boolean seedEnabled) {
        this.networkRepo = networkRepo;
        this.linkRepo = linkRepo;
        this.obsRepo = obsRepo;
        this.blockRepo = blockRepo;
        this.facilityRepo = facilityRepo;
        this.impactService = impactService;
        this.seedEnabled = seedEnabled;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (!seedEnabled || networkRepo.count() > 0) {
            return;
        }
        log.info("写入路网可达性演示数据: NET-DEMO");
        Instant t0 = Instant.now().truncatedTo(ChronoUnit.HOURS);

        RoadNetwork network = new RoadNetwork();
        network.setNetworkCode("NET-DEMO");
        network.setName("示例城区积水路网（桥+双替代通道+医院急诊阈值+缺测支路）");
        network.setHorizonStart(t0);
        network.setHorizonEnd(t0.plus(5, ChronoUnit.HOURS));
        network.setSlotMinutes(60);
        network.setDepthValidityMinutes(90);
        networkRepo.save(network);
        Long nid = network.getId();

        // 街区与常住人口（孤立人口下界口径）
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
        pop.forEach((node, p) -> block(nid, node, "街区-" + node, p));

        // 路段：code, 名称, 类型, 起, 讫, 长度, 封闭阈值(m), 替代组, 是否替代
        link(nid, "L-N0-N1", "河西大街北段", RoadKind.ROAD, "N0", "N1", 300, 0.30, null, false);
        link(nid, "L-N2-N1", "避难通道", RoadKind.ROAD, "N2", "N1", 350, 0.30, null, false);
        link(nid, "L-W-MAIN", "河西主街（一号街道主通道）", RoadKind.ROAD, "N1", "N3", 500, 0.30, "G1", false);
        link(nid, "L-W-ALT1", "一号街道替代环路A段", RoadKind.ROAD, "N1", "N9", 300, 0.30, "G1", true);
        link(nid, "L-W-ALT2", "一号街道替代环路B段", RoadKind.ROAD, "N9", "N3", 300, 0.30, "G1", true);
        link(nid, "L-BRIDGE", "跨河桥（唯一东西通道）", RoadKind.BRIDGE, "N3", "N4", 200, 0.35, null, false);
        link(nid, "L-N4-N5", "河东联络线", RoadKind.ROAD, "N4", "N5", 200, 0.30, null, false);
        link(nid, "L-E-MAIN", "河东主街（二号街道主通道）", RoadKind.ROAD, "N5", "N6", 400, 0.30, "G2", false);
        link(nid, "L-E-ALT1", "二号街道替代环路A段", RoadKind.ROAD, "N5", "N10", 300, 0.30, "G2", true);
        link(nid, "L-E-ALT2", "二号街道替代环路B段", RoadKind.ROAD, "N10", "N6", 300, 0.30, "G2", true);
        link(nid, "L-N6-N7", "河东南街", RoadKind.ROAD, "N6", "N7", 250, 0.30, null, false);
        // 末端支路：无水深观测 → 全窗口 UNKNOWN
        link(nid, "L-BRANCH", "南郊支路（水深缺测）", RoadKind.ROAD, "N7", "N8", 200, 0.30, null, false);
        // 医院急诊入口：路段不按水深封闭，服务区入口阈值 0.4m 单独生效
        RoadLink entry = link(nid, "L-H-ENTRY", "医院急诊入口通道", RoadKind.ROAD, "N6", "N6H", 100, null, null, false);

        // 应急服务区
        facility(nid, "FIRE-01", "一号消防站", FacilityType.FIRE_STATION, "N0", null, null);
        facility(nid, "HOSP-01", "市中心医院", FacilityType.HOSPITAL, "N6", entry.getLinkCode(), 0.40);
        facility(nid, "SHELTER-01", "河西避难点", FacilityType.SHELTER, "N2", null, null);

        // 逐时水深（米），T0..T5；L-BRANCH  deliberately 不写任何观测
        Map<String, double[]> profiles = new LinkedHashMap<>();
        profiles.put("L-BRIDGE", new double[]{0.10, 0.20, 0.40, 0.45, 0.30, 0.15});
        profiles.put("L-W-MAIN", new double[]{0.05, 0.15, 0.35, 0.38, 0.20, 0.10});
        profiles.put("L-E-MAIN", new double[]{0.05, 0.12, 0.33, 0.36, 0.18, 0.08});
        profiles.put("L-H-ENTRY", new double[]{0.10, 0.25, 0.38, 0.45, 0.30, 0.20});
        for (RoadLink l : linkRepo.findByNetworkIdOrderByLinkCodeAsc(nid)) {
            if ("L-BRANCH".equals(l.getLinkCode())) {
                continue;
            }
            double[] series = profiles.getOrDefault(l.getLinkCode(), new double[]{0.10, 0.10, 0.10, 0.10, 0.10, 0.10});
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

        AccessImpactSnapshot v1 = impactService.computeSnapshot(network, ImpactTriggerType.INITIAL, "seed");
        log.info("路网演示数据就绪: networkId={}, 初始快照版本 v{}", nid, v1.getVersionNo());
    }

    private void block(Long nid, String node, String name, int population) {
        CityBlock b = new CityBlock();
        b.setNetworkId(nid);
        b.setNodeCode(node);
        b.setName(name);
        b.setResidentPopulation(population);
        blockRepo.save(b);
    }

    private RoadLink link(Long nid, String code, String name, RoadKind kind, String from, String to,
                          double length, Double threshold, String altGroup, boolean alternative) {
        RoadLink l = new RoadLink();
        l.setNetworkId(nid);
        l.setLinkCode(code);
        l.setName(name);
        l.setKind(kind);
        l.setDirection(DirectionType.BIDIRECTIONAL);
        l.setFromNode(from);
        l.setToNode(to);
        l.setLengthM(length);
        l.setDepthCloseThresholdM(threshold);
        l.setAltGroup(altGroup);
        l.setAlternative(alternative);
        return linkRepo.save(l);
    }

    private void facility(Long nid, String code, String name, FacilityType type, String node,
                          String entryLink, Double entryThreshold) {
        ServiceFacility f = new ServiceFacility();
        f.setNetworkId(nid);
        f.setFacilityCode(code);
        f.setName(name);
        f.setType(type);
        f.setNodeCode(node);
        f.setEntryLinkCode(entryLink);
        f.setEntryThresholdM(entryThreshold);
        facilityRepo.save(f);
    }
}
