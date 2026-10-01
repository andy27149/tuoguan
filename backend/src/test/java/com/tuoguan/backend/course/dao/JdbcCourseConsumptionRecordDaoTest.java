package com.tuoguan.backend.course.dao;

import com.tuoguan.backend.auth.dao.InstitutionDao;
import com.tuoguan.backend.auth.dao.TeacherDao;
import com.tuoguan.backend.auth.domain.Role;
import com.tuoguan.backend.auth.domain.Teacher;
import com.tuoguan.backend.course.domain.Course;
import com.tuoguan.backend.course.domain.CourseConsumptionRecord;
import com.tuoguan.backend.roster.dao.StudentDao;
import com.tuoguan.backend.roster.domain.Student;
import com.tuoguan.backend.support.IntegrationTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class JdbcCourseConsumptionRecordDaoTest extends IntegrationTestBase {

    @Autowired
    private InstitutionDao institutionDao;

    @Autowired
    private TeacherDao teacherDao;

    @Autowired
    private CourseDao courseDao;

    @Autowired
    private StudentDao studentDao;

    @Autowired
    private CourseConsumptionRecordDao consumptionRecordDao;

    private Long createCourse(Long institutionId, Long teacherId) {
        return courseDao.insert(new Course(null, institutionId, teacherId, "数学课",
                new BigDecimal("50.00"), 60, true, null));
    }

    private Long createStudent(Long institutionId) {
        return studentDao.insert(new Student(null, institutionId, null, "课外学生", null, true, null, null));
    }

    @Test
    void insertAndFindAllByStudentIdAndCourseIdAndDateRoundTrips() {
        Long institutionId = institutionDao.insert("消课测试机构A");
        Long teacherId = teacherDao.insert(new Teacher(null, institutionId, "13900022001", "hash",
                Role.TEACHER, false, null));
        Long courseId = createCourse(institutionId, teacherId);
        Long studentId = createStudent(institutionId);
        LocalDate date = LocalDate.of(2024, 1, 2);

        consumptionRecordDao.insert(new CourseConsumptionRecord(null, institutionId, studentId, courseId, date,
                new BigDecimal("50.00"), teacherId, null));

        List<CourseConsumptionRecord> found = consumptionRecordDao
                .findAllByStudentIdAndCourseIdAndDate(studentId, courseId, date);
        assertThat(found).hasSize(1);
        assertThat(found.get(0).priceSnapshot()).isEqualByComparingTo("50.00");
    }

    @Test
    void sameDaySecondConsumptionIsAllowedAtDataLayer() {
        Long institutionId = institutionDao.insert("消课测试机构B");
        Long teacherId = teacherDao.insert(new Teacher(null, institutionId, "13900022002", "hash",
                Role.TEACHER, false, null));
        Long courseId = createCourse(institutionId, teacherId);
        Long studentId = createStudent(institutionId);
        LocalDate date = LocalDate.of(2024, 1, 2);

        consumptionRecordDao.insert(new CourseConsumptionRecord(null, institutionId, studentId, courseId, date,
                new BigDecimal("50.00"), teacherId, null));
        consumptionRecordDao.insert(new CourseConsumptionRecord(null, institutionId, studentId, courseId, date,
                new BigDecimal("50.00"), teacherId, null));

        List<CourseConsumptionRecord> found = consumptionRecordDao
                .findAllByStudentIdAndCourseIdAndDate(studentId, courseId, date);
        assertThat(found).hasSize(2);
    }

    @Test
    void findAllByStudentIdAndDateRangeFiltersByRange() {
        Long institutionId = institutionDao.insert("消课测试机构C");
        Long teacherId = teacherDao.insert(new Teacher(null, institutionId, "13900022003", "hash",
                Role.TEACHER, false, null));
        Long courseId = createCourse(institutionId, teacherId);
        Long studentId = createStudent(institutionId);
        consumptionRecordDao.insert(new CourseConsumptionRecord(null, institutionId, studentId, courseId,
                LocalDate.of(2024, 1, 15), new BigDecimal("50.00"), teacherId, null));
        consumptionRecordDao.insert(new CourseConsumptionRecord(null, institutionId, studentId, courseId,
                LocalDate.of(2024, 2, 1), new BigDecimal("50.00"), teacherId, null));

        List<CourseConsumptionRecord> found = consumptionRecordDao.findAllByStudentIdAndDateRange(studentId,
                LocalDate.of(2024, 1, 1), LocalDate.of(2024, 1, 31));

        assertThat(found).hasSize(1);
        assertThat(found.get(0).consumptionDate()).isEqualTo(LocalDate.of(2024, 1, 15));
    }

    @Test
    void findAllByStudentIdReturnsAllRecords() {
        Long institutionId = institutionDao.insert("消课测试机构D");
        Long teacherId = teacherDao.insert(new Teacher(null, institutionId, "13900022004", "hash",
                Role.TEACHER, false, null));
        Long courseId = createCourse(institutionId, teacherId);
        Long studentId = createStudent(institutionId);
        consumptionRecordDao.insert(new CourseConsumptionRecord(null, institutionId, studentId, courseId,
                LocalDate.of(2024, 1, 2), new BigDecimal("50.00"), teacherId, null));
        consumptionRecordDao.insert(new CourseConsumptionRecord(null, institutionId, studentId, courseId,
                LocalDate.of(2024, 1, 3), new BigDecimal("50.00"), teacherId, null));

        assertThat(consumptionRecordDao.findAllByStudentId(studentId)).hasSize(2);
    }

    @Test
    void sumByStudentIdSumsPriceSnapshots() {
        Long institutionId = institutionDao.insert("消课测试机构E");
        Long teacherId = teacherDao.insert(new Teacher(null, institutionId, "13900022005", "hash",
                Role.TEACHER, false, null));
        Long courseId = createCourse(institutionId, teacherId);
        Long studentId = createStudent(institutionId);
        consumptionRecordDao.insert(new CourseConsumptionRecord(null, institutionId, studentId, courseId,
                LocalDate.of(2024, 1, 2), new BigDecimal("50.00"), teacherId, null));
        consumptionRecordDao.insert(new CourseConsumptionRecord(null, institutionId, studentId, courseId,
                LocalDate.of(2024, 1, 3), new BigDecimal("30.00"), teacherId, null));

        assertThat(consumptionRecordDao.sumByStudentId(studentId)).isEqualByComparingTo("80.00");
    }

    @Test
    void sumByStudentIdReturnsZeroWhenNoRecords() {
        Long institutionId = institutionDao.insert("消课测试机构F");
        Long studentId = createStudent(institutionId);

        assertThat(consumptionRecordDao.sumByStudentId(studentId)).isEqualByComparingTo("0.00");
    }

    @Test
    void deleteAllByStudentIdRemovesRecords() {
        Long institutionId = institutionDao.insert("消课测试机构G");
        Long teacherId = teacherDao.insert(new Teacher(null, institutionId, "13900022006", "hash",
                Role.TEACHER, false, null));
        Long courseId = createCourse(institutionId, teacherId);
        Long studentId = createStudent(institutionId);
        consumptionRecordDao.insert(new CourseConsumptionRecord(null, institutionId, studentId, courseId,
                LocalDate.of(2024, 1, 2), new BigDecimal("50.00"), teacherId, null));

        consumptionRecordDao.deleteAllByStudentId(studentId);

        assertThat(consumptionRecordDao.findAllByStudentId(studentId)).isEmpty();
    }

    @Test
    void deleteAllByCourseIdRemovesRecords() {
        Long institutionId = institutionDao.insert("消课测试机构H");
        Long teacherId = teacherDao.insert(new Teacher(null, institutionId, "13900022007", "hash",
                Role.TEACHER, false, null));
        Long courseId = createCourse(institutionId, teacherId);
        Long studentId = createStudent(institutionId);
        consumptionRecordDao.insert(new CourseConsumptionRecord(null, institutionId, studentId, courseId,
                LocalDate.of(2024, 1, 2), new BigDecimal("50.00"), teacherId, null));

        consumptionRecordDao.deleteAllByCourseId(courseId);

        assertThat(consumptionRecordDao.findAllByStudentId(studentId)).isEmpty();
    }
}
