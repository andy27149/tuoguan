package com.tuoguan.backend.course.web;

import com.tuoguan.backend.course.domain.Course;

import java.math.BigDecimal;

public record AdminCourseResponse(Long id, String name, Long teacherId, String teacherName, String teacherPhone,
                                   BigDecimal pricePerLesson, int lessonDurationMinutes, boolean active) {

    public static AdminCourseResponse from(Course course, String teacherName, String teacherPhone) {
        return new AdminCourseResponse(course.id(), course.name(), course.teacherId(), teacherName, teacherPhone,
                course.pricePerLesson(), course.lessonDurationMinutes(), course.active());
    }
}
