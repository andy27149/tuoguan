package com.tuoguan.backend.roster.web;

import com.tuoguan.backend.roster.domain.Student;

public record StudentResponse(Long id, String name, String schoolClassName, boolean enrolled, String avatarUrl) {

    public static StudentResponse from(Student student, String avatarUrl) {
        return new StudentResponse(student.id(), student.name(), student.schoolClassName(), student.enrolled(),
                avatarUrl);
    }
}
