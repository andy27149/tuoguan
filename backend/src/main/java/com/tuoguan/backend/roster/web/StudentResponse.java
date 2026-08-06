package com.tuoguan.backend.roster.web;

import com.tuoguan.backend.roster.domain.Student;

public record StudentResponse(Long id, String name, String schoolClassName, boolean enrolled) {

    public static StudentResponse from(Student student) {
        return new StudentResponse(student.id(), student.name(), student.schoolClassName(), student.enrolled());
    }
}
