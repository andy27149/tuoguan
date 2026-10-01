package com.tuoguan.backend.course.dao;

import com.tuoguan.backend.auth.dao.InstitutionDao;
import com.tuoguan.backend.auth.dao.TeacherDao;
import com.tuoguan.backend.auth.domain.Role;
import com.tuoguan.backend.auth.domain.Teacher;
import com.tuoguan.backend.course.domain.Course;
import com.tuoguan.backend.support.IntegrationTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class JdbcCourseDaoTest extends IntegrationTestBase {

    @Autowired
    private InstitutionDao institutionDao;

    @Autowired
    private TeacherDao teacherDao;

    @Autowired
    private CourseDao courseDao;

    @Test
    void insertAndFindByIdRoundTripsWithNullPrice() {
        Long institutionId = institutionDao.insert("课程测试机构A");
        Long teacherId = teacherDao.insert(new Teacher(null, institutionId, "13900020001", "hash",
                Role.TEACHER, false, null));

        Long courseId = courseDao.insert(new Course(null, institutionId, teacherId, "数学课", null, 60, true, null));

        Optional<Course> found = courseDao.findById(courseId);
        assertThat(found).isPresent();
        assertThat(found.get().name()).isEqualTo("数学课");
        assertThat(found.get().pricePerLesson()).isNull();
        assertThat(found.get().lessonDurationMinutes()).isEqualTo(60);
        assertThat(found.get().active()).isTrue();
    }

    @Test
    void findAllByTeacherIdOnlyReturnsOwnCourses() {
        Long institutionId = institutionDao.insert("课程测试机构B");
        Long teacherAId = teacherDao.insert(new Teacher(null, institutionId, "13900020002", "hash",
                Role.TEACHER, false, null));
        Long teacherBId = teacherDao.insert(new Teacher(null, institutionId, "13900020003", "hash",
                Role.TEACHER, false, null));
        courseDao.insert(new Course(null, institutionId, teacherAId, "英语课", null, 45, true, null));
        courseDao.insert(new Course(null, institutionId, teacherBId, "科学课", null, 45, true, null));

        List<Course> found = courseDao.findAllByTeacherId(teacherAId);

        assertThat(found).hasSize(1);
        assertThat(found.get(0).name()).isEqualTo("英语课");
    }

    @Test
    void findAllByInstitutionIdReturnsAllCourses() {
        Long institutionId = institutionDao.insert("课程测试机构C");
        Long teacherAId = teacherDao.insert(new Teacher(null, institutionId, "13900020004", "hash",
                Role.TEACHER, false, null));
        Long teacherBId = teacherDao.insert(new Teacher(null, institutionId, "13900020005", "hash",
                Role.TEACHER, false, null));
        courseDao.insert(new Course(null, institutionId, teacherAId, "语文课", null, 45, true, null));
        courseDao.insert(new Course(null, institutionId, teacherBId, "美术课", null, 45, true, null));

        List<Course> found = courseDao.findAllByInstitutionId(institutionId);

        assertThat(found).hasSize(2);
    }

    @Test
    void setPriceUpdatesPricePerLesson() {
        Long institutionId = institutionDao.insert("课程测试机构D");
        Long teacherId = teacherDao.insert(new Teacher(null, institutionId, "13900020006", "hash",
                Role.TEACHER, false, null));
        Long courseId = courseDao.insert(new Course(null, institutionId, teacherId, "音乐课", null, 45, true, null));

        courseDao.setPrice(courseId, new BigDecimal("80.00"));

        Course found = courseDao.findById(courseId).orElseThrow();
        assertThat(found.pricePerLesson()).isEqualByComparingTo("80.00");
    }

    @Test
    void setActiveTogglesActiveFlag() {
        Long institutionId = institutionDao.insert("课程测试机构E");
        Long teacherId = teacherDao.insert(new Teacher(null, institutionId, "13900020007", "hash",
                Role.TEACHER, false, null));
        Long courseId = courseDao.insert(new Course(null, institutionId, teacherId, "体育课", null, 45, true, null));

        courseDao.setActive(courseId, false);

        assertThat(courseDao.findById(courseId).orElseThrow().active()).isFalse();
    }

    @Test
    void reassignTeacherChangesOwningTeacher() {
        Long institutionId = institutionDao.insert("课程测试机构F");
        Long teacherAId = teacherDao.insert(new Teacher(null, institutionId, "13900020008", "hash",
                Role.TEACHER, false, null));
        Long teacherBId = teacherDao.insert(new Teacher(null, institutionId, "13900020009", "hash",
                Role.TEACHER, false, null));
        Long courseId = courseDao.insert(new Course(null, institutionId, teacherAId, "书法课", null, 45, true, null));

        courseDao.reassignTeacher(courseId, teacherBId);

        assertThat(courseDao.findById(courseId).orElseThrow().teacherId()).isEqualTo(teacherBId);
    }

    @Test
    void reassignAllTeacherMovesAllCoursesFromOldToNewTeacher() {
        Long institutionId = institutionDao.insert("课程测试机构G");
        Long teacherAId = teacherDao.insert(new Teacher(null, institutionId, "13900020010", "hash",
                Role.TEACHER, false, null));
        Long teacherBId = teacherDao.insert(new Teacher(null, institutionId, "13900020011", "hash",
                Role.TEACHER, false, null));
        courseDao.insert(new Course(null, institutionId, teacherAId, "舞蹈课", null, 45, true, null));
        courseDao.insert(new Course(null, institutionId, teacherAId, "围棋课", null, 45, true, null));

        courseDao.reassignAllTeacher(teacherAId, teacherBId);

        assertThat(courseDao.findAllByTeacherId(teacherAId)).isEmpty();
        assertThat(courseDao.findAllByTeacherId(teacherBId)).hasSize(2);
    }

    @Test
    void deleteByIdRemovesCourse() {
        Long institutionId = institutionDao.insert("课程测试机构H");
        Long teacherId = teacherDao.insert(new Teacher(null, institutionId, "13900020012", "hash",
                Role.TEACHER, false, null));
        Long courseId = courseDao.insert(new Course(null, institutionId, teacherId, "编程课", null, 45, true, null));

        courseDao.deleteById(courseId);

        assertThat(courseDao.findById(courseId)).isEmpty();
    }
}
