package com.tuoguan.backend.kanban.web;

import com.tuoguan.backend.auth.security.TeacherPrincipal;
import com.tuoguan.backend.kanban.service.ClassDismissalService;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@RestController
public class ClassDismissalController {

    private final ClassDismissalService classDismissalService;

    public ClassDismissalController(ClassDismissalService classDismissalService) {
        this.classDismissalService = classDismissalService;
    }

    @PostMapping("/api/classes/{classId}/dismissal")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void dismiss(@AuthenticationPrincipal TeacherPrincipal principal,
                         @PathVariable Long classId,
                         @RequestBody DismissalRequest request) {
        classDismissalService.dismiss(principal.teacherId(), classId, request.date());
    }

    @DeleteMapping("/api/classes/{classId}/dismissal")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void undoDismiss(@AuthenticationPrincipal TeacherPrincipal principal,
                             @PathVariable Long classId,
                             @RequestParam LocalDate date) {
        classDismissalService.undoDismiss(principal.teacherId(), classId, date);
    }

    @GetMapping("/api/classes/{classId}/dismissal")
    public DismissalStatusResponse status(@AuthenticationPrincipal TeacherPrincipal principal,
                                           @PathVariable Long classId,
                                           @RequestParam LocalDate date) {
        return new DismissalStatusResponse(classDismissalService.isDismissed(principal.teacherId(), classId, date));
    }
}
