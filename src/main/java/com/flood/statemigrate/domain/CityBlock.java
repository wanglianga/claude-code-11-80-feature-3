package com.flood.statemigrate.domain;

import jakarta.persistence.*;

/**
 * 街区（图节点）：可达性分析的起讫单元，登记常住人口（用于被孤立人口下界估计）。
 * 人口为下界口径：只统计登记常住人口，不含临时人口。
 */
@Entity
@Table(name = "city_block", uniqueConstraints = @UniqueConstraint(columnNames = {"networkId", "nodeCode"}))
public class CityBlock {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long networkId;

    @Column(nullable = false, length = 32)
    private String nodeCode;

    @Column(nullable = false, length = 128)
    private String name;

    @Column(nullable = false)
    private int residentPopulation;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getNetworkId() { return networkId; }
    public void setNetworkId(Long networkId) { this.networkId = networkId; }
    public String getNodeCode() { return nodeCode; }
    public void setNodeCode(String nodeCode) { this.nodeCode = nodeCode; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public int getResidentPopulation() { return residentPopulation; }
    public void setResidentPopulation(int residentPopulation) { this.residentPopulation = residentPopulation; }
}
