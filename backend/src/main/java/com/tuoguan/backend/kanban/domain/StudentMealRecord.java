package com.tuoguan.backend.kanban.domain;

import java.time.Instant;
import java.time.LocalDate;

public record StudentMealRecord(Long id, Long institutionId, Long teachingUnitId, Long studentId,
                                 LocalDate mealDate, Instant createdAt) {
}
