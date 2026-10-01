package com.tuoguan.backend.course.web;

public class CourseNotEnrolledException extends RuntimeException {

    public CourseNotEnrolledException(String message) {
        super(message);
    }
}
