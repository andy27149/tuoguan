package com.tuoguan.backend.kanban.web;

import com.tuoguan.backend.auth.security.TeacherPrincipal;
import com.tuoguan.backend.kanban.service.StudentPickupCheckinService;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
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
public class StudentPickupCheckinController {

    private final StudentPickupCheckinService studentPickupCheckinService;

    public StudentPickupCheckinController(StudentPickupCheckinService studentPickupCheckinService) {
        this.studentPickupCheckinService = studentPickupCheckinService;
    }

    @GetMapping("/api/classes/{classId}/pickups")
    public List<StudentPickupCheckinResponse> listForClass(@AuthenticationPrincipal TeacherPrincipal principal,
                                                             @PathVariable Long classId,
                                                             @RequestParam LocalDate date) {
        return studentPickupCheckinService.listForClass(principal.teacherId(), classId, date).stream()
                .map(StudentPickupCheckinResponse::from)
                .toList();
    }

    @PatchMapping("/api/students/{studentId}/pickup")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void setPickup(@AuthenticationPrincipal TeacherPrincipal principal,
                           @PathVariable Long studentId,
                           @RequestBody SetPickupRequest request) {
        studentPickupCheckinService.setPickup(principal.teacherId(), studentId, request.date(),
                request.pickedUpBy(), request.pickedUpAt());
    }
}
