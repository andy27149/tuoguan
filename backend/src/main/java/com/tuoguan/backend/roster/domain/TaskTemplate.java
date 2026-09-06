package com.tuoguan.backend.roster.domain;

import java.time.Instant;

public record TaskTemplate(Long id, Long institutionId, Long teacherId, String subject, String name,
                            Instant createdAt, boolean archived) {
}
