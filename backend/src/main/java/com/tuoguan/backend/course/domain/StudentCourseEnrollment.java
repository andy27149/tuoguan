package com.tuoguan.backend.course.domain;

import java.time.Instant;

public record StudentCourseEnrollment(Long id, Long institutionId, Long studentId, Long courseId, boolean active,
                                       Instant createdAt) {
}
