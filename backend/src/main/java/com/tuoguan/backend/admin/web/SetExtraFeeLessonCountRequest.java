package com.tuoguan.backend.admin.web;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record SetExtraFeeLessonCountRequest(@NotNull @Min(0) Integer lessonCount) {
}
