package com.tuoguan.backend.admin.web;

public record AdminStudentResponse(Long id, String name, String schoolClassName, Long classRoomId,
                                    String classRoomName, boolean offCampusOnly) {
}
