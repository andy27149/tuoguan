package com.tuoguan.backend.kanban.service;

import com.tuoguan.backend.kanban.dao.StudentArrivalCheckinDao;
import com.tuoguan.backend.kanban.domain.StudentArrivalCheckin;
import com.tuoguan.backend.roster.dao.StudentDao;
import com.tuoguan.backend.roster.domain.Student;
import com.tuoguan.backend.roster.service.ClassRoomService;
import com.tuoguan.backend.roster.web.NotFoundException;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;

@Service
public class StudentArrivalCheckinService {

    private final StudentArrivalCheckinDao studentArrivalCheckinDao;
    private final StudentDao studentDao;
    private final ClassRoomService classRoomService;

    public StudentArrivalCheckinService(StudentArrivalCheckinDao studentArrivalCheckinDao, StudentDao studentDao,
                                         ClassRoomService classRoomService) {
        this.studentArrivalCheckinDao = studentArrivalCheckinDao;
        this.studentDao = studentDao;
        this.classRoomService = classRoomService;
    }

    public List<StudentArrivalCheckin> listForClass(Long teacherId, Long classRoomId, LocalDate date) {
        classRoomService.getOwnedByTeacher(teacherId, classRoomId);
        return studentArrivalCheckinDao.findAllByClassRoomIdAndDate(classRoomId, date);
    }

    public void setArrival(Long teacherId, Long studentId, LocalDate date, String arrivedAt) {
        Student student = findStudentOwnedByTeacher(teacherId, studentId);
        studentArrivalCheckinDao.upsert(student.institutionId(), student.classRoomId(), studentId, date, arrivedAt);
    }

    public void clearArrival(Long teacherId, Long studentId, LocalDate date) {
        findStudentOwnedByTeacher(teacherId, studentId);
        studentArrivalCheckinDao.clear(studentId, date);
    }

    private Student findStudentOwnedByTeacher(Long teacherId, Long studentId) {
        Student student = studentDao.findById(studentId)
                .orElseThrow(() -> new NotFoundException("Student not found: " + studentId));
        classRoomService.getOwnedByTeacher(teacherId, student.classRoomId());
        return student;
    }
}
