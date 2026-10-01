package com.tuoguan.backend.course.web;

import com.tuoguan.backend.course.domain.Course;

import java.math.BigDecimal;

public record CourseResponse(Long id, String name, BigDecimal pricePerLesson, int lessonDurationMinutes,
                              boolean active) {

    public static CourseResponse from(Course course) {
        return new CourseResponse(course.id(), course.name(), course.pricePerLesson(),
                course.lessonDurationMinutes(), course.active());
    }
}
