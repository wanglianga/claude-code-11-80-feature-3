package com.flood.statemigrate.service.access;

import com.flood.statemigrate.domain.AccessImpactSnapshot;
import com.flood.statemigrate.domain.CityBlock;
import com.flood.statemigrate.domain.ClosureOverride;
import com.flood.statemigrate.domain.DepthObservation;
import com.flood.statemigrate.domain.ImpactTriggerType;
import com.flood.statemigrate.domain.RoadLink;
import com.flood.statemigrate.domain.RoadNetwork;
import com.flood.statemigrate.domain.ServiceFacility;
import com.flood.statemigrate.domain.SnapshotStatus;
import com.flood.statemigrate.error.ApiException;
import com.flood.statemigrate.repo.AccessImpactSnapshotRepo;
import com.flood.statemigrate.repo.CityBlockRepo;
import com.flood.statemigrate.repo.ClosureOverrideRepo;
import com.flood.statemigrate.repo.DepthObservationRepo;
import com.flood.statemigrate.repo.RoadLinkRepo;
import com.flood.statemigrate.repo.RoadNetworkRepo;
import com.flood.statemigrate.repo.ServiceFacilityRepo;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 路网资料与触发输入管理：路段（方向/限高限载/桥涵/替代通道/封闭阈值/有效期）、
 * 水深观测（含补测）、封路更正、街区人口、应急服务区的登记，以及影响快照版本账入口。
 */
@Service
public class AccessNetworkService {

    private final RoadNetworkRepo networkRepo;
    private final RoadLinkRepo linkRepo;
    private final DepthObservationRepo obsRepo;
    private final ClosureOverrideRepo overrideRepo;
    private final CityBlockRepo blockRepo;
    private final ServiceFacilityRepo facilityRepo;
    private final AccessImpactSnapshotRepo snapshotRepo;
    private final AccessImpactService impactService;

    public AccessNetworkService(RoadNetworkRepo networkRepo, RoadLinkRepo linkRepo,
                                DepthObservationRepo obsRepo, ClosureOverrideRepo overrideRepo,
                                CityBlockRepo blockRepo, ServiceFacilityRepo facilityRepo,
                                AccessImpactSnapshotRepo snapshotRepo, AccessImpactService impactService) {
        this.networkRepo = networkRepo;
        this.linkRepo = linkRepo;
        this.obsRepo = obsRepo;
        this.overrideRepo = overrideRepo;
        this.blockRepo = blockRepo;
        this.facilityRepo = facilityRepo;
        this.snapshotRepo = snapshotRepo;
        this.impactService = impactService;
    }

    public RoadNetwork getNetwork(Long id) {
        return networkRepo.findById(id)
                .orElseThrow(() -> ApiException.notFound("路网不存在: id=" + id));
    }

    public RoadNetwork getNetworkByCode(String code) {
        return networkRepo.findByNetworkCode(code)
                .orElseThrow(() -> ApiException.notFound("路网不存在: code=" + code));
    }

    public List<RoadNetwork> listNetworks() {
        return networkRepo.findAll();
    }

    @Transactional
    public RoadNetwork createNetwork(RoadNetwork n) {
        networkRepo.findByNetworkCode(n.getNetworkCode()).ifPresent(x -> {
            throw ApiException.conflict("路网编码已存在: " + n.getNetworkCode());
        });
        if (!n.getHorizonEnd().isAfter(n.getHorizonStart())) {
            throw ApiException.badRequest("分析窗口终点必须晚于起点");
        }
        if (n.getSlotMinutes() <= 0 || n.getDepthValidityMinutes() <= 0) {
            throw ApiException.badRequest("时间片长度与水深有效期必须为正数");
        }
        return networkRepo.save(n);
    }

    @Transactional
    public RoadLink addLink(Long networkId, RoadLink link) {
        getNetwork(networkId);
        linkRepo.findByNetworkIdAndLinkCode(networkId, link.getLinkCode()).ifPresent(x -> {
            throw ApiException.conflict("路段编码已存在: " + link.getLinkCode());
        });
        if (link.getLengthM() <= 0) {
            throw ApiException.badRequest("路段长度必须为正数");
        }
        if (link.getDepthCloseThresholdM() != null && link.getDepthCloseThresholdM() < 0) {
            throw ApiException.badRequest("积水封闭阈值不能为负");
        }
        link.setNetworkId(networkId);
        return linkRepo.save(link);
    }

    public List<RoadLink> listLinks(Long networkId) {
        getNetwork(networkId);
        return linkRepo.findByNetworkIdOrderByLinkCodeAsc(networkId);
    }

    @Transactional
    public CityBlock addBlock(Long networkId, CityBlock block) {
        getNetwork(networkId);
        block.setNetworkId(networkId);
        return blockRepo.save(block);
    }

    @Transactional
    public ServiceFacility addFacility(Long networkId, ServiceFacility facility) {
        getNetwork(networkId);
        facility.setNetworkId(networkId);
        if (facility.getEntryLinkCode() != null
                && linkRepo.findByNetworkIdAndLinkCode(networkId, facility.getEntryLinkCode()).isEmpty()) {
            throw ApiException.badRequest("入口路段不存在: " + facility.getEntryLinkCode());
        }
        return facilityRepo.save(facility);
    }

    /** 写入水深观测（普通观测）。不自动出快照，由显式重算或补测/更正触发版本账。 */
    @Transactional
    public DepthObservation addObservation(Long networkId, DepthObservation o) {
        getNetwork(networkId);
        ensureLinkExists(networkId, o.getLinkCode());
        if (o.getSource() == null || o.getSource().isBlank()) {
            o.setSource("SENSOR");
        }
        o.setNetworkId(networkId);
        return obsRepo.save(o);
    }

    /** 水深补测：写入历史时刻观测并立即生成新版本快照（DEPTH_BACKFILL）。 */
    @Transactional
    public AccessImpactSnapshot backfillDepth(Long networkId, DepthObservation o) {
        getNetwork(networkId);
        ensureLinkExists(networkId, o.getLinkCode());
        o.setSource("BACKFILL");
        o.setNetworkId(networkId);
        DepthObservation saved = obsRepo.save(o);
        return impactService.computeSnapshot(getNetwork(networkId),
                ImpactTriggerType.DEPTH_BACKFILL, "depthObservationId=" + saved.getId());
    }

    /** 补测批次：多条历史观测一次性写入，只生成一版 DEPTH_BACKFILL 快照。 */
    @Transactional
    public AccessImpactSnapshot backfillDepthBatch(Long networkId, List<DepthObservation> batch) {
        getNetwork(networkId);
        List<Long> ids = new java.util.ArrayList<>();
        for (DepthObservation o : batch) {
            ensureLinkExists(networkId, o.getLinkCode());
            o.setSource("BACKFILL");
            o.setNetworkId(networkId);
            ids.add(obsRepo.save(o).getId());
        }
        return impactService.computeSnapshot(getNetwork(networkId),
                ImpactTriggerType.DEPTH_BACKFILL, "depthObservationIds=" + ids);
    }

    /** 封路更正：写入区间覆盖并立即生成新版本快照（CLOSURE_CORRECTION）。 */
    @Transactional
    public AccessImpactSnapshot addClosureOverride(Long networkId, ClosureOverride c) {
        getNetwork(networkId);
        ensureLinkExists(networkId, c.getLinkCode());
        if (!c.getEffectiveTo().isAfter(c.getEffectiveFrom())) {
            throw ApiException.badRequest("封路更正生效终点必须晚于起点");
        }
        c.setNetworkId(networkId);
        ClosureOverride saved = overrideRepo.save(c);
        return impactService.computeSnapshot(getNetwork(networkId),
                ImpactTriggerType.CLOSURE_CORRECTION, "closureOverrideId=" + saved.getId());
    }

    /** 手动重算（首次建模用 INITIAL，之后用 MANUAL_RECOMPUTE）。 */
    @Transactional
    public AccessImpactSnapshot recompute(Long networkId, ImpactTriggerType trigger) {
        RoadNetwork network = getNetwork(networkId);
        if (linkRepo.findByNetworkIdOrderByLinkCodeAsc(networkId).isEmpty()) {
            throw ApiException.unprocessable("路网尚无路段，无法计算可达性影响");
        }
        ImpactTriggerType type = trigger != null ? trigger
                : (snapshotRepo.countByNetworkId(networkId) == 0 ? ImpactTriggerType.INITIAL
                : ImpactTriggerType.MANUAL_RECOMPUTE);
        return impactService.computeSnapshot(network, type, "manual");
    }

    public List<AccessImpactSnapshot> listSnapshots(Long networkId) {
        getNetwork(networkId);
        return snapshotRepo.findByNetworkIdOrderByVersionNoDesc(networkId);
    }

    public AccessImpactSnapshot getSnapshot(Long snapshotId) {
        return snapshotRepo.findById(snapshotId)
                .orElseThrow(() -> ApiException.notFound("影响快照不存在: id=" + snapshotId));
    }

    public AccessImpactSnapshot currentSnapshot(Long networkId) {
        getNetwork(networkId);
        return snapshotRepo.findByNetworkIdAndStatus(networkId, SnapshotStatus.CURRENT)
                .orElseThrow(() -> ApiException.notFound("路网尚无影响快照，请先触发计算"));
    }

    public List<ServiceFacility> listFacilities(Long networkId) {
        getNetwork(networkId);
        return facilityRepo.findByNetworkIdOrderByFacilityCodeAsc(networkId);
    }

    public List<DepthObservation> listObservations(Long networkId) {
        getNetwork(networkId);
        return obsRepo.findByNetworkIdOrderByObservedAtAsc(networkId);
    }

    public List<ClosureOverride> listOverrides(Long networkId) {
        getNetwork(networkId);
        return overrideRepo.findByNetworkIdOrderByIdAsc(networkId);
    }

    private void ensureLinkExists(Long networkId, String linkCode) {
        if (linkRepo.findByNetworkIdAndLinkCode(networkId, linkCode).isEmpty()) {
            throw ApiException.badRequest("路段不存在: " + linkCode);
        }
    }
}
