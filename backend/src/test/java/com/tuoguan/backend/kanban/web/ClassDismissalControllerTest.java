package com.tuoguan.backend.kanban.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tuoguan.backend.auth.dao.InstitutionDao;
import com.tuoguan.backend.auth.dao.TeacherDao;
import com.tuoguan.backend.auth.domain.Role;
import com.tuoguan.backend.auth.domain.Teacher;
import com.tuoguan.backend.auth.web.LoginResponse;
import com.tuoguan.backend.roster.dao.ClassRoomDao;
import com.tuoguan.backend.roster.domain.ClassRoom;
import com.tuoguan.backend.support.IntegrationTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MvcResult;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ClassDismissalControllerTest extends IntegrationTestBase {

    @Autowired
    private InstitutionDao institutionDao;

    @Autowired
    private TeacherDao teacherDao;

    @Autowired
    private ClassRoomDao classRoomDao;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void dismissAndUndoTogglesStatusForOnlyThatClass() throws Exception {
        Long institutionId = institutionDao.insert("放学控制器测试机构A");
        Long teacherId = teacherDao.insert(new Teacher(null, institutionId, "13900006001",
                passwordEncoder.encode("password"), Role.TEACHER, false, null));
        Long classRoomAId = classRoomDao.insert(new ClassRoom(null, institutionId, teacherId, "A班", null));
        Long classRoomBId = classRoomDao.insert(new ClassRoom(null, institutionId, teacherId, "B班", null));
        String token = login("13900006001", "password");

        mockMvc.perform(post("/api/classes/" + classRoomAId + "/dismissal")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"date\":\"2026-08-06\"}"))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/classes/" + classRoomAId + "/dismissal?date=2026-08-06")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dismissed").value(true));

        mockMvc.perform(get("/api/classes/" + classRoomBId + "/dismissal?date=2026-08-06")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dismissed").value(false));

        mockMvc.perform(delete("/api/classes/" + classRoomAId + "/dismissal?date=2026-08-06")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/classes/" + classRoomAId + "/dismissal?date=2026-08-06")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dismissed").value(false));
    }

    @Test
    void teacherCannotDismissAnotherTeachersClass() throws Exception {
        Long institutionId = institutionDao.insert("放学控制器测试机构B");
        Long teacherAId = teacherDao.insert(new Teacher(null, institutionId, "13900006002",
                passwordEncoder.encode("password-a"), Role.TEACHER, false, null));
        Long teacherBId = teacherDao.insert(new Teacher(null, institutionId, "13900006003",
                passwordEncoder.encode("password-b"), Role.TEACHER, false, null));
        Long classRoomAId = classRoomDao.insert(new ClassRoom(null, institutionId, teacherAId, "A班", null));
        String tokenB = login("13900006003", "password-b");

        mockMvc.perform(post("/api/classes/" + classRoomAId + "/dismissal")
                        .header("Authorization", "Bearer " + tokenB)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"date\":\"2026-08-06\"}"))
                .andExpect(status().isNotFound());

        mockMvc.perform(get("/api/classes/" + classRoomAId + "/dismissal?date=2026-08-06")
                        .header("Authorization", "Bearer " + tokenB))
                .andExpect(status().isNotFound());
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
