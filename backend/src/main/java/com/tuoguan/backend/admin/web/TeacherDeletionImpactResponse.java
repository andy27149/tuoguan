package com.tuoguan.backend.admin.web;

import com.tuoguan.backend.admin.service.AdminTeacherService.TeacherDeletionImpact;

public record TeacherDeletionImpactResponse(int classCount, int studentCount, int templateCount, int courseCount,
                                             boolean hasStudents) {

    public static TeacherDeletionImpactResponse from(TeacherDeletionImpact impact) {
        return new TeacherDeletionImpactResponse(impact.classCount(), impact.studentCount(),
                impact.templateCount(), impact.courseCount(), impact.hasStudents());
    }
}
