package com.tuoguan.backend.kanban.web;

import com.tuoguan.backend.auth.security.TeacherPrincipal;
import com.tuoguan.backend.kanban.service.MonthlyStatsService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.YearMonth;

@RestController
public class MonthlyStatsController {

    private final MonthlyStatsService monthlyStatsService;

    public MonthlyStatsController(MonthlyStatsService monthlyStatsService) {
        this.monthlyStatsService = monthlyStatsService;
    }

    @GetMapping("/api/students/{studentId}/monthly-stats")
    public ResponseEntity<MonthlyStatsResponse> getMonthlyStats(@AuthenticationPrincipal TeacherPrincipal principal,
                                                                  @PathVariable Long studentId,
                                                                  @RequestParam(required = false) String month) {
        YearMonth yearMonth = month != null ? YearMonth.parse(month) : YearMonth.now();
        MonthlyStatsService.MonthlyStatsResult result =
                monthlyStatsService.getMonthlyStats(principal.teacherId(), studentId, yearMonth);
        return ResponseEntity.ok(MonthlyStatsResponse.from(result));
    }
}
