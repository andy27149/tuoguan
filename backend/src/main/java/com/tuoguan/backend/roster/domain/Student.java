package com.tuoguan.backend.roster.domain;

import java.time.Instant;

public record Student(Long id, Long institutionId, Long teachingUnitId, String name, String schoolClassName,
                       boolean enrolled, String avatarObjectKey, Instant createdAt) {
}
