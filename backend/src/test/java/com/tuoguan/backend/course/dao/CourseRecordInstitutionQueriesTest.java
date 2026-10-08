package com.tuoguan.backend.course.dao;

import com.tuoguan.backend.auth.dao.InstitutionDao;
import com.tuoguan.backend.auth.dao.TeacherDao;
import com.tuoguan.backend.auth.domain.Role;
import com.tuoguan.backend.auth.domain.Teacher;
import com.tuoguan.backend.course.domain.CourseConsumptionRecord;
import com.tuoguan.backend.course.domain.CourseRechargeRecord;
import com.tuoguan.backend.roster.dao.StudentDao;
import com.tuoguan.backend.roster.domain.Student;
import com.tuoguan.backend.support.IntegrationTestBase;
import com.tuoguan.backend.unit.dao.TeachingUnitDao;
import com.tuoguan.backend.unit.domain.BillingMode;
import com.tuoguan.backend.unit.domain.TeachingUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CourseRecordInstitutionQueriesTest extends IntegrationTestBase {

    @Autowired
    private InstitutionDao institutionDao;

    @Autowired
    private TeacherDao teacherDao;

    @Autowired
    private TeachingUnitDao teachingUnitDao;

    @Autowired
    private StudentDao studentDao;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private CourseRechargeRecordDao courseRechargeRecordDao;

    @Autowired
    private CourseConsumptionRecordDao courseConsumptionRecordDao;

    @Test
    void findAllByInstitutionIdReturnsOnlyThatInstitutionsRecordsForBothDaos() {
        Long institutionAId = institutionDao.insert("机构范围查询测试机构A");
        Long teacherAId = teacherDao.insert(new Teacher(null, institutionAId, "13900015001",
                passwordEncoder.encode("password"), Role.TEACHER, false, null));
        Long courseAId = teachingUnitDao.insert(new TeachingUnit(null, institutionAId, teacherAId, "课程A",
                BillingMode.LESSON_COUNT, null, null, true, null));
        Long studentAId = studentDao.insert(new Student(null, institutionAId, courseAId, "学生A", null, true, null, null));

        Long institutionBId = institutionDao.insert("机构范围查询测试机构B");
        Long teacherBId = teacherDao.insert(new Teacher(null, institutionBId, "13900015002",
                passwordEncoder.encode("password"), Role.TEACHER, false, null));
        Long courseBId = teachingUnitDao.insert(new TeachingUnit(null, institutionBId, teacherBId, "课程B",
                BillingMode.LESSON_COUNT, null, null, true, null));
        Long studentBId = studentDao.insert(new Student(null, institutionBId, courseBId, "学生B", null, true, null, null));

        Long rechargeAId = courseRechargeRecordDao.insert(
                new CourseRechargeRecord(null, institutionAId, studentAId, courseAId, 5, null, teacherAId, null));
        courseRechargeRecordDao.insert(
                new CourseRechargeRecord(null, institutionBId, studentBId, courseBId, 5, null, teacherBId, null));

        Long consumptionAId = courseConsumptionRecordDao.insert(
                new CourseConsumptionRecord(null, institutionAId, studentAId, courseAId, LocalDate.now(),
                        BigDecimal.valueOf(50), teacherAId, null));
        courseConsumptionRecordDao.insert(
                new CourseConsumptionRecord(null, institutionBId, studentBId, courseBId, LocalDate.now(),
                        BigDecimal.valueOf(50), teacherBId, null));

        List<CourseRechargeRecord> rechargesA = courseRechargeRecordDao.findAllByInstitutionId(institutionAId);
        assertThat(rechargesA).hasSize(1);
        assertThat(rechargesA.get(0).id()).isEqualTo(rechargeAId);
        assertThat(rechargesA.get(0).institutionId()).isEqualTo(institutionAId);

        List<CourseConsumptionRecord> consumptionsA = courseConsumptionRecordDao.findAllByInstitutionId(institutionAId);
        assertThat(consumptionsA).hasSize(1);
        assertThat(consumptionsA.get(0).id()).isEqualTo(consumptionAId);
        assertThat(consumptionsA.get(0).institutionId()).isEqualTo(institutionAId);
    }
}
