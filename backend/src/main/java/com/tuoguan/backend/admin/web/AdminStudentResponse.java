package com.tuoguan.backend.admin.web;

import java.util.List;

public record AdminStudentResponse(Long id, String name, String schoolClassName, Long classRoomId,
                                    String classRoomName, boolean offCampusOnly,
                                    List<String> enrolledCourseNames) {
}
