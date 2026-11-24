package com.flood.statemigrate.web;

import com.flood.statemigrate.domain.AccessImpactSnapshot;
import com.flood.statemigrate.domain.CityBlock;
import com.flood.statemigrate.domain.ClosureOverride;
import com.flood.statemigrate.domain.DepthObservation;
import com.flood.statemigrate.domain.RoadLink;
import com.flood.statemigrate.domain.RoadNetwork;
import com.flood.statemigrate.domain.ServiceFacility;
import com.flood.statemigrate.service.access.AccessNetworkService;
import com.flood.statemigrate.web.dto.BlockReq;
import com.flood.statemigrate.web.dto.ClosureOverrideReq;
import com.flood.statemigrate.web.dto.DepthBatchReq;
import com.flood.statemigrate.web.dto.DepthObsReq;
import com.flood.statemigrate.web.dto.FacilityReq;
import com.flood.statemigrate.web.dto.LinkReq;
import com.flood.statemigrate.web.dto.NetworkReq;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "路网可达性影响")
@RestController
@RequestMapping("/api/access/networks")
public class AccessNetworkController {

    private final AccessNetworkService service;

    public AccessNetworkController(AccessNetworkService service) {
        this.service = service;
    }

    @Operation(summary = "登记路网（分析时间轴、时间片长度、水深有效期）")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public RoadNetwork createNetwork(@Valid @RequestBody NetworkReq req) {
        RoadNetwork n = new RoadNetwork();
        n.setNetworkCode(req.networkCode());
        n.setName(req.name());
        n.setHorizonStart(req.horizonStart());
        n.setHorizonEnd(req.horizonEnd());
        n.setSlotMinutes(req.slotMinutes());
        n.setDepthValidityMinutes(req.depthValidityMinutes());
        return service.createNetwork(n);
    }

    @Operation(summary = "路网列表")
    @GetMapping
    public List<RoadNetwork> listNetworks() {
        return service.listNetworks();
    }

    @Operation(summary = "路网详情")
    @GetMapping("/{id}")
    public RoadNetwork getNetwork(@PathVariable Long id) {
        return service.getNetwork(id);
    }

    @Operation(summary = "登记路段：方向、限高/限载、桥涵、替代通道、封闭阈值与状态有效期")
    @PostMapping("/{id}/links")
    @ResponseStatus(HttpStatus.CREATED)
    public RoadLink addLink(@PathVariable Long id, @Valid @RequestBody LinkReq req) {
        RoadLink l = new RoadLink();
        l.setLinkCode(req.linkCode());
        l.setName(req.name());
        l.setKind(req.kind());
        l.setDirection(req.direction());
        l.setFromNode(req.fromNode());
        l.setToNode(req.toNode());
        l.setLengthM(req.lengthM());
        l.setHeightLimitM(req.heightLimitM());
        l.setWeightLimitT(req.weightLimitT());
        l.setDepthCloseThresholdM(req.depthCloseThresholdM());
        l.setAltGroup(req.altGroup());
        l.setAlternative(Boolean.TRUE.equals(req.alternative()));
        l.setValidFrom(req.validFrom());
        l.setValidTo(req.validTo());
        return service.addLink(id, l);
    }

    @Operation(summary = "路段列表")
    @GetMapping("/{id}/links")
    public List<RoadLink> listLinks(@PathVariable Long id) {
        return service.listLinks(id);
    }

    @Operation(summary = "登记街区（图节点）与常住人口（孤立人口下界口径）")
    @PostMapping("/{id}/blocks")
    @ResponseStatus(HttpStatus.CREATED)
    public CityBlock addBlock(@PathVariable Long id, @Valid @RequestBody BlockReq req) {
        CityBlock b = new CityBlock();
        b.setNodeCode(req.nodeCode());
        b.setName(req.name());
        b.setResidentPopulation(req.residentPopulation());
        return service.addBlock(id, b);
    }

    @Operation(summary = "登记应急服务区（消防站/医院/避难点，含入口独立阈值）")
    @PostMapping("/{id}/facilities")
    @ResponseStatus(HttpStatus.CREATED)
    public ServiceFacility addFacility(@PathVariable Long id, @Valid @RequestBody FacilityReq req) {
        ServiceFacility f = new ServiceFacility();
        f.setFacilityCode(req.facilityCode());
        f.setName(req.name());
        f.setType(req.type());
        f.setNodeCode(req.nodeCode());
        f.setEntryLinkCode(req.entryLinkCode());
        f.setEntryThresholdM(req.entryThresholdM());
        return service.addFacility(id, f);
    }

    @Operation(summary = "服务区列表")
    @GetMapping("/{id}/facilities")
    public List<ServiceFacility> listFacilities(@PathVariable Long id) {
        return service.listFacilities(id);
    }

    @Operation(summary = "写入水深观测（不自动出快照）")
    @PostMapping("/{id}/depths")
    @ResponseStatus(HttpStatus.CREATED)
    public DepthObservation addDepth(@PathVariable Long id, @Valid @RequestBody DepthObsReq req) {
        DepthObservation o = new DepthObservation();
        o.setLinkCode(req.linkCode());
        o.setObservedAt(req.observedAt());
        o.setDepthM(req.depthM());
        o.setSource(req.source() == null || req.source().isBlank() ? "SENSOR" : req.source());
        return service.addObservation(id, o);
    }

    @Operation(summary = "水深观测列表")
    @GetMapping("/{id}/depths")
    public List<DepthObservation> listDepths(@PathVariable Long id) {
        return service.listObservations(id);
    }

    @Operation(summary = "水深补测：写入历史时刻观测并生成新版本影响快照")
    @PostMapping("/{id}/depths/backfill")
    @ResponseStatus(HttpStatus.CREATED)
    public AccessImpactSnapshot backfill(@PathVariable Long id, @Valid @RequestBody DepthObsReq req) {
        DepthObservation o = new DepthObservation();
        o.setLinkCode(req.linkCode());
        o.setObservedAt(req.observedAt());
        o.setDepthM(req.depthM());
        return service.backfillDepth(id, o);
    }

    @Operation(summary = "水深补测批次：多条历史观测一次写入，只生成一版影响快照")
    @PostMapping("/{id}/depths/backfill-batch")
    @ResponseStatus(HttpStatus.CREATED)
    public AccessImpactSnapshot backfillBatch(@PathVariable Long id,
                                              @Valid @RequestBody DepthBatchReq req) {
        List<DepthObservation> batch = req.observations().stream().map(r -> {
            DepthObservation o = new DepthObservation();
            o.setLinkCode(r.linkCode());
            o.setObservedAt(r.observedAt());
            o.setDepthM(r.depthM());
            return o;
        }).toList();
        return service.backfillDepthBatch(id, batch);
    }

    @Operation(summary = "封路更正：区间强制封闭/放行并生成新版本影响快照（缺测不会被放行掩盖）")
    @PostMapping("/{id}/closures")
    @ResponseStatus(HttpStatus.CREATED)
    public AccessImpactSnapshot closure(@PathVariable Long id, @Valid @RequestBody ClosureOverrideReq req) {
        ClosureOverride c = new ClosureOverride();
        c.setLinkCode(req.linkCode());
        c.setClosed(Boolean.TRUE.equals(req.closed()));
        c.setEffectiveFrom(req.effectiveFrom());
        c.setEffectiveTo(req.effectiveTo());
        c.setReason(req.reason());
        return service.addClosureOverride(id, c);
    }

    @Operation(summary = "封路更正列表")
    @GetMapping("/{id}/closures")
    public List<ClosureOverride> listClosures(@PathVariable Long id) {
        return service.listOverrides(id);
    }

    @Operation(summary = "（重）计算可达性影响并落一版快照（首次 INITIAL，之后 MANUAL_RECOMPUTE）")
    @PostMapping("/{id}/snapshots/recompute")
    public AccessImpactSnapshot recompute(@PathVariable Long id) {
        return service.recompute(id, null);
    }

    @Operation(summary = "快照版本账（新版本在前）")
    @GetMapping("/{id}/snapshots")
    public List<AccessImpactSnapshot> listSnapshots(@PathVariable Long id) {
        return service.listSnapshots(id);
    }

    @Operation(summary = "当前版本快照")
    @GetMapping("/{id}/snapshots/current")
    public AccessImpactSnapshot current(@PathVariable Long id) {
        return service.currentSnapshot(id);
    }
}
