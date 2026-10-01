package com.tuoguan.backend.course.service;

import com.tuoguan.backend.course.dao.StudentCourseEnrollmentDao;
import com.tuoguan.backend.course.domain.Course;
import com.tuoguan.backend.course.domain.StudentCourseEnrollment;
import com.tuoguan.backend.roster.dao.StudentDao;
import com.tuoguan.backend.roster.domain.Student;
import com.tuoguan.backend.roster.web.NotFoundException;
import org.springframework.stereotype.Service;

@Service
public class CourseEnrollmentService {

    private final CourseService courseService;
    private final StudentDao studentDao;
    private final StudentCourseEnrollmentDao enrollmentDao;

    public CourseEnrollmentService(CourseService courseService, StudentDao studentDao,
                                    StudentCourseEnrollmentDao enrollmentDao) {
        this.courseService = courseService;
        this.studentDao = studentDao;
        this.enrollmentDao = enrollmentDao;
    }

    public Student createAndEnrollStudent(Long teacherId, Long courseId, String name, String schoolClassName) {
        Course course = courseService.getOwnedByTeacher(teacherId, courseId);
        Student student = new Student(null, course.institutionId(), null, name, schoolClassName, true, null, null);
        Long studentId = studentDao.insert(student);
        enrollmentDao.insert(new StudentCourseEnrollment(null, course.institutionId(), studentId, course.id(), true,
                null));
        return studentDao.findById(studentId)
                .orElseThrow(() -> new IllegalStateException("Student not found after insert: " + studentId));
    }

    public void enrollExistingStudent(Long teacherId, Long courseId, Long studentId) {
        Course course = courseService.getOwnedByTeacher(teacherId, courseId);
        Student student = requireStudentInInstitution(course.institutionId(), studentId);
        enrollmentDao.findByStudentIdAndCourseId(student.id(), course.id())
                .ifPresentOrElse(
                        existing -> enrollmentDao.setActive(existing.id(), true),
                        () -> enrollmentDao.insert(new StudentCourseEnrollment(null, course.institutionId(),
                                student.id(), course.id(), true, null)));
    }

    public void unenroll(Long teacherId, Long courseId, Long studentId) {
        Course course = courseService.getOwnedByTeacher(teacherId, courseId);
        StudentCourseEnrollment enrollment = enrollmentDao.findByStudentIdAndCourseId(studentId, course.id())
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
