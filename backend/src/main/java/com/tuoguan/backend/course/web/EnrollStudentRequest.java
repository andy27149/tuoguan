package com.tuoguan.backend.course.web;

import jakarta.validation.constraints.NotNull;

public record EnrollStudentRequest(@NotNull Long studentId) {
}
