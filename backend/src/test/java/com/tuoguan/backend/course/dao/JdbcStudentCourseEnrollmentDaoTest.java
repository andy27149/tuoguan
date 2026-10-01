package com.tuoguan.backend.course.dao;

import com.tuoguan.backend.auth.dao.InstitutionDao;
import com.tuoguan.backend.auth.dao.TeacherDao;
import com.tuoguan.backend.auth.domain.Role;
import com.tuoguan.backend.auth.domain.Teacher;
import com.tuoguan.backend.course.domain.Course;
import com.tuoguan.backend.course.domain.StudentCourseEnrollment;
import com.tuoguan.backend.roster.dao.StudentDao;
import com.tuoguan.backend.roster.domain.Student;
import com.tuoguan.backend.support.IntegrationTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class JdbcStudentCourseEnrollmentDaoTest extends IntegrationTestBase {

    @Autowired
    private InstitutionDao institutionDao;

    @Autowired
    private TeacherDao teacherDao;

    @Autowired
    private CourseDao courseDao;

    @Autowired
    private StudentDao studentDao;

    @Autowired
    private StudentCourseEnrollmentDao enrollmentDao;

    private Long createCourse(Long institutionId, Long teacherId, String name) {
        return courseDao.insert(new Course(null, institutionId, teacherId, name, null, 45, true, null));
    }

    private Long createStudent(Long institutionId) {
        return studentDao.insert(new Student(null, institutionId, null, "课外学生", null, true, null, null));
    }

    @Test
    void insertAndFindByStudentIdAndCourseIdRoundTrips() {
        Long institutionId = institutionDao.insert("报名测试机构A");
        Long teacherId = teacherDao.insert(new Teacher(null, institutionId, "13900021001", "hash",
                Role.TEACHER, false, null));
        Long courseId = createCourse(institutionId, teacherId, "数学课");
        Long studentId = createStudent(institutionId);

        enrollmentDao.insert(new StudentCourseEnrollment(null, institutionId, studentId, courseId, true, null));

        Optional<StudentCourseEnrollment> found = enrollmentDao.findByStudentIdAndCourseId(studentId, courseId);
        assertThat(found).isPresent();
        assertThat(found.get().active()).isTrue();
    }

    @Test
    void findAllByCourseIdReturnsAllEnrollments() {
        Long institutionId = institutionDao.insert("报名测试机构B");
        Long teacherId = teacherDao.insert(new Teacher(null, institutionId, "13900021002", "hash",
                Role.TEACHER, false, null));
        Long courseId = createCourse(institutionId, teacherId, "英语课");
        Long studentAId = createStudent(institutionId);
        Long studentBId = createStudent(institutionId);
        enrollmentDao.insert(new StudentCourseEnrollment(null, institutionId, studentAId, courseId, true, null));
        enrollmentDao.insert(new StudentCourseEnrollment(null, institutionId, studentBId, courseId, true, null));

        List<StudentCourseEnrollment> found = enrollmentDao.findAllByCourseId(courseId);

        assertThat(found).hasSize(2);
    }

    @Test
    void findAllByStudentIdReturnsAllEnrollments() {
        Long institutionId = institutionDao.insert("报名测试机构C");
        Long teacherId = teacherDao.insert(new Teacher(null, institutionId, "13900021003", "hash",
                Role.TEACHER, false, null));
        Long courseAId = createCourse(institutionId, teacherId, "语文课");
        Long courseBId = createCourse(institutionId, teacherId, "科学课");
        Long studentId = createStudent(institutionId);
        enrollmentDao.insert(new StudentCourseEnrollment(null, institutionId, studentId, courseAId, true, null));
        enrollmentDao.insert(new StudentCourseEnrollment(null, institutionId, studentId, courseBId, true, null));

        List<StudentCourseEnrollment> found = enrollmentDao.findAllByStudentId(studentId);

        assertThat(found).hasSize(2);
    }

    @Test
    void setActiveTogglesEnrollmentStatus() {
        Long institutionId = institutionDao.insert("报名测试机构D");
        Long teacherId = teacherDao.insert(new Teacher(null, institutionId, "13900021004", "hash",
                Role.TEACHER, false, null));
        Long courseId = createCourse(institutionId, teacherId, "美术课");
        Long studentId = createStudent(institutionId);
        Long enrollmentId = enrollmentDao.insert(
                new StudentCourseEnrollment(null, institutionId, studentId, courseId, true, null));

        enrollmentDao.setActive(enrollmentId, false);

        Optional<StudentCourseEnrollment> found = enrollmentDao.findByStudentIdAndCourseId(studentId, courseId);
        assertThat(found).isPresent();
        assertThat(found.get().active()).isFalse();
    }

    @Test
    void deleteAllByStudentIdRemovesEnrollments() {
        Long institutionId = institutionDao.insert("报名测试机构E");
        Long teacherId = teacherDao.insert(new Teacher(null, institutionId, "13900021005", "hash",
                Role.TEACHER, false, null));
        Long courseId = createCourse(institutionId, teacherId, "体育课");
        Long studentId = createStudent(institutionId);
        enrollmentDao.insert(new StudentCourseEnrollment(null, institutionId, studentId, courseId, true, null));

        enrollmentDao.deleteAllByStudentId(studentId);

        assertThat(enrollmentDao.findAllByStudentId(studentId)).isEmpty();
    }

    @Test
    void deleteAllByCourseIdRemovesEnrollments() {
        Long institutionId = institutionDao.insert("报名测试机构F");
        Long teacherId = teacherDao.insert(new Teacher(null, institutionId, "13900021006", "hash",
                Role.TEACHER, false, null));
        Long courseId = createCourse(institutionId, teacherId, "音乐课");
        Long studentId = createStudent(institutionId);
        enrollmentDao.insert(new StudentCourseEnrollment(null, institutionId, studentId, courseId, true, null));

        enrollmentDao.deleteAllByCourseId(courseId);

        assertThat(enrollmentDao.findAllByCourseId(courseId)).isEmpty();
    }
}
