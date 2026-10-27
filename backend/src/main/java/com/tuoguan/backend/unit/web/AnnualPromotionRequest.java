package com.tuoguan.backend.unit.web;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public record AnnualPromotionRequest(@NotEmpty @Valid List<AnnualPromotionStepRequest> steps) {
}
