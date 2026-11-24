package com.flood.statemigrate.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

import java.time.Instant;

/** 水深观测/补测请求；补测走独立端点并把 source 固定为 BACKFILL */
public record DepthObsReq(
        @NotBlank String linkCode,
        @NotNull Instant observedAt,
        @PositiveOrZero double depthM,
        String source
) {
}
