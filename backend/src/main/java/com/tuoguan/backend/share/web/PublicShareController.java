package com.tuoguan.backend.share.web;

import com.tuoguan.backend.kanban.web.MonthlyStatsResponse;
import com.tuoguan.backend.share.service.PublicShareService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.YearMonth;

@RestController
public class PublicShareController {

    private final PublicShareService publicShareService;

    public PublicShareController(PublicShareService publicShareService) {
        this.publicShareService = publicShareService;
    }

    @GetMapping("/api/public/share/{token}")
    public PublicShareResponse getShare(@PathVariable String token,
                                         @RequestParam(required = false) String month) {
        YearMonth yearMonth = month != null ? YearMonth.parse(month) : YearMonth.now();
        PublicShareService.PublicShareResult result = publicShareService.getShare(token, yearMonth);
        return new PublicShareResponse(result.studentName(), result.schoolClassName(), result.avatarUrl(),
                MonthlyStatsResponse.from(result.stats()));
    }
}
