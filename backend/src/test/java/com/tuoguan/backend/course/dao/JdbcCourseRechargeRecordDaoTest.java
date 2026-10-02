package com.tuoguan.backend.course.dao;

import com.tuoguan.backend.auth.dao.InstitutionDao;
import com.tuoguan.backend.auth.dao.TeacherDao;
import com.tuoguan.backend.auth.domain.Role;
import com.tuoguan.backend.auth.domain.Teacher;
import com.tuoguan.backend.course.domain.CourseRechargeRecord;
import com.tuoguan.backend.roster.dao.StudentDao;
import com.tuoguan.backend.roster.domain.Student;
import com.tuoguan.backend.support.IntegrationTestBase;
import com.tuoguan.backend.unit.dao.TeachingUnitDao;
import com.tuoguan.backend.unit.domain.BillingMode;
import com.tuoguan.backend.unit.domain.TeachingUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class JdbcCourseRechargeRecordDaoTest extends IntegrationTestBase {

    @Autowired
    private InstitutionDao institutionDao;

    @Autowired
    private TeacherDao teacherDao;

    @Autowired
    private StudentDao studentDao;

    @Autowired
    private TeachingUnitDao teachingUnitDao;

    @Autowired
    private CourseRechargeRecordDao rechargeRecordDao;

    private Long createStudent(Long institutionId) {
        return studentDao.insert(new Student(null, institutionId, null, "课外学生", null, true, null, null));
    }

    private Long createAdmin(Long institutionId, String phone) {
        return teacherDao.insert(new Teacher(null, institutionId, phone, "hash", Role.ADMIN, false, null));
    }

    private Long createCourse(Long institutionId, Long teacherId) {
        return teachingUnitDao.insert(new TeachingUnit(null, institutionId, teacherId, "书法课",
                BillingMode.LESSON_COUNT, 60, null, true, null));
    }

    @Test
    void insertAndFindAllByStudentIdRoundTrips() {
        Long institutionId = institutionDao.insert("充值测试机构A");
        Long studentId = createStudent(institutionId);
        Long adminId = createAdmin(institutionId, "13900023001");
        Long courseId = createCourse(institutionId, adminId);

        rechargeRecordDao.insert(new CourseRechargeRecord(null, institutionId, studentId, courseId, 10,
                "微信转账", adminId, null));

        List<CourseRechargeRecord> found = rechargeRecordDao.findAllByStudentId(studentId);
        assertThat(found).hasSize(1);
        assertThat(found.get(0).teachingUnitId()).isEqualTo(courseId);
        assertThat(found.get(0).lessonCount()).isEqualTo(10);
        assertThat(found.get(0).note()).isEqualTo("微信转账");
    }

    @Test
    void deleteAllByStudentIdRemovesRecords() {
        Long institutionId = institutionDao.insert("充值测试机构D");
        Long studentId = createStudent(institutionId);
        Long adminId = createAdmin(institutionId, "13900023003");
        Long courseId = createCourse(institutionId, adminId);
        rechargeRecordDao.insert(new CourseRechargeRecord(null, institutionId, studentId, courseId, 10,
                null, adminId, null));

        rechargeRecordDao.deleteAllByStudentId(studentId);

        assertThat(rechargeRecordDao.findAllByStudentId(studentId)).isEmpty();
    }
}
