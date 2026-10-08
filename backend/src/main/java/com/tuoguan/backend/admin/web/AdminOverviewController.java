package com.tuoguan.backend.admin.web;

import com.tuoguan.backend.auth.security.TeacherPrincipal;
import com.tuoguan.backend.course.service.CourseAccountService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/admin/overview")
@PreAuthorize("hasRole('ADMIN')")
public class AdminOverviewController {

    private final CourseAccountService courseAccountService;

    public AdminOverviewController(CourseAccountService courseAccountService) {
        this.courseAccountService = courseAccountService;
    }

    @GetMapping("/low-balance")
    public List<LowBalanceRow> lowBalance(@AuthenticationPrincipal TeacherPrincipal principal) {
        return courseAccountService.getLowBalanceEntries(principal.institutionId(), 3);
    }
}
