package com.tuoguan.backend.kanban.web;

import com.tuoguan.backend.kanban.service.MonthlyStatsService;

import java.util.List;

public record MonthlyStatsResponse(int completedDays, int incompleteDays, List<DailyRate> dailyRates,
                                    double averageRating, List<DailyRating> dailyRatings, List<DayDetail> days) {

    public record DailyRate(String date, double rate) {

        public static DailyRate from(MonthlyStatsService.DailyRate rate) {
            return new DailyRate(rate.date(), rate.rate());
        }
    }

    public record DailyRating(String date, int rating) {

        public static DailyRating from(MonthlyStatsService.DailyRating rating) {
            return new DailyRating(rating.date(), rating.rating());
        }
    }

    public record DayTask(Long id, String subject, String name, boolean completed) {

        public static DayTask from(MonthlyStatsService.DayTask task) {
            return new DayTask(task.id(), task.subject(), task.name(), task.completed());
        }
    }

    public record DayDetail(String date, List<DayTask> tasks, int rating, String comment, String arrivedAt) {

        public static DayDetail from(MonthlyStatsService.DayDetail detail) {
            return new DayDetail(detail.date(), detail.tasks().stream().map(DayTask::from).toList(),
                    detail.rating(), detail.comment(), detail.arrivedAt());
        }
    }

    public static MonthlyStatsResponse from(MonthlyStatsService.MonthlyStatsResult result) {
        return new MonthlyStatsResponse(
                result.completedDays(),
                result.incompleteDays(),
                result.dailyRates().stream().map(DailyRate::from).toList(),
                result.averageRating(),
                result.dailyRatings().stream().map(DailyRating::from).toList(),
                result.days().stream().map(DayDetail::from).toList());
    }
}
