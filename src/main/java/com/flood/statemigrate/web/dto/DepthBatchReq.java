package com.flood.statemigrate.web.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

/** 水深补测批次：一次写入多条历史观测，只生成一版影响快照 */
public record DepthBatchReq(
        @NotEmpty @Valid List<DepthObsReq> observations
) {
}
