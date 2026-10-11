package com.tuoguan.backend.platform.web;

import com.tuoguan.backend.platform.domain.RenewalPlan;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

public record RenewalRecordRow(Long id, RenewalPlan plan, BigDecimal amount, LocalDate validUntil,
                                String note, String recordedByTeacherName, Instant createdAt) {
}
