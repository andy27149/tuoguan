package com.tuoguan.backend.roster.web;

import com.fasterxml.jackson.databind.ObjectMapper;
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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class TaskTemplateControllerTest extends IntegrationTestBase {

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
    void createAndListReturnsOwnInstitutionTemplates() throws Exception {
        Long institutionId = institutionDao.insert("任务库控制器测试机构A");
        teacherDao.insert(new Teacher(null, institutionId, "13800006001",
                passwordEncoder.encode("password"), Role.TEACHER, false, null));
        String token = login("13800006001", "password");

        mockMvc.perform(post("/api/task-templates")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"subject\":\"数学\",\"name\":\"口算练习\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.subject").value("数学"))
                .andExpect(jsonPath("$.name").value("口算练习"));

        mockMvc.perform(get("/api/task-templates").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].subject").value("数学"));
    }

    @Test
    void teachersFromDifferentInstitutionsDoNotShareTemplates() throws Exception {
        Long institutionAId = institutionDao.insert("任务库控制器测试机构B");
        teacherDao.insert(new Teacher(null, institutionAId, "13800006002",
                passwordEncoder.encode("password-a"), Role.TEACHER, false, null));
        Long institutionBId = institutionDao.insert("任务库控制器测试机构C");
        teacherDao.insert(new Teacher(null, institutionBId, "13800006003",
                passwordEncoder.encode("password-b"), Role.TEACHER, false, null));

        String tokenA = login("13800006002", "password-a");
        String tokenB = login("13800006003", "password-b");

        mockMvc.perform(post("/api/task-templates")
                        .header("Authorization", "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"subject\":\"语文\",\"name\":\"背诵古诗\"}"))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/task-templates").header("Authorization", "Bearer " + tokenB))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void deleteTemplateFromAnotherInstitutionReturnsNotFound() throws Exception {
        Long institutionAId = institutionDao.insert("任务库控制器测试机构D");
        teacherDao.insert(new Teacher(null, institutionAId, "13800006004",
                passwordEncoder.encode("password-a"), Role.TEACHER, false, null));
        Long institutionBId = institutionDao.insert("任务库控制器测试机构E");
        teacherDao.insert(new Teacher(null, institutionBId, "13800006005",
                passwordEncoder.encode("password-b"), Role.TEACHER, false, null));

        String tokenA = login("13800006004", "password-a");
        String tokenB = login("13800006005", "password-b");

        MvcResult createResult = mockMvc.perform(post("/api/task-templates")
                        .header("Authorization", "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"subject\":\"英语\",\"name\":\"单词听写\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        TaskTemplateResponse created = objectMapper.readValue(
                createResult.getResponse().getContentAsString(), TaskTemplateResponse.class);

        mockMvc.perform(delete("/api/task-templates/" + created.id())
                        .header("Authorization", "Bearer " + tokenB))
                .andExpect(status().isNotFound());

        mockMvc.perform(delete("/api/task-templates/" + created.id())
                        .header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isNoContent());
    }

    @Test
    void deletingAssignedTemplateArchivesItInsteadOfFailing() throws Exception {
        Long institutionId = institutionDao.insert("任务库控制器测试机构F");
        Long teacherId = teacherDao.insert(new Teacher(null, institutionId, "13800006006",
                passwordEncoder.encode("password"), Role.TEACHER, false, null));
        Long classRoomId = classRoomDao.insert(new ClassRoom(null, institutionId, teacherId, "托管班", null));
        studentDao.insert(new Student(null, institutionId, classRoomId, "小明", "三年级2班", true, null, null));
        String token = login("13800006006", "password");

        MvcResult createResult = mockMvc.perform(post("/api/task-templates")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"subject\":\"数学\",\"name\":\"口算练习\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        TaskTemplateResponse created = objectMapper.readValue(
                createResult.getResponse().getContentAsString(), TaskTemplateResponse.class);

        mockMvc.perform(post("/api/classes/" + classRoomId + "/daily-tasks/batch")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"taskTemplateIds\":[" + created.id() + "],\"date\":\"2026-08-06\"}"))
                .andExpect(status().isCreated());

        mockMvc.perform(delete("/api/task-templates/" + created.id())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/task-templates").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));

        mockMvc.perform(get("/api/classes/" + classRoomId + "/daily-tasks?date=2026-08-06")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].subject").value("数学"));
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
