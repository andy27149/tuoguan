package com.tuoguan.backend.billing.domain;

import java.time.LocalDate;

public record MonthlyBillLeaveLine(Long id, Long monthlyBillId, LocalDate leaveDate, String reason) {
}
