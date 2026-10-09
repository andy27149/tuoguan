package com.tuoguan.backend.admin.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tuoguan.backend.auth.dao.InstitutionDao;
import com.tuoguan.backend.auth.dao.TeacherDao;
import com.tuoguan.backend.auth.domain.Role;
import com.tuoguan.backend.auth.domain.Teacher;
import com.tuoguan.backend.auth.web.LoginResponse;
import com.tuoguan.backend.billing.dao.StudentLeaveRecordDao;
import com.tuoguan.backend.course.dao.CourseConsumptionRecordDao;
import com.tuoguan.backend.course.dao.CourseRechargeRecordDao;
import com.tuoguan.backend.course.domain.CourseConsumptionRecord;
import com.tuoguan.backend.course.domain.CourseRechargeRecord;
import com.tuoguan.backend.kanban.dao.StudentArrivalCheckinDao;
import com.tuoguan.backend.kanban.dao.StudentMealRecordDao;
import com.tuoguan.backend.roster.dao.StudentDao;
import com.tuoguan.backend.roster.domain.Student;
import com.tuoguan.backend.unit.dao.StudentUnitEnrollmentDao;
import com.tuoguan.backend.unit.domain.StudentUnitEnrollment;
import com.tuoguan.backend.unit.dao.TeachingUnitDao;
import com.tuoguan.backend.unit.domain.BillingMode;
import com.tuoguan.backend.unit.domain.TeachingUnit;
import com.tuoguan.backend.support.IntegrationTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AdminOverviewControllerTest extends IntegrationTestBase {

    @Autowired
    private InstitutionDao institutionDao;

    @Autowired
    private TeacherDao teacherDao;

    @Autowired
    private TeachingUnitDao teachingUnitDao;

    @Autowired
    private StudentDao studentDao;

    @Autowired
    private StudentArrivalCheckinDao studentArrivalCheckinDao;

    @Autowired
    private StudentMealRecordDao studentMealRecordDao;

    @Autowired
    private StudentLeaveRecordDao studentLeaveRecordDao;

    @Autowired
    private CourseRechargeRecordDao courseRechargeRecordDao;

    @Autowired
    private CourseConsumptionRecordDao courseConsumptionRecordDao;

    @Autowired
    private StudentUnitEnrollmentDao enrollmentDao;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void lowBalanceIncludesStudentBelowThresholdAndExcludesStudentAtOrAboveIt() throws Exception {
        Long institutionId = institutionDao.insert("总览课时预警测试机构A");
        teacherDao.insert(new Teacher(null, institutionId, "13900015001",
                passwordEncoder.encode("admin-password"), Role.ADMIN, false, null));
        Long teacherId = teacherDao.insert(new Teacher(null, institutionId, "13900015002",
                passwordEncoder.encode("teacher-password"), Role.TEACHER, false, null));
        Long courseId = teachingUnitDao.insert(new TeachingUnit(null, institutionId, teacherId, "数学课",
                BillingMode.LESSON_COUNT, 60, null, true, null));
        Long lowBalanceStudentId = studentDao.insert(
                new Student(null, institutionId, null, "低余额学生", null, true, null, null));
        Long healthyStudentId = studentDao.insert(
                new Student(null, institutionId, null, "余额充足学生", null, true, null, null));
        // 低余额学生：充值 2，消课 0，余额 2（< 3，应该出现）。
        courseRechargeRecordDao.insert(new CourseRechargeRecord(null, institutionId, lowBalanceStudentId, courseId,
                2, null, teacherId, null));
        // 余额充足学生：充值 5，消课 0，余额 5（>= 3，不应该出现）。
        courseRechargeRecordDao.insert(new CourseRechargeRecord(null, institutionId, healthyStudentId, courseId,
                5, null, teacherId, null));
        String adminToken = login("13900015001", "admin-password");

        mockMvc.perform(get("/api/admin/overview/low-balance")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.studentId == " + lowBalanceStudentId + ")].balance").value(
                        org.hamcrest.Matchers.contains(2)))
                .andExpect(jsonPath("$[?(@.studentId == " + healthyStudentId + ")]").isEmpty());
    }

    @Test
    void lowBalanceIncludesZeroAndNegativeBalances() throws Exception {
        Long institutionId = institutionDao.insert("总览课时预警测试机构B");
        teacherDao.insert(new Teacher(null, institutionId, "13900015010",
                passwordEncoder.encode("admin-password"), Role.ADMIN, false, null));
        Long teacherId = teacherDao.insert(new Teacher(null, institutionId, "13900015011",
                passwordEncoder.encode("teacher-password"), Role.TEACHER, false, null));
        Long courseId = teachingUnitDao.insert(new TeachingUnit(null, institutionId, teacherId, "英语课",
                BillingMode.LESSON_COUNT, 60, null, true, null));
        Long studentId = studentDao.insert(new Student(null, institutionId, null, "欠费学生", null, true, null, null));
        // 充值 1，消课 2，余额 -1。
        courseRechargeRecordDao.insert(new CourseRechargeRecord(null, institutionId, studentId, courseId,
                1, null, teacherId, null));
        courseConsumptionRecordDao.insert(new CourseConsumptionRecord(null, institutionId, studentId, courseId,
                LocalDate.now(), BigDecimal.valueOf(50), teacherId, null));
        courseConsumptionRecordDao.insert(new CourseConsumptionRecord(null, institutionId, studentId, courseId,
                LocalDate.now().minusDays(1), BigDecimal.valueOf(50), teacherId, null));
        String adminToken = login("13900015010", "admin-password");

        mockMvc.perform(get("/api/admin/overview/low-balance")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.studentId == " + studentId + ")].balance").value(
                        org.hamcrest.Matchers.contains(-1)));
    }

    @Test
    void lowBalanceListsTwoCoursesForTheSameStudentAsSeparateRows() throws Exception {
        Long institutionId = institutionDao.insert("总览课时预警测试机构C");
        teacherDao.insert(new Teacher(null, institutionId, "13900015020",
                passwordEncoder.encode("admin-password"), Role.ADMIN, false, null));
        Long teacherId = teacherDao.insert(new Teacher(null, institutionId, "13900015021",
                passwordEncoder.encode("teacher-password"), Role.TEACHER, false, null));
        Long courseAId = teachingUnitDao.insert(new TeachingUnit(null, institutionId, teacherId, "语文课",
                BillingMode.LESSON_COUNT, 60, null, true, null));
        Long courseBId = teachingUnitDao.insert(new TeachingUnit(null, institutionId, teacherId, "数学课",
                BillingMode.LESSON_COUNT, 60, null, true, null));
        Long studentId = studentDao.insert(new Student(null, institutionId, null, "双课程学生", null, true, null, null));
        courseRechargeRecordDao.insert(new CourseRechargeRecord(null, institutionId, studentId, courseAId,
                1, null, teacherId, null));
        courseRechargeRecordDao.insert(new CourseRechargeRecord(null, institutionId, studentId, courseBId,
                2, null, teacherId, null));
        String adminToken = login("13900015020", "admin-password");

        mockMvc.perform(get("/api/admin/overview/low-balance")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.courseId == " + courseAId + ")].balance").value(
                        org.hamcrest.Matchers.contains(1)))
                .andExpect(jsonPath("$[?(@.courseId == " + courseBId + ")].balance").value(
                        org.hamcrest.Matchers.contains(2)));
    }

    @Test
    void lowBalanceReturnsEmptyListForInstitutionWithNoRecords() throws Exception {
        Long institutionId = institutionDao.insert("总览课时预警测试机构D");
        teacherDao.insert(new Teacher(null, institutionId, "13900015030",
                passwordEncoder.encode("admin-password"), Role.ADMIN, false, null));
        String adminToken = login("13900015030", "admin-password");

        mockMvc.perform(get("/api/admin/overview/low-balance")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    void lowBalanceNeverLeaksAnotherInstitutionsStudents() throws Exception {
        Long institutionAId = institutionDao.insert("总览课时预警测试机构E");
        Long institutionBId = institutionDao.insert("总览课时预警测试机构F");
        teacherDao.insert(new Teacher(null, institutionAId, "13900015040",
                passwordEncoder.encode("admin-password"), Role.ADMIN, false, null));
        Long teacherBId = teacherDao.insert(new Teacher(null, institutionBId, "13900015041",
                passwordEncoder.encode("teacher-password"), Role.TEACHER, false, null));
        Long courseBId = teachingUnitDao.insert(new TeachingUnit(null, institutionBId, teacherBId, "B机构课程",
                BillingMode.LESSON_COUNT, 60, null, true, null));
        Long studentBId = studentDao.insert(
                new Student(null, institutionBId, null, "B机构低余额学生", null, true, null, null));
        courseRechargeRecordDao.insert(new CourseRechargeRecord(null, institutionBId, studentBId, courseBId,
                1, null, teacherBId, null));
        String adminAToken = login("13900015040", "admin-password");

        mockMvc.perform(get("/api/admin/overview/low-balance")
                        .header("Authorization", "Bearer " + adminAToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    void nonAdminIsForbiddenFromLowBalance() throws Exception {
        Long institutionId = institutionDao.insert("总览课时预警测试机构G");
        teacherDao.insert(new Teacher(null, institutionId, "13900015050",
                passwordEncoder.encode("teacher-password"), Role.TEACHER, false, null));
        String teacherToken = login("13900015050", "teacher-password");

        mockMvc.perform(get("/api/admin/overview/low-balance")
                        .header("Authorization", "Bearer " + teacherToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void unpaidBillsSummaryListsEveryUnpaidMonthSeparatelyAndExcludesPaidAndUngenerated() throws Exception {
        Long institutionId = institutionDao.insert("总览欠费测试机构");
        teacherDao.insert(new Teacher(null, institutionId, "13900015060",
                passwordEncoder.encode("admin-password"), Role.ADMIN, false, null));
        String adminToken = login("13900015060", "admin-password");

        // 学生甲：两个月都没缴费，必须出现两行，各自标对月份。
        Long studentAId = studentDao.insert(new Student(null, institutionId, null, "学生甲", null, true, null, null));
        mockMvc.perform(post("/api/admin/students/" + studentAId + "/bills/generate?month=2024-01")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/admin/students/" + studentAId + "/bills/generate?month=2024-02")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());

        // 学生乙：账单已缴费，不该出现。
        Long studentBId = studentDao.insert(new Student(null, institutionId, null, "学生乙", null, true, null, null));
        MvcResult generateResult = mockMvc.perform(post("/api/admin/students/" + studentBId + "/bills/generate?month=2024-01")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andReturn();
        Long paidBillId = objectMapper.readTree(generateResult.getResponse().getContentAsString()).get("id").asLong();
        mockMvc.perform(patch("/api/admin/bills/" + paidBillId + "/paid")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"isPaid\":true}"))
                .andExpect(status().isOk());

        // 学生丙：从没生成过账单，不该出现。
        studentDao.insert(new Student(null, institutionId, null, "学生丙", null, true, null, null));

        MvcResult result = mockMvc.perform(get("/api/admin/overview/unpaid-bills")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andReturn();
        String body = result.getResponse().getContentAsString();

        assertThat(objectMapper.readTree(body).get("count").asInt()).isEqualTo(2);
        assertThat(objectMapper.readTree(body).get("rows")).hasSize(2);
        assertThat(body).contains("\"yearMonth\":\"2024-01\"");
        assertThat(body).contains("\"yearMonth\":\"2024-02\"");
        assertThat(body).doesNotContain("学生乙");
        assertThat(body).doesNotContain("学生丙");
    }

    @Test
    void unpaidBillsSummaryIsolatesByInstitution() throws Exception {
        Long institutionAId = institutionDao.insert("总览欠费隔离机构A");
        teacherDao.insert(new Teacher(null, institutionAId, "13900015061",
                passwordEncoder.encode("admin-password"), Role.ADMIN, false, null));
        String adminAToken = login("13900015061", "admin-password");

        Long institutionBId = institutionDao.insert("总览欠费隔离机构B");
        teacherDao.insert(new Teacher(null, institutionBId, "13900015062",
                passwordEncoder.encode("admin-password"), Role.ADMIN, false, null));
        String adminBToken = login("13900015062", "admin-password");
        Long studentBId = studentDao.insert(new Student(null, institutionBId, null, "机构B学生", null, true, null, null));
        mockMvc.perform(post("/api/admin/students/" + studentBId + "/bills/generate?month=2024-01")
                        .header("Authorization", "Bearer " + adminBToken))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/admin/overview/unpaid-bills")
                        .header("Authorization", "Bearer " + adminAToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(0))
                .andExpect(jsonPath("$.rows").isEmpty());
    }

    @Test
    void unpaidBillsSummaryRequiresAdminRole() throws Exception {
        Long institutionId = institutionDao.insert("总览欠费鉴权测试机构");
        teacherDao.insert(new Teacher(null, institutionId, "13900015063",
                passwordEncoder.encode("teacher-password"), Role.TEACHER, false, null));
        String teacherToken = login("13900015063", "teacher-password");

        mockMvc.perform(get("/api/admin/overview/unpaid-bills")
                        .header("Authorization", "Bearer " + teacherToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void enrollmentReportsCustodyAndOffCampusCounts() throws Exception {
        Long institutionId = institutionDao.insert("总览-在读规模测试机构");
        teacherDao.insert(new Teacher(null, institutionId, "13600007001",
                passwordEncoder.encode("admin-password"), Role.ADMIN, false, null));
        Long teacherId = teacherDao.insert(new Teacher(null, institutionId, "13600007002", "老师",
                passwordEncoder.encode("teacher-password"), Role.TEACHER, false, null));
        Long classId = teachingUnitDao.insert(new TeachingUnit(null, institutionId, teacherId, "一班",
                BillingMode.MONTHLY, null, null, true, null));
        studentDao.insert(new Student(null, institutionId, classId, "托管生", "一班", true, null, null));
        studentDao.insert(new Student(null, institutionId, null, "纯课外生", null, true, null, null));
        String adminToken = login("13600007001", "admin-password");

        mockMvc.perform(get("/api/admin/overview/enrollment")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalCount").value(2))
                .andExpect(jsonPath("$.custodyCount").value(1))
                .andExpect(jsonPath("$.offCampusOnlyCount").value(1))
                .andExpect(jsonPath("$.byTeacher[0].teacherName").value("老师"))
                .andExpect(jsonPath("$.byTeacher[0].studentCount").value(1));
    }

    @Test
    void nonAdminTeacherIsForbiddenFromViewingEnrollmentSummary() throws Exception {
        Long institutionId = institutionDao.insert("总览-在读规模权限测试机构");
        teacherDao.insert(new Teacher(null, institutionId, "13600007003",
                passwordEncoder.encode("teacher-password"), Role.TEACHER, false, null));
        String teacherToken = login("13600007003", "teacher-password");

        mockMvc.perform(get("/api/admin/overview/enrollment")
                        .header("Authorization", "Bearer " + teacherToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void todaySnapshotCountsArrivalMealLeaveForOneCustodyClass() throws Exception {
        Long institutionId = institutionDao.insert("今日快照测试机构A");
        teacherDao.insert(new Teacher(null, institutionId, "13900016001",
                passwordEncoder.encode("admin-password"), Role.ADMIN, false, null));
        Long teacherId = teacherDao.insert(new Teacher(null, institutionId, "13900016002",
                passwordEncoder.encode("teacher-password"), Role.TEACHER, false, null));
        Long classRoomId = teachingUnitDao.insert(new TeachingUnit(null, institutionId, teacherId, "快照一班",
                BillingMode.MONTHLY, null, null, true, null));
        Long student1Id = studentDao.insert(new Student(null, institutionId, classRoomId, "学生甲", "一班", true, null, null));
        studentDao.insert(new Student(null, institutionId, classRoomId, "学生乙", "一班", true, null, null));
        LocalDate today = LocalDate.now();

        studentArrivalCheckinDao.upsert(institutionId, classRoomId, student1Id, today, "08:00");
        studentMealRecordDao.upsert(institutionId, classRoomId, student1Id, today);

        String adminToken = login("13900016001", "admin-password");

        mockMvc.perform(get("/api/admin/overview/today")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.arrivedCount").value(1))
                .andExpect(jsonPath("$.mealCount").value(1))
                .andExpect(jsonPath("$.leaveCount").value(0))
                .andExpect(jsonPath("$.totalCustodyStudentCount").value(2));
    }

    @Test
    void todaySnapshotSumsAcrossMultipleCustodyClasses() throws Exception {
        Long institutionId = institutionDao.insert("今日快照测试机构B");
        teacherDao.insert(new Teacher(null, institutionId, "13900016010",
                passwordEncoder.encode("admin-password"), Role.ADMIN, false, null));
        Long teacherAId = teacherDao.insert(new Teacher(null, institutionId, "13900016011",
                passwordEncoder.encode("teacher-password-a"), Role.TEACHER, false, null));
        Long teacherBId = teacherDao.insert(new Teacher(null, institutionId, "13900016012",
                passwordEncoder.encode("teacher-password-b"), Role.TEACHER, false, null));
        Long classAId = teachingUnitDao.insert(new TeachingUnit(null, institutionId, teacherAId, "快照A班",
                BillingMode.MONTHLY, null, null, true, null));
        Long classBId = teachingUnitDao.insert(new TeachingUnit(null, institutionId, teacherBId, "快照B班",
                BillingMode.MONTHLY, null, null, true, null));
        Long studentAId = studentDao.insert(new Student(null, institutionId, classAId, "甲班学生", "一班", true, null, null));
        Long studentBId = studentDao.insert(new Student(null, institutionId, classBId, "乙班学生", "一班", true, null, null));
        LocalDate today = LocalDate.now();

        studentArrivalCheckinDao.upsert(institutionId, classAId, studentAId, today, "08:00");
        studentArrivalCheckinDao.upsert(institutionId, classBId, studentBId, today, "08:30");
        studentLeaveRecordDao.upsert(institutionId, studentBId, classBId, today, "发烧");

        String adminToken = login("13900016010", "admin-password");

        mockMvc.perform(get("/api/admin/overview/today")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.arrivedCount").value(2))
                .andExpect(jsonPath("$.mealCount").value(0))
                .andExpect(jsonPath("$.leaveCount").value(1))
                .andExpect(jsonPath("$.totalCustodyStudentCount").value(2));
    }

    @Test
    void todaySnapshotExcludesDisabledStudentsFromTotalCustodyCount() throws Exception {
        Long institutionId = institutionDao.insert("今日快照测试机构C");
        teacherDao.insert(new Teacher(null, institutionId, "13900016020",
                passwordEncoder.encode("admin-password"), Role.ADMIN, false, null));
        Long teacherId = teacherDao.insert(new Teacher(null, institutionId, "13900016021",
                passwordEncoder.encode("teacher-password"), Role.TEACHER, false, null));
        Long classRoomId = teachingUnitDao.insert(new TeachingUnit(null, institutionId, teacherId, "快照C班",
                BillingMode.MONTHLY, null, null, true, null));
        studentDao.insert(new Student(null, institutionId, classRoomId, "在读学生", "一班", true, null, null));
        studentDao.insert(new Student(null, institutionId, classRoomId, "停用学生", "一班", false, null, null));

        String adminToken = login("13900016020", "admin-password");

        mockMvc.perform(get("/api/admin/overview/today")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalCustodyStudentCount").value(1))
                .andExpect(jsonPath("$.arrivedCount").value(0));
    }

    @Test
    void todaySnapshotForEmptyInstitutionReturnsAllZeros() throws Exception {
        Long institutionId = institutionDao.insert("今日快照测试机构D");
        teacherDao.insert(new Teacher(null, institutionId, "13900016030",
                passwordEncoder.encode("admin-password"), Role.ADMIN, false, null));
        String adminToken = login("13900016030", "admin-password");

        mockMvc.perform(get("/api/admin/overview/today")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.arrivedCount").value(0))
                .andExpect(jsonPath("$.mealCount").value(0))
                .andExpect(jsonPath("$.leaveCount").value(0))
                .andExpect(jsonPath("$.totalCustodyStudentCount").value(0));
    }

    @Test
    void todaySnapshotForClassWithNoStudentsYetReturnsAllZerosWithoutThrowing() throws Exception {
        // 覆盖"机构下有托管班，但这个班还没有学生"这个场景——跟上一个测试（机构里
        // 一个托管班都没有）是不同的代码路径：这里 for 循环会真的跑一轮，只是内部
        // 的学生列表和签到/用餐/请假列表都是空的，必须确认不会抛异常、不会把 null
        // 当成 0 处理错。
        Long institutionId = institutionDao.insert("今日快照测试机构F");
        teacherDao.insert(new Teacher(null, institutionId, "13900016050",
                passwordEncoder.encode("admin-password"), Role.ADMIN, false, null));
        Long teacherId = teacherDao.insert(new Teacher(null, institutionId, "13900016051",
                passwordEncoder.encode("teacher-password"), Role.TEACHER, false, null));
        teachingUnitDao.insert(new TeachingUnit(null, institutionId, teacherId, "空班",
                BillingMode.MONTHLY, null, null, true, null));
        String adminToken = login("13900016050", "admin-password");

        mockMvc.perform(get("/api/admin/overview/today")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.arrivedCount").value(0))
                .andExpect(jsonPath("$.mealCount").value(0))
                .andExpect(jsonPath("$.leaveCount").value(0))
                .andExpect(jsonPath("$.totalCustodyStudentCount").value(0));
    }

    @Test
    void todaySnapshotRejectsNonAdminCaller() throws Exception {
        Long institutionId = institutionDao.insert("今日快照测试机构E");
        teacherDao.insert(new Teacher(null, institutionId, "13900016040",
                passwordEncoder.encode("teacher-password"), Role.TEACHER, false, null));
        String teacherToken = login("13900016040", "teacher-password");

        mockMvc.perform(get("/api/admin/overview/today")
                        .header("Authorization", "Bearer " + teacherToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void revenueSnapshotSumsLastMonthTuitionAndExcludesCurrentMonth() throws Exception {
        Long institutionId = institutionDao.insert("收入快照测试机构A");
        teacherDao.insert(new Teacher(null, institutionId, "13900017001",
                passwordEncoder.encode("admin-password"), Role.ADMIN, false, null));
        Long teacherId = teacherDao.insert(new Teacher(null, institutionId, "13900017002",
                passwordEncoder.encode("teacher-password"), Role.TEACHER, false, null));
        Long classRoomId = teachingUnitDao.insert(new TeachingUnit(null, institutionId, teacherId, "收入一班",
                BillingMode.MONTHLY, null, null, true, null));
        Long paidStudentId = studentDao.insert(
                new Student(null, institutionId, classRoomId, "已缴费学生", "一班", true, null, null));
        Long unpaidStudentId = studentDao.insert(
                new Student(null, institutionId, classRoomId, "未缴费学生", "一班", true, null, null));
        String adminToken = login("13900017001", "admin-password");

        mockMvc.perform(put("/api/admin/classes/" + classRoomId + "/billing-rate")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tuitionRatePerMonth\":500.00,\"mealRatePerDay\":10.00}"))
                .andExpect(status().isOk());

        YearMonth lastMonth = YearMonth.now().minusMonths(1);
        YearMonth currentMonth = YearMonth.now();

        MvcResult paidGenerate = mockMvc.perform(post("/api/admin/students/" + paidStudentId
                        + "/bills/generate?month=" + lastMonth)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andReturn();
        Long paidBillId = objectMapper.readTree(paidGenerate.getResponse().getContentAsString()).get("id").asLong();
        mockMvc.perform(patch("/api/admin/bills/" + paidBillId + "/paid")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"isPaid\":true}"))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/admin/students/" + unpaidStudentId + "/bills/generate?month=" + lastMonth)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/admin/students/" + paidStudentId + "/bills/generate?month=" + currentMonth)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());

        MvcResult result = mockMvc.perform(get("/api/admin/overview/revenue")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tuitionMonth").value(lastMonth.toString()))
                .andExpect(jsonPath("$.consumptionMonth").value(currentMonth.toString()))
                .andReturn();

        var json = objectMapper.readTree(result.getResponse().getContentAsString());
        BigDecimal paidAmount = objectMapper.readTree(paidGenerate.getResponse().getContentAsString())
                .get("totalAmount").decimalValue();
        assertTrue(json.get("tuitionBilled").decimalValue().compareTo(json.get("tuitionCollected").decimalValue()) > 0);
        assertTrue(json.get("tuitionCollected").decimalValue().compareTo(paidAmount) >= 0);
    }

    @Test
    void revenueSnapshotCountsOnlyCurrentMonthOffCampusConsumptions() throws Exception {
        Long institutionId = institutionDao.insert("收入快照测试机构B");
        teacherDao.insert(new Teacher(null, institutionId, "13900017010",
                passwordEncoder.encode("admin-password"), Role.ADMIN, false, null));
        Long teacherId = teacherDao.insert(new Teacher(null, institutionId, "13900017011",
                passwordEncoder.encode("teacher-password"), Role.TEACHER, false, null));
        Long courseId = teachingUnitDao.insert(new TeachingUnit(null, institutionId, teacherId, "收入课程",
                BillingMode.LESSON_COUNT, 45, new BigDecimal("50.00"), true, null));
        Long studentId = studentDao.insert(new Student(null, institutionId, null, "消课学生", null, true, null, null));
        enrollmentDao.insert(new StudentUnitEnrollment(null, institutionId, studentId, courseId, true, null));
        String adminToken = login("13900017010", "admin-password");

        courseConsumptionRecordDao.insert(new CourseConsumptionRecord(null, institutionId, studentId, courseId,
                java.time.LocalDate.now(), new BigDecimal("50.00"), teacherId, null));
        courseConsumptionRecordDao.insert(new CourseConsumptionRecord(null, institutionId, studentId, courseId,
                java.time.LocalDate.now().minusMonths(1), new BigDecimal("50.00"), teacherId, null));

        mockMvc.perform(get("/api/admin/overview/revenue")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.offCampusConsumptionCount").value(1));
    }

    @Test
    void revenueSnapshotForEmptyInstitutionReturnsZeroes() throws Exception {
        Long institutionId = institutionDao.insert("收入快照测试机构C");
        teacherDao.insert(new Teacher(null, institutionId, "13900017020",
                passwordEncoder.encode("admin-password"), Role.ADMIN, false, null));
        String adminToken = login("13900017020", "admin-password");

        mockMvc.perform(get("/api/admin/overview/revenue")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tuitionBilled").value(0))
                .andExpect(jsonPath("$.tuitionCollected").value(0))
                .andExpect(jsonPath("$.offCampusConsumptionCount").value(0))
                .andExpect(jsonPath("$.tuitionMonth").value(YearMonth.now().minusMonths(1).toString()))
                .andExpect(jsonPath("$.consumptionMonth").value(YearMonth.now().toString()));
    }

    @Test
    void revenueSnapshotRejectsNonAdminCaller() throws Exception {
        Long institutionId = institutionDao.insert("收入快照测试机构D");
        teacherDao.insert(new Teacher(null, institutionId, "13900017030",
                passwordEncoder.encode("teacher-password"), Role.TEACHER, false, null));
        String teacherToken = login("13900017030", "teacher-password");

        mockMvc.perform(get("/api/admin/overview/revenue")
                        .header("Authorization", "Bearer " + teacherToken))
                .andExpect(status().isForbidden());
    }

    private String login(String phone, String password) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"phone\":\"" + phone + "\",\"password\":\"" + password + "\"}"))
                .andExpect(status().isOk())
                .andReturn();
        LoginResponse response = objectMapper.readValue(result.getResponse().getContentAsString(), LoginResponse.class);
        return response.token();
    }
}
