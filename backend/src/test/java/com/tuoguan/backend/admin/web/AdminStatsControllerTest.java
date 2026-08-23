package com.tuoguan.backend.admin.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tuoguan.backend.auth.dao.InstitutionDao;
import com.tuoguan.backend.auth.dao.TeacherDao;
import com.tuoguan.backend.auth.domain.Role;
import com.tuoguan.backend.auth.domain.Teacher;
import com.tuoguan.backend.auth.web.LoginResponse;
import com.tuoguan.backend.kanban.dao.DailyTaskDao;
import com.tuoguan.backend.kanban.domain.DailyTask;
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

import java.time.LocalDate;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AdminStatsControllerTest extends IntegrationTestBase {

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
    private PasswordEncoder passwordEncoder;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void dashboardReportsPerClassCompletionCounts() throws Exception {
        LocalDate date = LocalDate.of(2026, 8, 6);
        Long institutionId = institutionDao.insert("管理员统计测试机构A");
        teacherDao.insert(new Teacher(null, institutionId, "13600002001",
                passwordEncoder.encode("admin-password"), Role.ADMIN, false, null));
        Long teacherId = teacherDao.insert(new Teacher(null, institutionId, "13600002002",
                passwordEncoder.encode("teacher-password"), Role.TEACHER, false, null));

        // Class 1: two enrolled students; one fully done, one partially done.
        Long classRoom1Id = classRoomDao.insert(new ClassRoom(null, institutionId, teacherId, "一班", null));
        Long student1AId = studentDao.insert(new Student(null, institutionId, classRoom1Id, "小明", "三年级2班",
                true, null, null));
        Long student1BId = studentDao.insert(new Student(null, institutionId, classRoom1Id, "小红", "三年级2班",
                true, null, null));
        // Unenrolled student should not count toward studentCount.
        Long droppedId = studentDao.insert(new Student(null, institutionId, classRoom1Id, "小刚", "四年级1班",
                true, null, null));
        studentDao.update(new Student(droppedId, institutionId, classRoom1Id, "小刚", "四年级1班", false, null, null));

        insertTask(institutionId, classRoom1Id, student1AId, date, "数学", true);
        insertTask(institutionId, classRoom1Id, student1AId, date, "语文", true);
        insertTask(institutionId, classRoom1Id, student1BId, date, "数学", true);
        insertTask(institutionId, classRoom1Id, student1BId, date, "语文", false);
        insertTask(institutionId, classRoom1Id, droppedId, date, "数学", true);

        // Class 2: one enrolled student with no tasks that day.
        Long classRoom2Id = classRoomDao.insert(new ClassRoom(null, institutionId, teacherId, "二班", null));
        studentDao.insert(new Student(null, institutionId, classRoom2Id, "小丽", "三年级1班", true, null, null));

        String token = login("13600002001", "admin-password");

        MvcResult result = mockMvc.perform(get("/api/admin/dashboard?date=2026-08-06")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.date").value("2026-08-06"))
                .andExpect(jsonPath("$.classes.length()").value(2))
                .andReturn();

        mockMvc.perform(get("/api/admin/dashboard?date=2026-08-06")
                        .header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.classes[0].className").value("一班"))
                .andExpect(jsonPath("$.classes[0].studentCount").value(2))
                .andExpect(jsonPath("$.classes[0].completedStudentCount").value(1))
                .andExpect(jsonPath("$.classes[1].className").value("二班"))
                .andExpect(jsonPath("$.classes[1].studentCount").value(1))
                .andExpect(jsonPath("$.classes[1].completedStudentCount").value(0));
    }

    @Test
    void nonAdminTeacherIsForbiddenFromViewingDashboard() throws Exception {
        Long institutionId = institutionDao.insert("管理员统计测试机构B");
        teacherDao.insert(new Teacher(null, institutionId, "13600002003",
                passwordEncoder.encode("teacher-password"), Role.TEACHER, false, null));
        String token = login("13600002003", "teacher-password");

        mockMvc.perform(get("/api/admin/dashboard")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    private void insertTask(Long institutionId, Long classRoomId, Long studentId, LocalDate date, String subject,
                             boolean completed) {
        Long id = dailyTaskDao.insert(new DailyTask(null, institutionId, classRoomId, studentId, date,
                null, subject, subject + "练习", true, false, null));
        if (completed) {
            dailyTaskDao.updateCompleted(id, true);
        }
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
