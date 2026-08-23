package com.tuoguan.backend.kanban.web;

import com.tuoguan.backend.kanban.service.MonthlyStatsService;

import java.util.List;

public record MonthlyStatsResponse(int completedDays, int incompleteDays, List<DailyRate> dailyRates,
                                    double averageRating) {

    public record DailyRate(String date, double rate) {

        public static DailyRate from(MonthlyStatsService.DailyRate rate) {
            return new DailyRate(rate.date(), rate.rate());
        }
    }

    public static MonthlyStatsResponse from(MonthlyStatsService.MonthlyStatsResult result) {
        return new MonthlyStatsResponse(
                result.completedDays(),
                result.incompleteDays(),
                result.dailyRates().stream().map(DailyRate::from).toList(),
                result.averageRating());
    }
}
