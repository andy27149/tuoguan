package com.tuoguan.backend.roster.service;

import com.tuoguan.backend.roster.dao.StudentDao;
import com.tuoguan.backend.roster.domain.Student;
import com.tuoguan.backend.unit.dao.StudentUnitEnrollmentDao;
import com.tuoguan.backend.unit.dao.TeachingUnitDao;
import com.tuoguan.backend.unit.domain.StudentUnitEnrollment;
import com.tuoguan.backend.unit.domain.TeachingUnit;
import com.tuoguan.backend.roster.web.InvalidAvatarException;
import com.tuoguan.backend.roster.web.NotFoundException;
import com.tuoguan.backend.storage.StorageService;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;
import java.util.Set;

@Service
public class StudentService {

    private static final Set<String> ALLOWED_AVATAR_CONTENT_TYPES = Set.of("image/jpeg", "image/png", "image/webp");

    private final StudentDao studentDao;
    private final ClassRoomService classRoomService;
    private final StorageService storageService;
    private final StudentUnitEnrollmentDao enrollmentDao;
    private final TeachingUnitDao teachingUnitDao;

    public StudentService(StudentDao studentDao, ClassRoomService classRoomService, StorageService storageService,
                           StudentUnitEnrollmentDao enrollmentDao, TeachingUnitDao teachingUnitDao) {
        this.studentDao = studentDao;
        this.classRoomService = classRoomService;
        this.storageService = storageService;
        this.enrollmentDao = enrollmentDao;
        this.teachingUnitDao = teachingUnitDao;
    }

    public List<Student> list(Long teacherId, Long teachingUnitId) {
        classRoomService.getOwnedByTeacher(teacherId, teachingUnitId);
        return studentDao.findAllByTeachingUnitId(teachingUnitId);
    }

    public Student update(Long teacherId, Long studentId, String name, String schoolClassName, boolean enrolled) {
        Student existing = findOwnedByTeacher(teacherId, studentId);
        Student updated = new Student(existing.id(), existing.institutionId(), existing.teachingUnitId(),
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

    public List<String> enrolledCourseNames(Long studentId) {
        return enrollmentDao.findAllByStudentId(studentId).stream()
                .filter(StudentUnitEnrollment::active)
                .map(e -> teachingUnitDao.findById(e.teachingUnitId()).map(TeachingUnit::name).orElse(null))
                .filter(Objects::nonNull)
                .toList();
    }

    public String shareToken(Long teacherId, Long studentId) {
        Student existing = findOwnedByTeacher(teacherId, studentId);
        return studentDao.findShareToken(existing.id());
    }

    // 纯课外课学生（teachingUnitId==null）没有托管班可归属校验；改为校验该教师名下是否有
    // 课程报名了这个学生——任一满足即可管理（改名/头像/分享链接），不要求两者都满足。
    // 这条口径之前漏掉了，导致纯课外课学生创建后谁都拿不到分享链接，见产品诊断 #06 延伸。
    private Student findOwnedByTeacher(Long teacherId, Long studentId) {
        Student student = studentDao.findById(studentId)
                .orElseThrow(() -> new NotFoundException("Student not found: " + studentId));
        if (student.teachingUnitId() != null) {
            classRoomService.getOwnedByTeacher(teacherId, student.teachingUnitId());
            return student;
        }
        boolean enrolledInTeachersCourse = enrollmentDao.findAllByStudentId(studentId).stream()
                .filter(StudentUnitEnrollment::active)
                .anyMatch(e -> teachingUnitDao.findById(e.teachingUnitId())
                        .filter(unit -> unit.teacherId().equals(teacherId))
                        .isPresent());
        if (!enrolledInTeachersCourse) {
            throw new NotFoundException("Student not found: " + studentId);
        }
        return student;
    }
}
