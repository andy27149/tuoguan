package com.tuoguan.backend.unit.web;

import java.util.List;

public record AnnualPromotionResult(int totalMovedCount, List<AnnualPromotionStepResult> steps) {
}
