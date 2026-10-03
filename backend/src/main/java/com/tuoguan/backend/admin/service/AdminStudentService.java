package com.tuoguan.backend.admin.service;

import com.tuoguan.backend.admin.web.AdminCreateStudentRequest;
import com.tuoguan.backend.admin.web.AdminStudentResponse;
import com.tuoguan.backend.roster.dao.StudentDao;
import com.tuoguan.backend.roster.domain.Student;
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
import java.util.stream.Collectors;

@Service
public class AdminStudentService {

    private final StudentDao studentDao;
    private final TeachingUnitDao teachingUnitDao;
    private final StudentUnitEnrollmentDao enrollmentDao;

    public AdminStudentService(StudentDao studentDao, TeachingUnitDao teachingUnitDao,
                                StudentUnitEnrollmentDao enrollmentDao) {
        this.studentDao = studentDao;
        this.teachingUnitDao = teachingUnitDao;
        this.enrollmentDao = enrollmentDao;
    }

    public List<AdminStudentResponse> listStudents(Long institutionId) {
        List<TeachingUnit> units = teachingUnitDao.findAllByInstitutionId(institutionId);
        Map<Long, TeachingUnit> classRoomById = units.stream()
                .filter(u -> u.billingMode() == BillingMode.MONTHLY)
                .collect(Collectors.toMap(TeachingUnit::id, u -> u));
        Map<Long, String> courseNameById = units.stream()
                .filter(u -> u.billingMode() == BillingMode.LESSON_COUNT)
                .collect(Collectors.toMap(TeachingUnit::id, TeachingUnit::name));
        return studentDao.findAllByInstitutionId(institutionId).stream()
                .map(student -> {
                    TeachingUnit classRoom = student.teachingUnitId() != null
                            ? classRoomById.get(student.teachingUnitId()) : null;
                    List<String> enrolledCourseNames = enrollmentDao.findAllByStudentId(student.id()).stream()
                            .filter(StudentUnitEnrollment::active)
                            .map(e -> courseNameById.get(e.teachingUnitId()))
                            .filter(Objects::nonNull)
                            .toList();
                    return new AdminStudentResponse(student.id(), student.name(), student.schoolClassName(),
                            student.teachingUnitId(), classRoom != null ? classRoom.name() : null,
                            student.teachingUnitId() == null, enrolledCourseNames);
                })
                .toList();
    }

    public AdminStudentResponse createStudent(Long institutionId, AdminCreateStudentRequest request) {
        if (request.teachingUnitId() != null) {
            TeachingUnit unit = teachingUnitDao.findById(request.teachingUnitId())
                    .orElseThrow(() -> new NotFoundException("Teaching unit not found: " + request.teachingUnitId()));
            if (!unit.institutionId().equals(institutionId)) {
                throw new NotFoundException("Teaching unit not found: " + request.teachingUnitId());
            }
            if (unit.billingMode() != BillingMode.MONTHLY) {
                throw new InvalidTeachingUnitRequestException(
                        "Student can only be assigned to a MONTHLY teaching unit: " + request.teachingUnitId());
            }
        }
        Long id = studentDao.insert(new Student(null, institutionId, request.teachingUnitId(), request.name(),
                request.schoolClassName(), true, null, null));
        Student student = studentDao.findById(id)
                .orElseThrow(() -> new IllegalStateException("Student not found after insert: " + id));
        TeachingUnit classRoom = student.teachingUnitId() != null
                ? teachingUnitDao.findById(student.teachingUnitId()).orElse(null) : null;
        return new AdminStudentResponse(student.id(), student.name(), student.schoolClassName(),
                student.teachingUnitId(), classRoom != null ? classRoom.name() : null,
                student.teachingUnitId() == null, List.of());
    }
}
