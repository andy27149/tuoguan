package com.tuoguan.backend.kanban.web;

import com.tuoguan.backend.auth.security.TeacherPrincipal;
import com.tuoguan.backend.kanban.service.StudentDailyNoteService;
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
public class StudentDailyNoteController {

    private final StudentDailyNoteService studentDailyNoteService;

    public StudentDailyNoteController(StudentDailyNoteService studentDailyNoteService) {
        this.studentDailyNoteService = studentDailyNoteService;
    }

    @GetMapping("/api/classes/{classId}/student-notes")
    public List<StudentDailyNoteResponse> listForClass(@AuthenticationPrincipal TeacherPrincipal principal,
                                                         @PathVariable Long classId,
                                                         @RequestParam LocalDate date) {
        return studentDailyNoteService.listForClass(principal.teacherId(), classId, date).stream()
                .map(StudentDailyNoteResponse::from)
                .toList();
    }

    @PatchMapping("/api/students/{studentId}/rating")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void setRating(@AuthenticationPrincipal TeacherPrincipal principal,
                           @PathVariable Long studentId,
                           @RequestBody SetRatingRequest request) {
        studentDailyNoteService.setRating(principal.teacherId(), studentId, request.date(), request.rating());
    }

    @PatchMapping("/api/students/{studentId}/comment")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void setComment(@AuthenticationPrincipal TeacherPrincipal principal,
                            @PathVariable Long studentId,
                            @RequestBody SetCommentRequest request) {
        studentDailyNoteService.setComment(principal.teacherId(), studentId, request.date(), request.comment());
    }
}
