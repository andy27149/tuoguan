package com.tuoguan.backend.roster.service;

import com.tuoguan.backend.roster.dao.StudentDao;
import com.tuoguan.backend.roster.domain.ClassRoom;
import com.tuoguan.backend.roster.domain.Student;
import com.tuoguan.backend.roster.web.NotFoundException;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class StudentService {

    private final StudentDao studentDao;
    private final ClassRoomService classRoomService;

    public StudentService(StudentDao studentDao, ClassRoomService classRoomService) {
        this.studentDao = studentDao;
        this.classRoomService = classRoomService;
    }

    public Student create(Long teacherId, Long classRoomId, String name, String schoolClassName) {
        ClassRoom classRoom = classRoomService.getOwnedByTeacher(teacherId, classRoomId);
        Student student = new Student(null, classRoom.institutionId(), classRoom.id(), name, schoolClassName,
                true, null);
        Long id = studentDao.insert(student);
        return studentDao.findById(id)
                .orElseThrow(() -> new IllegalStateException("Student not found after insert: " + id));
    }

    public List<Student> list(Long teacherId, Long classRoomId) {
        classRoomService.getOwnedByTeacher(teacherId, classRoomId);
        return studentDao.findAllByClassRoomId(classRoomId);
    }

    public Student update(Long teacherId, Long studentId, String name, String schoolClassName, boolean enrolled) {
        Student existing = findOwnedByTeacher(teacherId, studentId);
        Student updated = new Student(existing.id(), existing.institutionId(), existing.classRoomId(),
                name, schoolClassName, enrolled, existing.createdAt());
        studentDao.update(updated);
        return studentDao.findById(existing.id())
                .orElseThrow(() -> new IllegalStateException("Student not found after update: " + existing.id()));
    }

    public void delete(Long teacherId, Long studentId) {
        Student existing = findOwnedByTeacher(teacherId, studentId);
        studentDao.deleteById(existing.id());
    }

    private Student findOwnedByTeacher(Long teacherId, Long studentId) {
        Student student = studentDao.findById(studentId)
                .orElseThrow(() -> new NotFoundException("Student not found: " + studentId));
        classRoomService.getOwnedByTeacher(teacherId, student.classRoomId());
        return student;
    }
}
