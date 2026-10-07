package com.tuoguan.backend.kanban.web;

import com.tuoguan.backend.auth.security.TeacherPrincipal;
import com.tuoguan.backend.kanban.service.StudentLeaveRecordService;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

@RestController
public class StudentLeaveRecordController {

    private final StudentLeaveRecordService studentLeaveRecordService;

    public StudentLeaveRecordController(StudentLeaveRecordService studentLeaveRecordService) {
        this.studentLeaveRecordService = studentLeaveRecordService;
    }

    @GetMapping("/api/classes/{classId}/leaves")
    public List<StudentLeaveRecordResponse> listForClass(@AuthenticationPrincipal TeacherPrincipal principal,
                                                          @PathVariable Long classId,
                                                          @RequestParam LocalDate date) {
        return studentLeaveRecordService.listForClass(principal.teacherId(), classId, date).stream()
                .map(StudentLeaveRecordResponse::from)
                .toList();
    }

    @PatchMapping("/api/students/{studentId}/leave")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void setLeave(@AuthenticationPrincipal TeacherPrincipal principal,
                          @PathVariable Long studentId,
                          @RequestBody SetLeaveRequest request) {
        studentLeaveRecordService.setLeave(principal.teacherId(), studentId, request.date(), request.reason());
    }

    @DeleteMapping("/api/students/{studentId}/leave")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void clearLeave(@AuthenticationPrincipal TeacherPrincipal principal,
                            @PathVariable Long studentId,
                            @RequestParam LocalDate date) {
        studentLeaveRecordService.clearLeave(principal.teacherId(), studentId, date);
    }
}
