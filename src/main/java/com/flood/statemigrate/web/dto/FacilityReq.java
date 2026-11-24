package com.flood.statemigrate.web.dto;

import com.flood.statemigrate.domain.FacilityType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/** 应急服务区登记请求；entryLinkCode+entryThresholdM 表达入口独立阈值（如急诊 0.4m） */
public record FacilityReq(
        @NotBlank String facilityCode,
        @NotBlank String name,
        @NotNull FacilityType type,
        @NotBlank String nodeCode,
        String entryLinkCode,
        Double entryThresholdM
) {
}
