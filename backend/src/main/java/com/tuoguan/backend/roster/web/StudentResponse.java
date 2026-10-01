package com.tuoguan.backend.roster.web;

import com.tuoguan.backend.roster.domain.Student;

import java.util.List;

public record StudentResponse(Long id, String name, String schoolClassName, boolean enrolled, String avatarUrl,
                               List<String> enrolledCourseNames) {

    public static StudentResponse from(Student student, String avatarUrl, List<String> enrolledCourseNames) {
        return new StudentResponse(student.id(), student.name(), student.schoolClassName(), student.enrolled(),
                avatarUrl, enrolledCourseNames);
    }
}
