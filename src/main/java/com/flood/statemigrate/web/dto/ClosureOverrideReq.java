package com.flood.statemigrate.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;

/** 封路更正请求：closed=true 强制封闭，false 为人工放行（缺测仍按 UNKNOWN，不被放行掩盖） */
public record ClosureOverrideReq(
        @NotBlank String linkCode,
        Boolean closed,
        @NotNull Instant effectiveFrom,
        @NotNull Instant effectiveTo,
        String reason
) {
}
