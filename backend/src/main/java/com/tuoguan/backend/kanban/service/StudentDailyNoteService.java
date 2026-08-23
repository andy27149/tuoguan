package com.tuoguan.backend.kanban.service;

import com.tuoguan.backend.kanban.dao.StudentDailyNoteDao;
import com.tuoguan.backend.kanban.domain.StudentDailyNote;
import com.tuoguan.backend.roster.dao.StudentDao;
import com.tuoguan.backend.roster.domain.Student;
import com.tuoguan.backend.roster.service.ClassRoomService;
import com.tuoguan.backend.roster.web.NotFoundException;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;

@Service
public class StudentDailyNoteService {

    private final StudentDailyNoteDao studentDailyNoteDao;
    private final StudentDao studentDao;
    private final ClassRoomService classRoomService;

    public StudentDailyNoteService(StudentDailyNoteDao studentDailyNoteDao, StudentDao studentDao,
                                    ClassRoomService classRoomService) {
        this.studentDailyNoteDao = studentDailyNoteDao;
        this.studentDao = studentDao;
        this.classRoomService = classRoomService;
    }

    public List<StudentDailyNote> listForClass(Long teacherId, Long classRoomId, LocalDate date) {
        classRoomService.getOwnedByTeacher(teacherId, classRoomId);
        return studentDailyNoteDao.findAllByClassRoomIdAndDate(classRoomId, date);
    }

    public void setRating(Long teacherId, Long studentId, LocalDate date, int rating) {
        Student student = findStudentOwnedByTeacher(teacherId, studentId);
        studentDailyNoteDao.upsertRating(student.institutionId(), student.classRoomId(), studentId, date, rating);
    }

    public void setComment(Long teacherId, Long studentId, LocalDate date, String comment) {
        Student student = findStudentOwnedByTeacher(teacherId, studentId);
        studentDailyNoteDao.upsertComment(student.institutionId(), student.classRoomId(), studentId, date, comment);
    }

    private Student findStudentOwnedByTeacher(Long teacherId, Long studentId) {
        Student student = studentDao.findById(studentId)
                .orElseThrow(() -> new NotFoundException("Student not found: " + studentId));
        classRoomService.getOwnedByTeacher(teacherId, student.classRoomId());
        return student;
    }
}
