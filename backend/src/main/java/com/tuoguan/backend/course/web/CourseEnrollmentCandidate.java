package com.tuoguan.backend.course.web;

import com.tuoguan.backend.roster.domain.Student;

/**
 * 可被加入某课外课花名册的纯课外课学生候选人（不挂靠任何托管班，且尚未报名该课程）。
 * 教师原有的「添加已有学生」只能从自己名下的托管班选人，管理员新建的纯课外课学生
 * 没有托管班、不会出现在那个列表里，永远加不进任何课程——见产品诊断 #06 延伸。
 */
public record CourseEnrollmentCandidate(Long studentId, String name, String schoolClassName) {

    public static CourseEnrollmentCandidate from(Student student) {
        return new CourseEnrollmentCandidate(student.id(), student.name(), student.schoolClassName());
    }
}
