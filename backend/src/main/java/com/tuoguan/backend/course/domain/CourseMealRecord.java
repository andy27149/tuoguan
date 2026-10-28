package com.tuoguan.backend.course.domain;

import java.time.Instant;
import java.time.LocalDate;

public record CourseMealRecord(Long id, Long institutionId, Long studentId, LocalDate mealDate,
                                Long recordedByTeacherId, Instant createdAt) {
}
