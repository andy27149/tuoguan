package com.tuoguan.backend.monthlyquickbill.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tuoguan.backend.auth.dao.InstitutionDao;
import com.tuoguan.backend.auth.dao.TeacherDao;
import com.tuoguan.backend.auth.domain.Role;
import com.tuoguan.backend.auth.domain.Teacher;
import com.tuoguan.backend.auth.web.LoginResponse;
import com.tuoguan.backend.kanban.dao.StudentArrivalCheckinDao;
import com.tuoguan.backend.roster.dao.StudentDao;
import com.tuoguan.backend.roster.domain.Student;
import com.tuoguan.backend.support.IntegrationTestBase;
import com.tuoguan.backend.unit.dao.StudentUnitEnrollmentDao;
import com.tuoguan.backend.unit.dao.TeachingUnitDao;
import com.tuoguan.backend.unit.domain.BillingMode;
import com.tuoguan.backend.unit.domain.StudentUnitEnrollment;
import com.tuoguan.backend.unit.domain.TeachingUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class MonthlyQuickBillControllerTest extends IntegrationTestBase {

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

    @Autowired
    private StudentArrivalCheckinDao arrivalCheckinDao;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void listStudentsReturnsOnlyEnrolledStudentsWithTheirLessonCountCourses() throws Exception {
        Long institutionId = institutionDao.insert("快速账单测试机构A");
        String adminPhone = uniquePhone();
        teacherDao.insert(new Teacher(null, institutionId, adminPhone,
                passwordEncoder.encode("admin-password"), Role.ADMIN, false, null));
        Long teacherId = teacherDao.insert(new Teacher(null, institutionId, uniquePhone(),
                passwordEncoder.encode("teacher-password"), Role.TEACHER, false, null));
        Long classRoomId = teachingUnitDao.insert(new TeachingUnit(null, institutionId, teacherId, "快速一班",
                BillingMode.MONTHLY, null, null, true, null));
        Long courseId = teachingUnitDao.insert(new TeachingUnit(null, institutionId, teacherId, "羽毛球课",
                BillingMode.LESSON_COUNT, 60, new BigDecimal("50.00"), true, null));
        Long enrolledStudentId = studentDao.insert(new Student(null, institutionId, classRoomId, "小明", "一班",
                true, null, null));
        enrollmentDao.insert(new StudentUnitEnrollment(null, institutionId, enrolledStudentId, courseId, true, null));
        // 已退班的学生不应该出现在列表里
        studentDao.insert(new Student(null, institutionId, classRoomId, "小退", "一班", false, null, null));
        String adminToken = login(adminPhone, "admin-password");

        mockMvc.perform(get("/api/admin/monthly-quick-bill/classes/" + classRoomId + "/students")
                        .header("Authorization", "Bearer " + adminToken)
                        .param("month", "2030-02"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].studentId").value(enrolledStudentId))
                .andExpect(jsonPath("$[0].name").value("小明"))
                .andExpect(jsonPath("$[0].mealDays").value(0))
                .andExpect(jsonPath("$[0].leaveDays").value(0))
                .andExpect(jsonPath("$[0].courses.length()").value(1))
                .andExpect(jsonPath("$[0].courses[0].courseId").value(courseId))
                .andExpect(jsonPath("$[0].courses[0].name").value("羽毛球课"))
                .andExpect(jsonPath("$[0].courses[0].consumptionCount").value(0));
    }

    @Test
    void listStudentsReflectsPreviouslySavedCountsSoOperatorsCanTopUpInAFollowUpVisit() throws Exception {
        // 对应真实需求：运营人员可能分两次录入同一个月的数据（月中录一次、月底再录一次）。
        // 界面要靠这个接口预填"已经保存的数"，运营人员在这个基础上改成新的整月累计数，
        // 而不是凭记忆口算、一不小心把之前存的数字覆盖掉。
        Long institutionId = institutionDao.insert("快速账单测试机构L");
        String adminPhone = uniquePhone();
        teacherDao.insert(new Teacher(null, institutionId, adminPhone,
                passwordEncoder.encode("admin-password"), Role.ADMIN, false, null));
        Long teacherId = teacherDao.insert(new Teacher(null, institutionId, uniquePhone(),
                passwordEncoder.encode("teacher-password"), Role.TEACHER, false, null));
        Long classRoomId = teachingUnitDao.insert(new TeachingUnit(null, institutionId, teacherId, "快速十班",
                BillingMode.MONTHLY, null, null, true, null));
        Long courseId = teachingUnitDao.insert(new TeachingUnit(null, institutionId, teacherId, "声乐课",
                BillingMode.LESSON_COUNT, 60, new BigDecimal("60.00"), true, null));
        Long studentId = studentDao.insert(new Student(null, institutionId, classRoomId, "小银", "十班",
                true, null, null));
        enrollmentDao.insert(new StudentUnitEnrollment(null, institutionId, studentId, courseId, true, null));
        String adminToken = login(adminPhone, "admin-password");

        // 第一次录入（比如月中）：用餐5天、请假1天、声乐课2节。
        mockMvc.perform(post("/api/admin/monthly-quick-bill/students/" + studentId + "/submit")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"month\":\"2030-11\",\"mealDays\":5,\"leaveDays\":1,"
                                + "\"courseConsumptions\":[{\"courseId\":" + courseId + ",\"count\":2}]}"))
                .andExpect(status().isNoContent());

        // 再次打开页面，应该看到刚才保存的数字——用餐/请假是预填出来准备被改成新累计数，
        // 课外课因为已经有消课记录，count>0，前端要把这一格锁成只读。
        mockMvc.perform(get("/api/admin/monthly-quick-bill/classes/" + classRoomId + "/students")
                        .header("Authorization", "Bearer " + adminToken)
                        .param("month", "2030-11"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].mealDays").value(5))
                .andExpect(jsonPath("$[0].leaveDays").value(1))
                .andExpect(jsonPath("$[0].courses[0].consumptionCount").value(2));
    }

    @Test
    void submitConvertsAggregateCountsIntoRecordsThatDriveTheExistingBillEngine() throws Exception {
        Long institutionId = institutionDao.insert("快速账单测试机构B");
        String adminPhone = uniquePhone();
        teacherDao.insert(new Teacher(null, institutionId, adminPhone,
                passwordEncoder.encode("admin-password"), Role.ADMIN, false, null));
        Long teacherId = teacherDao.insert(new Teacher(null, institutionId, uniquePhone(),
                passwordEncoder.encode("teacher-password"), Role.TEACHER, false, null));
        Long classRoomId = teachingUnitDao.insert(new TeachingUnit(null, institutionId, teacherId, "快速二班",
                BillingMode.MONTHLY, null, null, true, null));
        Long studentId = studentDao.insert(new Student(null, institutionId, classRoomId, "小红", "二班",
                true, null, null));
        String adminToken = login(adminPhone, "admin-password");

        mockMvc.perform(put("/api/admin/classes/" + classRoomId + "/billing-rate")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tuitionRatePerMonth\":500.00,\"mealRatePerDay\":10.00}"))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/admin/monthly-quick-bill/students/" + studentId + "/submit")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"month\":\"2030-03\",\"mealDays\":3,\"leaveDays\":2,\"courseConsumptions\":[]}"))
                .andExpect(status().isNoContent());

        mockMvc.perform(post("/api/admin/classes/" + classRoomId + "/bills/generate?month=2030-03")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].mealAmount").value(30.00))
                .andExpect(jsonPath("$[0].mealRecordDates.length()").value(3))
                .andExpect(jsonPath("$[0].leaveDays").value(2));
    }

    @Test
    void submitWritesIntoThePreviousMonthWhenInstitutionBillsTuitionInAdvance() throws Exception {
        // 本地用真实 E2E 数据手动验证时踩到的真实bug：机构开了"托管费提前收"之后，
        // BillGenerationService.resolveUsageMonth 规定第 M 月账单的出勤/用餐/消课数据要看
        // 第 M-1 月的真实记录。如果这里直接把 request.month 当成记录该落在哪个月，运营人员
        // 对着"2030-10月账单"填的数字会被写进10月，但账单引擎读的是9月，表面提交成功、
        // 账单数字却纹丝不动。
        Long institutionId = institutionDao.insert("快速账单测试机构J");
        institutionDao.updateBillTuitionInAdvance(institutionId, true);
        String adminPhone = uniquePhone();
        teacherDao.insert(new Teacher(null, institutionId, adminPhone,
                passwordEncoder.encode("admin-password"), Role.ADMIN, false, null));
        Long teacherId = teacherDao.insert(new Teacher(null, institutionId, uniquePhone(),
                passwordEncoder.encode("teacher-password"), Role.TEACHER, false, null));
        Long classRoomId = teachingUnitDao.insert(new TeachingUnit(null, institutionId, teacherId, "快速八班",
                BillingMode.MONTHLY, null, null, true, null));
        Long studentId = studentDao.insert(new Student(null, institutionId, classRoomId, "小黑", "八班",
                true, null, null));
        String adminToken = login(adminPhone, "admin-password");

        mockMvc.perform(put("/api/admin/classes/" + classRoomId + "/billing-rate")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tuitionRatePerMonth\":500.00,\"mealRatePerDay\":10.00}"))
                .andExpect(status().isOk());

        // 提交时传的是"2030-10"（这期账单的标签），用餐记录应该落在2030-09（提前收款机构
        // 的 usageMonth），不是字面上的2030-10。
        mockMvc.perform(post("/api/admin/monthly-quick-bill/students/" + studentId + "/submit")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"month\":\"2030-10\",\"mealDays\":4,\"leaveDays\":1,\"courseConsumptions\":[]}"))
                .andExpect(status().isNoContent());

        mockMvc.perform(post("/api/admin/classes/" + classRoomId + "/bills/generate?month=2030-10")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].mealAmount").value(40.00))
                .andExpect(jsonPath("$[0].mealRecordDates.length()").value(4))
                .andExpect(jsonPath("$[0].mealRecordDates[0]", org.hamcrest.Matchers.startsWith("2030-09")))
                .andExpect(jsonPath("$[0].leaveDays").value(1))
                .andExpect(jsonPath("$[0].leaveLines[0].leaveDate", org.hamcrest.Matchers.startsWith("2030-09")));
    }

    @Test
    void resubmittingWithDifferentCountsReplacesRatherThanAddsToThePreviousSubmission() throws Exception {
        Long institutionId = institutionDao.insert("快速账单测试机构H");
        String adminPhone = uniquePhone();
        teacherDao.insert(new Teacher(null, institutionId, adminPhone,
                passwordEncoder.encode("admin-password"), Role.ADMIN, false, null));
        Long teacherId = teacherDao.insert(new Teacher(null, institutionId, uniquePhone(),
                passwordEncoder.encode("teacher-password"), Role.TEACHER, false, null));
        Long classRoomId = teachingUnitDao.insert(new TeachingUnit(null, institutionId, teacherId, "快速六班",
                BillingMode.MONTHLY, null, null, true, null));
        Long studentId = studentDao.insert(new Student(null, institutionId, classRoomId, "小紫", "六班",
                true, null, null));
        String adminToken = login(adminPhone, "admin-password");

        mockMvc.perform(put("/api/admin/classes/" + classRoomId + "/billing-rate")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tuitionRatePerMonth\":500.00,\"mealRatePerDay\":10.00}"))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/admin/monthly-quick-bill/students/" + studentId + "/submit")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"month\":\"2030-07\",\"mealDays\":5,\"leaveDays\":3,\"courseConsumptions\":[]}"))
                .andExpect(status().isNoContent());

        // 改小一个数字重新提交——不应该在旧记录基础上叠加，账单要跟着新数字变，不是累加。
        mockMvc.perform(post("/api/admin/monthly-quick-bill/students/" + studentId + "/submit")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"month\":\"2030-07\",\"mealDays\":2,\"leaveDays\":1,\"courseConsumptions\":[]}"))
                .andExpect(status().isNoContent());

        mockMvc.perform(post("/api/admin/classes/" + classRoomId + "/bills/generate?month=2030-07")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].mealAmount").value(20.00))
                .andExpect(jsonPath("$[0].mealRecordDates.length()").value(2))
                .andExpect(jsonPath("$[0].leaveDays").value(1));
    }

    @Test
    void resubmittingNeverTouchesARealArrivalRecordEvenIfItCoincidentallyMatchesTheDefaultTime() throws Exception {
        // 真实踩到过的事故：早期实现靠"到校时间是不是固定的哨兵值"猜一条记录是不是本工具
        // 自己写的，结果机构数据里一条真实打卡记录凑巧用的也是同一个时间字符串，重新提交时
        // 被误判成"自己写的"直接删掉。这里故意构造这种巧合，验证现在靠专门的记账表
        // （monthly_quick_bill_record）识别，不会再犯同样的错误。
        Long institutionId = institutionDao.insert("快速账单测试机构K");
        String adminPhone = uniquePhone();
        teacherDao.insert(new Teacher(null, institutionId, adminPhone,
                passwordEncoder.encode("admin-password"), Role.ADMIN, false, null));
        Long teacherId = teacherDao.insert(new Teacher(null, institutionId, uniquePhone(),
                passwordEncoder.encode("teacher-password"), Role.TEACHER, false, null));
        Long classRoomId = teachingUnitDao.insert(new TeachingUnit(null, institutionId, teacherId, "快速九班",
                BillingMode.MONTHLY, null, null, true, null));
        Long studentId = studentDao.insert(new Student(null, institutionId, classRoomId, "小粉", "九班",
                true, null, null));
        String adminToken = login(adminPhone, "admin-password");

        mockMvc.perform(put("/api/admin/classes/" + classRoomId + "/billing-rate")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tuitionRatePerMonth\":500.00,\"mealRatePerDay\":10.00}"))
                .andExpect(status().isOk());

        // 真实的一条到校记录，巧合地用了跟本工具默认值一模一样的时间字符串"15:30"。
        arrivalCheckinDao.upsert(institutionId, classRoomId, studentId, java.time.LocalDate.of(2030, 9, 2), "15:30");

        mockMvc.perform(post("/api/admin/monthly-quick-bill/students/" + studentId + "/submit")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"month\":\"2030-09\",\"mealDays\":3,\"leaveDays\":0,\"courseConsumptions\":[]}"))
                .andExpect(status().isNoContent());

        // 重新提交一次（数字不变），如果还在靠猜值识别，这条真实记录会被当成"自己写的"清掉。
        mockMvc.perform(post("/api/admin/monthly-quick-bill/students/" + studentId + "/submit")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"month\":\"2030-09\",\"mealDays\":3,\"leaveDays\":0,\"courseConsumptions\":[]}"))
                .andExpect(status().isNoContent());

        assertThat(arrivalCheckinDao.findAllByStudentIdAndDateRange(studentId,
                java.time.LocalDate.of(2030, 9, 1), java.time.LocalDate.of(2030, 9, 30)))
                .extracting(a -> a.checkinDate())
                .contains(java.time.LocalDate.of(2030, 9, 2));
    }

    @Test
    void resubmittingCourseConsumptionForTheSameMonthIsRejectedInsteadOfSilentlyMiscounting() throws Exception {
        Long institutionId = institutionDao.insert("快速账单测试机构I");
        String adminPhone = uniquePhone();
        teacherDao.insert(new Teacher(null, institutionId, adminPhone,
                passwordEncoder.encode("admin-password"), Role.ADMIN, false, null));
        Long teacherId = teacherDao.insert(new Teacher(null, institutionId, uniquePhone(),
                passwordEncoder.encode("teacher-password"), Role.TEACHER, false, null));
        Long classRoomId = teachingUnitDao.insert(new TeachingUnit(null, institutionId, teacherId, "快速七班",
                BillingMode.MONTHLY, null, null, true, null));
        Long courseId = teachingUnitDao.insert(new TeachingUnit(null, institutionId, teacherId, "跳绳课",
                BillingMode.LESSON_COUNT, 60, new BigDecimal("40.00"), true, null));
        Long studentId = studentDao.insert(new Student(null, institutionId, classRoomId, "小橙", "七班",
                true, null, null));
        enrollmentDao.insert(new StudentUnitEnrollment(null, institutionId, studentId, courseId, true, null));
        String adminToken = login(adminPhone, "admin-password");

        mockMvc.perform(post("/api/admin/monthly-quick-bill/students/" + studentId + "/submit")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"month\":\"2030-08\",\"mealDays\":0,\"leaveDays\":0,"
                                + "\"courseConsumptions\":[{\"courseId\":" + courseId + ",\"count\":2}]}"))
                .andExpect(status().isNoContent());

        // 同一个学生同一门课这个月已经提交过了，再提交应该明确拒绝，而不是悄悄叠加/少算。
        mockMvc.perform(post("/api/admin/monthly-quick-bill/students/" + studentId + "/submit")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"month\":\"2030-08\",\"mealDays\":0,\"leaveDays\":0,"
                                + "\"courseConsumptions\":[{\"courseId\":" + courseId + ",\"count\":5}]}"))
                .andExpect(status().isBadRequest());

        mockMvc.perform(get("/api/admin/students/" + studentId + "/course-consumption")
                        .header("Authorization", "Bearer " + adminToken)
                        .param("month", "2030-08"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].lessonCount").value(2));
    }

    @Test
    void submitRecordsCourseConsumptionForEnrolledCourse() throws Exception {
        Long institutionId = institutionDao.insert("快速账单测试机构C");
        String adminPhone = uniquePhone();
        teacherDao.insert(new Teacher(null, institutionId, adminPhone,
                passwordEncoder.encode("admin-password"), Role.ADMIN, false, null));
        Long teacherId = teacherDao.insert(new Teacher(null, institutionId, uniquePhone(),
                passwordEncoder.encode("teacher-password"), Role.TEACHER, false, null));
        Long classRoomId = teachingUnitDao.insert(new TeachingUnit(null, institutionId, teacherId, "快速三班",
                BillingMode.MONTHLY, null, null, true, null));
        Long courseId = teachingUnitDao.insert(new TeachingUnit(null, institutionId, teacherId, "绘画课",
                BillingMode.LESSON_COUNT, 60, new BigDecimal("80.00"), true, null));
        Long studentId = studentDao.insert(new Student(null, institutionId, classRoomId, "小蓝", "三班",
                true, null, null));
        enrollmentDao.insert(new StudentUnitEnrollment(null, institutionId, studentId, courseId, true, null));
        String adminToken = login(adminPhone, "admin-password");

        mockMvc.perform(post("/api/admin/monthly-quick-bill/students/" + studentId + "/submit")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"month\":\"2030-04\",\"mealDays\":0,\"leaveDays\":0,"
                                + "\"courseConsumptions\":[{\"courseId\":" + courseId + ",\"count\":4}]}"))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/admin/students/" + studentId + "/course-consumption")
                        .header("Authorization", "Bearer " + adminToken)
                        .param("month", "2030-04"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].courseName").value("绘画课"))
                .andExpect(jsonPath("$[0].lessonCount").value(4))
                .andExpect(jsonPath("$[0].amount").value(320.00));
    }

    @Test
    void submitRejectsWhenMealPlusLeaveExceedsWeekdaysInMonth() throws Exception {
        Long institutionId = institutionDao.insert("快速账单测试机构D");
        String adminPhone = uniquePhone();
        teacherDao.insert(new Teacher(null, institutionId, adminPhone,
                passwordEncoder.encode("admin-password"), Role.ADMIN, false, null));
        Long teacherId = teacherDao.insert(new Teacher(null, institutionId, uniquePhone(),
                passwordEncoder.encode("teacher-password"), Role.TEACHER, false, null));
        Long classRoomId = teachingUnitDao.insert(new TeachingUnit(null, institutionId, teacherId, "快速四班",
                BillingMode.MONTHLY, null, null, true, null));
        Long studentId = studentDao.insert(new Student(null, institutionId, classRoomId, "小绿", "四班",
                true, null, null));
        String adminToken = login(adminPhone, "admin-password");

        mockMvc.perform(post("/api/admin/monthly-quick-bill/students/" + studentId + "/submit")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"month\":\"2030-05\",\"mealDays\":15,\"leaveDays\":15,\"courseConsumptions\":[]}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void submitForStudentInAnotherInstitutionReturnsNotFound() throws Exception {
        Long institutionAId = institutionDao.insert("快速账单测试机构E");
        Long institutionBId = institutionDao.insert("快速账单测试机构F");
        String adminPhone = uniquePhone();
        teacherDao.insert(new Teacher(null, institutionAId, adminPhone,
                passwordEncoder.encode("admin-password"), Role.ADMIN, false, null));
        Long teacherBId = teacherDao.insert(new Teacher(null, institutionBId, uniquePhone(),
                passwordEncoder.encode("teacher-password"), Role.TEACHER, false, null));
        Long classRoomBId = teachingUnitDao.insert(new TeachingUnit(null, institutionBId, teacherBId, "乙机构班",
                BillingMode.MONTHLY, null, null, true, null));
        Long studentBId = studentDao.insert(new Student(null, institutionBId, classRoomBId, "小乙", "乙班",
                true, null, null));
        String adminToken = login(adminPhone, "admin-password");

        mockMvc.perform(post("/api/admin/monthly-quick-bill/students/" + studentBId + "/submit")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"month\":\"2030-06\",\"mealDays\":1,\"leaveDays\":0,\"courseConsumptions\":[]}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void nonAdminTeacherIsForbiddenFromUsingQuickBill() throws Exception {
        Long institutionId = institutionDao.insert("快速账单测试机构G");
        String teacherPhone = uniquePhone();
        Long teacherId = teacherDao.insert(new Teacher(null, institutionId, teacherPhone,
                passwordEncoder.encode("teacher-password"), Role.TEACHER, false, null));
        Long classRoomId = teachingUnitDao.insert(new TeachingUnit(null, institutionId, teacherId, "快速五班",
                BillingMode.MONTHLY, null, null, true, null));
        String token = login(teacherPhone, "teacher-password");

        mockMvc.perform(get("/api/admin/monthly-quick-bill/classes/" + classRoomId + "/students")
                        .header("Authorization", "Bearer " + token)
                        .param("month", "2030-05"))
                .andExpect(status().isForbidden());
    }

    @Test
    void submitRejectsCourseConsumptionInsteadOfSilentlySucceedingWhenStudentIsNotEnrolled() throws Exception {
        // recordBatchConsumption 对没报名这门课的学生是静默跳过、不抛异常（这是给"一次
        // 点名一整班"场景设计的行为）。这里故意不建报名关系，验证 quickbill 自己会把这种
        // "实际一条记录都没写成"的情况识别出来并报错，而不是回 204 让运营人员误以为存上了。
        Long institutionId = institutionDao.insert("快速账单测试机构M");
        String adminPhone = uniquePhone();
        teacherDao.insert(new Teacher(null, institutionId, adminPhone,
                passwordEncoder.encode("admin-password"), Role.ADMIN, false, null));
        Long teacherId = teacherDao.insert(new Teacher(null, institutionId, uniquePhone(),
                passwordEncoder.encode("teacher-password"), Role.TEACHER, false, null));
        Long classRoomId = teachingUnitDao.insert(new TeachingUnit(null, institutionId, teacherId, "快速十一班",
                BillingMode.MONTHLY, null, null, true, null));
        Long courseId = teachingUnitDao.insert(new TeachingUnit(null, institutionId, teacherId, "围棋课",
                BillingMode.LESSON_COUNT, 60, new BigDecimal("70.00"), true, null));
        Long studentId = studentDao.insert(new Student(null, institutionId, classRoomId, "小灰", "十一班",
                true, null, null));
        // 故意不插入报名记录。
        String adminToken = login(adminPhone, "admin-password");

        mockMvc.perform(post("/api/admin/monthly-quick-bill/students/" + studentId + "/submit")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"month\":\"2030-10\",\"mealDays\":0,\"leaveDays\":0,"
                                + "\"courseConsumptions\":[{\"courseId\":" + courseId + ",\"count\":3}]}"))
                .andExpect(status().isBadRequest());

        mockMvc.perform(get("/api/admin/students/" + studentId + "/course-consumption")
                        .header("Authorization", "Bearer " + adminToken)
                        .param("month", "2030-10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void submitRejectsMalformedMonthWithBadRequestInsteadOfServerError() throws Exception {
        Long institutionId = institutionDao.insert("快速账单测试机构N");
        String adminPhone = uniquePhone();
        teacherDao.insert(new Teacher(null, institutionId, adminPhone,
                passwordEncoder.encode("admin-password"), Role.ADMIN, false, null));
        Long teacherId = teacherDao.insert(new Teacher(null, institutionId, uniquePhone(),
                passwordEncoder.encode("teacher-password"), Role.TEACHER, false, null));
        Long classRoomId = teachingUnitDao.insert(new TeachingUnit(null, institutionId, teacherId, "快速十二班",
                BillingMode.MONTHLY, null, null, true, null));
        Long studentId = studentDao.insert(new Student(null, institutionId, classRoomId, "小橄", "十二班",
                true, null, null));
        String adminToken = login(adminPhone, "admin-password");

        mockMvc.perform(post("/api/admin/monthly-quick-bill/students/" + studentId + "/submit")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"month\":\"2030-13\",\"mealDays\":0,\"leaveDays\":0,\"courseConsumptions\":[]}"))
                .andExpect(status().isBadRequest());

        mockMvc.perform(get("/api/admin/monthly-quick-bill/classes/" + classRoomId + "/students")
                        .header("Authorization", "Bearer " + adminToken)
                        .param("month", "not-a-month"))
                .andExpect(status().isBadRequest());
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
