package com.tuoguan.backend.course.web;

import com.tuoguan.backend.unit.domain.TeachingUnit;

import java.math.BigDecimal;

public record CourseResponse(Long id, String name, BigDecimal pricePerLesson, int lessonDurationMinutes,
                              boolean active) {

    public static CourseResponse from(TeachingUnit course) {
        return new CourseResponse(course.id(), course.name(), course.pricePerLesson(),
                course.lessonDurationMinutes(), course.active());
    }
}
