package com.flood.statemigrate.domain;

import jakarta.persistence.*;
import java.time.Instant;

/**
 * 路网：一次积水影响分析所基于的有向图拓扑与分析时间轴。
 * 时间轴按 slotMinutes（默认逐时 60 分钟）切时间片；
 * depthValidityMinutes 规定水深观测的有效期，超出有效期即视为缺测（UNKNOWN，不得当畅通）。
 */
@Entity
@Table(name = "road_network")
public class RoadNetwork {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 64)
    private String networkCode;

    @Column(nullable = false, length = 128)
    private String name;

    /** 分析窗口起点（含），按 slotMinutes 对齐 */
    @Column(nullable = false)
    private Instant horizonStart;

    /** 分析窗口终点（含） */
    @Column(nullable = false)
    private Instant horizonEnd;

    @Column(nullable = false)
    private int slotMinutes;

    /** 水深观测有效期（分钟）：最近观测早于 t-validity 的时间片按缺测处理 */
    @Column(nullable = false)
    private int depthValidityMinutes;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
        updatedAt = createdAt;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getNetworkCode() { return networkCode; }
    public void setNetworkCode(String networkCode) { this.networkCode = networkCode; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public Instant getHorizonStart() { return horizonStart; }
    public void setHorizonStart(Instant horizonStart) { this.horizonStart = horizonStart; }
    public Instant getHorizonEnd() { return horizonEnd; }
    public void setHorizonEnd(Instant horizonEnd) { this.horizonEnd = horizonEnd; }
    public int getSlotMinutes() { return slotMinutes; }
    public void setSlotMinutes(int slotMinutes) { this.slotMinutes = slotMinutes; }
    public int getDepthValidityMinutes() { return depthValidityMinutes; }
    public void setDepthValidityMinutes(int depthValidityMinutes) { this.depthValidityMinutes = depthValidityMinutes; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
