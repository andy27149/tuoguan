package com.tuoguan.backend.admin.web;

import com.tuoguan.backend.admin.service.AdminKanbanService;
import com.tuoguan.backend.auth.security.TeacherPrincipal;
import com.tuoguan.backend.kanban.web.MonthlyStatsResponse;
import com.tuoguan.backend.roster.web.ShareLinkResponse;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.YearMonth;

@RestController
@RequestMapping("/api/admin/students/{studentId}")
@PreAuthorize("hasRole('ADMIN')")
public class AdminStudentKanbanController {

    private final AdminKanbanService adminKanbanService;

    public AdminStudentKanbanController(AdminKanbanService adminKanbanService) {
        this.adminKanbanService = adminKanbanService;
    }

    @GetMapping("/monthly-stats")
    public MonthlyStatsResponse monthlyStats(@AuthenticationPrincipal TeacherPrincipal principal,
                                              @PathVariable Long studentId,
                                              @RequestParam(required = false) String month) {
        YearMonth yearMonth = month != null ? YearMonth.parse(month) : YearMonth.now();
        return MonthlyStatsResponse.from(adminKanbanService.getMonthlyStats(principal.institutionId(), studentId, yearMonth));
    }

    @GetMapping("/share-link")
    public ShareLinkResponse shareLink(@AuthenticationPrincipal TeacherPrincipal principal,
                                        @PathVariable Long studentId) {
        return new ShareLinkResponse(adminKanbanService.getShareToken(principal.institutionId(), studentId));
    }
}
