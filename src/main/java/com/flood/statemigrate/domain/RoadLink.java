package com.flood.statemigrate.domain;

import jakarta.persistence.*;

/**
 * 路段（有向边）：保存方向、限高/限载、桥涵标记、替代通道归属与积水封闭阈值。
 * depthCloseThresholdM 为 null 表示该路段不因积水深度封闭（但人工封路与缺测规则仍生效）。
 * validFrom/validTo 为路段自身的状态有效期（如施工/定时管制），为空表示全窗口有效。
 * altGroup 非空时标识同组替代通道（同一走廊的主通道与替代路径）。
 */
@Entity
@Table(name = "road_link", uniqueConstraints = @UniqueConstraint(columnNames = {"networkId", "linkCode"}))
public class RoadLink {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long networkId;

    @Column(nullable = false, length = 64)
    private String linkCode;

    @Column(nullable = false, length = 128)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private RoadKind kind;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private DirectionType direction;

    @Column(nullable = false, length = 32)
    private String fromNode;

    @Column(nullable = false, length = 32)
    private String toNode;

    /** 路段长度（米），最短路权重 */
    @Column(nullable = false)
    private double lengthM;

    /** 限高（米），null 表示不限 */
    private Double heightLimitM;

    /** 限载（吨），null 表示不限 */
    private Double weightLimitT;

    /** 积水封闭阈值（米）：水深 ≥ 阈值即封闭；null 表示不按水深封闭 */
    private Double depthCloseThresholdM;

    /** 替代通道分组：同组路段互为替代路径，null 表示普通路段 */
    @Column(length = 64)
    private String altGroup;

    /** 是否为替代通道（主通道为 false） */
    @Column(nullable = false)
    private boolean alternative;

    /** 路段状态有效期起点（含），null 表示自始有效 */
    private java.time.Instant validFrom;

    /** 路段状态有效期终点（含），null 表示长期有效 */
    private java.time.Instant validTo;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getNetworkId() { return networkId; }
    public void setNetworkId(Long networkId) { this.networkId = networkId; }
    public String getLinkCode() { return linkCode; }
    public void setLinkCode(String linkCode) { this.linkCode = linkCode; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public RoadKind getKind() { return kind; }
    public void setKind(RoadKind kind) { this.kind = kind; }
    public DirectionType getDirection() { return direction; }
    public void setDirection(DirectionType direction) { this.direction = direction; }
    public String getFromNode() { return fromNode; }
    public void setFromNode(String fromNode) { this.fromNode = fromNode; }
    public String getToNode() { return toNode; }
    public void setToNode(String toNode) { this.toNode = toNode; }
    public double getLengthM() { return lengthM; }
    public void setLengthM(double lengthM) { this.lengthM = lengthM; }
    public Double getHeightLimitM() { return heightLimitM; }
    public void setHeightLimitM(Double heightLimitM) { this.heightLimitM = heightLimitM; }
    public Double getWeightLimitT() { return weightLimitT; }
    public void setWeightLimitT(Double weightLimitT) { this.weightLimitT = weightLimitT; }
    public Double getDepthCloseThresholdM() { return depthCloseThresholdM; }
    public void setDepthCloseThresholdM(Double depthCloseThresholdM) { this.depthCloseThresholdM = depthCloseThresholdM; }
    public String getAltGroup() { return altGroup; }
    public void setAltGroup(String altGroup) { this.altGroup = altGroup; }
    public boolean isAlternative() { return alternative; }
    public void setAlternative(boolean alternative) { this.alternative = alternative; }
    public java.time.Instant getValidFrom() { return validFrom; }
    public void setValidFrom(java.time.Instant validFrom) { this.validFrom = validFrom; }
    public java.time.Instant getValidTo() { return validTo; }
    public void setValidTo(java.time.Instant validTo) { this.validTo = validTo; }
}
