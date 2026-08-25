package com.tuoguan.backend.kanban.domain;

import java.time.Instant;
import java.time.LocalDate;

public record StudentPickupCheckin(Long id, Long institutionId, Long classRoomId, Long studentId,
                                    LocalDate checkinDate, String pickedUpBy, String pickedUpAt,
                                    Instant createdAt, Instant updatedAt) {
}
