package com.tuoguan.backend.roster.domain;

import java.time.Instant;

public record TaskTemplate(Long id, Long institutionId, String subject, String name, Instant createdAt) {
}
