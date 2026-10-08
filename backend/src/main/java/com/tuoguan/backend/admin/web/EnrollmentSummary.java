package com.tuoguan.backend.admin.web;

import java.util.List;

public record EnrollmentSummary(int totalCount, int custodyCount, int offCampusOnlyCount,
                                 List<TeacherStudentCount> byTeacher) {
}
