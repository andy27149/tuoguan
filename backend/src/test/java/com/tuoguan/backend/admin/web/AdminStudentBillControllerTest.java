package com.tuoguan.backend.admin.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tuoguan.backend.admin.service.AdminTeacherService;
import com.tuoguan.backend.auth.dao.InstitutionDao;
import com.tuoguan.backend.auth.dao.TeacherDao;
import com.tuoguan.backend.auth.domain.Role;
import com.tuoguan.backend.auth.domain.Teacher;
import com.tuoguan.backend.auth.web.LoginResponse;
import com.tuoguan.backend.roster.dao.StudentDao;
import com.tuoguan.backend.roster.domain.Student;
import com.tuoguan.backend.support.IntegrationTestBase;
import com.tuoguan.backend.unit.dao.TeachingUnitDao;
import com.tuoguan.backend.unit.domain.BillingMode;
import com.tuoguan.backend.unit.domain.TeachingUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MvcResult;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AdminStudentBillControllerTest extends IntegrationTestBase {

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
    private ObjectMapper objectMapper;

    @Test
    void generatingWithoutRateConfiguredReturnsBadRequest() throws Exception {
        Long institutionId = institutionDao.insert("账单测试机构A");
        teacherDao.insert(new Teacher(null, institutionId, "13900013001",
                passwordEncoder.encode("admin-password"), Role.ADMIN, false, null));
        Long teacherId = teacherDao.insert(new Teacher(null, institutionId, "13900013002",
                passwordEncoder.encode("teacher-password"), Role.TEACHER, false, null));
        Long classRoomId = teachingUnitDao.insert(new TeachingUnit(null, institutionId, teacherId, "账单一班",
                BillingMode.MONTHLY, null, null, true, null));
        Long studentId = studentDao.insert(new Student(null, institutionId, classRoomId, "学生甲", "一班", true, null, null));
        String adminToken = login("13900013001", "admin-password");

        mockMvc.perform(post("/api/admin/students/" + studentId + "/bills/generate?month=2024-01")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isBadRequest());
    }

    @Test
    void generatesBillWithFullAttendanceMatchingWeekdayCount() throws Exception {
        Long institutionId = institutionDao.insert("账单测试机构B");
        teacherDao.insert(new Teacher(null, institutionId, "13900013003",
                passwordEncoder.encode("admin-password"), Role.ADMIN, false, null));
        Long teacherId = teacherDao.insert(new Teacher(null, institutionId, "13900013004",
                passwordEncoder.encode("teacher-password"), Role.TEACHER, false, null));
        Long classRoomId = teachingUnitDao.insert(new TeachingUnit(null, institutionId, teacherId, "账单二班",
                BillingMode.MONTHLY, null, null, true, null));
        Long studentId = studentDao.insert(new Student(null, institutionId, classRoomId, "学生乙", "一班", true, null, null));
        String adminToken = login("13900013003", "admin-password");
        String teacherToken = login("13900013004", "teacher-password");

        mockMvc.perform(put("/api/admin/classes/" + classRoomId + "/billing-rate")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tuitionRatePerMonth\":50.00,\"mealRatePerDay\":10.00}"))
                .andExpect(status().isOk());
        // 出勤（attendanceDays）继续按工作日-请假天数推算，不受这次改动影响；餐费
        // 改成只认老师实际标记的用餐记录，这里特意只记 3 天，远少于当月工作日数，
        // 用来证明两者已经彻底解耦。
        for (String date : List.of("2024-01-02", "2024-01-03", "2024-01-04")) {
            mockMvc.perform(patch("/api/students/" + studentId + "/arrival")
                            .header("Authorization", "Bearer " + teacherToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"date\":\"" + date + "\",\"arrivedAt\":\"08:00\"}"))
                    .andExpect(status().isNoContent());
            mockMvc.perform(patch("/api/students/" + studentId + "/meal")
                            .header("Authorization", "Bearer " + teacherToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"date\":\"" + date + "\"}"))
                    .andExpect(status().isNoContent());
        }

        int weekdays = countWeekdays(YearMonth.of(2024, 1));
        double expectedTuition = 50.00;
        double expectedMeal = 3 * 10.00;

        mockMvc.perform(post("/api/admin/students/" + studentId + "/bills/generate?month=2024-01")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalWeekdays").value(weekdays))
                .andExpect(jsonPath("$.leaveDays").value(0))
                .andExpect(jsonPath("$.attendanceDays").value(weekdays))
                .andExpect(jsonPath("$.tuitionAmount").value(expectedTuition))
                .andExpect(jsonPath("$.mealAmount").value(expectedMeal))
                .andExpect(jsonPath("$.mealRecordDates.length()").value(3))
                .andExpect(jsonPath("$.totalAmount").value(expectedTuition + expectedMeal));
    }

    @Test
    void leaveDaysReduceAttendanceAndAmount() throws Exception {
        Long institutionId = institutionDao.insert("账单测试机构C");
        teacherDao.insert(new Teacher(null, institutionId, "13900013005",
                passwordEncoder.encode("admin-password"), Role.ADMIN, false, null));
        Long teacherId = teacherDao.insert(new Teacher(null, institutionId, "13900013006",
                passwordEncoder.encode("teacher-password"), Role.TEACHER, false, null));
        Long classRoomId = teachingUnitDao.insert(new TeachingUnit(null, institutionId, teacherId, "账单三班",
                BillingMode.MONTHLY, null, null, true, null));
        Long studentId = studentDao.insert(new Student(null, institutionId, classRoomId, "学生丙", "一班", true, null, null));
        String adminToken = login("13900013005", "admin-password");
        String teacherToken = login("13900013006", "teacher-password");

        mockMvc.perform(put("/api/admin/classes/" + classRoomId + "/billing-rate")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tuitionRatePerMonth\":50.00,\"mealRatePerDay\":10.00}"))
                .andExpect(status().isOk());

        // 2024-01-08, 2024-01-09 are both weekdays (Mon/Tue). 请假登记现在走老师端
        // 单日接口，不再是机构后台的区间登记。
        for (String date : List.of("2024-01-08", "2024-01-09")) {
            mockMvc.perform(patch("/api/students/" + studentId + "/leave")
                            .header("Authorization", "Bearer " + teacherToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"date\":\"" + date + "\",\"reason\":\"发烧\"}"))
                    .andExpect(status().isNoContent());
        }

        int weekdays = countWeekdays(YearMonth.of(2024, 1));
        int expectedAttendance = weekdays - 2;

        mockMvc.perform(post("/api/admin/students/" + studentId + "/bills/generate?month=2024-01")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalWeekdays").value(weekdays))
                .andExpect(jsonPath("$.leaveDays").value(2))
                .andExpect(jsonPath("$.attendanceDays").value(expectedAttendance))
                .andExpect(jsonPath("$.tuitionAmount").value(50.00));
    }

    @Test
    void extraFeesAreIncludedInBillTotal() throws Exception {
        Long institutionId = institutionDao.insert("账单测试机构D");
        teacherDao.insert(new Teacher(null, institutionId, "13900013007",
                passwordEncoder.encode("admin-password"), Role.ADMIN, false, null));
        Long teacherId = teacherDao.insert(new Teacher(null, institutionId, "13900013008",
                passwordEncoder.encode("teacher-password"), Role.TEACHER, false, null));
        Long classRoomId = teachingUnitDao.insert(new TeachingUnit(null, institutionId, teacherId, "账单四班",
                BillingMode.MONTHLY, null, null, true, null));
        Long studentId = studentDao.insert(new Student(null, institutionId, classRoomId, "学生丁", "一班", true, null, null));
        String adminToken = login("13900013007", "admin-password");

        mockMvc.perform(put("/api/admin/classes/" + classRoomId + "/billing-rate")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tuitionRatePerMonth\":50.00,\"mealRatePerDay\":10.00}"))
                .andExpect(status().isOk());

        String teacherToken = login("13900013008", "teacher-password");
        MvcResult courseResult = mockMvc.perform(post("/api/admin/teaching-units")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"billingMode\":\"LESSON_COUNT\",\"name\":\"数学课\",\"lessonDurationMinutes\":60,\"teacherId\":" + teacherId + "}"))
                .andExpect(status().isCreated())
                .andReturn();
        Long courseId = objectMapper.readTree(courseResult.getResponse().getContentAsString()).get("id").asLong();
        mockMvc.perform(patch("/api/admin/teaching-units/" + courseId)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"pricePerLesson\":50.00}"))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/courses/" + courseId + "/enrollments")
                        .header("Authorization", "Bearer " + teacherToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"studentId\":" + studentId + "}"))
                .andExpect(status().isCreated());
        for (String date : List.of("2024-01-02", "2024-01-03", "2024-01-04", "2024-01-05")) {
            mockMvc.perform(post("/api/courses/" + courseId + "/consumption")
                            .header("Authorization", "Bearer " + teacherToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"studentId\":" + studentId + ",\"date\":\"" + date + "\",\"confirm\":false}"))
                    .andExpect(status().isCreated());
        }

        // 没有老师标记任何用餐记录，餐费按新公式应该是 0——这个测试本身关心的是课外课
        // 附加费有没有正确计入总额，不是餐费，所以这里不特意造用餐记录。
        double baseAmount = 50.00;

        mockMvc.perform(post("/api/admin/students/" + studentId + "/bills/generate?month=2024-01")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.extraFeeTotal").value(200.00))
                .andExpect(jsonPath("$.totalAmount").value(baseAmount + 200.00))
                .andExpect(jsonPath("$.extraFeeLines.length()").value(1))
                .andExpect(jsonPath("$.extraFeeLines[0].name").value("数学课"))
                .andExpect(jsonPath("$.extraFeeLines[0].pricePerLesson").value(50.00))
                .andExpect(jsonPath("$.extraFeeLines[0].lessonCount").value(4))
                .andExpect(jsonPath("$.extraFeeLines[0].amount").value(200.00));
    }

    @Test
    void billAmountUsesPriceSnapshotImmuneToLaterPriceChanges() throws Exception {
        Long institutionId = institutionDao.insert("账单测试机构J");
        teacherDao.insert(new Teacher(null, institutionId, "13900013017",
                passwordEncoder.encode("admin-password"), Role.ADMIN, false, null));
        Long teacherId = teacherDao.insert(new Teacher(null, institutionId, "13900013018",
                passwordEncoder.encode("teacher-password"), Role.TEACHER, false, null));
        Long classRoomId = teachingUnitDao.insert(new TeachingUnit(null, institutionId, teacherId, "账单九班",
                BillingMode.MONTHLY, null, null, true, null));
        Long studentId = studentDao.insert(new Student(null, institutionId, classRoomId, "学生壬", "一班", true, null, null));
        String adminToken = login("13900013017", "admin-password");

        mockMvc.perform(put("/api/admin/classes/" + classRoomId + "/billing-rate")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tuitionRatePerMonth\":50.00,\"mealRatePerDay\":10.00}"))
                .andExpect(status().isOk());

        String teacherToken = login("13900013018", "teacher-password");
        MvcResult courseResult = mockMvc.perform(post("/api/admin/teaching-units")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"billingMode\":\"LESSON_COUNT\",\"name\":\"数学课\",\"lessonDurationMinutes\":60,\"teacherId\":" + teacherId + "}"))
                .andExpect(status().isCreated())
                .andReturn();
        Long courseId = objectMapper.readTree(courseResult.getResponse().getContentAsString()).get("id").asLong();
        mockMvc.perform(patch("/api/admin/teaching-units/" + courseId)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"pricePerLesson\":50.00}"))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/courses/" + courseId + "/enrollments")
                        .header("Authorization", "Bearer " + teacherToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"studentId\":" + studentId + "}"))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/courses/" + courseId + "/consumption")
                        .header("Authorization", "Bearer " + teacherToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"studentId\":" + studentId + ",\"date\":\"2024-01-02\",\"confirm\":false}"))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/admin/students/" + studentId + "/bills/generate?month=2024-01")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.extraFeeLines[0].amount").value(50.00));

        mockMvc.perform(patch("/api/admin/teaching-units/" + courseId)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"pricePerLesson\":999.00}"))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/admin/students/" + studentId + "/bills/generate?month=2024-01")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.extraFeeLines[0].amount").value(50.00))
                .andExpect(jsonPath("$.extraFeeLines[0].pricePerLesson").value(999.00));
    }

    @Test
    void generatingBillForPureOffCampusStudentReturnsZeroTuitionAndMeal() throws Exception {
        Long institutionId = institutionDao.insert("账单测试机构K");
        teacherDao.insert(new Teacher(null, institutionId, "13900013019",
                passwordEncoder.encode("admin-password"), Role.ADMIN, false, null));
        Long teacherId = teacherDao.insert(new Teacher(null, institutionId, "13900013020",
                passwordEncoder.encode("teacher-password"), Role.TEACHER, false, null));
        Long studentId = studentDao.insert(new Student(null, institutionId, null, "学生癸", null, true, null, null));
        String adminToken = login("13900013019", "admin-password");

        mockMvc.perform(post("/api/admin/students/" + studentId + "/bills/generate?month=2024-01")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tuitionAmount").value(0.00))
                .andExpect(jsonPath("$.mealAmount").value(0.00))
                .andExpect(jsonPath("$.totalAmount").value(0.00));
    }

    @Test
    void pureOffCampusStudentBillOnlyChargesConsumptionNotCoveredByPrepaidBalance() throws Exception {
        Long institutionId = institutionDao.insert("账单测试机构L");
        teacherDao.insert(new Teacher(null, institutionId, "13900013021",
                passwordEncoder.encode("admin-password"), Role.ADMIN, false, null));
        Long teacherId = teacherDao.insert(new Teacher(null, institutionId, "13900013022",
                passwordEncoder.encode("teacher-password"), Role.TEACHER, false, null));
        Long studentId = studentDao.insert(new Student(null, institutionId, null, "学生子", null, true, null, null));
        String adminToken = login("13900013021", "admin-password");
        String teacherToken = login("13900013022", "teacher-password");

        MvcResult courseResult = mockMvc.perform(post("/api/admin/teaching-units")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"billingMode\":\"LESSON_COUNT\",\"name\":\"围棋课\",\"lessonDurationMinutes\":60,\"teacherId\":" + teacherId + "}"))
                .andExpect(status().isCreated())
                .andReturn();
        Long courseId = objectMapper.readTree(courseResult.getResponse().getContentAsString()).get("id").asLong();
        mockMvc.perform(patch("/api/admin/teaching-units/" + courseId)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"pricePerLesson\":50.00}"))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/courses/" + courseId + "/enrollments")
                        .header("Authorization", "Bearer " + teacherToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"studentId\":" + studentId + "}"))
                .andExpect(status().isCreated());

        // 先充 2 课时，再消 4 次课：前 2 次被预充值覆盖，后 2 次需要计入账单。充值的
        // created_at 由数据库在"现在"自动生成，消课日期必须晚于"现在"才能被这笔充值覆盖
        // （充值没法倒追回去覆盖发生在它之前的消课），因此这里用安全的未来月份而不是常见
        // 测试惯用的 2024-01——那会落在充值之前，导致 4 次消课全部判定为未覆盖。
        mockMvc.perform(post("/api/admin/students/" + studentId + "/recharges")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"courseId\":" + courseId + ",\"lessonCount\":2}"))
                .andExpect(status().isCreated());
        for (String date : List.of("2030-01-02", "2030-01-03", "2030-01-04", "2030-01-05")) {
            mockMvc.perform(post("/api/courses/" + courseId + "/consumption")
                            .header("Authorization", "Bearer " + teacherToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"studentId\":" + studentId + ",\"date\":\"" + date + "\",\"confirm\":false}"))
                    .andExpect(status().isCreated());
        }

        mockMvc.perform(get("/api/admin/students/" + studentId + "/course-consumption?month=2030-01")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].lessonCount").value(2))
                .andExpect(jsonPath("$[0].coveredByBalanceCount").value(2))
                .andExpect(jsonPath("$[0].amount").value(100.00));

        mockMvc.perform(post("/api/admin/students/" + studentId + "/bills/generate?month=2030-01")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tuitionAmount").value(0.00))
                .andExpect(jsonPath("$.mealAmount").value(0.00))
                .andExpect(jsonPath("$.extraFeeTotal").value(100.00))
                .andExpect(jsonPath("$.totalAmount").value(100.00))
                .andExpect(jsonPath("$.extraFeeLines.length()").value(1))
                .andExpect(jsonPath("$.extraFeeLines[0].lessonCount").value(2))
                .andExpect(jsonPath("$.extraFeeLines[0].amount").value(100.00));
    }

    @Test
    void regeneratingSameMonthUpsertsInsteadOfDuplicating() throws Exception {
        Long institutionId = institutionDao.insert("账单测试机构E");
        teacherDao.insert(new Teacher(null, institutionId, "13900013009",
                passwordEncoder.encode("admin-password"), Role.ADMIN, false, null));
        Long teacherId = teacherDao.insert(new Teacher(null, institutionId, "13900013010",
                passwordEncoder.encode("teacher-password"), Role.TEACHER, false, null));
        Long classRoomId = teachingUnitDao.insert(new TeachingUnit(null, institutionId, teacherId, "账单五班",
                BillingMode.MONTHLY, null, null, true, null));
        Long studentId = studentDao.insert(new Student(null, institutionId, classRoomId, "学生戊", "一班", true, null, null));
        String adminToken = login("13900013009", "admin-password");

        mockMvc.perform(put("/api/admin/classes/" + classRoomId + "/billing-rate")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tuitionRatePerMonth\":50.00,\"mealRatePerDay\":10.00}"))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/admin/students/" + studentId + "/bills/generate?month=2024-01")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/admin/students/" + studentId + "/bills/generate?month=2024-01")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());

        Integer billCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM monthly_bill WHERE student_id = ? AND bill_month = ?",
                Integer.class, studentId, "2024-01");
        assertThat(billCount).isEqualTo(1);
    }

    @Test
    void generatingBillForStudentInAnotherInstitutionReturnsNotFound() throws Exception {
        Long institutionAId = institutionDao.insert("账单测试机构F");
        Long institutionBId = institutionDao.insert("账单测试机构G");
        teacherDao.insert(new Teacher(null, institutionAId, "13900013011",
                passwordEncoder.encode("admin-password"), Role.ADMIN, false, null));
        Long teacherBId = teacherDao.insert(new Teacher(null, institutionBId, "13900013012",
                passwordEncoder.encode("teacher-password"), Role.TEACHER, false, null));
        Long classRoomBId = teachingUnitDao.insert(new TeachingUnit(null, institutionBId, teacherBId, "账单六班",
                BillingMode.MONTHLY, null, null, true, null));
        Long studentBId = studentDao.insert(new Student(null, institutionBId, classRoomBId, "学生己", "一班", true, null, null));
        String adminToken = login("13900013011", "admin-password");

        mockMvc.perform(post("/api/admin/students/" + studentBId + "/bills/generate?month=2024-01")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNotFound());
    }

    @Test
    void deletingClassRoomCascadesBillingData() throws Exception {
        Long institutionId = institutionDao.insert("账单测试机构H");
        teacherDao.insert(new Teacher(null, institutionId, "13900013013",
                passwordEncoder.encode("admin-password"), Role.ADMIN, false, null));
        Long teacherId = teacherDao.insert(new Teacher(null, institutionId, "13900013014",
                passwordEncoder.encode("teacher-password"), Role.TEACHER, false, null));
        Long classRoomId = teachingUnitDao.insert(new TeachingUnit(null, institutionId, teacherId, "账单七班",
                BillingMode.MONTHLY, null, null, true, null));
        Long studentId = studentDao.insert(new Student(null, institutionId, classRoomId, "学生庚", "一班", true, null, null));
        String adminToken = login("13900013013", "admin-password");

        mockMvc.perform(put("/api/admin/classes/" + classRoomId + "/billing-rate")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tuitionRatePerMonth\":50.00,\"mealRatePerDay\":10.00}"))
                .andExpect(status().isOk());
        String teacherToken = login("13900013014", "teacher-password");
        createCourseWithConsumption(adminToken, teacherId, teacherToken, studentId);
        mockMvc.perform(patch("/api/students/" + studentId + "/leave")
                        .header("Authorization", "Bearer " + teacherToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"date\":\"2024-01-08\",\"reason\":\"事假\"}"))
                .andExpect(status().isNoContent());
        mockMvc.perform(post("/api/admin/students/" + studentId + "/bills/generate?month=2024-01")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());

        mockMvc.perform(delete("/api/admin/teaching-units/" + classRoomId)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNoContent());

        assertBillingDataCleared(classRoomId, studentId);
    }

    @Test
    void deletingTeacherWithDeleteAllModeCascadesBillingData() throws Exception {
        Long institutionId = institutionDao.insert("账单测试机构I");
        teacherDao.insert(new Teacher(null, institutionId, "13900013015",
                passwordEncoder.encode("admin-password"), Role.ADMIN, false, null));
        Long teacherId = teacherDao.insert(new Teacher(null, institutionId, "13900013016",
                passwordEncoder.encode("teacher-password"), Role.TEACHER, false, null));
        Long classRoomId = teachingUnitDao.insert(new TeachingUnit(null, institutionId, teacherId, "账单八班",
                BillingMode.MONTHLY, null, null, true, null));
        Long studentId = studentDao.insert(new Student(null, institutionId, classRoomId, "学生辛", "一班", true, null, null));
        String adminToken = login("13900013015", "admin-password");

        mockMvc.perform(put("/api/admin/classes/" + classRoomId + "/billing-rate")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tuitionRatePerMonth\":50.00,\"mealRatePerDay\":10.00}"))
                .andExpect(status().isOk());
        createCourseWithConsumption(adminToken, teacherId, login("13900013016", "teacher-password"), studentId);
        mockMvc.perform(post("/api/admin/students/" + studentId + "/bills/generate?month=2024-01")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());

        mockMvc.perform(delete("/api/admin/teachers/" + teacherId)
                        .header("Authorization", "Bearer " + adminToken)
                        .param("mode", AdminTeacherService.DeleteMode.DELETE_ALL.name()))
                .andExpect(status().isNoContent());

        assertBillingDataCleared(classRoomId, studentId);
    }

    private void assertBillingDataCleared(Long classRoomId, Long studentId) {
        Integer rateCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM class_billing_rate WHERE teaching_unit_id = ?", Integer.class, classRoomId);
        Integer consumptionCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM course_consumption_record WHERE student_id = ?", Integer.class, studentId);
        Integer leaveCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM student_leave_record WHERE teaching_unit_id = ?", Integer.class, classRoomId);
        Integer billCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM monthly_bill WHERE teaching_unit_id = ?", Integer.class, classRoomId);
        Integer billLineCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM monthly_bill_extra_fee_line l "
                        + "JOIN monthly_bill b ON l.monthly_bill_id = b.id WHERE b.teaching_unit_id = ?",
                Integer.class, classRoomId);
        assertThat(rateCount).isZero();
        assertThat(consumptionCount).isZero();
        assertThat(leaveCount).isZero();
        assertThat(billCount).isZero();
        assertThat(billLineCount).isZero();
    }

    private void createCourseWithConsumption(String adminToken, Long teacherId, String teacherToken, Long studentId) throws Exception {
        MvcResult courseResult = mockMvc.perform(post("/api/admin/teaching-units")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"billingMode\":\"LESSON_COUNT\",\"name\":\"数学课\",\"lessonDurationMinutes\":60,\"teacherId\":" + teacherId + "}"))
                .andExpect(status().isCreated())
                .andReturn();
        Long courseId = objectMapper.readTree(courseResult.getResponse().getContentAsString()).get("id").asLong();
        mockMvc.perform(patch("/api/admin/teaching-units/" + courseId)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"pricePerLesson\":50.00}"))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/courses/" + courseId + "/enrollments")
                        .header("Authorization", "Bearer " + teacherToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"studentId\":" + studentId + "}"))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/courses/" + courseId + "/consumption")
                        .header("Authorization", "Bearer " + teacherToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"studentId\":" + studentId + ",\"date\":\"2024-01-02\",\"confirm\":false}"))
                .andExpect(status().isCreated());
    }

    private int countWeekdays(YearMonth month) {
        int count = 0;
        LocalDate start = month.atDay(1);
        LocalDate end = month.atEndOfMonth();
        for (LocalDate d = start; !d.isAfter(end); d = d.plusDays(1)) {
            DayOfWeek dow = d.getDayOfWeek();
            if (dow != DayOfWeek.SATURDAY && dow != DayOfWeek.SUNDAY) {
                count++;
            }
        }
        return count;
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
