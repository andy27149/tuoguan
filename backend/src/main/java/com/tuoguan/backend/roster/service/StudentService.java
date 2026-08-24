package com.tuoguan.backend.roster.service;

import com.tuoguan.backend.roster.dao.StudentDao;
import com.tuoguan.backend.roster.domain.ClassRoom;
import com.tuoguan.backend.roster.domain.Student;
import com.tuoguan.backend.roster.web.InvalidAvatarException;
import com.tuoguan.backend.roster.web.NotFoundException;
import com.tuoguan.backend.storage.StorageService;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;

@Service
public class StudentService {

    private static final Set<String> ALLOWED_AVATAR_CONTENT_TYPES = Set.of("image/jpeg", "image/png", "image/webp");

    private final StudentDao studentDao;
    private final ClassRoomService classRoomService;
    private final StorageService storageService;

    public StudentService(StudentDao studentDao, ClassRoomService classRoomService, StorageService storageService) {
        this.studentDao = studentDao;
        this.classRoomService = classRoomService;
        this.storageService = storageService;
    }

    public Student create(Long teacherId, Long classRoomId, String name, String schoolClassName) {
        ClassRoom classRoom = classRoomService.getOwnedByTeacher(teacherId, classRoomId);
        Student student = new Student(null, classRoom.institutionId(), classRoom.id(), name, schoolClassName,
                true, null, null);
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
                name, schoolClassName, enrolled, existing.avatarObjectKey(), existing.createdAt());
        studentDao.update(updated);
        return studentDao.findById(existing.id())
                .orElseThrow(() -> new IllegalStateException("Student not found after update: " + existing.id()));
    }

    public Student updateAvatar(Long teacherId, Long studentId, String contentType, byte[] content) {
        if (!ALLOWED_AVATAR_CONTENT_TYPES.contains(contentType)) {
            throw new InvalidAvatarException("Unsupported avatar content type: " + contentType);
        }
        Student existing = findOwnedByTeacher(teacherId, studentId);
        String objectKey = storageService.uploadAvatar(existing.institutionId(), existing.id(), contentType, content);
        studentDao.updateAvatarObjectKey(existing.id(), objectKey);
        String previousObjectKey = existing.avatarObjectKey();
        if (previousObjectKey != null) {
            storageService.delete(previousObjectKey);
        }
        return studentDao.findById(existing.id())
                .orElseThrow(() -> new IllegalStateException("Student not found after avatar update: " + existing.id()));
    }

    public String avatarUrl(Student student) {
        return storageService.avatarUrl(student.avatarObjectKey());
    }

    private Student findOwnedByTeacher(Long teacherId, Long studentId) {
        Student student = studentDao.findById(studentId)
                .orElseThrow(() -> new NotFoundException("Student not found: " + studentId));
        classRoomService.getOwnedByTeacher(teacherId, student.classRoomId());
        return student;
    }
}
