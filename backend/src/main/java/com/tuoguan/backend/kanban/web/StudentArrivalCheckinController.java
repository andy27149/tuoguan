package com.tuoguan.backend.kanban.web;

import com.tuoguan.backend.auth.security.TeacherPrincipal;
import com.tuoguan.backend.kanban.service.StudentArrivalCheckinService;
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
public class StudentArrivalCheckinController {

    private final StudentArrivalCheckinService studentArrivalCheckinService;

    public StudentArrivalCheckinController(StudentArrivalCheckinService studentArrivalCheckinService) {
        this.studentArrivalCheckinService = studentArrivalCheckinService;
    }

    @GetMapping("/api/classes/{classId}/arrivals")
    public List<StudentArrivalCheckinResponse> listForClass(@AuthenticationPrincipal TeacherPrincipal principal,
                                                              @PathVariable Long classId,
                                                              @RequestParam LocalDate date) {
        return studentArrivalCheckinService.listForClass(principal.teacherId(), classId, date).stream()
                .map(StudentArrivalCheckinResponse::from)
                .toList();
    }

    @PatchMapping("/api/students/{studentId}/arrival")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void setArrival(@AuthenticationPrincipal TeacherPrincipal principal,
                            @PathVariable Long studentId,
                            @RequestBody SetArrivalRequest request) {
        studentArrivalCheckinService.setArrival(principal.teacherId(), studentId, request.date(), request.arrivedAt());
    }

    @DeleteMapping("/api/students/{studentId}/arrival")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void clearArrival(@AuthenticationPrincipal TeacherPrincipal principal,
                              @PathVariable Long studentId,
                              @RequestParam LocalDate date) {
        studentArrivalCheckinService.clearArrival(principal.teacherId(), studentId, date);
    }
}
