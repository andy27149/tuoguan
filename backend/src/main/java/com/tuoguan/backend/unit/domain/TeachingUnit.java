package com.tuoguan.backend.unit.domain;

import java.math.BigDecimal;
import java.time.Instant;

public record TeachingUnit(Long id, Long institutionId, Long teacherId, String name, BillingMode billingMode,
                            Integer lessonDurationMinutes, BigDecimal pricePerLesson, boolean active,
                            Instant createdAt) {
}
