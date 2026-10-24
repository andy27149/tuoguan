package com.tuoguan.backend.admin.service;

import com.tuoguan.backend.admin.web.EnrollmentSummary;
import com.tuoguan.backend.admin.web.TeacherStudentCount;
import com.tuoguan.backend.auth.dao.InstitutionDao;
import com.tuoguan.backend.auth.dao.TeacherDao;
import com.tuoguan.backend.auth.domain.Role;
import com.tuoguan.backend.auth.domain.Teacher;
import com.tuoguan.backend.roster.dao.StudentDao;
import com.tuoguan.backend.roster.domain.Student;
import com.tuoguan.backend.unit.dao.TeachingUnitDao;
import com.tuoguan.backend.unit.domain.BillingMode;
import com.tuoguan.backend.unit.domain.TeachingUnit;
import com.tuoguan.backend.support.IntegrationTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

class AdminStatsServiceTest extends IntegrationTestBase {

    @Autowired
    private AdminStatsService adminStatsService;

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

    @Test
    void countsCustodyAndOffCampusStudentsSeparatelyAndGroupsCustodyStudentsByTeacher() {
        Long institutionId = institutionDao.insert("在读规模测试机构A");
        Long teacherAId = teacherDao.insert(new Teacher(null, institutionId, "13600006001", "A老师",
                passwordEncoder.encode("password"), Role.TEACHER, false, null));
        Long teacherBId = teacherDao.insert(new Teacher(null, institutionId, "13600006002", "B老师",
                passwordEncoder.encode("password"), Role.TEACHER, false, null));
        Long classAId = teachingUnitDao.insert(new TeachingUnit(null, institutionId, teacherAId, "A班",
                BillingMode.MONTHLY, null, null, true, null));
        Long classBId = teachingUnitDao.insert(new TeachingUnit(null, institutionId, teacherBId, "B班",
                BillingMode.MONTHLY, null, null, true, null));

        studentDao.insert(new Student(null, institutionId, classAId, "托管生甲", "一班", true, null, null));
        studentDao.insert(new Student(null, institutionId, classBId, "托管生乙", "二班", true, null, null));
        studentDao.insert(new Student(null, institutionId, null, "纯课外生", null, true, null, null));

        EnrollmentSummary summary = adminStatsService.getEnrollmentSummary(institutionId);

        assertThat(summary.totalCount()).isEqualTo(3);
        assertThat(summary.custodyCount()).isEqualTo(2);
        assertThat(summary.offCampusOnlyCount()).isEqualTo(1);
        assertThat(summary.byTeacher())
                .extracting(TeacherStudentCount::teacherName, TeacherStudentCount::studentCount)
                .containsExactlyInAnyOrder(tuple("A老师", 1), tuple("B老师", 1));
    }

    @Test
    void combinesStudentsFromTwoClassesOwnedByTheSameTeacherIntoOneEntry() {
        Long institutionId = institutionDao.insert("在读规模测试机构B");
        Long teacherId = teacherDao.insert(new Teacher(null, institutionId, "13600006003", "一人两班老师",
                passwordEncoder.encode("password"), Role.TEACHER, false, null));
        Long classAId = teachingUnitDao.insert(new TeachingUnit(null, institutionId, teacherId, "A班",
                BillingMode.MONTHLY, null, null, true, null));
        Long classBId = teachingUnitDao.insert(new TeachingUnit(null, institutionId, teacherId, "B班",
                BillingMode.MONTHLY, null, null, true, null));

        studentDao.insert(new Student(null, institutionId, classAId, "学生一", "一班", true, null, null));
        studentDao.insert(new Student(null, institutionId, classBId, "学生二", "二班", true, null, null));

        EnrollmentSummary summary = adminStatsService.getEnrollmentSummary(institutionId);

        assertThat(summary.byTeacher()).hasSize(1);
        assertThat(summary.byTeacher().get(0).teacherName()).isEqualTo("一人两班老师");
        assertThat(summary.byTeacher().get(0).studentCount()).isEqualTo(2);
    }

    @Test
    void excludesDisabledStudentsFromEveryCount() {
        Long institutionId = institutionDao.insert("在读规模测试机构C");
        Long teacherId = teacherDao.insert(new Teacher(null, institutionId, "13600006004", "老师",
                passwordEncoder.encode("password"), Role.TEACHER, false, null));
        Long classId = teachingUnitDao.insert(new TeachingUnit(null, institutionId, teacherId, "一班",
                BillingMode.MONTHLY, null, null, true, null));

        studentDao.insert(new Student(null, institutionId, classId, "在读生", "一班", true, null, null));
        studentDao.insert(new Student(null, institutionId, classId, "已停用生", "一班", false, null, null));

        EnrollmentSummary summary = adminStatsService.getEnrollmentSummary(institutionId);

        assertThat(summary.totalCount()).isEqualTo(1);
        assertThat(summary.custodyCount()).isEqualTo(1);
        assertThat(summary.byTeacher()).containsExactly(new TeacherStudentCount(teacherId, "老师", "13600006004", 1));
    }

    // 真实场景（力达托管机构实际数据）：3 年级和 4 年级的托管班负责老师恰好都叫
    // "刘老师"，但手机号不同——首页"在读规模"卡片的"按老师分布"统计之前只按
    // teacherName 分组展示，两条统计行会渲染成一模一样、无法区分的"刘老师"。
    // 这里锁定响应体里每条统计行都带着能唯一区分身份的 teacherId/teacherPhone。
    @Test
    void keepsTwoSameNamedTeachersDistinguishableByIdAndPhone() {
        Long institutionId = institutionDao.insert("在读规模测试机构G-同名老师");
        Long teacherAId = teacherDao.insert(new Teacher(null, institutionId, "13600006006", "刘老师",
                passwordEncoder.encode("password"), Role.TEACHER, false, null));
        Long teacherBId = teacherDao.insert(new Teacher(null, institutionId, "13600006007", "刘老师",
                passwordEncoder.encode("password"), Role.TEACHER, false, null));
        Long classAId = teachingUnitDao.insert(new TeachingUnit(null, institutionId, teacherAId, "3年级托管班",
                BillingMode.MONTHLY, null, null, true, null));
        Long classBId = teachingUnitDao.insert(new TeachingUnit(null, institutionId, teacherBId, "4年级托管班",
                BillingMode.MONTHLY, null, null, true, null));

        studentDao.insert(new Student(null, institutionId, classAId, "小刘甲", "3年级1班", true, null, null));
        studentDao.insert(new Student(null, institutionId, classAId, "小刘甲2", "3年级1班", true, null, null));
        studentDao.insert(new Student(null, institutionId, classBId, "小刘乙", "4年级1班", true, null, null));

        EnrollmentSummary summary = adminStatsService.getEnrollmentSummary(institutionId);

        assertThat(summary.byTeacher())
                .extracting(TeacherStudentCount::teacherId, TeacherStudentCount::teacherPhone,
                        TeacherStudentCount::studentCount)
                .containsExactlyInAnyOrder(
                        tuple(teacherAId, "13600006006", 2),
                        tuple(teacherBId, "13600006007", 1));
    }

    @Test
    void returnsAllZeroSummaryForAnEmptyInstitution() {
        Long institutionId = institutionDao.insert("在读规模测试机构D（空机构）");

        EnrollmentSummary summary = adminStatsService.getEnrollmentSummary(institutionId);

        assertThat(summary.totalCount()).isZero();
        assertThat(summary.custodyCount()).isZero();
        assertThat(summary.offCampusOnlyCount()).isZero();
        assertThat(summary.byTeacher()).isEmpty();
    }

    @Test
    void neverCountsAnotherInstitutionsStudents() {
        Long institutionAId = institutionDao.insert("在读规模测试机构E");
        Long institutionBId = institutionDao.insert("在读规模测试机构F");
        Long teacherBId = teacherDao.insert(new Teacher(null, institutionBId, "13600006005", "B机构老师",
                passwordEncoder.encode("password"), Role.TEACHER, false, null));
        Long classBId = teachingUnitDao.insert(new TeachingUnit(null, institutionBId, teacherBId, "B机构一班",
                BillingMode.MONTHLY, null, null, true, null));
        studentDao.insert(new Student(null, institutionBId, classBId, "B机构学生", "一班", true, null, null));

        EnrollmentSummary summary = adminStatsService.getEnrollmentSummary(institutionAId);

        assertThat(summary.totalCount()).isZero();
        assertThat(summary.byTeacher()).isEmpty();
    }
}
