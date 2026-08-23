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

class AdminTeacherControllerTest extends IntegrationTestBase {

    @Autowired
    private InstitutionDao institutionDao;

    @Autowired
    private TeacherDao teacherDao;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void adminCreatesTeacherAccountWithoutExposingPassword() throws Exception {
        Long institutionId = institutionDao.insert("管理员教师测试机构A");
        teacherDao.insert(new Teacher(null, institutionId, "13700001001",
                passwordEncoder.encode("admin-password"), Role.ADMIN, false, null));
        String token = login("13700001001", "admin-password");

        mockMvc.perform(post("/api/admin/teachers")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"phone\":\"13700001002\",\"initialPassword\":\"initial123\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.phone").value("13700001002"))
                .andExpect(jsonPath("$.role").value("TEACHER"))
                .andExpect(jsonPath("$.mustChangePassword").value(true))
                .andExpect(jsonPath("$.passwordHash").doesNotExist())
                .andExpect(jsonPath("$.initialPassword").doesNotExist());
    }

    @Test
    void rejectsDuplicatePhoneWhenCreatingTeacher() throws Exception {
        Long institutionId = institutionDao.insert("管理员教师测试机构B");
        teacherDao.insert(new Teacher(null, institutionId, "13700001003",
                passwordEncoder.encode("admin-password"), Role.ADMIN, false, null));
        teacherDao.insert(new Teacher(null, institutionId, "13700001004",
                passwordEncoder.encode("existing"), Role.TEACHER, true, null));
        String token = login("13700001003", "admin-password");

        mockMvc.perform(post("/api/admin/teachers")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"phone\":\"13700001004\",\"initialPassword\":\"initial123\"}"))
                .andExpect(status().isConflict());
    }

    @Test
    void nonAdminTeacherIsForbiddenFromCreatingOrListingTeachers() throws Exception {
        Long institutionId = institutionDao.insert("管理员教师测试机构C");
        teacherDao.insert(new Teacher(null, institutionId, "13700001005",
                passwordEncoder.encode("teacher-password"), Role.TEACHER, false, null));
        String token = login("13700001005", "teacher-password");

        mockMvc.perform(post("/api/admin/teachers")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"phone\":\"13700001006\",\"initialPassword\":\"initial123\"}"))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/admin/teachers")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminOnlySeesTeachersFromOwnInstitution() throws Exception {
        Long institutionAId = institutionDao.insert("管理员教师测试机构D");
        Long institutionBId = institutionDao.insert("管理员教师测试机构E");
        teacherDao.insert(new Teacher(null, institutionAId, "13700001007",
                passwordEncoder.encode("admin-password"), Role.ADMIN, false, null));
        teacherDao.insert(new Teacher(null, institutionAId, "13700001008",
                passwordEncoder.encode("teacher-password"), Role.TEACHER, false, null));
        teacherDao.insert(new Teacher(null, institutionBId, "13700001009",
                passwordEncoder.encode("teacher-password"), Role.TEACHER, false, null));
        String token = login("13700001007", "admin-password");

        mockMvc.perform(get("/api/admin/teachers")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));
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
