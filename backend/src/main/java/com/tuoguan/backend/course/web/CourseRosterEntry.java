package com.tuoguan.backend.course.web;

import com.tuoguan.backend.roster.domain.Student;

public record CourseRosterEntry(Long studentId, String name, String schoolClassName, boolean offCampusOnly) {

    public static CourseRosterEntry from(Student student) {
        return new CourseRosterEntry(student.id(), student.name(), student.schoolClassName(),
                student.classRoomId() == null);
    }
}
