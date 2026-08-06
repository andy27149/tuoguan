package com.tuoguan.backend.kanban.domain;

import java.time.Instant;
import java.time.LocalDate;

public record DailyTask(Long id, Long institutionId, Long classRoomId, Long studentId, LocalDate taskDate,
                         Long taskTemplateId, String subject, String name, boolean custom, boolean completed,
                         Instant createdAt) {
}
