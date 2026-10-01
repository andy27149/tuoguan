package com.tuoguan.backend.admin.web;

import java.math.BigDecimal;

public record CourseConsumptionSummaryRow(Long courseId, String courseName, BigDecimal pricePerLesson,
                                           int lessonCount, BigDecimal amount) {
}
