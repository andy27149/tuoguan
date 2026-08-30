package com.tuoguan.backend.admin.service;

import com.tuoguan.backend.kanban.dao.ClassDismissalDao;
import com.tuoguan.backend.kanban.dao.DailyTaskDao;
import com.tuoguan.backend.kanban.dao.StudentArrivalCheckinDao;
import com.tuoguan.backend.kanban.dao.StudentDailyNoteDao;
import com.tuoguan.backend.kanban.domain.DailyTask;
import com.tuoguan.backend.kanban.domain.StudentArrivalCheckin;
import com.tuoguan.backend.kanban.domain.StudentDailyNote;
import com.tuoguan.backend.kanban.service.MonthlyStatsService;
import com.tuoguan.backend.roster.dao.ClassRoomDao;
import com.tuoguan.backend.roster.dao.StudentDao;
import com.tuoguan.backend.roster.domain.Student;
import com.tuoguan.backend.roster.web.NotFoundException;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

@Service
public class AdminKanbanService {

    private final ClassRoomDao classRoomDao;
    private final StudentDao studentDao;
    private final DailyTaskDao dailyTaskDao;
    private final ClassDismissalDao classDismissalDao;
    private final StudentDailyNoteDao studentDailyNoteDao;
    private final StudentArrivalCheckinDao studentArrivalCheckinDao;
    private final MonthlyStatsService monthlyStatsService;

    public AdminKanbanService(ClassRoomDao classRoomDao, StudentDao studentDao, DailyTaskDao dailyTaskDao,
                               ClassDismissalDao classDismissalDao, StudentDailyNoteDao studentDailyNoteDao,
                               StudentArrivalCheckinDao studentArrivalCheckinDao,
                               MonthlyStatsService monthlyStatsService) {
        this.classRoomDao = classRoomDao;
        this.studentDao = studentDao;
        this.dailyTaskDao = dailyTaskDao;
        this.classDismissalDao = classDismissalDao;
        this.studentDailyNoteDao = studentDailyNoteDao;
        this.studentArrivalCheckinDao = studentArrivalCheckinDao;
        this.monthlyStatsService = monthlyStatsService;
    }

    public List<Student> listStudents(Long institutionId, Long classRoomId) {
        requireClassInInstitution(institutionId, classRoomId);
        return studentDao.findAllByClassRoomId(classRoomId);
    }

    public List<DailyTask> listDailyTasks(Long institutionId, Long classRoomId, LocalDate date) {
        requireClassInInstitution(institutionId, classRoomId);
        return dailyTaskDao.findAllByClassRoomIdAndDate(classRoomId, date);
    }

    public boolean isDismissed(Long institutionId, Long classRoomId, LocalDate date) {
        requireClassInInstitution(institutionId, classRoomId);
        return classDismissalDao.findByClassRoomIdAndDate(classRoomId, date).isPresent();
    }

    public List<StudentDailyNote> listNotes(Long institutionId, Long classRoomId, LocalDate date) {
        requireClassInInstitution(institutionId, classRoomId);
        return studentDailyNoteDao.findAllByClassRoomIdAndDate(classRoomId, date);
    }

    public List<StudentArrivalCheckin> listArrivals(Long institutionId, Long classRoomId, LocalDate date) {
        requireClassInInstitution(institutionId, classRoomId);
        return studentArrivalCheckinDao.findAllByClassRoomIdAndDate(classRoomId, date);
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

    private void requireClassInInstitution(Long institutionId, Long classRoomId) {
        classRoomDao.findById(classRoomId)
                .filter(c -> c.institutionId().equals(institutionId))
                .orElseThrow(() -> new NotFoundException("Class not found: " + classRoomId));
    }
}
