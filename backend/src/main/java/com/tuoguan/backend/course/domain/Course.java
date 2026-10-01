package com.tuoguan.backend.course.domain;

import java.math.BigDecimal;
import java.time.Instant;

public record Course(Long id, Long institutionId, Long teacherId, String name, BigDecimal pricePerLesson,
                      int lessonDurationMinutes, boolean active, Instant createdAt) {
}
