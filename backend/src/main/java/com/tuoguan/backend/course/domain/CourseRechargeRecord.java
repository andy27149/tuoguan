package com.tuoguan.backend.course.domain;

import java.time.Instant;

public record CourseRechargeRecord(Long id, Long institutionId, Long studentId, Long teachingUnitId,
                                    Integer lessonCount, String note, Long recordedByTeacherId, Instant createdAt) {
}
