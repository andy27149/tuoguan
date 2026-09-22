package com.tuoguan.backend.billing.domain;

import java.math.BigDecimal;

public record MonthlyBillExtraFeeLine(Long id, Long monthlyBillId, String name, BigDecimal pricePerLesson,
                                       Integer lessonCount, BigDecimal amount) {
}
