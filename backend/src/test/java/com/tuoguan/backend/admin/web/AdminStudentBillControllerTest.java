package com.tuoguan.backend.admin.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tuoguan.backend.admin.service.AdminTeacherService;
import com.tuoguan.backend.auth.dao.InstitutionDao;
import com.tuoguan.backend.auth.dao.TeacherDao;
import com.tuoguan.backend.auth.domain.Role;
import com.tuoguan.backend.auth.domain.Teacher;
import com.tuoguan.backend.auth.web.LoginResponse;
import com.tuoguan.backend.roster.dao.ClassRoomDao;
import com.tuoguan.backend.roster.dao.StudentDao;
import com.tuoguan.backend.roster.domain.ClassRoom;
import com.tuoguan.backend.roster.domain.Student;
import com.tuoguan.backend.support.IntegrationTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MvcResult;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
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
    private ClassRoomDao classRoomDao;

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
        Long classRoomId = classRoomDao.insert(new ClassRoom(null, institutionId, teacherId, "账单一班", null));
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
        Long classRoomId = classRoomDao.insert(new ClassRoom(null, institutionId, teacherId, "账单二班", null));
        Long studentId = studentDao.insert(new Student(null, institutionId, classRoomId, "学生乙", "一班", true, null, null));
        String adminToken = login("13900013003", "admin-password");

        mockMvc.perform(put("/api/admin/classes/" + classRoomId + "/billing-rate")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tuitionRatePerMonth\":50.00,\"mealRatePerDay\":10.00}"))
                .andExpect(status().isOk());

        int weekdays = countWeekdays(YearMonth.of(2024, 1));
        double expectedTuition = 50.00;
        double expectedMeal = weekdays * 10.00;

        mockMvc.perform(post("/api/admin/students/" + studentId + "/bills/generate?month=2024-01")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalWeekdays").value(weekdays))
                .andExpect(jsonPath("$.leaveDays").value(0))
                .andExpect(jsonPath("$.attendanceDays").value(weekdays))
                .andExpect(jsonPath("$.tuitionAmount").value(expectedTuition))
                .andExpect(jsonPath("$.mealAmount").value(expectedMeal))
                .andExpect(jsonPath("$.totalAmount").value(expectedTuition + expectedMeal));
    }

    @Test
    void leaveDaysReduceAttendanceAndAmount() throws Exception {
        Long institutionId = institutionDao.insert("账单测试机构C");
        teacherDao.insert(new Teacher(null, institutionId, "13900013005",
                passwordEncoder.encode("admin-password"), Role.ADMIN, false, null));
        Long teacherId = teacherDao.insert(new Teacher(null, institutionId, "13900013006",
                passwordEncoder.encode("teacher-password"), Role.TEACHER, false, null));
        Long classRoomId = classRoomDao.insert(new ClassRoom(null, institutionId, teacherId, "账单三班", null));
        Long studentId = studentDao.insert(new Student(null, institutionId, classRoomId, "学生丙", "一班", true, null, null));
        String adminToken = login("13900013005", "admin-password");

        mockMvc.perform(put("/api/admin/classes/" + classRoomId + "/billing-rate")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tuitionRatePerMonth\":50.00,\"mealRatePerDay\":10.00}"))
                .andExpect(status().isOk());

        // 2024-01-08, 2024-01-09 are both weekdays (Mon/Tue)
        mockMvc.perform(post("/api/admin/students/" + studentId + "/leave-records")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"startDate\":\"2024-01-08\",\"endDate\":\"2024-01-09\",\"reason\":\"发烧\"}"))
                .andExpect(status().isOk());

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
        Long classRoomId = classRoomDao.insert(new ClassRoom(null, institutionId, teacherId, "账单四班", null));
        Long studentId = studentDao.insert(new Student(null, institutionId, classRoomId, "学生丁", "一班", true, null, null));
        String adminToken = login("13900013007", "admin-password");

        mockMvc.perform(put("/api/admin/classes/" + classRoomId + "/billing-rate")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tuitionRatePerMonth\":50.00,\"mealRatePerDay\":10.00}"))
                .andExpect(status().isOk());

        MvcResult feeResult = mockMvc.perform(post("/api/admin/students/" + studentId + "/extra-fees")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"数学课\",\"pricePerLesson\":50.00}"))
                .andExpect(status().isCreated())
                .andReturn();
        Long feeId = objectMapper.readTree(feeResult.getResponse().getContentAsString()).get("id").asLong();
        mockMvc.perform(put("/api/admin/students/" + studentId + "/extra-fees/" + feeId
                        + "/lesson-count?month=2024-01")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"lessonCount\":4}"))
                .andExpect(status().isOk());

        int weekdays = countWeekdays(YearMonth.of(2024, 1));
        double baseAmount = 50.00 + weekdays * 10.00;

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
    void regeneratingSameMonthUpsertsInsteadOfDuplicating() throws Exception {
        Long institutionId = institutionDao.insert("账单测试机构E");
        teacherDao.insert(new Teacher(null, institutionId, "13900013009",
                passwordEncoder.encode("admin-password"), Role.ADMIN, false, null));
        Long teacherId = teacherDao.insert(new Teacher(null, institutionId, "13900013010",
                passwordEncoder.encode("teacher-password"), Role.TEACHER, false, null));
        Long classRoomId = classRoomDao.insert(new ClassRoom(null, institutionId, teacherId, "账单五班", null));
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
        Long classRoomBId = classRoomDao.insert(new ClassRoom(null, institutionBId, teacherBId, "账单六班", null));
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
        Long classRoomId = classRoomDao.insert(new ClassRoom(null, institutionId, teacherId, "账单七班", null));
        Long studentId = studentDao.insert(new Student(null, institutionId, classRoomId, "学生庚", "一班", true, null, null));
        String adminToken = login("13900013013", "admin-password");

        mockMvc.perform(put("/api/admin/classes/" + classRoomId + "/billing-rate")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tuitionRatePerMonth\":50.00,\"mealRatePerDay\":10.00}"))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/admin/students/" + studentId + "/extra-fees")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"数学课\",\"pricePerLesson\":50.00}"))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/admin/students/" + studentId + "/leave-records")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"startDate\":\"2024-01-08\",\"endDate\":\"2024-01-08\",\"reason\":\"事假\"}"))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/admin/students/" + studentId + "/bills/generate?month=2024-01")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());

        mockMvc.perform(delete("/api/admin/classes/" + classRoomId)
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
        Long classRoomId = classRoomDao.insert(new ClassRoom(null, institutionId, teacherId, "账单八班", null));
        Long studentId = studentDao.insert(new Student(null, institutionId, classRoomId, "学生辛", "一班", true, null, null));
        String adminToken = login("13900013015", "admin-password");

        mockMvc.perform(put("/api/admin/classes/" + classRoomId + "/billing-rate")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tuitionRatePerMonth\":50.00,\"mealRatePerDay\":10.00}"))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/admin/students/" + studentId + "/extra-fees")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"数学课\",\"pricePerLesson\":50.00}"))
                .andExpect(status().isCreated());
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
                "SELECT COUNT(*) FROM class_billing_rate WHERE class_room_id = ?", Integer.class, classRoomId);
        Integer feeCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM student_extra_fee WHERE student_id = ?", Integer.class, studentId);
        Integer leaveCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM student_leave_record WHERE class_room_id = ?", Integer.class, classRoomId);
        Integer billCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM monthly_bill WHERE class_room_id = ?", Integer.class, classRoomId);
        Integer billLineCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM monthly_bill_extra_fee_line l "
                        + "JOIN monthly_bill b ON l.monthly_bill_id = b.id WHERE b.class_room_id = ?",
                Integer.class, classRoomId);
        assertThat(rateCount).isZero();
        assertThat(feeCount).isZero();
        assertThat(leaveCount).isZero();
        assertThat(billCount).isZero();
        assertThat(billLineCount).isZero();
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
