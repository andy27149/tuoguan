package com.tuoguan.backend.kanban.web;

import com.tuoguan.backend.auth.security.TeacherPrincipal;
import com.tuoguan.backend.kanban.service.StudentMealRecordService;
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
public class StudentMealRecordController {

    private final StudentMealRecordService studentMealRecordService;

    public StudentMealRecordController(StudentMealRecordService studentMealRecordService) {
        this.studentMealRecordService = studentMealRecordService;
    }

    @GetMapping("/api/classes/{classId}/meals")
    public List<StudentMealRecordResponse> listForClass(@AuthenticationPrincipal TeacherPrincipal principal,
                                                          @PathVariable Long classId,
                                                          @RequestParam LocalDate date) {
        return studentMealRecordService.listForClass(principal.teacherId(), classId, date).stream()
                .map(StudentMealRecordResponse::from)
                .toList();
    }

    @PatchMapping("/api/students/{studentId}/meal")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void setMeal(@AuthenticationPrincipal TeacherPrincipal principal,
                         @PathVariable Long studentId,
                         @RequestBody SetMealRequest request) {
        studentMealRecordService.setMeal(principal.teacherId(), studentId, request.date());
    }

    @DeleteMapping("/api/students/{studentId}/meal")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void clearMeal(@AuthenticationPrincipal TeacherPrincipal principal,
                           @PathVariable Long studentId,
                           @RequestParam LocalDate date) {
        studentMealRecordService.clearMeal(principal.teacherId(), studentId, date);
    }
}
