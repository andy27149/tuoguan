package com.tuoguan.backend.platform.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

public record InstitutionRenewalRecord(Long id, Long institutionId, RenewalPlan plan, BigDecimal amount,
                                        LocalDate validUntil, String note, Long recordedByTeacherId,
                                        Instant createdAt) {
}
