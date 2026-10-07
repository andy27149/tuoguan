package com.tuoguan.backend.admin.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tuoguan.backend.auth.dao.InstitutionDao;
import com.tuoguan.backend.auth.dao.TeacherDao;
import com.tuoguan.backend.auth.domain.Role;
import com.tuoguan.backend.auth.domain.Teacher;
import com.tuoguan.backend.auth.web.LoginResponse;
import com.tuoguan.backend.billing.dao.ClassBillingRateDao;
import com.tuoguan.backend.roster.dao.StudentDao;
import com.tuoguan.backend.roster.domain.Student;
import com.tuoguan.backend.unit.dao.TeachingUnitDao;
import com.tuoguan.backend.unit.domain.BillingMode;
import com.tuoguan.backend.unit.domain.TeachingUnit;
import com.tuoguan.backend.support.IntegrationTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AdminClassBillingControllerTest extends IntegrationTestBase {

    @Autowired
    private InstitutionDao institutionDao;

    @Autowired
    private TeacherDao teacherDao;

    @Autowired
    private TeachingUnitDao teachingUnitDao;

    @Autowired
    private StudentDao studentDao;

    @Autowired
    private ClassBillingRateDao classBillingRateDao;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void billingRateNotConfiguredReturnsNull() throws Exception {
        Long institutionId = institutionDao.insert("班级计费测试机构A");
        teacherDao.insert(new Teacher(null, institutionId, "13900010001",
                passwordEncoder.encode("admin-password"), Role.ADMIN, false, null));
        Long teacherId = teacherDao.insert(new Teacher(null, institutionId, "13900010002",
                passwordEncoder.encode("teacher-password"), Role.TEACHER, false, null));
        Long classRoomId = teachingUnitDao.insert(new TeachingUnit(null, institutionId, teacherId, "计费一班",
                BillingMode.MONTHLY, null, null, true, null));
        String adminToken = login("13900010001", "admin-password");

        mockMvc.perform(get("/api/admin/classes/" + classRoomId + "/billing-rate")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").doesNotExist());
    }

    @Test
    void upsertBillingRateThenReadItBack() throws Exception {
        Long institutionId = institutionDao.insert("班级计费测试机构B");
        teacherDao.insert(new Teacher(null, institutionId, "13900010003",
                passwordEncoder.encode("admin-password"), Role.ADMIN, false, null));
        Long teacherId = teacherDao.insert(new Teacher(null, institutionId, "13900010004",
                passwordEncoder.encode("teacher-password"), Role.TEACHER, false, null));
        Long classRoomId = teachingUnitDao.insert(new TeachingUnit(null, institutionId, teacherId, "计费二班",
                BillingMode.MONTHLY, null, null, true, null));
        String adminToken = login("13900010003", "admin-password");

        mockMvc.perform(put("/api/admin/classes/" + classRoomId + "/billing-rate")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tuitionRatePerMonth\":50.00,\"mealRatePerDay\":10.00}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tuitionRatePerMonth").value(50.00))
                .andExpect(jsonPath("$.mealRatePerDay").value(10.00));

        mockMvc.perform(get("/api/admin/classes/" + classRoomId + "/billing-rate")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tuitionRatePerMonth").value(50.00));

        // upsert again should update, not duplicate
        mockMvc.perform(put("/api/admin/classes/" + classRoomId + "/billing-rate")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tuitionRatePerMonth\":60.00,\"mealRatePerDay\":12.00}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tuitionRatePerMonth").value(60.00));
        assertThat(classBillingRateDao.findByTeachingUnitId(classRoomId)).get()
                .extracting(r -> r.mealRatePerDay().doubleValue())
                .isEqualTo(12.00);
    }

    @Test
    void billingRateForClassRoomInAnotherInstitutionReturnsNotFound() throws Exception {
        Long institutionAId = institutionDao.insert("班级计费测试机构C");
        Long institutionBId = institutionDao.insert("班级计费测试机构D");
        teacherDao.insert(new Teacher(null, institutionAId, "13900010005",
                passwordEncoder.encode("admin-password"), Role.ADMIN, false, null));
        Long teacherBId = teacherDao.insert(new Teacher(null, institutionBId, "13900010006",
                passwordEncoder.encode("teacher-password"), Role.TEACHER, false, null));
        Long classRoomId = teachingUnitDao.insert(new TeachingUnit(null, institutionBId, teacherBId, "计费三班",
                BillingMode.MONTHLY, null, null, true, null));
        String adminToken = login("13900010005", "admin-password");

        mockMvc.perform(get("/api/admin/classes/" + classRoomId + "/billing-rate")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNotFound());
    }

    @Test
    void nonAdminTeacherIsForbiddenFromReadingBillingRate() throws Exception {
        Long institutionId = institutionDao.insert("班级计费测试机构E");
        Long teacherId = teacherDao.insert(new Teacher(null, institutionId, "13900010007",
                passwordEncoder.encode("teacher-password"), Role.TEACHER, false, null));
        Long classRoomId = teachingUnitDao.insert(new TeachingUnit(null, institutionId, teacherId, "计费四班",
                BillingMode.MONTHLY, null, null, true, null));
        String token = login("13900010007", "teacher-password");

        mockMvc.perform(get("/api/admin/classes/" + classRoomId + "/billing-rate")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    @Test
    void generateBillsForClassSkipsUnenrolledStudentsAndFailsWithoutRate() throws Exception {
        Long institutionId = institutionDao.insert("班级计费测试机构G");
        teacherDao.insert(new Teacher(null, institutionId, "13900010010",
                passwordEncoder.encode("admin-password"), Role.ADMIN, false, null));
        Long teacherId = teacherDao.insert(new Teacher(null, institutionId, "13900010011",
                passwordEncoder.encode("teacher-password"), Role.TEACHER, false, null));
        Long classRoomId = teachingUnitDao.insert(new TeachingUnit(null, institutionId, teacherId, "计费六班",
                BillingMode.MONTHLY, null, null, true, null));
        studentDao.insert(new Student(null, institutionId, classRoomId, "学生丁", "一班", true, null, null));
        String adminToken = login("13900010010", "admin-password");

        // no billing rate configured yet
        mockMvc.perform(post("/api/admin/classes/" + classRoomId + "/bills/generate?month=2024-01")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isBadRequest());

        mockMvc.perform(put("/api/admin/classes/" + classRoomId + "/billing-rate")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tuitionRatePerMonth\":50.00,\"mealRatePerDay\":10.00}"))
                .andExpect(status().isOk());

        studentDao.insert(new Student(null, institutionId, classRoomId, "学生戊", "二班", false, null, null));

        mockMvc.perform(post("/api/admin/classes/" + classRoomId + "/bills/generate?month=2024-01")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));

        mockMvc.perform(get("/api/admin/classes/" + classRoomId + "/bills?month=2024-01")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    void mealFeeIsCalculatedFromActualMealRecordsNotAttendance() throws Exception {
        // 验证核心设计变更：餐费不再按"出勤天数"（工作日-请假天数，通常一个月 20+ 天）推算，
        // 改成按老师实际标记的用餐记录天数算。这里只记 3 天用餐、不请假，如果账单金额
        // 还是按旧的出勤公式算，mealAmount 会是接近 20 天的量级而不是 3 天。
        Long institutionId = institutionDao.insert("用餐账单测试机构A");
        teacherDao.insert(new Teacher(null, institutionId, "13900015001",
                passwordEncoder.encode("admin-password"), Role.ADMIN, false, null));
        Long teacherId = teacherDao.insert(new Teacher(null, institutionId, "13900015002",
                passwordEncoder.encode("teacher-password"), Role.TEACHER, false, null));
        Long classRoomId = teachingUnitDao.insert(new TeachingUnit(null, institutionId, teacherId, "用餐一班",
                BillingMode.MONTHLY, null, null, true, null));
        Long studentId = studentDao.insert(new Student(null, institutionId, classRoomId, "小明", "一班",
                true, null, null));
        String adminToken = login("13900015001", "admin-password");
        String teacherToken = login("13900015002", "teacher-password");

        mockMvc.perform(put("/api/admin/classes/" + classRoomId + "/billing-rate")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tuitionRatePerMonth\":500.00,\"mealRatePerDay\":10.00}"))
                .andExpect(status().isOk());

        for (String date : java.util.List.of("2024-01-06", "2024-01-07", "2024-01-08")) {
            mockMvc.perform(patch("/api/students/" + studentId + "/meal")
                            .header("Authorization", "Bearer " + teacherToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"date\":\"" + date + "\"}"))
                    .andExpect(status().isNoContent());
        }

        mockMvc.perform(post("/api/admin/students/" + studentId + "/bills/generate?month=2024-01")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mealAmount").value(30.00))
                .andExpect(jsonPath("$.mealRecordDates.length()").value(3))
                .andExpect(jsonPath("$.mealRecordDates", org.hamcrest.Matchers.containsInAnyOrder(
                        "2024-01-06", "2024-01-07", "2024-01-08")));
    }

    @Test
    void generatedBillStaysFixedAfterMealRecordsChangeUntilRegenerated() throws Exception {
        Long institutionId = institutionDao.insert("用餐账单测试机构B");
        teacherDao.insert(new Teacher(null, institutionId, "13900015003",
                passwordEncoder.encode("admin-password"), Role.ADMIN, false, null));
        Long teacherId = teacherDao.insert(new Teacher(null, institutionId, "13900015004",
                passwordEncoder.encode("teacher-password"), Role.TEACHER, false, null));
        Long classRoomId = teachingUnitDao.insert(new TeachingUnit(null, institutionId, teacherId, "用餐二班",
                BillingMode.MONTHLY, null, null, true, null));
        Long studentId = studentDao.insert(new Student(null, institutionId, classRoomId, "小红", "二班",
                true, null, null));
        String adminToken = login("13900015003", "admin-password");
        String teacherToken = login("13900015004", "teacher-password");

        mockMvc.perform(put("/api/admin/classes/" + classRoomId + "/billing-rate")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tuitionRatePerMonth\":500.00,\"mealRatePerDay\":10.00}"))
                .andExpect(status().isOk());
        mockMvc.perform(patch("/api/students/" + studentId + "/meal")
                        .header("Authorization", "Bearer " + teacherToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"date\":\"2024-02-05\"}"))
                .andExpect(status().isNoContent());
        mockMvc.perform(patch("/api/students/" + studentId + "/meal")
                        .header("Authorization", "Bearer " + teacherToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"date\":\"2024-02-06\"}"))
                .andExpect(status().isNoContent());

        MvcResult generateResult = mockMvc.perform(
                        post("/api/admin/students/" + studentId + "/bills/generate?month=2024-02")
                                .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mealAmount").value(20.00))
                .andReturn();
        Long billId = objectMapper.readTree(generateResult.getResponse().getContentAsString()).get("id").asLong();

        // 账单生成之后，老师又补记了一天用餐——已生成的账单不应该跟着变。
        mockMvc.perform(patch("/api/students/" + studentId + "/meal")
                        .header("Authorization", "Bearer " + teacherToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"date\":\"2024-02-07\"}"))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/admin/bills/" + billId)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mealAmount").value(20.00))
                .andExpect(jsonPath("$.mealRecordDates.length()").value(2));

        // 管理员手动重新生成，才会用上最新的用餐记录。
        mockMvc.perform(post("/api/admin/students/" + studentId + "/bills/generate?month=2024-02")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mealAmount").value(30.00))
                .andExpect(jsonPath("$.mealRecordDates.length()").value(3));
    }

    @Test
    void leaveDaysComeFromActualLeaveRecordsRegisteredByTheTeacher() throws Exception {
        // 验证核心设计变更：请假天数不再走机构后台的区间登记接口（已删除），改成
        // 读老师在看板上逐日登记的请假记录；账单上还要能看到具体哪天、什么原因。
        Long institutionId = institutionDao.insert("请假账单测试机构A");
        teacherDao.insert(new Teacher(null, institutionId, "13900016001",
                passwordEncoder.encode("admin-password"), Role.ADMIN, false, null));
        Long teacherId = teacherDao.insert(new Teacher(null, institutionId, "13900016002",
                passwordEncoder.encode("teacher-password"), Role.TEACHER, false, null));
        Long classRoomId = teachingUnitDao.insert(new TeachingUnit(null, institutionId, teacherId, "请假一班",
                BillingMode.MONTHLY, null, null, true, null));
        Long studentId = studentDao.insert(new Student(null, institutionId, classRoomId, "小刚", "一班",
                true, null, null));
        String adminToken = login("13900016001", "admin-password");
        String teacherToken = login("13900016002", "teacher-password");

        mockMvc.perform(put("/api/admin/classes/" + classRoomId + "/billing-rate")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tuitionRatePerMonth\":500.00,\"mealRatePerDay\":10.00}"))
                .andExpect(status().isOk());

        mockMvc.perform(patch("/api/students/" + studentId + "/leave")
                        .header("Authorization", "Bearer " + teacherToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"date\":\"2024-01-08\",\"reason\":\"发烧\"}"))
                .andExpect(status().isNoContent());
        mockMvc.perform(patch("/api/students/" + studentId + "/leave")
                        .header("Authorization", "Bearer " + teacherToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"date\":\"2024-01-09\",\"reason\":\"仍未退烧\"}"))
                .andExpect(status().isNoContent());

        mockMvc.perform(post("/api/admin/students/" + studentId + "/bills/generate?month=2024-01")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.leaveDays").value(2))
                .andExpect(jsonPath("$.leaveLines.length()").value(2))
                .andExpect(jsonPath("$.leaveLines[0].leaveDate").value("2024-01-08"))
                .andExpect(jsonPath("$.leaveLines[0].reason").value("发烧"))
                .andExpect(jsonPath("$.leaveLines[1].leaveDate").value("2024-01-09"))
                .andExpect(jsonPath("$.leaveLines[1].reason").value("仍未退烧"));
    }

    @Test
    void generatedBillStaysFixedAfterLeaveRecordsChangeUntilRegenerated() throws Exception {
        Long institutionId = institutionDao.insert("请假账单测试机构B");
        teacherDao.insert(new Teacher(null, institutionId, "13900016003",
                passwordEncoder.encode("admin-password"), Role.ADMIN, false, null));
        Long teacherId = teacherDao.insert(new Teacher(null, institutionId, "13900016004",
                passwordEncoder.encode("teacher-password"), Role.TEACHER, false, null));
        Long classRoomId = teachingUnitDao.insert(new TeachingUnit(null, institutionId, teacherId, "请假二班",
                BillingMode.MONTHLY, null, null, true, null));
        Long studentId = studentDao.insert(new Student(null, institutionId, classRoomId, "小丽", "二班",
                true, null, null));
        String adminToken = login("13900016003", "admin-password");
        String teacherToken = login("13900016004", "teacher-password");

        mockMvc.perform(put("/api/admin/classes/" + classRoomId + "/billing-rate")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tuitionRatePerMonth\":500.00,\"mealRatePerDay\":10.00}"))
                .andExpect(status().isOk());
        mockMvc.perform(patch("/api/students/" + studentId + "/leave")
                        .header("Authorization", "Bearer " + teacherToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"date\":\"2024-02-05\",\"reason\":\"事假\"}"))
                .andExpect(status().isNoContent());

        MvcResult generateResult = mockMvc.perform(
                        post("/api/admin/students/" + studentId + "/bills/generate?month=2024-02")
                                .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.leaveDays").value(1))
                .andReturn();
        Long billId = objectMapper.readTree(generateResult.getResponse().getContentAsString()).get("id").asLong();

        // 账单生成之后，老师又补登了一天请假——已生成的账单不应该跟着变。
        mockMvc.perform(patch("/api/students/" + studentId + "/leave")
                        .header("Authorization", "Bearer " + teacherToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"date\":\"2024-02-06\",\"reason\":\"继续请假\"}"))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/admin/bills/" + billId)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.leaveDays").value(1))
                .andExpect(jsonPath("$.leaveLines.length()").value(1));

        // 管理员手动重新生成，才会用上最新的请假记录。
        mockMvc.perform(post("/api/admin/students/" + studentId + "/bills/generate?month=2024-02")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.leaveDays").value(2))
                .andExpect(jsonPath("$.leaveLines.length()").value(2));
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
