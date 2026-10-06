package com.tuoguan.backend.kanban.service;

import com.tuoguan.backend.kanban.dao.StudentMealRecordDao;
import com.tuoguan.backend.kanban.domain.StudentMealRecord;
import com.tuoguan.backend.roster.dao.StudentDao;
import com.tuoguan.backend.roster.domain.Student;
import com.tuoguan.backend.roster.service.ClassRoomService;
import com.tuoguan.backend.roster.web.NotFoundException;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;

@Service
public class StudentMealRecordService {

    private final StudentMealRecordDao studentMealRecordDao;
    private final StudentDao studentDao;
    private final ClassRoomService classRoomService;

    public StudentMealRecordService(StudentMealRecordDao studentMealRecordDao, StudentDao studentDao,
                                     ClassRoomService classRoomService) {
        this.studentMealRecordDao = studentMealRecordDao;
        this.studentDao = studentDao;
        this.classRoomService = classRoomService;
    }

    public List<StudentMealRecord> listForClass(Long teacherId, Long teachingUnitId, LocalDate date) {
        classRoomService.getOwnedByTeacher(teacherId, teachingUnitId);
        return studentMealRecordDao.findAllByTeachingUnitIdAndDate(teachingUnitId, date);
    }

    public void setMeal(Long teacherId, Long studentId, LocalDate date) {
        Student student = findStudentOwnedByTeacher(teacherId, studentId);
        studentMealRecordDao.upsert(student.institutionId(), student.teachingUnitId(), studentId, date);
    }

    public void clearMeal(Long teacherId, Long studentId, LocalDate date) {
        findStudentOwnedByTeacher(teacherId, studentId);
        studentMealRecordDao.clear(studentId, date);
    }

    private Student findStudentOwnedByTeacher(Long teacherId, Long studentId) {
        Student student = studentDao.findById(studentId)
                .orElseThrow(() -> new NotFoundException("Student not found: " + studentId));
        classRoomService.getOwnedByTeacher(teacherId, student.teachingUnitId());
        return student;
    }
}
