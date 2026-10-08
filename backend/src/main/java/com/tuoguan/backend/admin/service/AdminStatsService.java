package com.tuoguan.backend.admin.service;

import com.tuoguan.backend.admin.web.AdminDashboardResponse.ClassSummary;
import com.tuoguan.backend.admin.web.EnrollmentSummary;
import com.tuoguan.backend.admin.web.TeacherStudentCount;
import com.tuoguan.backend.auth.dao.TeacherDao;
import com.tuoguan.backend.auth.domain.Teacher;
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
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class AdminStatsService {

    private final TeachingUnitDao teachingUnitDao;
    private final StudentDao studentDao;
    private final DailyTaskDao dailyTaskDao;
    private final StudentArrivalCheckinDao studentArrivalCheckinDao;
    private final TeacherDao teacherDao;

    public AdminStatsService(TeachingUnitDao teachingUnitDao, StudentDao studentDao, DailyTaskDao dailyTaskDao,
                              StudentArrivalCheckinDao studentArrivalCheckinDao, TeacherDao teacherDao) {
        this.teachingUnitDao = teachingUnitDao;
        this.studentDao = studentDao;
        this.dailyTaskDao = dailyTaskDao;
        this.studentArrivalCheckinDao = studentArrivalCheckinDao;
        this.teacherDao = teacherDao;
    }

    public EnrollmentSummary getEnrollmentSummary(Long institutionId) {
        List<Student> enrolledStudents = studentDao.findAllByInstitutionId(institutionId).stream()
                .filter(Student::enrolled)
                .toList();

        int custodyCount = (int) enrolledStudents.stream().filter(s -> s.teachingUnitId() != null).count();
        int offCampusOnlyCount = enrolledStudents.size() - custodyCount;

        Map<Long, TeachingUnit> unitsById = teachingUnitDao.findAllByInstitutionId(institutionId).stream()
                .collect(Collectors.toMap(TeachingUnit::id, u -> u));

        Map<Long, Long> studentCountByTeacherId = enrolledStudents.stream()
                .filter(s -> s.teachingUnitId() != null)
                .map(s -> unitsById.get(s.teachingUnitId()))
                .filter(unit -> unit != null)
                .collect(Collectors.groupingBy(TeachingUnit::teacherId, Collectors.counting()));

        List<TeacherStudentCount> byTeacher = studentCountByTeacherId.entrySet().stream()
                .map(entry -> new TeacherStudentCount(
                        teacherDao.findById(entry.getKey()).map(Teacher::name).orElse("-"),
                        entry.getValue().intValue()))
                .sorted(Comparator.comparingInt(TeacherStudentCount::studentCount).reversed())
                .toList();

        return new EnrollmentSummary(enrolledStudents.size(), custodyCount, offCampusOnlyCount, byTeacher);
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
