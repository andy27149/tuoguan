package com.tuoguan.backend.unit.dao;

import com.tuoguan.backend.auth.dao.InstitutionDao;
import com.tuoguan.backend.auth.dao.TeacherDao;
import com.tuoguan.backend.auth.domain.Role;
import com.tuoguan.backend.auth.domain.Teacher;
import com.tuoguan.backend.roster.dao.StudentDao;
import com.tuoguan.backend.roster.domain.Student;
import com.tuoguan.backend.support.IntegrationTestBase;
import com.tuoguan.backend.unit.domain.BillingMode;
import com.tuoguan.backend.unit.domain.StudentUnitEnrollment;
import com.tuoguan.backend.unit.domain.TeachingUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class JdbcStudentUnitEnrollmentDaoTest extends IntegrationTestBase {

    @Autowired
    private InstitutionDao institutionDao;

    @Autowired
    private TeacherDao teacherDao;

    @Autowired
    private TeachingUnitDao teachingUnitDao;

    @Autowired
    private StudentDao studentDao;

    @Autowired
    private StudentUnitEnrollmentDao enrollmentDao;

    private Long createCourseUnit(Long institutionId, Long teacherId, String name) {
        return teachingUnitDao.insert(new TeachingUnit(null, institutionId, teacherId, name,
                BillingMode.LESSON_COUNT, 45, null, true, null));
    }

    private Long createStudent(Long institutionId) {
        return studentDao.insert(new Student(null, institutionId, null, "课外学生", null, true, null, null));
    }

    @Test
    void insertAndFindByStudentIdAndTeachingUnitIdRoundTrips() {
        Long institutionId = institutionDao.insert("报名测试机构A");
        Long teacherId = teacherDao.insert(new Teacher(null, institutionId, "13900021001", "hash",
                Role.TEACHER, false, null));
        Long unitId = createCourseUnit(institutionId, teacherId, "数学课");
        Long studentId = createStudent(institutionId);

        enrollmentDao.insert(new StudentUnitEnrollment(null, institutionId, studentId, unitId, true, null));

        Optional<StudentUnitEnrollment> found = enrollmentDao.findByStudentIdAndTeachingUnitId(studentId, unitId);
        assertThat(found).isPresent();
        assertThat(found.get().active()).isTrue();
    }

    @Test
    void findAllByTeachingUnitIdReturnsAllEnrollments() {
        Long institutionId = institutionDao.insert("报名测试机构B");
        Long teacherId = teacherDao.insert(new Teacher(null, institutionId, "13900021002", "hash",
                Role.TEACHER, false, null));
        Long unitId = createCourseUnit(institutionId, teacherId, "英语课");
        Long studentAId = createStudent(institutionId);
        Long studentBId = createStudent(institutionId);
        enrollmentDao.insert(new StudentUnitEnrollment(null, institutionId, studentAId, unitId, true, null));
        enrollmentDao.insert(new StudentUnitEnrollment(null, institutionId, studentBId, unitId, true, null));

        List<StudentUnitEnrollment> found = enrollmentDao.findAllByTeachingUnitId(unitId);

        assertThat(found).hasSize(2);
    }

    @Test
    void findAllByStudentIdReturnsAllEnrollments() {
        Long institutionId = institutionDao.insert("报名测试机构C");
        Long teacherId = teacherDao.insert(new Teacher(null, institutionId, "13900021003", "hash",
                Role.TEACHER, false, null));
        Long unitAId = createCourseUnit(institutionId, teacherId, "语文课");
        Long unitBId = createCourseUnit(institutionId, teacherId, "科学课");
        Long studentId = createStudent(institutionId);
        enrollmentDao.insert(new StudentUnitEnrollment(null, institutionId, studentId, unitAId, true, null));
        enrollmentDao.insert(new StudentUnitEnrollment(null, institutionId, studentId, unitBId, true, null));

        List<StudentUnitEnrollment> found = enrollmentDao.findAllByStudentId(studentId);

        assertThat(found).hasSize(2);
    }

    @Test
    void setActiveTogglesEnrollmentStatus() {
        Long institutionId = institutionDao.insert("报名测试机构D");
        Long teacherId = teacherDao.insert(new Teacher(null, institutionId, "13900021004", "hash",
                Role.TEACHER, false, null));
        Long unitId = createCourseUnit(institutionId, teacherId, "美术课");
        Long studentId = createStudent(institutionId);
        Long enrollmentId = enrollmentDao.insert(
                new StudentUnitEnrollment(null, institutionId, studentId, unitId, true, null));

        enrollmentDao.setActive(enrollmentId, false);

        Optional<StudentUnitEnrollment> found = enrollmentDao.findByStudentIdAndTeachingUnitId(studentId, unitId);
        assertThat(found).isPresent();
        assertThat(found.get().active()).isFalse();
    }

    @Test
    void deleteAllByStudentIdRemovesEnrollments() {
        Long institutionId = institutionDao.insert("报名测试机构E");
        Long teacherId = teacherDao.insert(new Teacher(null, institutionId, "13900021005", "hash",
                Role.TEACHER, false, null));
        Long unitId = createCourseUnit(institutionId, teacherId, "体育课");
        Long studentId = createStudent(institutionId);
        enrollmentDao.insert(new StudentUnitEnrollment(null, institutionId, studentId, unitId, true, null));

        enrollmentDao.deleteAllByStudentId(studentId);

        assertThat(enrollmentDao.findAllByStudentId(studentId)).isEmpty();
    }

    @Test
    void deleteAllByTeachingUnitIdRemovesEnrollments() {
        Long institutionId = institutionDao.insert("报名测试机构F");
        Long teacherId = teacherDao.insert(new Teacher(null, institutionId, "13900021006", "hash",
                Role.TEACHER, false, null));
        Long unitId = createCourseUnit(institutionId, teacherId, "音乐课");
        Long studentId = createStudent(institutionId);
        enrollmentDao.insert(new StudentUnitEnrollment(null, institutionId, studentId, unitId, true, null));

        enrollmentDao.deleteAllByTeachingUnitId(unitId);

        assertThat(enrollmentDao.findAllByStudentId(studentId)).isEmpty();
    }
}
