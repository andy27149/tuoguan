package com.tuoguan.backend.billing.domain;

import java.math.BigDecimal;
import java.time.Instant;

public record StudentExtraFee(Long id, Long institutionId, Long studentId, String name,
                               BigDecimal pricePerLesson, Instant createdAt) {
}
