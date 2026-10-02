package com.tuoguan.backend.course.web;

import com.tuoguan.backend.auth.security.TeacherPrincipal;
import com.tuoguan.backend.course.service.AdminCourseService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
public class AdminCourseController {

    private final AdminCourseService adminCourseService;

    public AdminCourseController(AdminCourseService adminCourseService) {
        this.adminCourseService = adminCourseService;
    }

    @GetMapping("/api/admin/courses")
    @PreAuthorize("hasRole('ADMIN')")
    public List<AdminCourseResponse> list(@AuthenticationPrincipal TeacherPrincipal principal) {
        return adminCourseService.listCourses(principal.institutionId());
    }

    @PostMapping("/api/admin/courses")
    @PreAuthorize("hasRole('ADMIN')")
    @ResponseStatus(HttpStatus.CREATED)
    public AdminCourseResponse create(@AuthenticationPrincipal TeacherPrincipal principal,
                                       @Valid @RequestBody AdminCreateCourseRequest request) {
        return adminCourseService.createCourse(principal.institutionId(), request.name(),
                request.lessonDurationMinutes(), request.teacherId(), request.pricePerLesson());
    }

    @PatchMapping("/api/admin/courses/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public void update(@AuthenticationPrincipal TeacherPrincipal principal, @PathVariable Long id,
                        @RequestBody AdminUpdateCourseRequest request) {
        if (request.pricePerLesson() != null) {
            adminCourseService.setPrice(principal.institutionId(), id, request.pricePerLesson());
        }
        if (request.teacherId() != null) {
            adminCourseService.reassignTeacher(principal.institutionId(), id, request.teacherId());
        }
        if (request.active() != null) {
            adminCourseService.setActive(principal.institutionId(), id, request.active());
        }
    }
}
