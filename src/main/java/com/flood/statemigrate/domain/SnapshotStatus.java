package com.flood.statemigrate.domain;

/** 影响快照版本状态：当前版本 / 已被新版本替代 */
public enum SnapshotStatus {
    CURRENT, SUPERSEDED
}
