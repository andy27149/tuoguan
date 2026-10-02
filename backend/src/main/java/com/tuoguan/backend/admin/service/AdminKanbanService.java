package com.tuoguan.backend.admin.service;

import com.tuoguan.backend.kanban.dao.ClassDismissalDao;
import com.tuoguan.backend.kanban.dao.DailyTaskDao;
import com.tuoguan.backend.kanban.dao.StudentArrivalCheckinDao;
import com.tuoguan.backend.kanban.dao.StudentDailyNoteDao;
import com.tuoguan.backend.kanban.domain.DailyTask;
import com.tuoguan.backend.kanban.domain.StudentArrivalCheckin;
import com.tuoguan.backend.kanban.domain.StudentDailyNote;
import com.tuoguan.backend.kanban.service.MonthlyStatsService;
import com.tuoguan.backend.roster.dao.StudentDao;
import com.tuoguan.backend.roster.domain.Student;
import com.tuoguan.backend.roster.web.NotFoundException;
import com.tuoguan.backend.unit.dao.TeachingUnitDao;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

@Service
public class AdminKanbanService {

    private final TeachingUnitDao teachingUnitDao;
    private final StudentDao studentDao;
    private final DailyTaskDao dailyTaskDao;
    private final ClassDismissalDao classDismissalDao;
    private final StudentDailyNoteDao studentDailyNoteDao;
    private final StudentArrivalCheckinDao studentArrivalCheckinDao;
    private final MonthlyStatsService monthlyStatsService;

    public AdminKanbanService(TeachingUnitDao teachingUnitDao, StudentDao studentDao, DailyTaskDao dailyTaskDao,
                               ClassDismissalDao classDismissalDao, StudentDailyNoteDao studentDailyNoteDao,
                               StudentArrivalCheckinDao studentArrivalCheckinDao,
                               MonthlyStatsService monthlyStatsService) {
        this.teachingUnitDao = teachingUnitDao;
        this.studentDao = studentDao;
        this.dailyTaskDao = dailyTaskDao;
        this.classDismissalDao = classDismissalDao;
        this.studentDailyNoteDao = studentDailyNoteDao;
        this.studentArrivalCheckinDao = studentArrivalCheckinDao;
        this.monthlyStatsService = monthlyStatsService;
    }

    public List<Student> listStudents(Long institutionId, Long teachingUnitId) {
        requireUnitInInstitution(institutionId, teachingUnitId);
        return studentDao.findAllByTeachingUnitId(teachingUnitId);
    }

    public List<DailyTask> listDailyTasks(Long institutionId, Long teachingUnitId, LocalDate date) {
        requireUnitInInstitution(institutionId, teachingUnitId);
        return dailyTaskDao.findAllByTeachingUnitIdAndDate(teachingUnitId, date);
    }

    public boolean isDismissed(Long institutionId, Long teachingUnitId, LocalDate date) {
        requireUnitInInstitution(institutionId, teachingUnitId);
        return classDismissalDao.findByTeachingUnitIdAndDate(teachingUnitId, date).isPresent();
    }

    public List<StudentDailyNote> listNotes(Long institutionId, Long teachingUnitId, LocalDate date) {
        requireUnitInInstitution(institutionId, teachingUnitId);
        return studentDailyNoteDao.findAllByTeachingUnitIdAndDate(teachingUnitId, date);
    }

    public List<StudentArrivalCheckin> listArrivals(Long institutionId, Long teachingUnitId, LocalDate date) {
        requireUnitInInstitution(institutionId, teachingUnitId);
        return studentArrivalCheckinDao.findAllByTeachingUnitIdAndDate(teachingUnitId, date);
    }

    public MonthlyStatsService.MonthlyStatsResult getMonthlyStats(Long institutionId, Long studentId, YearMonth month) {
        Student student = requireStudentInInstitution(institutionId, studentId);
        return monthlyStatsService.getMonthlyStatsForStudent(student.id(), month);
    }

    public String getShareToken(Long institutionId, Long studentId) {
        Student student = requireStudentInInstitution(institutionId, studentId);
        return studentDao.findShareToken(student.id());
    }

    private Student requireStudentInInstitution(Long institutionId, Long studentId) {
        Student student = studentDao.findById(studentId)
                .orElseThrow(() -> new NotFoundException("Student not found: " + studentId));
        if (!student.institutionId().equals(institutionId)) {
            throw new NotFoundException("Student not found: " + studentId);
        }
        return student;
    }

    private void requireUnitInInstitution(Long institutionId, Long teachingUnitId) {
        teachingUnitDao.findById(teachingUnitId)
                .filter(u -> u.institutionId().equals(institutionId))
                .orElseThrow(() -> new NotFoundException("Class not found: " + teachingUnitId));
    }
}
