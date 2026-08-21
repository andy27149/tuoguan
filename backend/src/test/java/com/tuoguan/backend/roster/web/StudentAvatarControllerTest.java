package com.tuoguan.backend.roster.web;

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
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MvcResult;

import com.fasterxml.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class StudentAvatarControllerTest extends IntegrationTestBase {

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

    private Long createStudent(String token, Long classRoomId, String name) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/classes/" + classRoomId + "/students")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"" + name + "\",\"schoolClassName\":\"三年级2班\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readValue(result.getResponse().getContentAsString(), StudentResponse.class).id();
    }

    @Test
    void uploadingAvatarReturnsPresignedUrl() throws Exception {
        Long institutionId = institutionDao.insert("头像测试机构A");
        Long teacherId = teacherDao.insert(new Teacher(null, institutionId, "13800009001",
                passwordEncoder.encode("password"), Role.TEACHER, false, null));
        Long classRoomId = classRoomDao.insert(new ClassRoom(null, institutionId, teacherId, "托管班", null));
        String token = login("13800009001", "password");
        Long studentId = createStudent(token, classRoomId, "小明");
        MockMultipartFile file = new MockMultipartFile("file", "avatar.png", "image/png", new byte[]{1, 2, 3, 4});

        MvcResult result = mockMvc.perform(multipart("/api/students/" + studentId + "/avatar")
                        .file(file)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
        StudentResponse updated = objectMapper.readValue(
                result.getResponse().getContentAsString(), StudentResponse.class);

        assertThat(updated.avatarUrl()).isNotBlank();
    }

    @Test
    void rejectsUnsupportedContentType() throws Exception {
        Long institutionId = institutionDao.insert("头像测试机构B");
        Long teacherId = teacherDao.insert(new Teacher(null, institutionId, "13800009002",
                passwordEncoder.encode("password"), Role.TEACHER, false, null));
        Long classRoomId = classRoomDao.insert(new ClassRoom(null, institutionId, teacherId, "托管班", null));
        String token = login("13800009002", "password");
        Long studentId = createStudent(token, classRoomId, "小红");
        MockMultipartFile file = new MockMultipartFile("file", "avatar.txt", "text/plain", new byte[]{1, 2, 3});

        mockMvc.perform(multipart("/api/students/" + studentId + "/avatar")
                        .file(file)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest());
    }

    @Test
    void teacherCannotUploadAvatarForAnotherTeachersStudent() throws Exception {
        Long institutionId = institutionDao.insert("头像测试机构C");
        Long teacherAId = teacherDao.insert(new Teacher(null, institutionId, "13800009003",
                passwordEncoder.encode("password-a"), Role.TEACHER, false, null));
        teacherDao.insert(new Teacher(null, institutionId, "13800009004",
                passwordEncoder.encode("password-b"), Role.TEACHER, false, null));
        Long classRoomAId = classRoomDao.insert(new ClassRoom(null, institutionId, teacherAId, "A班", null));
        String tokenA = login("13800009003", "password-a");
        String tokenB = login("13800009004", "password-b");
        Long studentId = createStudent(tokenA, classRoomAId, "小刚");
        MockMultipartFile file = new MockMultipartFile("file", "avatar.png", "image/png", new byte[]{1, 2, 3, 4});

        mockMvc.perform(multipart("/api/students/" + studentId + "/avatar")
                        .file(file)
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
