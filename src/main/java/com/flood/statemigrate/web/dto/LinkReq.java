package com.flood.statemigrate.web.dto;

import com.flood.statemigrate.domain.DirectionType;
import com.flood.statemigrate.domain.RoadKind;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.time.Instant;

/** 路段登记请求：方向、限高/限载、桥涵、替代通道、积水封闭阈值与状态有效期 */
public record LinkReq(
        @NotBlank String linkCode,
        @NotBlank String name,
        @NotNull RoadKind kind,
        @NotNull DirectionType direction,
        @NotBlank String fromNode,
        @NotBlank String toNode,
        @Positive double lengthM,
        Double heightLimitM,
        Double weightLimitT,
        Double depthCloseThresholdM,
        String altGroup,
        Boolean alternative,
        Instant validFrom,
        Instant validTo
) {
}
