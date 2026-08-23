package com.tuoguan.backend.admin.service;

import com.tuoguan.backend.admin.web.AdminDashboardResponse.ClassSummary;
import com.tuoguan.backend.kanban.dao.DailyTaskDao;
import com.tuoguan.backend.kanban.domain.DailyTask;
import com.tuoguan.backend.roster.dao.ClassRoomDao;
import com.tuoguan.backend.roster.dao.StudentDao;
import com.tuoguan.backend.roster.domain.ClassRoom;
import com.tuoguan.backend.roster.domain.Student;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class AdminStatsService {

    private final ClassRoomDao classRoomDao;
    private final StudentDao studentDao;
    private final DailyTaskDao dailyTaskDao;

    public AdminStatsService(ClassRoomDao classRoomDao, StudentDao studentDao, DailyTaskDao dailyTaskDao) {
        this.classRoomDao = classRoomDao;
        this.studentDao = studentDao;
        this.dailyTaskDao = dailyTaskDao;
    }

    public List<ClassSummary> getDashboard(Long institutionId, LocalDate date) {
        return classRoomDao.findAllByInstitutionId(institutionId).stream()
                .map(classRoom -> buildSummary(classRoom, date))
                .toList();
    }

    private ClassSummary buildSummary(ClassRoom classRoom, LocalDate date) {
        List<Student> enrolledStudents = studentDao.findAllByClassRoomId(classRoom.id()).stream()
                .filter(Student::enrolled)
                .toList();
        List<DailyTask> tasks = dailyTaskDao.findAllByClassRoomIdAndDate(classRoom.id(), date);
        Map<Long, List<DailyTask>> tasksByStudent = tasks.stream()
                .collect(Collectors.groupingBy(DailyTask::studentId));

        int completedStudentCount = (int) enrolledStudents.stream()
                .filter(student -> {
                    List<DailyTask> studentTasks = tasksByStudent.get(student.id());
                    return studentTasks != null && !studentTasks.isEmpty()
                            && studentTasks.stream().allMatch(DailyTask::completed);
                })
                .count();

        return new ClassSummary(classRoom.id(), classRoom.name(), enrolledStudents.size(), completedStudentCount);
    }
}
