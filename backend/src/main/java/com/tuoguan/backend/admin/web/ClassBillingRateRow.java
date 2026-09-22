package com.tuoguan.backend.admin.web;

import java.math.BigDecimal;

public record ClassBillingRateRow(Long classRoomId, String className, BigDecimal tuitionRatePerMonth,
                                   BigDecimal mealRatePerDay) {
}
