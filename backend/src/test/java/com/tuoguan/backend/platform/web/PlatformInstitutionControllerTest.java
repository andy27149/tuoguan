package com.tuoguan.backend.platform.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tuoguan.backend.auth.dao.InstitutionDao;
import com.tuoguan.backend.auth.dao.TeacherDao;
import com.tuoguan.backend.auth.domain.Role;
import com.tuoguan.backend.auth.domain.Teacher;
import com.tuoguan.backend.auth.web.LoginResponse;
import com.tuoguan.backend.support.IntegrationTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MvcResult;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class PlatformInstitutionControllerTest extends IntegrationTestBase {

    @Autowired
    private InstitutionDao institutionDao;

    @Autowired
    private TeacherDao teacherDao;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private com.tuoguan.backend.unit.dao.TeachingUnitDao teachingUnitDao;

    @Autowired
    private com.tuoguan.backend.roster.dao.StudentDao studentDao;

    @Test
    void platformAdminCreatesInstitutionAndNewAdminCanLogIn() throws Exception {
        teacherDao.insert(new Teacher(null, null, "13600003001",
                passwordEncoder.encode("platform-password"), Role.PLATFORM_ADMIN, false, null));
        String platformToken = login("13600003001", "platform-password");

        MvcResult createResult = mockMvc.perform(post("/api/platform/institutions")
                        .header("Authorization", "Bearer " + platformToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"institutionName\":\"新开通机构A\",\"adminPhone\":\"13600003002\","
                                + "\"adminInitialPassword\":\"initial123\",\"contactPhone\":\"13900000001\","
                                + "\"trialValidUntil\":\"2026-12-01\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.institutionName").value("新开通机构A"))
                .andExpect(jsonPath("$.adminPhone").value("13600003002"))
                .andReturn();
        PlatformInstitutionResponse created = objectMapper.readValue(
                createResult.getResponse().getContentAsString(), PlatformInstitutionResponse.class);

        String newAdminToken = login("13600003002", "initial123");
        mockMvc.perform(post("/api/auth/change-password")
                        .header("Authorization", "Bearer " + newAdminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"oldPassword\":\"initial123\",\"newPassword\":\"new-password-1\"}"))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/admin/teachers")
                        .header("Authorization", "Bearer " + newAdminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));

        mockMvc.perform(get("/api/platform/institutions")
                        .header("Authorization", "Bearer " + platformToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == " + created.institutionId() + ")].teacherCount").value(1))
                .andExpect(jsonPath("$[?(@.id == " + created.institutionId() + ")].validUntil")
                        .value(org.hamcrest.Matchers.contains("2026-12-01")))
                .andExpect(jsonPath("$[?(@.id == " + created.institutionId() + ")].currentPlan")
                        .value(org.hamcrest.Matchers.contains("TRIAL")))
                .andExpect(jsonPath("$[?(@.id == " + created.institutionId() + ")].status")
                        .value(org.hamcrest.Matchers.contains("ACTIVE")));
    }

    @Test
    void listIncludesStudentAndClassCounts() throws Exception {
        teacherDao.insert(new Teacher(null, null, "13600003010",
                passwordEncoder.encode("platform-password"), Role.PLATFORM_ADMIN, false, null));
        String platformToken = login("13600003010", "platform-password");
        Long institutionId = institutionDao.insert("规模统计测试机构");
        Long teacherId = teacherDao.insert(new Teacher(null, institutionId, "13600003011",
                passwordEncoder.encode("teacher-password"), Role.TEACHER, false, null));
        Long classRoomId = teachingUnitDao.insert(new com.tuoguan.backend.unit.domain.TeachingUnit(
                null, institutionId, teacherId, "规模一班",
                com.tuoguan.backend.unit.domain.BillingMode.MONTHLY, null, null, true, null));
        studentDao.insert(new com.tuoguan.backend.roster.domain.Student(
                null, institutionId, classRoomId, "在读学生", "一班", true, null, null));
        studentDao.insert(new com.tuoguan.backend.roster.domain.Student(
                null, institutionId, classRoomId, "已退学学生", "一班", false, null, null));

        mockMvc.perform(get("/api/platform/institutions")
                        .header("Authorization", "Bearer " + platformToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == " + institutionId + ")].studentCount")
                        .value(org.hamcrest.Matchers.contains(1)))
                .andExpect(jsonPath("$[?(@.id == " + institutionId + ")].classCount")
                        .value(org.hamcrest.Matchers.contains(1)));
    }

    @Test
    void statusIsNoneWhenValidUntilNotSet() throws Exception {
        teacherDao.insert(new Teacher(null, null, "13600003012",
                passwordEncoder.encode("platform-password"), Role.PLATFORM_ADMIN, false, null));
        String platformToken = login("13600003012", "platform-password");
        Long institutionId = institutionDao.insert("未设置到期日测试机构");

        mockMvc.perform(get("/api/platform/institutions")
                        .header("Authorization", "Bearer " + platformToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == " + institutionId + ")].status")
                        .value(org.hamcrest.Matchers.contains("NONE")))
                .andExpect(jsonPath("$[?(@.id == " + institutionId + ")].currentPlan")
                        .value(org.hamcrest.Matchers.contains(org.hamcrest.Matchers.nullValue())));
    }

    @Test
    void statusIsExpiredWhenValidUntilIsYesterday() throws Exception {
        teacherDao.insert(new Teacher(null, null, "13600003013",
                passwordEncoder.encode("platform-password"), Role.PLATFORM_ADMIN, false, null));
        String platformToken = login("13600003013", "platform-password");
        Long institutionId = institutionDao.insert("已过期测试机构");
        institutionDao.updateValidUntil(institutionId, java.time.LocalDate.now().minusDays(1));

        mockMvc.perform(get("/api/platform/institutions")
                        .header("Authorization", "Bearer " + platformToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == " + institutionId + ")].status")
                        .value(org.hamcrest.Matchers.contains("EXPIRED")));
    }

    @Test
    void statusIsExpiringSoonAtExactlySevenDaysLeftButActiveAtEight() throws Exception {
        teacherDao.insert(new Teacher(null, null, "13600003014",
                passwordEncoder.encode("platform-password"), Role.PLATFORM_ADMIN, false, null));
        String platformToken = login("13600003014", "platform-password");
        Long sevenDaysId = institutionDao.insert("七天后到期测试机构");
        institutionDao.updateValidUntil(sevenDaysId, java.time.LocalDate.now().plusDays(7));
        Long eightDaysId = institutionDao.insert("八天后到期测试机构");
        institutionDao.updateValidUntil(eightDaysId, java.time.LocalDate.now().plusDays(8));

        mockMvc.perform(get("/api/platform/institutions")
                        .header("Authorization", "Bearer " + platformToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == " + sevenDaysId + ")].status")
                        .value(org.hamcrest.Matchers.contains("EXPIRING_SOON")))
                .andExpect(jsonPath("$[?(@.id == " + eightDaysId + ")].status")
                        .value(org.hamcrest.Matchers.contains("ACTIVE")));
    }

    @Test
    void recordRenewalUpdatesValidUntilAndIsReflectedInList() throws Exception {
        teacherDao.insert(new Teacher(null, null, "13600003020",
                passwordEncoder.encode("platform-password"), Role.PLATFORM_ADMIN, false, null));
        String platformToken = login("13600003020", "platform-password");
        Long institutionId = institutionDao.insert("续费接口测试机构A");
        institutionDao.updateValidUntil(institutionId, java.time.LocalDate.of(2026, 11, 1));

        mockMvc.perform(post("/api/platform/institutions/" + institutionId + "/renewals")
                        .header("Authorization", "Bearer " + platformToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"plan\":\"MONTHLY\",\"amount\":299.00,\"validUntil\":\"2026-12-01\","
                                + "\"note\":\"首次续费\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.validUntil").value("2026-12-01"))
                .andExpect(jsonPath("$.currentPlan").value("MONTHLY"))
                .andExpect(jsonPath("$.status").value("ACTIVE"));

        mockMvc.perform(get("/api/platform/institutions")
                        .header("Authorization", "Bearer " + platformToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == " + institutionId + ")].validUntil")
                        .value(org.hamcrest.Matchers.contains("2026-12-01")));
    }

    @Test
    void recordRenewalAllowsNullAmountForAnyPlan() throws Exception {
        teacherDao.insert(new Teacher(null, null, "13600003021",
                passwordEncoder.encode("platform-password"), Role.PLATFORM_ADMIN, false, null));
        String platformToken = login("13600003021", "platform-password");
        Long institutionId = institutionDao.insert("续费接口测试机构B");

        mockMvc.perform(post("/api/platform/institutions/" + institutionId + "/renewals")
                        .header("Authorization", "Bearer " + platformToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"plan\":\"YEARLY\",\"validUntil\":\"2027-10-01\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.validUntil").value("2027-10-01"));
    }

    @Test
    void renewalHistoryReturnsRecordsNewestFirstWithRecorderName() throws Exception {
        teacherDao.insert(new Teacher(null, null, "13600003022", "平台",
                passwordEncoder.encode("platform-password"), Role.PLATFORM_ADMIN, false, null));
        String platformToken = login("13600003022", "platform-password");
        Long institutionId = institutionDao.insert("续费历史测试机构");
        mockMvc.perform(post("/api/platform/institutions/" + institutionId + "/renewals")
                        .header("Authorization", "Bearer " + platformToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"plan\":\"TRIAL\",\"validUntil\":\"2026-11-01\"}"))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/platform/institutions/" + institutionId + "/renewals")
                        .header("Authorization", "Bearer " + platformToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"plan\":\"HALF_YEAR\",\"amount\":1499.00,\"validUntil\":\"2027-05-01\","
                                + "\"note\":\"老客户优惠\"}"))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/platform/institutions/" + institutionId + "/renewals")
                        .header("Authorization", "Bearer " + platformToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].plan").value("HALF_YEAR"))
                .andExpect(jsonPath("$[0].note").value("老客户优惠"))
                .andExpect(jsonPath("$[0].recordedByTeacherName").value("平台"))
                .andExpect(jsonPath("$[1].plan").value("TRIAL"));
    }

    @Test
    void nonPlatformAdminCannotRecordOrViewRenewals() throws Exception {
        Long institutionId = institutionDao.insert("续费权限测试机构");
        teacherDao.insert(new Teacher(null, institutionId, "13600003023",
                passwordEncoder.encode("admin-password"), Role.ADMIN, false, null));
        String adminToken = login("13600003023", "admin-password");

        mockMvc.perform(post("/api/platform/institutions/" + institutionId + "/renewals")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"plan\":\"MONTHLY\",\"validUntil\":\"2026-12-01\"}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/platform/institutions/" + institutionId + "/renewals")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void updateContactPersistsAndIsReflectedInList() throws Exception {
        teacherDao.insert(new Teacher(null, null, "13600003030",
                passwordEncoder.encode("platform-password"), Role.PLATFORM_ADMIN, false, null));
        String platformToken = login("13600003030", "platform-password");
        Long institutionId = institutionDao.insert("联系信息测试机构");

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .patch("/api/platform/institutions/" + institutionId + "/contact")
                        .header("Authorization", "Bearer " + platformToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"contactName\":\"王校长\",\"contactPhone\":\"13900009999\","
                                + "\"address\":\"示例大道2号\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.contactName").value("王校长"))
                .andExpect(jsonPath("$.contactPhone").value("13900009999"))
                .andExpect(jsonPath("$.address").value("示例大道2号"));

        mockMvc.perform(get("/api/platform/institutions")
                        .header("Authorization", "Bearer " + platformToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == " + institutionId + ")].contactName")
                        .value(org.hamcrest.Matchers.contains("王校长")));
    }

    @Test
    void nonPlatformAdminCannotUpdateContact() throws Exception {
        Long institutionId = institutionDao.insert("联系信息权限测试机构");
        teacherDao.insert(new Teacher(null, institutionId, "13600003031",
                passwordEncoder.encode("admin-password"), Role.ADMIN, false, null));
        String adminToken = login("13600003031", "admin-password");

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .patch("/api/platform/institutions/" + institutionId + "/contact")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"contactName\":\"不该成功\",\"contactPhone\":\"13900000000\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void rejectsMissingContactPhone() throws Exception {
        teacherDao.insert(new Teacher(null, null, "13600003007",
                passwordEncoder.encode("platform-password"), Role.PLATFORM_ADMIN, false, null));
        String platformToken = login("13600003007", "platform-password");

        mockMvc.perform(post("/api/platform/institutions")
                        .header("Authorization", "Bearer " + platformToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"institutionName\":\"缺联系电话测试机构\",\"adminPhone\":\"13600003008\","
                                + "\"adminInitialPassword\":\"initial123\",\"trialValidUntil\":\"2026-12-01\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void rejectsDuplicateAdminPhone() throws Exception {
        teacherDao.insert(new Teacher(null, null, "13600003003",
                passwordEncoder.encode("platform-password"), Role.PLATFORM_ADMIN, false, null));
        String platformToken = login("13600003003", "platform-password");

        mockMvc.perform(post("/api/platform/institutions")
                        .header("Authorization", "Bearer " + platformToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"institutionName\":\"新开通机构B\",\"adminPhone\":\"13600003004\","
                                + "\"adminInitialPassword\":\"initial123\",\"contactPhone\":\"13900000002\","
                                + "\"trialValidUntil\":\"2026-12-01\"}"))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/platform/institutions")
                        .header("Authorization", "Bearer " + platformToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"institutionName\":\"新开通机构C\",\"adminPhone\":\"13600003004\","
                                + "\"adminInitialPassword\":\"initial123\",\"contactPhone\":\"13900000003\","
                                + "\"trialValidUntil\":\"2026-12-01\"}"))
                .andExpect(status().isConflict());
    }

    @Test
    void nonPlatformAdminIsForbidden() throws Exception {
        Long institutionId = institutionDao.insert("平台管理测试机构");
        teacherDao.insert(new Teacher(null, institutionId, "13600003005",
                passwordEncoder.encode("admin-password"), Role.ADMIN, false, null));
        String adminToken = login("13600003005", "admin-password");

        mockMvc.perform(post("/api/platform/institutions")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"institutionName\":\"新开通机构D\",\"adminPhone\":\"13600003006\","
                                + "\"adminInitialPassword\":\"initial123\",\"contactPhone\":\"13900000004\","
                                + "\"trialValidUntil\":\"2026-12-01\"}"))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/platform/institutions")
                        .header("Authorization", "Bearer " + adminToken))
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
