package com.tuoguan.backend.roster.domain;

import java.time.Instant;

public record ClassRoom(Long id, Long institutionId, Long teacherId, String name, Instant createdAt) {
}
