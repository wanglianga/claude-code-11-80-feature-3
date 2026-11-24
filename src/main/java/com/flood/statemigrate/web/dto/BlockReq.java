package com.flood.statemigrate.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;

/** 街区（图节点）登记请求：人口为常住口径，用于被孤立人口下界 */
public record BlockReq(
        @NotBlank String nodeCode,
        @NotBlank String name,
        @PositiveOrZero int residentPopulation
) {
}
