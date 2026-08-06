package com.tuoguan.backend.roster.domain;

import java.time.Instant;

public record Student(Long id, Long institutionId, Long classRoomId, String name, String schoolClassName,
                       boolean enrolled, Instant createdAt) {
}
