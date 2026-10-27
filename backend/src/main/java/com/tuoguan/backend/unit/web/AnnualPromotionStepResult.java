package com.tuoguan.backend.unit.web;

public record AnnualPromotionStepResult(Long sourceTeachingUnitId, String sourceTeachingUnitName,
                                         AnnualPromotionOutcome outcome,
                                         Long targetTeachingUnitId, String targetTeachingUnitName,
                                         int movedCount) {
}
