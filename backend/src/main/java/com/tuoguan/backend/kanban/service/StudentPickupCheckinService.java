package com.tuoguan.backend.kanban.service;

import com.tuoguan.backend.kanban.dao.StudentPickupCheckinDao;
import com.tuoguan.backend.kanban.domain.StudentPickupCheckin;
import com.tuoguan.backend.roster.dao.StudentDao;
import com.tuoguan.backend.roster.domain.Student;
import com.tuoguan.backend.roster.service.ClassRoomService;
import com.tuoguan.backend.roster.web.NotFoundException;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;

@Service
public class StudentPickupCheckinService {

    private final StudentPickupCheckinDao studentPickupCheckinDao;
    private final StudentDao studentDao;
    private final ClassRoomService classRoomService;

    public StudentPickupCheckinService(StudentPickupCheckinDao studentPickupCheckinDao, StudentDao studentDao,
                                        ClassRoomService classRoomService) {
        this.studentPickupCheckinDao = studentPickupCheckinDao;
        this.studentDao = studentDao;
        this.classRoomService = classRoomService;
    }

    public List<StudentPickupCheckin> listForClass(Long teacherId, Long classRoomId, LocalDate date) {
        classRoomService.getOwnedByTeacher(teacherId, classRoomId);
        return studentPickupCheckinDao.findAllByClassRoomIdAndDate(classRoomId, date);
    }

    public void setPickup(Long teacherId, Long studentId, LocalDate date, String pickedUpBy, String pickedUpAt) {
        Student student = findStudentOwnedByTeacher(teacherId, studentId);
        studentPickupCheckinDao.upsert(student.institutionId(), student.classRoomId(), studentId, date, pickedUpBy,
                pickedUpAt);
    }

    private Student findStudentOwnedByTeacher(Long teacherId, Long studentId) {
        Student student = studentDao.findById(studentId)
                .orElseThrow(() -> new NotFoundException("Student not found: " + studentId));
        classRoomService.getOwnedByTeacher(teacherId, student.classRoomId());
        return student;
    }
}
