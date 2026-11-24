package com.flood.statemigrate.domain;

/**
 * 路段在某个时间片的通行状态。
 * OPEN：有观测且未超阈（或人工放行）；CLOSED：超阈/人工封闭/有效期外；
 * UNKNOWN：水深缺测或观测过期——保守按不可通行处理，且不得当作畅通证据。
 */
public enum LinkTimeState {
    OPEN, CLOSED, UNKNOWN
}
