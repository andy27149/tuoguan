package com.tuoguan.backend.kanban.domain;

import java.time.Instant;
import java.time.LocalDate;

public record ClassDismissal(Long id, Long institutionId, Long teachingUnitId, LocalDate dismissalDate,
                              Instant createdAt) {
}
