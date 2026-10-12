package com.tuoguan.backend.share.dao;

import com.tuoguan.backend.auth.dao.InstitutionDao;
import com.tuoguan.backend.auth.dao.TeacherDao;
import com.tuoguan.backend.auth.domain.Role;
import com.tuoguan.backend.auth.domain.Teacher;
import com.tuoguan.backend.roster.dao.StudentDao;
import com.tuoguan.backend.roster.domain.Student;
import com.tuoguan.backend.support.IntegrationTestBase;
import com.tuoguan.backend.unit.dao.TeachingUnitDao;
import com.tuoguan.backend.unit.domain.BillingMode;
import com.tuoguan.backend.unit.domain.TeachingUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class JdbcStudentShareViewLogDaoTest extends IntegrationTestBase {

    @Autowired
    private StudentShareViewLogDao viewLogDao;

    @Autowired
    private InstitutionDao institutionDao;

    @Autowired
    private TeacherDao teacherDao;

    @Autowired
    private TeachingUnitDao teachingUnitDao;

    @Autowired
    private StudentDao studentDao;

    @Test
    void recordingTheSameStudentTwiceOnTheSameDayCountsOnce() {
        Long institutionId = institutionDao.insert("扫码统计测试机构A");
        Long teacherId = teacherDao.insert(new Teacher(null, institutionId, "13900045001",
                "hash", Role.TEACHER, false, null));
        Long classRoomId = teachingUnitDao.insert(new TeachingUnit(null, institutionId, teacherId, "一班",
                BillingMode.MONTHLY, null, null, true, null));
        Long studentId = studentDao.insert(new Student(null, institutionId, classRoomId, "小明", "一班",
                true, null, null));
        LocalDate today = LocalDate.now();

        viewLogDao.recordView(institutionId, studentId, today);
        viewLogDao.recordView(institutionId, studentId, today);

        assertThat(viewLogDao.countDistinctStudentsOn(institutionId, today)).isEqualTo(1);
    }

    @Test
    void countsEachDistinctStudentViewedOnTheGivenDate() {
        Long institutionId = institutionDao.insert("扫码统计测试机构B");
        Long teacherId = teacherDao.insert(new Teacher(null, institutionId, "13900045002",
                "hash", Role.TEACHER, false, null));
        Long classRoomId = teachingUnitDao.insert(new TeachingUnit(null, institutionId, teacherId, "一班",
                BillingMode.MONTHLY, null, null, true, null));
        Long studentAId = studentDao.insert(new Student(null, institutionId, classRoomId, "学生甲", "一班",
                true, null, null));
        Long studentBId = studentDao.insert(new Student(null, institutionId, classRoomId, "学生乙", "一班",
                true, null, null));
        LocalDate today = LocalDate.now();

        viewLogDao.recordView(institutionId, studentAId, today);
        viewLogDao.recordView(institutionId, studentBId, today);

        assertThat(viewLogDao.countDistinctStudentsOn(institutionId, today)).isEqualTo(2);
    }

    @Test
    void doesNotCountViewsFromOtherDatesOrOtherInstitutions() {
        Long institutionId = institutionDao.insert("扫码统计测试机构C");
        Long otherInstitutionId = institutionDao.insert("扫码统计测试机构D");
        Long teacherId = teacherDao.insert(new Teacher(null, institutionId, "13900045003",
                "hash", Role.TEACHER, false, null));
        Long otherTeacherId = teacherDao.insert(new Teacher(null, otherInstitutionId, "13900045004",
                "hash", Role.TEACHER, false, null));
        Long classRoomId = teachingUnitDao.insert(new TeachingUnit(null, institutionId, teacherId, "一班",
                BillingMode.MONTHLY, null, null, true, null));
        Long otherClassRoomId = teachingUnitDao.insert(new TeachingUnit(null, otherInstitutionId, otherTeacherId,
                "一班", BillingMode.MONTHLY, null, null, true, null));
        Long studentId = studentDao.insert(new Student(null, institutionId, classRoomId, "小红", "一班",
                true, null, null));
        Long otherStudentId = studentDao.insert(new Student(null, otherInstitutionId, otherClassRoomId, "小蓝", "一班",
                true, null, null));
        LocalDate today = LocalDate.now();
        LocalDate yesterday = today.minusDays(1);

        viewLogDao.recordView(institutionId, studentId, yesterday);
        viewLogDao.recordView(otherInstitutionId, otherStudentId, today);

        assertThat(viewLogDao.countDistinctStudentsOn(institutionId, today)).isZero();
    }
}
