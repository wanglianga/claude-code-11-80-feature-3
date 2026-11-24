package com.flood.statemigrate.domain;

/** 路段构造类型：普通道路 / 桥梁 / 涵洞 / 隧道（桥涵在积水中的封闭阈值通常更严格） */
public enum RoadKind {
    ROAD, BRIDGE, CULVERT, TUNNEL
}
