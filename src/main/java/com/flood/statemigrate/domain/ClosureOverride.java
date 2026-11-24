package com.flood.statemigrate.domain;

import jakarta.persistence.*;
import java.time.Instant;

/**
 * 封路更正（人工/权威渠道对路段通行状态的覆盖）。
 * 与水深阈值独立：effectiveFrom→effectiveTo 区间内强制 CLOSED（closed=true）或强制 OPEN（closed=false，
 * 仅在水深不缺测时才可能生效；缺测仍按 UNKNOWN 处理，不会被人工放行掩盖）。
 * 更正不删除历史记录，版本账据此生成新的影响快照。
 */
@Entity
@Table(name = "closure_override", indexes = {
        @Index(name = "idx_closure_link", columnList = "networkId,linkCode")
})
public class ClosureOverride {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long networkId;

    @Column(nullable = false, length = 64)
    private String linkCode;

    @Column(nullable = false)
    private boolean closed;

    @Column(nullable = false)
    private Instant effectiveFrom;

    @Column(nullable = false)
    private Instant effectiveTo;

    @Column(length = 128)
    private String reason;

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
    public boolean isClosed() { return closed; }
    public void setClosed(boolean closed) { this.closed = closed; }
    public Instant getEffectiveFrom() { return effectiveFrom; }
    public void setEffectiveFrom(Instant effectiveFrom) { this.effectiveFrom = effectiveFrom; }
    public Instant getEffectiveTo() { return effectiveTo; }
    public void setEffectiveTo(Instant effectiveTo) { this.effectiveTo = effectiveTo; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
    public Instant getCreatedAt() { return createdAt; }
}
