package com.tuoguan.backend.share.service;

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

    public PublicShareService(StudentDao studentDao, StorageService storageService,
                               MonthlyStatsService monthlyStatsService) {
        this.studentDao = studentDao;
        this.storageService = storageService;
        this.monthlyStatsService = monthlyStatsService;
    }

    public PublicShareResult getShare(String token, YearMonth month) {
        Student student = studentDao.findByShareToken(token)
                .orElseThrow(() -> new NotFoundException("Share link not found: " + token));
        String avatarUrl = storageService.avatarUrl(student.avatarObjectKey());
        MonthlyStatsService.MonthlyStatsResult stats =
                monthlyStatsService.getMonthlyStatsForStudent(student.id(), month);
        return new PublicShareResult(student.name(), student.schoolClassName(), avatarUrl, stats);
    }

    public record PublicShareResult(String studentName, String schoolClassName, String avatarUrl,
                                     MonthlyStatsService.MonthlyStatsResult stats) {
    }
}
