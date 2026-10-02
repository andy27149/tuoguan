package com.tuoguan.backend.unit.domain;

import java.time.Instant;

public record StudentUnitEnrollment(Long id, Long institutionId, Long studentId, Long teachingUnitId, boolean active,
                                     Instant createdAt) {
}
