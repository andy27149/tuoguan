package com.tuoguan.backend.course.web;

import jakarta.validation.constraints.NotBlank;

public record CreateCourseStudentRequest(@NotBlank String name, String schoolClassName) {
}
