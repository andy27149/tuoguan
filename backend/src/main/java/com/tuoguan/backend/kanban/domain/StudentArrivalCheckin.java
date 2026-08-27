package com.tuoguan.backend.kanban.domain;

import java.time.Instant;
import java.time.LocalDate;

public record StudentArrivalCheckin(Long id, Long institutionId, Long classRoomId, Long studentId,
                                     LocalDate checkinDate, String arrivedAt,
                                     Instant createdAt, Instant updatedAt) {
}
