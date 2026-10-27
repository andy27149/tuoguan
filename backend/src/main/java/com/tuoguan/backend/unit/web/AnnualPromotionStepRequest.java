package com.tuoguan.backend.unit.web;

import jakarta.validation.constraints.NotNull;

// targetTeachingUnitId 仅在 outcome=MOVE_TO_UNIT 时需要非空，由 AnnualPromotionService 校验，
// 这里不用 @NotNull 修饰，因为 OFF_CAMPUS/DEACTIVATE 时它必须为空。
public record AnnualPromotionStepRequest(@NotNull Long sourceTeachingUnitId,
                                          @NotNull AnnualPromotionOutcome outcome,
                                          Long targetTeachingUnitId) {
}
