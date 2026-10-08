package com.tuoguan.backend.course.web;

import com.tuoguan.backend.roster.domain.Student;

/**
 * {@code balance} 是该生在本课程下的预充值课时余额（已充值-已消课）。纯课外课学生
 * （{@code offCampusOnly=true}）恒展示，哪怕从未充值过也是 0；托管班学生（双重身份）
 * 只有对本课程充值过才展示，没充值过就是 null（他们消课主要走月度账单，没有余额概念）。
 */
public record CourseRosterEntry(Long studentId, String name, String schoolClassName, boolean offCampusOnly,
                                 Integer balance) {

    public static CourseRosterEntry from(Student student, Integer balance) {
        return new CourseRosterEntry(student.id(), student.name(), student.schoolClassName(),
                student.teachingUnitId() == null, balance);
    }
}
