package com.tuoguan.backend.share.web;

import com.tuoguan.backend.course.web.StudentCourseStatement;
import com.tuoguan.backend.kanban.web.MonthlyStatsResponse;

public record PublicShareResponse(String studentName, String schoolClassName, String avatarUrl,
                                   MonthlyStatsResponse stats, StudentCourseStatement courseStatement) {
}
