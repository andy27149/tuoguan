package com.tuoguan.backend.platform.web;

import java.time.Instant;

public record PlatformInstitutionSummary(Long id, String name, Instant createdAt, int teacherCount) {
}
