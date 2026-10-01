package com.tuoguan.backend.course.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

public record CourseConsumptionRecord(Long id, Long institutionId, Long studentId, Long courseId,
                                       LocalDate consumptionDate, BigDecimal priceSnapshot,
                                       Long recordedByTeacherId, Instant createdAt) {
}
