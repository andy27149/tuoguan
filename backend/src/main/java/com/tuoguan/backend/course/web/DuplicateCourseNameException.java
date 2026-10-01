package com.tuoguan.backend.course.web;

public class DuplicateCourseNameException extends RuntimeException {

    public DuplicateCourseNameException(String message) {
        super(message);
    }
}
