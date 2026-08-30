package com.tuoguan.backend.admin.web;

import com.tuoguan.backend.auth.dao.InstitutionDao;
import com.tuoguan.backend.auth.dao.TeacherDao;
import com.tuoguan.backend.auth.domain.Role;
import com.tuoguan.backend.auth.domain.Teacher;
import com.tuoguan.backend.auth.web.LoginResponse;
import com.tuoguan.backend.kanban.dao.ClassDismissalDao;
import com.tuoguan.backend.kanban.dao.DailyTaskDao;
import com.tuoguan.backend.kanban.dao.StudentArrivalCheckinDao;
import com.tuoguan.backend.kanban.dao.StudentDailyNoteDao;
import com.tuoguan.backend.kanban.domain.ClassDismissal;
import com.tuoguan.backend.kanban.domain.DailyTask;
import com.tuoguan.backend.roster.dao.ClassRoomDao;
import com.tuoguan.backend.roster.dao.StudentDao;
import com.tuoguan.backend.roster.domain.ClassRoom;
import com.tuoguan.backend.roster.domain.Student;
import com.tuoguan.backend.support.IntegrationTestBase;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MvcResult;

import java.time.LocalDate;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AdminKanbanControllerTest extends IntegrationTestBase {

    @Autowired
    private InstitutionDao institutionDao;

    @Autowired
    private TeacherDao teacherDao;

    @Autowired
    private ClassRoomDao classRoomDao;

    @Autowired
    private StudentDao studentDao;

    @Autowired
    private DailyTaskDao dailyTaskDao;

    @Autowired
    private ClassDismissalDao classDismissalDao;

    @Autowired
    private StudentDailyNoteDao studentDailyNoteDao;

    @Autowired
    private StudentArrivalCheckinDao studentArrivalCheckinDao;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void adminSeesFullKanbanDataForClassInOwnInstitution() throws Exception {
        Long institutionId = institutionDao.insert("管理员看板数据测试机构A");
        teacherDao.insert(new Teacher(null, institutionId, "13700002001",
                passwordEncoder.encode("admin-password"), Role.ADMIN, false, null));
        Long teacherId = teacherDao.insert(new Teacher(null, institutionId, "13700002002",
                passwordEncoder.encode("teacher-password"), Role.TEACHER, false, null));
        Long classRoomId = classRoomDao.insert(new ClassRoom(null, institutionId, teacherId, "托管一班", null));
        Long studentId = studentDao.insert(new Student(null, institutionId, classRoomId, "小明", "三年级2班",
                true, null, null));
        LocalDate date = LocalDate.of(2026, 8, 24);
        dailyTaskDao.insert(new DailyTask(null, institutionId, classRoomId, studentId, date,
                null, "数学", "口算练习", true, false, null));
        classDismissalDao.insert(new ClassDismissal(null, institutionId, classRoomId, date, null));
        studentDailyNoteDao.upsertRating(institutionId, classRoomId, studentId, date, 5);
        studentArrivalCheckinDao.upsert(institutionId, classRoomId, studentId, date, "08:30");
        String adminToken = login("13700002001", "admin-password");

        mockMvc.perform(get("/api/admin/classes/" + classRoomId + "/students")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].name").value("小明"));

        mockMvc.perform(get("/api/admin/classes/" + classRoomId + "/daily-tasks?date=" + date)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].name").value("口算练习"));

        mockMvc.perform(get("/api/admin/classes/" + classRoomId + "/dismissal?date=" + date)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dismissed").value(true));

        mockMvc.perform(get("/api/admin/classes/" + classRoomId + "/student-notes?date=" + date)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].rating").value(5));

        mockMvc.perform(get("/api/admin/classes/" + classRoomId + "/arrivals?date=" + date)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].arrivedAt").value("08:30"));

        mockMvc.perform(get("/api/admin/students/" + studentId + "/monthly-stats?month=" + date.toString().substring(0, 7))
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.averageRating").value(5.0));

        mockMvc.perform(get("/api/admin/students/" + studentId + "/share-link")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty());
    }

    @Test
    void adminCannotSeeClassFromAnotherInstitution() throws Exception {
        Long institutionAId = institutionDao.insert("管理员看板数据测试机构B");
        Long institutionBId = institutionDao.insert("管理员看板数据测试机构C");
        teacherDao.insert(new Teacher(null, institutionAId, "13700002003",
                passwordEncoder.encode("admin-password"), Role.ADMIN, false, null));
        Long teacherBId = teacherDao.insert(new Teacher(null, institutionBId, "13700002004",
                passwordEncoder.encode("teacher-password"), Role.TEACHER, false, null));
        Long classRoomId = classRoomDao.insert(new ClassRoom(null, institutionBId, teacherBId, "托管二班", null));
        Long studentId = studentDao.insert(new Student(null, institutionBId, classRoomId, "小红", "三年级3班",
                true, null, null));
        String adminToken = login("13700002003", "admin-password");

        mockMvc.perform(get("/api/admin/classes/" + classRoomId + "/students")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNotFound());

        mockMvc.perform(get("/api/admin/students/" + studentId + "/monthly-stats")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNotFound());

        mockMvc.perform(get("/api/admin/students/" + studentId + "/share-link")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNotFound());
    }

    @Test
    void nonAdminTeacherIsForbiddenFromAdminKanban() throws Exception {
        Long institutionId = institutionDao.insert("管理员看板数据测试机构D");
        Long teacherId = teacherDao.insert(new Teacher(null, institutionId, "13700002005",
                passwordEncoder.encode("teacher-password"), Role.TEACHER, false, null));
        Long classRoomId = classRoomDao.insert(new ClassRoom(null, institutionId, teacherId, "托管三班", null));
        String token = login("13700002005", "teacher-password");

        mockMvc.perform(get("/api/admin/classes/" + classRoomId + "/students")
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
