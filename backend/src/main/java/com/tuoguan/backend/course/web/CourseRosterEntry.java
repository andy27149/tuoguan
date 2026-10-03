package com.tuoguan.backend.course.web;

import com.tuoguan.backend.roster.domain.Student;

/**
 * {@code balance} 仅对纯课外课学生（{@code offCampusOnly=true}）有意义，是该生在本课程下的
 * 预充值课时余额（已充值-已消课）；托管班学生消课记入月度账单，没有余额概念，此处恒为 null。
 */
public record CourseRosterEntry(Long studentId, String name, String schoolClassName, boolean offCampusOnly,
                                 Integer balance) {

    public static CourseRosterEntry from(Student student, Integer balance) {
        return new CourseRosterEntry(student.id(), student.name(), student.schoolClassName(),
                student.teachingUnitId() == null, balance);
    }
}
