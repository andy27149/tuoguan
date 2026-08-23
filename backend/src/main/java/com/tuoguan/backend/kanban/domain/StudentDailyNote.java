package com.tuoguan.backend.kanban.domain;

import java.time.Instant;
import java.time.LocalDate;

public record StudentDailyNote(Long id, Long institutionId, Long classRoomId, Long studentId, LocalDate noteDate,
                                int rating, String comment, Instant createdAt, Instant updatedAt) {
}
