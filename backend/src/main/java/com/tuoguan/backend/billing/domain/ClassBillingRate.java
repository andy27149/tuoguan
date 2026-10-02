package com.tuoguan.backend.billing.domain;

import java.math.BigDecimal;
import java.time.Instant;

public record ClassBillingRate(Long id, Long institutionId, Long teachingUnitId, BigDecimal tuitionRatePerMonth,
                                BigDecimal mealRatePerDay, Instant updatedAt) {
}
