package com.tuoguan.backend.auth.domain;

import java.time.Instant;

public record Teacher(Long id, Long institutionId, String phone, String name, String passwordHash,
                       Role role, boolean mustChangePassword, Instant createdAt) {

    public Teacher(Long id, Long institutionId, String phone, String passwordHash,
                    Role role, boolean mustChangePassword, Instant createdAt) {
        this(id, institutionId, phone, "", passwordHash, role, mustChangePassword, createdAt);
    }
}
