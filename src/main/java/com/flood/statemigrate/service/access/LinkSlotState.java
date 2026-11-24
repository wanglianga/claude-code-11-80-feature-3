package com.flood.statemigrate.service.access;

import com.flood.statemigrate.domain.LinkTimeState;

/**
 * 单个时间片内一条路段的通行状态（时变通行图的最小单元）。
 * depthM 为该时间片采用的最新有效水深；缺测时为 null 且 state=UNKNOWN。
 * observationId 为取值依据的观测记录 ID，便于版本账追溯。
 */
public record LinkSlotState(
        String linkCode,
        int slotIndex,
        LinkTimeState state,
        String reason,
        Double depthM,
        Long observationId
) {
}
