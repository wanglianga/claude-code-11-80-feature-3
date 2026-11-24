package com.flood.statemigrate.domain;

import jakarta.persistence.*;
import java.time.Instant;

/**
 * 路网可达性影响快照（版本账）。
 * 每次水深补测或封路更正都生成一条新版本：旧版本置 SUPERSEDED，新版本为 CURRENT，
 * 并记录受影响服务点。影响图只作为独立分析结果保存，不回写作业迁移（流/检查点/闸门）状态。
 *
 * timeVaryingGraphJson：逐时间片各路段状态（OPEN/CLOSED/UNKNOWN 及原因、该时间片水深），
 *   即“积水窗口 × 封闭阈值 × 状态有效期”相交得到的时变通行图，UNKNOWN 为缺测边界。
 * serviceImpactJson：分消防站/医院/避难点给出逐时间片可达街区、最短绕行距离、距离增量与被孤立人口下界。
 * affectedFacilityCodesJson：相对上一版本发生可达性变化的服务点编码。
 * unknownLinkCodesJson：存在缺测时间片的路段编码（缺测不得当作畅通证据）。
 * inputRefsJson：本版本使用的水深观测与封路更正 ID 清单。
 */
@Entity
@Table(name = "access_impact_snapshot", indexes = {
        @Index(name = "idx_snapshot_network", columnList = "networkId,versionNo")
})
public class AccessImpactSnapshot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long networkId;

    @Column(nullable = false)
    private int versionNo;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private SnapshotStatus status;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private ImpactTriggerType triggerType;

    /** 触发说明，如补测观测 ID 或封路更正 ID */
    @Column(length = 256)
    private String triggerRef;

    /** 上一版本快照 ID（首版为空） */
    private Long baselineVersionId;

    @Column(nullable = false)
    private Instant horizonStart;

    @Column(nullable = false)
    private Instant horizonEnd;

    @Column(nullable = false)
    private int slotMinutes;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String timeVaryingGraphJson;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String serviceImpactJson;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String affectedFacilityCodesJson;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String unknownLinkCodesJson;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String inputRefsJson;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() { createdAt = Instant.now(); }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getNetworkId() { return networkId; }
    public void setNetworkId(Long networkId) { this.networkId = networkId; }
    public int getVersionNo() { return versionNo; }
    public void setVersionNo(int versionNo) { this.versionNo = versionNo; }
    public SnapshotStatus getStatus() { return status; }
    public void setStatus(SnapshotStatus status) { this.status = status; }
    public ImpactTriggerType getTriggerType() { return triggerType; }
    public void setTriggerType(ImpactTriggerType triggerType) { this.triggerType = triggerType; }
    public String getTriggerRef() { return triggerRef; }
    public void setTriggerRef(String triggerRef) { this.triggerRef = triggerRef; }
    public Long getBaselineVersionId() { return baselineVersionId; }
    public void setBaselineVersionId(Long baselineVersionId) { this.baselineVersionId = baselineVersionId; }
    public Instant getHorizonStart() { return horizonStart; }
    public void setHorizonStart(Instant horizonStart) { this.horizonStart = horizonStart; }
    public Instant getHorizonEnd() { return horizonEnd; }
    public void setHorizonEnd(Instant horizonEnd) { this.horizonEnd = horizonEnd; }
    public int getSlotMinutes() { return slotMinutes; }
    public void setSlotMinutes(int slotMinutes) { this.slotMinutes = slotMinutes; }
    public String getTimeVaryingGraphJson() { return timeVaryingGraphJson; }
    public void setTimeVaryingGraphJson(String timeVaryingGraphJson) { this.timeVaryingGraphJson = timeVaryingGraphJson; }
    public String getServiceImpactJson() { return serviceImpactJson; }
    public void setServiceImpactJson(String serviceImpactJson) { this.serviceImpactJson = serviceImpactJson; }
    public String getAffectedFacilityCodesJson() { return affectedFacilityCodesJson; }
    public void setAffectedFacilityCodesJson(String affectedFacilityCodesJson) { this.affectedFacilityCodesJson = affectedFacilityCodesJson; }
    public String getUnknownLinkCodesJson() { return unknownLinkCodesJson; }
    public void setUnknownLinkCodesJson(String unknownLinkCodesJson) { this.unknownLinkCodesJson = unknownLinkCodesJson; }
    public String getInputRefsJson() { return inputRefsJson; }
    public void setInputRefsJson(String inputRefsJson) { this.inputRefsJson = inputRefsJson; }
    public Instant getCreatedAt() { return createdAt; }
}
