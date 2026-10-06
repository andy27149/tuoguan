package com.tuoguan.backend.kanban.service;

import com.tuoguan.backend.kanban.dao.DailyTaskDao;
import com.tuoguan.backend.kanban.domain.DailyTask;
import com.tuoguan.backend.roster.dao.StudentDao;
import com.tuoguan.backend.roster.dao.TaskTemplateDao;
import com.tuoguan.backend.roster.domain.Student;
import com.tuoguan.backend.roster.domain.TaskTemplate;
import com.tuoguan.backend.roster.service.ClassRoomService;
import com.tuoguan.backend.roster.web.NotFoundException;
import com.tuoguan.backend.unit.domain.TeachingUnit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

@Service
public class DailyTaskService {

    private final DailyTaskDao dailyTaskDao;
    private final StudentDao studentDao;
    private final TaskTemplateDao taskTemplateDao;
    private final ClassRoomService classRoomService;

    public DailyTaskService(DailyTaskDao dailyTaskDao, StudentDao studentDao, TaskTemplateDao taskTemplateDao,
                             ClassRoomService classRoomService) {
        this.dailyTaskDao = dailyTaskDao;
        this.studentDao = studentDao;
        this.taskTemplateDao = taskTemplateDao;
        this.classRoomService = classRoomService;
    }

    @Transactional
    public List<DailyTask> batchAssign(Long teacherId, Long teachingUnitId, List<Long> taskTemplateIds,
                                        LocalDate date) {
        TeachingUnit teachingUnit = classRoomService.getOwnedByTeacher(teacherId, teachingUnitId);
        List<TaskTemplate> templates = taskTemplateIds.stream()
                .map(id -> taskTemplateDao.findById(id)
                        .filter(t -> t.institutionId().equals(teachingUnit.institutionId()))
                        .filter(t -> t.teacherId() != null && t.teacherId().equals(teacherId))
                        .orElseThrow(() -> new NotFoundException("Task template not found: " + id)))
                .toList();
        List<Student> enrolledStudents = studentDao.findAllByTeachingUnitId(teachingUnitId).stream()
                .filter(Student::enrolled)
                .toList();

        return enrolledStudents.stream()
                .flatMap(student -> templates.stream().map(template -> insertDailyTask(
                        teachingUnit.institutionId(), teachingUnitId, student.id(), date,
                        template.id(), template.subject(), template.name(), false)))
                .toList();
    }

    public DailyTask addForStudent(Long teacherId, Long studentId, Long taskTemplateId, String subject, String name,
                                    LocalDate date) {
        Student student = findStudentOwnedByTeacher(teacherId, studentId);

        String taskSubject;
        String taskName;
        boolean custom;
        if (taskTemplateId != null) {
            TaskTemplate template = taskTemplateDao.findById(taskTemplateId)
                    .filter(t -> t.institutionId().equals(student.institutionId()))
                    .filter(t -> t.teacherId() != null && t.teacherId().equals(teacherId))
                    .orElseThrow(() -> new NotFoundException("Task template not found: " + taskTemplateId));
            taskSubject = template.subject();
            taskName = template.name();
            custom = false;
        } else {
            taskSubject = subject;
            taskName = name;
            custom = true;
        }

        return insertDailyTask(student.institutionId(), student.teachingUnitId(), student.id(), date,
                taskTemplateId, taskSubject, taskName, custom);
    }

    public List<DailyTask> listForClass(Long teacherId, Long teachingUnitId, LocalDate date) {
        classRoomService.getOwnedByTeacher(teacherId, teachingUnitId);
        return dailyTaskDao.findAllByTeachingUnitIdAndDate(teachingUnitId, date);
    }

    public DailyTask setCompleted(Long teacherId, Long dailyTaskId, boolean completed) {
        DailyTask dailyTask = findOwnedByTeacher(teacherId, dailyTaskId);
        dailyTaskDao.updateCompleted(dailyTask.id(), completed);
        return dailyTaskDao.findById(dailyTask.id())
                .orElseThrow(() -> new IllegalStateException("Daily task not found after update: " + dailyTask.id()));
    }

    public void delete(Long teacherId, Long dailyTaskId) {
        DailyTask dailyTask = findOwnedByTeacher(teacherId, dailyTaskId);
        dailyTaskDao.deleteById(dailyTask.id());
    }

    private DailyTask insertDailyTask(Long institutionId, Long teachingUnitId, Long studentId, LocalDate date,
                                       Long taskTemplateId, String subject, String name, boolean custom) {
        DailyTask dailyTask = new DailyTask(null, institutionId, teachingUnitId, studentId, date,
                taskTemplateId, subject, name, custom, false, null);
        Long id = dailyTaskDao.insert(dailyTask);
        return dailyTaskDao.findById(id)
                .orElseThrow(() -> new IllegalStateException("Daily task not found after insert: " + id));
    }

    private Student findStudentOwnedByTeacher(Long teacherId, Long studentId) {
        Student student = studentDao.findById(studentId)
                .orElseThrow(() -> new NotFoundException("Student not found: " + studentId));
        classRoomService.getOwnedByTeacher(teacherId, student.teachingUnitId());
        return student;
    }

    private DailyTask findOwnedByTeacher(Long teacherId, Long dailyTaskId) {
        DailyTask dailyTask = dailyTaskDao.findById(dailyTaskId)
                .orElseThrow(() -> new NotFoundException("Daily task not found: " + dailyTaskId));
        classRoomService.getOwnedByTeacher(teacherId, dailyTask.teachingUnitId());
        return dailyTask;
    }
}
