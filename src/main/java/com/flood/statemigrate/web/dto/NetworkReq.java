package com.flood.statemigrate.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.time.Instant;

/** 路网登记请求：分析时间轴与水深有效期 */
public record NetworkReq(
        @NotBlank String networkCode,
        @NotBlank String name,
        @NotNull Instant horizonStart,
        @NotNull Instant horizonEnd,
        @Positive int slotMinutes,
        @Positive int depthValidityMinutes
) {
}
