package com.tuoguan.backend.course.service;

import com.tuoguan.backend.roster.dao.StudentDao;
import com.tuoguan.backend.roster.domain.Student;
import com.tuoguan.backend.roster.web.NotFoundException;
import com.tuoguan.backend.unit.dao.StudentUnitEnrollmentDao;
import com.tuoguan.backend.unit.domain.StudentUnitEnrollment;
import com.tuoguan.backend.unit.domain.TeachingUnit;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

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

    // 候选人来源：机构内所有纯课外课学生（没有托管班），排除已经在本课程花名册里的——
    // 不限于教师自己名下的托管班，因为纯课外课学生本来就不挂靠任何教师。见产品诊断 #06 延伸。
    public List<Student> listOffCampusCandidates(Long teacherId, Long courseId) {
        TeachingUnit course = courseService.getOwnedByTeacher(teacherId, courseId);
        Set<Long> alreadyEnrolled = enrollmentDao.findAllByTeachingUnitId(course.id()).stream()
                .filter(StudentUnitEnrollment::active)
                .map(StudentUnitEnrollment::studentId)
                .collect(Collectors.toSet());
        return studentDao.findAllByInstitutionId(course.institutionId()).stream()
                .filter(s -> s.teachingUnitId() == null)
                .filter(s -> !alreadyEnrolled.contains(s.id()))
                .toList();
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
