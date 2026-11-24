package com.flood.statemigrate.domain;

import jakarta.persistence.*;
import java.time.Instant;

/**
 * 路面积水深度观测（时序）。来源可为传感器、人工上报、视频识别或补测。
 * 同一 linkCode 的观测按 observedAt 排序后用于逐时水深取值；
 * 补测（backfill）写入的是历史时刻的新观测，不删除旧记录，版本账据此生成新快照。
 */
@Entity
@Table(name = "depth_observation", indexes = {
        @Index(name = "idx_depth_link_time", columnList = "networkId,linkCode,observedAt")
})
public class DepthObservation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long networkId;

    @Column(nullable = false, length = 64)
    private String linkCode;

    @Column(nullable = false)
    private Instant observedAt;

    /** 水深（米） */
    @Column(nullable = false)
    private double depthM;

    /** SENSOR / ROAD_REPORT / VIDEO / MANUAL / BACKFILL */
    @Column(nullable = false, length = 24)
    private String source;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() { createdAt = Instant.now(); }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getNetworkId() { return networkId; }
    public void setNetworkId(Long networkId) { this.networkId = networkId; }
    public String getLinkCode() { return linkCode; }
    public void setLinkCode(String linkCode) { this.linkCode = linkCode; }
    public Instant getObservedAt() { return observedAt; }
    public void setObservedAt(Instant observedAt) { this.observedAt = observedAt; }
    public double getDepthM() { return depthM; }
    public void setDepthM(double depthM) { this.depthM = depthM; }
    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }
    public Instant getCreatedAt() { return createdAt; }
}
