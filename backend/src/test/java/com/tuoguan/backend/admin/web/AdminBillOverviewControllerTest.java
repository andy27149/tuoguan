package com.tuoguan.backend.admin.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tuoguan.backend.auth.dao.InstitutionDao;
import com.tuoguan.backend.auth.dao.TeacherDao;
import com.tuoguan.backend.auth.domain.Role;
import com.tuoguan.backend.auth.domain.Teacher;
import com.tuoguan.backend.auth.web.LoginResponse;
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

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AdminBillOverviewControllerTest extends IntegrationTestBase {

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
    void getBillReturnsEnrichedDetailIncludingExtraFeeLines() throws Exception {
        Long institutionId = institutionDao.insert("账单详情测试机构A");
        teacherDao.insert(new Teacher(null, institutionId, "13900014001",
                passwordEncoder.encode("admin-password"), Role.ADMIN, false, null));
        Long teacherId = teacherDao.insert(new Teacher(null, institutionId, "13900014002",
                passwordEncoder.encode("teacher-password"), Role.TEACHER, false, null));
        Long classRoomId = teachingUnitDao.insert(new TeachingUnit(null, institutionId, teacherId, "详情一班",
                BillingMode.MONTHLY, null, null, true, null));
        Long studentId = studentDao.insert(new Student(null, institutionId, classRoomId, "学生甲", "一班", true, null, null));
        String adminToken = login("13900014001", "admin-password");

        mockMvc.perform(put("/api/admin/classes/" + classRoomId + "/billing-rate")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tuitionRatePerMonth\":50.00,\"mealRatePerDay\":10.00}"))
                .andExpect(status().isOk());

        String teacherToken = login("13900014002", "teacher-password");
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
        mockMvc.perform(patch("/api/admin/students/" + studentId)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"学生甲\",\"schoolClassName\":\"一班\",\"teachingUnitId\":" + classRoomId
                                + ",\"enrolled\":true,\"courseIds\":[" + courseId + "]}"))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/courses/" + courseId + "/consumption")
                        .header("Authorization", "Bearer " + teacherToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"studentId\":" + studentId + ",\"date\":\"2024-01-02\",\"confirm\":false}"))
                .andExpect(status().isCreated());

        MvcResult generateResult = mockMvc.perform(post("/api/admin/students/" + studentId + "/bills/generate?month=2024-01")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andReturn();
        Long billId = objectMapper.readTree(generateResult.getResponse().getContentAsString()).get("id").asLong();

        mockMvc.perform(get("/api/admin/bills/" + billId)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(billId))
                .andExpect(jsonPath("$.studentId").value(studentId))
                .andExpect(jsonPath("$.extraFeeLines.length()").value(1))
                .andExpect(jsonPath("$.extraFeeLines[0].name").value("数学课"));
    }

    @Test
    void listOverviewWithoutMonthShowsAllGeneratedMonthsAndStudentsWithNoBillYet() throws Exception {
        Long institutionId = institutionDao.insert("账单全部月份测试机构");
        teacherDao.insert(new Teacher(null, institutionId, "13900014010",
                passwordEncoder.encode("admin-password"), Role.ADMIN, false, null));
        Long teacherId = teacherDao.insert(new Teacher(null, institutionId, "13900014011",
                passwordEncoder.encode("teacher-password"), Role.TEACHER, false, null));
        Long classRoomId = teachingUnitDao.insert(new TeachingUnit(null, institutionId, teacherId, "全月一班",
                BillingMode.MONTHLY, null, null, true, null));
        Long billedStudentId = studentDao.insert(
                new Student(null, institutionId, classRoomId, "已出账学生", "一班", true, null, null));
        Long unbilledStudentId = studentDao.insert(
                new Student(null, institutionId, classRoomId, "未出账学生", "一班", true, null, null));
        String adminToken = login("13900014010", "admin-password");

        mockMvc.perform(put("/api/admin/classes/" + classRoomId + "/billing-rate")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tuitionRatePerMonth\":50.00,\"mealRatePerDay\":10.00}"))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/admin/students/" + billedStudentId + "/bills/generate?month=2024-01")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/admin/students/" + billedStudentId + "/bills/generate?month=2024-02")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/admin/bills").header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[?(@.studentId == " + billedStudentId + " && @.yearMonth == '2024-01')]").exists())
                .andExpect(jsonPath("$[?(@.studentId == " + billedStudentId + " && @.yearMonth == '2024-02')]").exists())
                .andExpect(jsonPath("$[?(@.studentId == " + unbilledStudentId + ")].billId").value(
                        org.hamcrest.Matchers.contains(org.hamcrest.Matchers.nullValue())));
    }

    @Test
    void generatedBillForPureOffCampusStudentShowsUpInOverviewWithAndWithoutMonthFilter() throws Exception {
        // 回归测试：此前 getBillOverview/getBillOverviewAllMonths 按 teaching_unit_id 遍历
        // 教学单元再反查账单，纯课外课学生的账单 teaching_unit_id 是 NULL，永远查不到——
        // 账单明明已经生成，总览列表却一直显示"未生成"。
        Long institutionId = institutionDao.insert("账单总览测试机构M");
        teacherDao.insert(new Teacher(null, institutionId, "13900014020",
                passwordEncoder.encode("admin-password"), Role.ADMIN, false, null));
        Long studentId = studentDao.insert(new Student(null, institutionId, null, "纯课外学生", null, true, null, null));
        String adminToken = login("13900014020", "admin-password");

        mockMvc.perform(post("/api/admin/students/" + studentId + "/bills/generate?month=2024-01")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/admin/bills?month=2024-01")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.studentId == " + studentId + ")].billId").value(
                        org.hamcrest.Matchers.contains(org.hamcrest.Matchers.notNullValue())));

        mockMvc.perform(get("/api/admin/bills")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.studentId == " + studentId + ")].billId").value(
                        org.hamcrest.Matchers.contains(org.hamcrest.Matchers.notNullValue())));
    }

    @Test
    void offCampusOnlyFilterShowsOnlyPureOffCampusStudentsWithAndWithoutMonthFilter() throws Exception {
        Long institutionId = institutionDao.insert("账单纯课外筛选测试机构");
        teacherDao.insert(new Teacher(null, institutionId, "13900014040",
                passwordEncoder.encode("admin-password"), Role.ADMIN, false, null));
        Long teacherId = teacherDao.insert(new Teacher(null, institutionId, "13900014041",
                passwordEncoder.encode("teacher-password"), Role.TEACHER, false, null));
        Long classRoomId = teachingUnitDao.insert(new TeachingUnit(null, institutionId, teacherId, "筛选一班",
                BillingMode.MONTHLY, null, null, true, null));
        Long custodyStudentId = studentDao.insert(
                new Student(null, institutionId, classRoomId, "托管学生", "一班", true, null, null));
        Long offCampusStudentId = studentDao.insert(
                new Student(null, institutionId, null, "纯课外学生", null, true, null, null));
        String adminToken = login("13900014040", "admin-password");

        mockMvc.perform(get("/api/admin/bills?offCampusOnly=true")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.studentId == " + offCampusStudentId + ")]").exists())
                .andExpect(jsonPath("$[?(@.studentId == " + custodyStudentId + ")]").doesNotExist());

        mockMvc.perform(get("/api/admin/bills?month=2024-01&offCampusOnly=true")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.studentId == " + offCampusStudentId + ")]").exists())
                .andExpect(jsonPath("$[?(@.studentId == " + custodyStudentId + ")]").doesNotExist());
    }

    @Test
    void setPaidTogglesBillAndRecordsAnAuditLogEntry() throws Exception {
        Long institutionId = institutionDao.insert("账单缴费状态测试机构");
        teacherDao.insert(new Teacher(null, institutionId, "13900014021",
                passwordEncoder.encode("admin-password"), Role.ADMIN, false, null));
        Long studentId = studentDao.insert(new Student(null, institutionId, null, "待缴费学生", null, true, null, null));
        String adminToken = login("13900014021", "admin-password");

        MvcResult generateResult = mockMvc.perform(post("/api/admin/students/" + studentId + "/bills/generate?month=2024-01")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andReturn();
        Long billId = objectMapper.readTree(generateResult.getResponse().getContentAsString()).get("id").asLong();

        mockMvc.perform(patch("/api/admin/bills/" + billId + "/paid")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"isPaid\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isPaid").value(true));

        Integer auditCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM audit_log WHERE action = 'BILL_SET_PAID' AND target_id = ?",
                Integer.class, billId);
        assertThat(auditCount).isEqualTo(1);
    }

    @Test
    void bulkGenerateCoversAllStudentsSkipsUnconfiguredClassesAndRecordsAnAuditLogEntry() throws Exception {
        Long institutionId = institutionDao.insert("账单批量生成测试机构");
        teacherDao.insert(new Teacher(null, institutionId, "13900014050",
                passwordEncoder.encode("admin-password"), Role.ADMIN, false, null));
        Long teacherId = teacherDao.insert(new Teacher(null, institutionId, "13900014051",
                passwordEncoder.encode("teacher-password"), Role.TEACHER, false, null));
        Long configuredClassId = teachingUnitDao.insert(new TeachingUnit(null, institutionId, teacherId, "已配置班",
                BillingMode.MONTHLY, null, null, true, null));
        Long unconfiguredClassId = teachingUnitDao.insert(new TeachingUnit(null, institutionId, teacherId, "未配置班",
                BillingMode.MONTHLY, null, null, true, null));
        Long configuredClassStudentId = studentDao.insert(
                new Student(null, institutionId, configuredClassId, "已配置班学生", "一班", true, null, null));
        Long unconfiguredClassStudentId = studentDao.insert(
                new Student(null, institutionId, unconfiguredClassId, "未配置班学生", "一班", true, null, null));
        Long offCampusStudentId = studentDao.insert(
                new Student(null, institutionId, null, "纯课外学生", null, true, null, null));
        Long unenrolledStudentId = studentDao.insert(
                new Student(null, institutionId, configuredClassId, "已退学学生", "一班", false, null, null));
        String adminToken = login("13900014050", "admin-password");

        mockMvc.perform(put("/api/admin/classes/" + configuredClassId + "/billing-rate")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tuitionRatePerMonth\":50.00,\"mealRatePerDay\":10.00}"))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/admin/bills/generate?month=2024-01")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.generatedCount").value(2))
                .andExpect(jsonPath("$.failures.length()").value(1))
                .andExpect(jsonPath("$.failures[0].studentId").value(unconfiguredClassStudentId))
                .andExpect(jsonPath("$.failures[0].studentName").value("未配置班学生"))
                .andExpect(jsonPath("$.failures[0].reason").value("班级未配置计费单价"));

        mockMvc.perform(get("/api/admin/bills?month=2024-01")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.studentId == " + configuredClassStudentId + ")].billId").value(
                        org.hamcrest.Matchers.contains(org.hamcrest.Matchers.notNullValue())))
                .andExpect(jsonPath("$[?(@.studentId == " + offCampusStudentId + ")].billId").value(
                        org.hamcrest.Matchers.contains(org.hamcrest.Matchers.notNullValue())))
                .andExpect(jsonPath("$[?(@.studentId == " + unconfiguredClassStudentId + ")].billId").value(
                        org.hamcrest.Matchers.contains(org.hamcrest.Matchers.nullValue())))
                .andExpect(jsonPath("$[?(@.studentId == " + unenrolledStudentId + ")]").doesNotExist());

        Integer auditCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM audit_log WHERE action = 'BILL_BULK_GENERATE' AND target_id = ?",
                Integer.class, institutionId);
        assertThat(auditCount).isEqualTo(1);
    }

    @Test
    void bulkGenerateOnlyCoversTheRequestingAdminsOwnInstitution() throws Exception {
        Long institutionAId = institutionDao.insert("账单批量生成隔离机构A");
        Long institutionBId = institutionDao.insert("账单批量生成隔离机构B");
        teacherDao.insert(new Teacher(null, institutionAId, "13900014060",
                passwordEncoder.encode("admin-password"), Role.ADMIN, false, null));
        Long studentAId = studentDao.insert(new Student(null, institutionAId, null, "机构A学生", null, true, null, null));
        studentDao.insert(new Student(null, institutionBId, null, "机构B学生", null, true, null, null));
        String adminAToken = login("13900014060", "admin-password");

        mockMvc.perform(post("/api/admin/bills/generate?month=2024-01")
                        .header("Authorization", "Bearer " + adminAToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.generatedCount").value(1))
                .andExpect(jsonPath("$.failures.length()").value(0));

        mockMvc.perform(get("/api/admin/bills?month=2024-01")
                        .header("Authorization", "Bearer " + adminAToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.studentId == " + studentAId + ")].billId").value(
                        org.hamcrest.Matchers.contains(org.hamcrest.Matchers.notNullValue())))
                .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    void getBillForNonExistentIdReturnsNotFound() throws Exception {
        Long institutionId = institutionDao.insert("账单详情测试机构B");
        teacherDao.insert(new Teacher(null, institutionId, "13900014003",
                passwordEncoder.encode("admin-password"), Role.ADMIN, false, null));
        String adminToken = login("13900014003", "admin-password");

        mockMvc.perform(get("/api/admin/bills/999999")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNotFound());
    }

    @Test
    void getBillForAnotherInstitutionReturnsNotFound() throws Exception {
        Long institutionAId = institutionDao.insert("账单详情测试机构C");
        Long institutionBId = institutionDao.insert("账单详情测试机构D");
        teacherDao.insert(new Teacher(null, institutionAId, "13900014004",
                passwordEncoder.encode("admin-password"), Role.ADMIN, false, null));
        Long teacherBId = teacherDao.insert(new Teacher(null, institutionBId, "13900014005",
                passwordEncoder.encode("teacher-password"), Role.TEACHER, false, null));
        Long classRoomBId = teachingUnitDao.insert(new TeachingUnit(null, institutionBId, teacherBId, "详情二班",
                BillingMode.MONTHLY, null, null, true, null));
        Long studentBId = studentDao.insert(new Student(null, institutionBId, classRoomBId, "学生乙", "一班", true, null, null));

        teacherDao.insert(new Teacher(null, institutionBId, "13900014006",
                passwordEncoder.encode("admin-password"), Role.ADMIN, false, null));
        String institutionBAdminToken = login("13900014006", "admin-password");
        mockMvc.perform(put("/api/admin/classes/" + classRoomBId + "/billing-rate")
                        .header("Authorization", "Bearer " + institutionBAdminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tuitionRatePerMonth\":50.00,\"mealRatePerDay\":10.00}"))
                .andExpect(status().isOk());
        MvcResult generateResult = mockMvc.perform(post("/api/admin/students/" + studentBId + "/bills/generate?month=2024-01")
                        .header("Authorization", "Bearer " + institutionBAdminToken))
                .andExpect(status().isOk())
                .andReturn();
        Long billId = objectMapper.readTree(generateResult.getResponse().getContentAsString()).get("id").asLong();

        String institutionAAdminToken = login("13900014004", "admin-password");
        mockMvc.perform(get("/api/admin/bills/" + billId)
                        .header("Authorization", "Bearer " + institutionAAdminToken))
                .andExpect(status().isNotFound());
    }

    @Test
    void overviewOrdersCustodyStudentsByTeacherThenPureOffCampusStudentsByFirstCourseName() throws Exception {
        Long institutionId = institutionDao.insert("账单总览排序测试机构");
        teacherDao.insert(new Teacher(null, institutionId, "13900014030",
                passwordEncoder.encode("admin-password"), Role.ADMIN, false, null));
        Long teacherAId = teacherDao.insert(new Teacher(null, institutionId, "13900014031", "A老师",
                passwordEncoder.encode("teacher-password-a"), Role.TEACHER, false, null));
        Long teacherBId = teacherDao.insert(new Teacher(null, institutionId, "13900014032", "B老师",
                passwordEncoder.encode("teacher-password-b"), Role.TEACHER, false, null));
        Long classRoomAId = teachingUnitDao.insert(new TeachingUnit(null, institutionId, teacherAId, "A班",
                BillingMode.MONTHLY, null, null, true, null));
        Long classRoomBId = teachingUnitDao.insert(new TeachingUnit(null, institutionId, teacherBId, "B班",
                BillingMode.MONTHLY, null, null, true, null));
        Long mathCourseId = teachingUnitDao.insert(new TeachingUnit(null, institutionId, teacherAId, "数学课",
                BillingMode.LESSON_COUNT, 60, null, true, null));
        Long chineseCourseId = teachingUnitDao.insert(new TeachingUnit(null, institutionId, teacherAId, "语文课",
                BillingMode.LESSON_COUNT, 60, null, true, null));
        String adminToken = login("13900014030", "admin-password");

        studentDao.insert(new Student(null, institutionId, classRoomBId, "托管乙", "一班", true, null, null));
        studentDao.insert(new Student(null, institutionId, classRoomAId, "托管甲", "一班", true, null, null));
        mockMvc.perform(post("/api/admin/students")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"课外乙\",\"courseIds\":[" + chineseCourseId + "]}"))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/admin/students")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"课外甲\",\"courseIds\":[" + mathCourseId + "]}"))
                .andExpect(status().isCreated());

        MvcResult result = mockMvc.perform(get("/api/admin/bills")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andReturn();
        // 显式指定 UTF-8——默认的 getContentAsString() 按 ISO-8859-1 解码会把中文拆成乱码。
        List<String> order = new java.util.ArrayList<>();
        objectMapper.readTree(result.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8))
                .forEach(node -> order.add(node.get("studentName").asText()));

        assertThat(order.indexOf("托管甲")).isLessThan(order.indexOf("托管乙"));
        assertThat(order.indexOf("托管乙")).isLessThan(order.indexOf("课外甲"));
        assertThat(order.indexOf("课外甲")).isLessThan(order.indexOf("课外乙"));
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
