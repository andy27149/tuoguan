package com.tuoguan.backend.auth.domain;

import java.time.Instant;

public record Institution(Long id, String name, String logoObjectKey, Instant createdAt) {
}
