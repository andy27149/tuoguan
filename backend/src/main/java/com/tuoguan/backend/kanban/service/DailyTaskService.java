package com.tuoguan.backend.kanban.service;

import com.tuoguan.backend.kanban.dao.DailyTaskDao;
import com.tuoguan.backend.kanban.domain.DailyTask;
import com.tuoguan.backend.roster.dao.StudentDao;
import com.tuoguan.backend.roster.dao.TaskTemplateDao;
import com.tuoguan.backend.roster.domain.ClassRoom;
import com.tuoguan.backend.roster.domain.Student;
import com.tuoguan.backend.roster.domain.TaskTemplate;
import com.tuoguan.backend.roster.service.ClassRoomService;
import com.tuoguan.backend.roster.web.NotFoundException;
import org.springframework.stereotype.Service;

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

    public List<DailyTask> batchAssign(Long teacherId, Long classRoomId, List<Long> taskTemplateIds,
                                        LocalDate date) {
        ClassRoom classRoom = classRoomService.getOwnedByTeacher(teacherId, classRoomId);
        List<TaskTemplate> templates = taskTemplateIds.stream()
                .map(id -> taskTemplateDao.findById(id)
                        .filter(t -> t.institutionId().equals(classRoom.institutionId()))
                        .orElseThrow(() -> new NotFoundException("Task template not found: " + id)))
                .toList();
        List<Student> enrolledStudents = studentDao.findAllByClassRoomId(classRoomId).stream()
                .filter(Student::enrolled)
                .toList();

        return enrolledStudents.stream()
                .flatMap(student -> templates.stream().map(template -> insertDailyTask(
                        classRoom.institutionId(), classRoomId, student.id(), date,
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
                    .orElseThrow(() -> new NotFoundException("Task template not found: " + taskTemplateId));
            taskSubject = template.subject();
            taskName = template.name();
            custom = false;
        } else {
            taskSubject = subject;
            taskName = name;
            custom = true;
        }

        DailyTask created = insertDailyTask(student.institutionId(), student.classRoomId(), student.id(), date,
                taskTemplateId, taskSubject, taskName, custom);

        studentDao.findAllByClassRoomId(student.classRoomId()).stream()
                .filter(Student::enrolled)
                .filter(peer -> !peer.id().equals(student.id()))
                .filter(peer -> peer.schoolClassName().equals(student.schoolClassName()))
                .forEach(peer -> insertDailyTask(peer.institutionId(), peer.classRoomId(), peer.id(), date,
                        taskTemplateId, taskSubject, taskName, custom));

        return created;
    }

    public List<DailyTask> listForClass(Long teacherId, Long classRoomId, LocalDate date) {
        classRoomService.getOwnedByTeacher(teacherId, classRoomId);
        return dailyTaskDao.findAllByClassRoomIdAndDate(classRoomId, date);
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

    private DailyTask insertDailyTask(Long institutionId, Long classRoomId, Long studentId, LocalDate date,
                                       Long taskTemplateId, String subject, String name, boolean custom) {
        DailyTask dailyTask = new DailyTask(null, institutionId, classRoomId, studentId, date,
                taskTemplateId, subject, name, custom, false, null);
        Long id = dailyTaskDao.insert(dailyTask);
        return dailyTaskDao.findById(id)
                .orElseThrow(() -> new IllegalStateException("Daily task not found after insert: " + id));
    }

    private Student findStudentOwnedByTeacher(Long teacherId, Long studentId) {
        Student student = studentDao.findById(studentId)
                .orElseThrow(() -> new NotFoundException("Student not found: " + studentId));
        classRoomService.getOwnedByTeacher(teacherId, student.classRoomId());
        return student;
    }

    private DailyTask findOwnedByTeacher(Long teacherId, Long dailyTaskId) {
        DailyTask dailyTask = dailyTaskDao.findById(dailyTaskId)
                .orElseThrow(() -> new NotFoundException("Daily task not found: " + dailyTaskId));
        classRoomService.getOwnedByTeacher(teacherId, dailyTask.classRoomId());
        return dailyTask;
    }
}
