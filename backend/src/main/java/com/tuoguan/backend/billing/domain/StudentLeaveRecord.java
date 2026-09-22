package com.tuoguan.backend.billing.domain;

import java.time.Instant;
import java.time.LocalDate;

public record StudentLeaveRecord(Long id, Long institutionId, Long studentId, Long classRoomId,
                                  LocalDate leaveDate, String reason, Instant createdAt) {
}
