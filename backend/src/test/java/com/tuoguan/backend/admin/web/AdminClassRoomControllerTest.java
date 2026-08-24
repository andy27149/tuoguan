package com.tuoguan.backend.admin.web;

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

class AdminClassRoomControllerTest extends IntegrationTestBase {

    @Autowired
    private InstitutionDao institutionDao;

    @Autowired
    private TeacherDao teacherDao;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void adminCreatesClassForTeacherAndBothCanSeeIt() throws Exception {
        Long institutionId = institutionDao.insert("管理员建班测试机构A");
        teacherDao.insert(new Teacher(null, institutionId, "13600001001",
                passwordEncoder.encode("admin-password"), Role.ADMIN, false, null));
        Long teacherId = teacherDao.insert(new Teacher(null, institutionId, "13600001002",
                passwordEncoder.encode("teacher-password"), Role.TEACHER, false, null));
        String adminToken = login("13600001001", "admin-password");

        mockMvc.perform(post("/api/admin/classes")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"托管一班\",\"teacherId\":" + teacherId + "}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("托管一班"))
                .andExpect(jsonPath("$.teacherId").value(teacherId))
                .andExpect(jsonPath("$.teacherPhone").value("13600001002"));

        mockMvc.perform(get("/api/admin/classes")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].name").value("托管一班"));

        String teacherToken = login("13600001002", "teacher-password");
        mockMvc.perform(get("/api/classes")
                        .header("Authorization", "Bearer " + teacherToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].name").value("托管一班"));
    }

    @Test
    void rejectsDuplicateClassNameForSameTeacher() throws Exception {
        Long institutionId = institutionDao.insert("管理员建班测试机构B");
        teacherDao.insert(new Teacher(null, institutionId, "13600001003",
                passwordEncoder.encode("admin-password"), Role.ADMIN, false, null));
        Long teacherId = teacherDao.insert(new Teacher(null, institutionId, "13600001004",
                passwordEncoder.encode("teacher-password"), Role.TEACHER, false, null));
        String adminToken = login("13600001003", "admin-password");

        mockMvc.perform(post("/api/admin/classes")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"托管二班\",\"teacherId\":" + teacherId + "}"))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/admin/classes")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"托管二班\",\"teacherId\":" + teacherId + "}"))
                .andExpect(status().isConflict());
    }

    @Test
    void rejectsTeacherIdFromAnotherInstitutionOrNonexistent() throws Exception {
        Long institutionAId = institutionDao.insert("管理员建班测试机构C");
        Long institutionBId = institutionDao.insert("管理员建班测试机构D");
        teacherDao.insert(new Teacher(null, institutionAId, "13600001005",
                passwordEncoder.encode("admin-password"), Role.ADMIN, false, null));
        Long foreignTeacherId = teacherDao.insert(new Teacher(null, institutionBId, "13600001006",
                passwordEncoder.encode("teacher-password"), Role.TEACHER, false, null));
        String adminToken = login("13600001005", "admin-password");

        mockMvc.perform(post("/api/admin/classes")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"托管三班\",\"teacherId\":" + foreignTeacherId + "}"))
                .andExpect(status().isNotFound());

        mockMvc.perform(post("/api/admin/classes")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"托管三班\",\"teacherId\":999999}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void nonAdminTeacherIsForbiddenFromCreatingOrListingAdminClasses() throws Exception {
        Long institutionId = institutionDao.insert("管理员建班测试机构E");
        Long teacherId = teacherDao.insert(new Teacher(null, institutionId, "13600001007",
                passwordEncoder.encode("teacher-password"), Role.TEACHER, false, null));
        String token = login("13600001007", "teacher-password");

        mockMvc.perform(post("/api/admin/classes")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"托管四班\",\"teacherId\":" + teacherId + "}"))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/admin/classes")
                        .header("Authorization", "Bearer " + token))
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
