package com.flood.statemigrate.domain;

import jakarta.persistence.*;

/**
 * 应急服务区（消防站/医院/避难点）：挂载在图节点上。
 * entryLinkCode/entryThresholdM 用于登记服务区自身入口的独立积水阈值
 * （如医院急诊入口 0.4m）；入口封闭时该服务区在对应时间片不可用。
 */
@Entity
@Table(name = "service_facility", uniqueConstraints = @UniqueConstraint(columnNames = {"networkId", "facilityCode"}))
public class ServiceFacility {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long networkId;

    @Column(nullable = false, length = 64)
    private String facilityCode;

    @Column(nullable = false, length = 128)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private FacilityType type;

    @Column(nullable = false, length = 32)
    private String nodeCode;

    /** 入口所在路段（可为空，表示无独立入口阈值） */
    @Column(length = 64)
    private String entryLinkCode;

    /** 入口积水封闭阈值（米），如急诊入口 0.4m */
    private Double entryThresholdM;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getNetworkId() { return networkId; }
    public void setNetworkId(Long networkId) { this.networkId = networkId; }
    public String getFacilityCode() { return facilityCode; }
    public void setFacilityCode(String facilityCode) { this.facilityCode = facilityCode; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public FacilityType getType() { return type; }
    public void setType(FacilityType type) { this.type = type; }
    public String getNodeCode() { return nodeCode; }
    public void setNodeCode(String nodeCode) { this.nodeCode = nodeCode; }
    public String getEntryLinkCode() { return entryLinkCode; }
    public void setEntryLinkCode(String entryLinkCode) { this.entryLinkCode = entryLinkCode; }
    public Double getEntryThresholdM() { return entryThresholdM; }
    public void setEntryThresholdM(Double entryThresholdM) { this.entryThresholdM = entryThresholdM; }
}
