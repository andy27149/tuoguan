package com.tuoguan.backend.kanban.service;

import com.tuoguan.backend.billing.dao.StudentLeaveRecordDao;
import com.tuoguan.backend.billing.domain.StudentLeaveRecord;
import com.tuoguan.backend.roster.dao.StudentDao;
import com.tuoguan.backend.roster.domain.Student;
import com.tuoguan.backend.roster.service.ClassRoomService;
import com.tuoguan.backend.roster.web.NotFoundException;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;

@Service
public class StudentLeaveRecordService {

    private final StudentLeaveRecordDao studentLeaveRecordDao;
    private final StudentDao studentDao;
    private final ClassRoomService classRoomService;

    public StudentLeaveRecordService(StudentLeaveRecordDao studentLeaveRecordDao, StudentDao studentDao,
                                      ClassRoomService classRoomService) {
        this.studentLeaveRecordDao = studentLeaveRecordDao;
        this.studentDao = studentDao;
        this.classRoomService = classRoomService;
    }

    public List<StudentLeaveRecord> listForClass(Long teacherId, Long teachingUnitId, LocalDate date) {
        classRoomService.getOwnedByTeacher(teacherId, teachingUnitId);
        return studentLeaveRecordDao.findAllByTeachingUnitIdAndDate(teachingUnitId, date);
    }

    public void setLeave(Long teacherId, Long studentId, LocalDate date, String reason) {
        Student student = findStudentOwnedByTeacher(teacherId, studentId);
        studentLeaveRecordDao.upsert(student.institutionId(), studentId, student.teachingUnitId(), date, reason);
    }

    public void clearLeave(Long teacherId, Long studentId, LocalDate date) {
        findStudentOwnedByTeacher(teacherId, studentId);
        studentLeaveRecordDao.deleteByStudentIdAndDate(studentId, date);
    }

    private Student findStudentOwnedByTeacher(Long teacherId, Long studentId) {
        Student student = studentDao.findById(studentId)
                .orElseThrow(() -> new NotFoundException("Student not found: " + studentId));
        classRoomService.getOwnedByTeacher(teacherId, student.teachingUnitId());
        return student;
    }
}
