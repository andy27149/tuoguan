package com.tuoguan.backend.course.service;

import com.tuoguan.backend.roster.dao.StudentDao;
import com.tuoguan.backend.roster.domain.Student;
import com.tuoguan.backend.roster.web.NotFoundException;
import com.tuoguan.backend.unit.dao.StudentUnitEnrollmentDao;
import com.tuoguan.backend.unit.domain.StudentUnitEnrollment;
import com.tuoguan.backend.unit.domain.TeachingUnit;
import org.springframework.stereotype.Service;

@Service
public class CourseEnrollmentService {

    private final CourseService courseService;
    private final StudentDao studentDao;
    private final StudentUnitEnrollmentDao enrollmentDao;

    public CourseEnrollmentService(CourseService courseService, StudentDao studentDao,
                                    StudentUnitEnrollmentDao enrollmentDao) {
        this.courseService = courseService;
        this.studentDao = studentDao;
        this.enrollmentDao = enrollmentDao;
    }

    public void enrollExistingStudent(Long teacherId, Long courseId, Long studentId) {
        TeachingUnit course = courseService.getOwnedByTeacher(teacherId, courseId);
        Student student = requireStudentInInstitution(course.institutionId(), studentId);
        enrollmentDao.findByStudentIdAndTeachingUnitId(student.id(), course.id())
                .ifPresentOrElse(
                        existing -> enrollmentDao.setActive(existing.id(), true),
                        () -> enrollmentDao.insert(new StudentUnitEnrollment(null, course.institutionId(),
                                student.id(), course.id(), true, null)));
    }

    public void unenroll(Long teacherId, Long courseId, Long studentId) {
        TeachingUnit course = courseService.getOwnedByTeacher(teacherId, courseId);
        StudentUnitEnrollment enrollment = enrollmentDao.findByStudentIdAndTeachingUnitId(studentId, course.id())
                .orElseThrow(() -> new NotFoundException("Enrollment not found: " + studentId));
        enrollmentDao.setActive(enrollment.id(), false);
    }

    private Student requireStudentInInstitution(Long institutionId, Long studentId) {
        Student student = studentDao.findById(studentId)
                .orElseThrow(() -> new NotFoundException("Student not found: " + studentId));
        if (!student.institutionId().equals(institutionId)) {
            throw new NotFoundException("Student not found: " + studentId);
        }
        return student;
    }
}
