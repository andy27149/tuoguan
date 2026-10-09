package com.tuoguan.backend.admin.service;

import com.tuoguan.backend.admin.web.AdminCreateStudentRequest;
import com.tuoguan.backend.admin.web.AdminStudentResponse;
import com.tuoguan.backend.admin.web.AdminUpdateStudentRequest;
import com.tuoguan.backend.audit.service.AuditLogService;
import com.tuoguan.backend.auth.dao.TeacherDao;
import com.tuoguan.backend.auth.domain.Teacher;
import com.tuoguan.backend.roster.dao.StudentDao;
import com.tuoguan.backend.roster.domain.Student;
import com.tuoguan.backend.roster.service.StudentOverviewOrder;
import com.tuoguan.backend.roster.web.NotFoundException;
import com.tuoguan.backend.unit.dao.StudentUnitEnrollmentDao;
import com.tuoguan.backend.unit.dao.TeachingUnitDao;
import com.tuoguan.backend.unit.domain.BillingMode;
import com.tuoguan.backend.unit.domain.StudentUnitEnrollment;
import com.tuoguan.backend.unit.domain.TeachingUnit;
import com.tuoguan.backend.unit.web.InvalidTeachingUnitRequestException;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class AdminStudentService {

    private final StudentDao studentDao;
    private final TeachingUnitDao teachingUnitDao;
    private final StudentUnitEnrollmentDao enrollmentDao;
    private final TeacherDao teacherDao;
    private final AuditLogService auditLogService;

    public AdminStudentService(StudentDao studentDao, TeachingUnitDao teachingUnitDao,
                                StudentUnitEnrollmentDao enrollmentDao, TeacherDao teacherDao,
                                AuditLogService auditLogService) {
        this.studentDao = studentDao;
        this.teachingUnitDao = teachingUnitDao;
        this.enrollmentDao = enrollmentDao;
        this.teacherDao = teacherDao;
        this.auditLogService = auditLogService;
    }

    public List<AdminStudentResponse> listStudents(Long institutionId) {
        List<TeachingUnit> units = teachingUnitDao.findAllByInstitutionId(institutionId);
        Map<Long, TeachingUnit> unitsById = units.stream().collect(Collectors.toMap(TeachingUnit::id, u -> u));
        Map<Long, String> courseNameByUnitId = units.stream()
                .filter(u -> u.billingMode() == BillingMode.LESSON_COUNT)
                .collect(Collectors.toMap(TeachingUnit::id, TeachingUnit::name));

        List<Student> students = studentDao.findAllByInstitutionId(institutionId);
        Map<Long, String> firstCourseNameByStudentId =
                StudentOverviewOrder.computeFirstCourseNames(students, courseNameByUnitId, enrollmentDao);

        return students.stream()
                .sorted(StudentOverviewOrder.comparator(unitsById, firstCourseNameByStudentId, teacherDao))
                .map(student -> toResponse(student, unitsById))
                .toList();
    }

    public AdminStudentResponse createStudent(Long institutionId, AdminCreateStudentRequest request) {
        TeachingUnit unit = validateTeachingUnitAssignment(institutionId, request.teachingUnitId());
        Long id = studentDao.insert(new Student(null, institutionId, request.teachingUnitId(), request.name(),
                request.schoolClassName(), true, null, null));
        syncCourseEnrollments(institutionId, id, request.courseIds());
        Student student = studentDao.findById(id)
                .orElseThrow(() -> new IllegalStateException("Student not found after insert: " + id));
        return toResponse(student, unit);
    }

    public AdminStudentResponse updateStudent(Long institutionId, Long actorTeacherId, Long studentId,
                                               AdminUpdateStudentRequest request) {
        Student existing = requireStudentInInstitution(institutionId, studentId);
        TeachingUnit unit = validateTeachingUnitAssignment(institutionId, request.teachingUnitId());
        Student updated = new Student(existing.id(), existing.institutionId(), request.teachingUnitId(),
                request.name(), request.schoolClassName(), request.enrolled(), existing.avatarObjectKey(),
                existing.createdAt());
        studentDao.update(updated);
        syncCourseEnrollments(institutionId, studentId, request.courseIds());
        if (existing.enrolled() != request.enrolled()) {
            auditLogService.record(institutionId, actorTeacherId,
                    request.enrolled() ? "STUDENT_ENABLE" : "STUDENT_DEACTIVATE", "STUDENT", studentId, null);
        }
        Student saved = studentDao.findById(studentId)
                .orElseThrow(() -> new IllegalStateException("Student not found after update: " + studentId));
        return toResponse(saved, unit);
    }

    // 全量覆盖同步：courseIds 代表该生"应该"报名的课外课完整集合。只在本机构启用中的
    // LESSON_COUNT 课程范围内做增删——管理员编辑表单的勾选列表本来就只展示这个范围，已停用
    // 课程的历史报名记录不在该范围内，不会因为没出现在 courseIds 里就被误判为要取消。
    private void syncCourseEnrollments(Long institutionId, Long studentId, List<Long> courseIds) {
        List<Long> desired = courseIds == null ? List.of() : courseIds;
        for (Long courseId : desired) {
            TeachingUnit course = teachingUnitDao.findById(courseId)
                    .orElseThrow(() -> new NotFoundException("Course not found: " + courseId));
            if (!course.institutionId().equals(institutionId)) {
                throw new NotFoundException("Course not found: " + courseId);
            }
            if (course.billingMode() != BillingMode.LESSON_COUNT) {
                throw new InvalidTeachingUnitRequestException(
                        "Course must be a LESSON_COUNT teaching unit: " + courseId);
            }
        }

        Set<Long> offerableCourseIds = teachingUnitDao.findAllByInstitutionId(institutionId).stream()
                .filter(u -> u.billingMode() == BillingMode.LESSON_COUNT && u.active())
                .map(TeachingUnit::id)
                .collect(Collectors.toSet());
        List<StudentUnitEnrollment> existing = enrollmentDao.findAllByStudentId(studentId);
        for (StudentUnitEnrollment enrollment : existing) {
            if (enrollment.active() && offerableCourseIds.contains(enrollment.teachingUnitId())
                    && !desired.contains(enrollment.teachingUnitId())) {
                enrollmentDao.setActive(enrollment.id(), false);
            }
        }
        for (Long courseId : desired) {
            StudentUnitEnrollment existingForCourse = existing.stream()
                    .filter(e -> e.teachingUnitId().equals(courseId))
                    .findFirst().orElse(null);
            if (existingForCourse == null) {
                enrollmentDao.insert(new StudentUnitEnrollment(null, institutionId, studentId, courseId, true, null));
            } else if (!existingForCourse.active()) {
                enrollmentDao.setActive(existingForCourse.id(), true);
            }
        }
    }

    private TeachingUnit validateTeachingUnitAssignment(Long institutionId, Long teachingUnitId) {
        if (teachingUnitId == null) {
            return null;
        }
        TeachingUnit unit = teachingUnitDao.findById(teachingUnitId)
                .orElseThrow(() -> new NotFoundException("Teaching unit not found: " + teachingUnitId));
        if (!unit.institutionId().equals(institutionId)) {
            throw new NotFoundException("Teaching unit not found: " + teachingUnitId);
        }
        if (unit.billingMode() != BillingMode.MONTHLY) {
            throw new InvalidTeachingUnitRequestException(
                    "Student can only be assigned to a MONTHLY teaching unit: " + teachingUnitId);
        }
        return unit;
    }

    private Student requireStudentInInstitution(Long institutionId, Long studentId) {
        Student student = studentDao.findById(studentId)
                .orElseThrow(() -> new NotFoundException("Student not found: " + studentId));
        if (!student.institutionId().equals(institutionId)) {
            throw new NotFoundException("Student not found: " + studentId);
        }
        return student;
    }

    private AdminStudentResponse toResponse(Student student, TeachingUnit classRoom) {
        String teacherName = classRoom != null
                ? teacherDao.findById(classRoom.teacherId()).map(Teacher::name).orElse(null) : null;
        List<StudentUnitEnrollment> activeEnrollments = enrollmentDao.findAllByStudentId(student.id()).stream()
                .filter(StudentUnitEnrollment::active)
                .toList();
        List<String> enrolledCourseNames = activeEnrollments.stream()
                .map(e -> teachingUnitDao.findById(e.teachingUnitId()).map(TeachingUnit::name).orElse(null))
                .filter(Objects::nonNull)
                .toList();
        List<Long> enrolledCourseIds = activeEnrollments.stream()
                .map(StudentUnitEnrollment::teachingUnitId)
                .toList();
        return new AdminStudentResponse(student.id(), student.name(), student.schoolClassName(),
                student.teachingUnitId(), classRoom != null ? classRoom.name() : null,
                student.teachingUnitId() == null, student.enrolled(), teacherName, enrolledCourseNames,
                enrolledCourseIds);
    }

    private AdminStudentResponse toResponse(Student student, Map<Long, TeachingUnit> unitsById) {
        TeachingUnit classRoom = student.teachingUnitId() != null ? unitsById.get(student.teachingUnitId()) : null;
        return toResponse(student, classRoom);
    }
}
