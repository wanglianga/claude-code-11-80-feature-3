package com.flood.statemigrate.domain;

/** 影响快照触发来源：首次建模 / 水深补测 / 封路更正 */
public enum ImpactTriggerType {
    INITIAL, DEPTH_BACKFILL, CLOSURE_CORRECTION, MANUAL_RECOMPUTE
}
