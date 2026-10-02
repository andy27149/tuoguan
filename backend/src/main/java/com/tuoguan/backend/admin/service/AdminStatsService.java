package com.tuoguan.backend.admin.service;

import com.tuoguan.backend.admin.web.AdminDashboardResponse.ClassSummary;
import com.tuoguan.backend.kanban.dao.DailyTaskDao;
import com.tuoguan.backend.kanban.dao.StudentArrivalCheckinDao;
import com.tuoguan.backend.kanban.domain.DailyTask;
import com.tuoguan.backend.roster.dao.StudentDao;
import com.tuoguan.backend.roster.domain.Student;
import com.tuoguan.backend.unit.dao.TeachingUnitDao;
import com.tuoguan.backend.unit.domain.BillingMode;
import com.tuoguan.backend.unit.domain.TeachingUnit;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class AdminStatsService {

    private final TeachingUnitDao teachingUnitDao;
    private final StudentDao studentDao;
    private final DailyTaskDao dailyTaskDao;
    private final StudentArrivalCheckinDao studentArrivalCheckinDao;

    public AdminStatsService(TeachingUnitDao teachingUnitDao, StudentDao studentDao, DailyTaskDao dailyTaskDao,
                              StudentArrivalCheckinDao studentArrivalCheckinDao) {
        this.teachingUnitDao = teachingUnitDao;
        this.studentDao = studentDao;
        this.dailyTaskDao = dailyTaskDao;
        this.studentArrivalCheckinDao = studentArrivalCheckinDao;
    }

    public List<ClassSummary> getDashboard(Long institutionId, LocalDate date) {
        return teachingUnitDao.findAllByInstitutionId(institutionId).stream()
                .filter(unit -> unit.billingMode() == BillingMode.MONTHLY)
                .map(unit -> buildSummary(unit, date))
                .toList();
    }

    private ClassSummary buildSummary(TeachingUnit teachingUnit, LocalDate date) {
        List<Student> enrolledStudents = studentDao.findAllByTeachingUnitId(teachingUnit.id()).stream()
                .filter(Student::enrolled)
                .toList();
        List<DailyTask> tasks = dailyTaskDao.findAllByTeachingUnitIdAndDate(teachingUnit.id(), date);
        Map<Long, List<DailyTask>> tasksByStudent = tasks.stream()
                .collect(Collectors.groupingBy(DailyTask::studentId));

        int completedStudentCount = (int) enrolledStudents.stream()
                .filter(student -> {
                    List<DailyTask> studentTasks = tasksByStudent.get(student.id());
                    return studentTasks != null && !studentTasks.isEmpty()
                            && studentTasks.stream().allMatch(DailyTask::completed);
                })
                .count();

        int checkinCount = studentArrivalCheckinDao.findAllByTeachingUnitIdAndDate(teachingUnit.id(), date).size();

        return new ClassSummary(teachingUnit.id(), teachingUnit.name(), enrolledStudents.size(), checkinCount,
                completedStudentCount);
    }
}
