package com.tuoguan.backend.monthlyquickbill.domain;

import java.time.Instant;
import java.time.LocalDate;

public record MonthlyQuickBillRecord(Long id, Long institutionId, Long studentId, QuickBillRecordType recordType,
                                      LocalDate recordDate, Instant createdAt) {
}
