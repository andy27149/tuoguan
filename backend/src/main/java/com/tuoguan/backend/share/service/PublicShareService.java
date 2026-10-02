package com.tuoguan.backend.share.service;

import com.tuoguan.backend.course.service.CourseAccountService;
import com.tuoguan.backend.course.web.StudentCourseStatement;
import com.tuoguan.backend.kanban.service.MonthlyStatsService;
import com.tuoguan.backend.roster.dao.StudentDao;
import com.tuoguan.backend.roster.domain.Student;
import com.tuoguan.backend.roster.web.NotFoundException;
import com.tuoguan.backend.storage.StorageService;
import org.springframework.stereotype.Service;

import java.time.YearMonth;

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
                    courseStatement);
        }

        MonthlyStatsService.MonthlyStatsResult stats =
                monthlyStatsService.getMonthlyStatsForStudent(student.id(), month);
        return new PublicShareResult(student.name(), student.schoolClassName(), avatarUrl, stats, null);
    }

    public record PublicShareResult(String studentName, String schoolClassName, String avatarUrl,
                                     MonthlyStatsService.MonthlyStatsResult stats,
                                     StudentCourseStatement courseStatement) {
    }
}
