package com.tuoguan.backend.kanban.domain;

import java.time.Instant;
import java.time.LocalDate;

public record ClassDismissal(Long id, Long institutionId, Long classRoomId, LocalDate dismissalDate,
                              Instant createdAt) {
}
