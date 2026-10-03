package com.tuoguan.backend.share.service;

import com.tuoguan.backend.course.service.CourseAccountService;
import com.tuoguan.backend.course.web.CourseActivityRow;
import com.tuoguan.backend.course.web.StudentCourseStatement;
import com.tuoguan.backend.kanban.service.MonthlyStatsService;
import com.tuoguan.backend.roster.dao.StudentDao;
import com.tuoguan.backend.roster.domain.Student;
import com.tuoguan.backend.roster.web.NotFoundException;
import com.tuoguan.backend.storage.StorageService;
import org.springframework.stereotype.Service;

import java.time.YearMonth;
import java.util.List;

@Service
public class PublicShareService {

    private final StudentDao studentDao;
    private final StorageService storageService;
    private final MonthlyStatsService monthlyStatsService;
    private final CourseAccountService courseAccountService;

    public PublicShareService(StudentDao studentDao, StorageService storageService,
                               MonthlyStatsService monthlyStatsService, CourseAccountService courseAccountService) {
        this.studentDao = studentDao;
        this.storageService = storageService;
        this.monthlyStatsService = monthlyStatsService;
        this.courseAccountService = courseAccountService;
    }

    public PublicShareResult getShare(String token, YearMonth month) {
        Student student = studentDao.findByShareToken(token)
                .orElseThrow(() -> new NotFoundException("Share link not found: " + token));
        String avatarUrl = storageService.avatarUrl(student.avatarObjectKey());

        if (student.teachingUnitId() == null) {
            StudentCourseStatement courseStatement =
                    courseAccountService.getStatement(student.institutionId(), student.id());
            return new PublicShareResult(student.name(), student.schoolClassName(), avatarUrl, null,
                    courseStatement, List.of());
        }

        // 托管班学生：月度统计必有；若同时报名了课外课，附带一张不含充值/余额的轻量课外课
        // 情况卡片（这类学生的课外课费用走托管月度账单附加费，余额概念不成立，见诊断 #06）。
        MonthlyStatsService.MonthlyStatsResult stats =
                monthlyStatsService.getMonthlyStatsForStudent(student.id(), month);
        List<CourseActivityRow> courseActivity =
                courseAccountService.getCourseActivity(student.institutionId(), student.id());
        return new PublicShareResult(student.name(), student.schoolClassName(), avatarUrl, stats, null,
                courseActivity);
    }

    public record PublicShareResult(String studentName, String schoolClassName, String avatarUrl,
                                     MonthlyStatsService.MonthlyStatsResult stats,
                                     StudentCourseStatement courseStatement,
                                     List<CourseActivityRow> courseActivity) {
    }
}
