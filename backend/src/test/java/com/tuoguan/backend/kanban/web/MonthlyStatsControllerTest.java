package com.tuoguan.backend.kanban.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tuoguan.backend.auth.dao.InstitutionDao;
import com.tuoguan.backend.auth.dao.TeacherDao;
import com.tuoguan.backend.auth.domain.Role;
import com.tuoguan.backend.auth.domain.Teacher;
import com.tuoguan.backend.auth.web.LoginResponse;
import com.tuoguan.backend.kanban.dao.DailyTaskDao;
import com.tuoguan.backend.kanban.dao.StudentDailyNoteDao;
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
import java.time.YearMonth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class MonthlyStatsControllerTest extends IntegrationTestBase {

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
    private StudentDailyNoteDao studentDailyNoteDao;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void computesCompletedIncompleteDaysRatesAndAverageRating() throws Exception {
        Long institutionId = institutionDao.insert("月度统计控制器测试机构A");
        Long teacherId = teacherDao.insert(new Teacher(null, institutionId, "13900008001",
                passwordEncoder.encode("password"), Role.TEACHER, false, null));
        Long classRoomId = classRoomDao.insert(new ClassRoom(null, institutionId, teacherId, "托管班", null));
        Long studentId = studentDao.insert(new Student(null, institutionId, classRoomId, "小明", "三年级2班",
                true, null, null));

        LocalDate today = LocalDate.now();
        YearMonth currentMonth = YearMonth.from(today);
        LocalDate day1 = currentMonth.atDay(1);
        LocalDate day2 = today.isAfter(day1) ? today : currentMonth.atDay(Math.min(currentMonth.lengthOfMonth(), 2));

        // day1: fully completed (2/2)
        dailyTaskDao.insert(new DailyTask(null, institutionId, classRoomId, studentId, day1, null,
                "数学", "口算练习", false, true, null));
        dailyTaskDao.insert(new DailyTask(null, institutionId, classRoomId, studentId, day1, null,
                "语文", "背诵古诗", false, true, null));

        // day2: partially completed (1/2)
        dailyTaskDao.insert(new DailyTask(null, institutionId, classRoomId, studentId, day2, null,
                "数学", "口算练习", false, true, null));
        dailyTaskDao.insert(new DailyTask(null, institutionId, classRoomId, studentId, day2, null,
                "英语", "单词听写", false, false, null));

        studentDailyNoteDao.upsertRating(institutionId, classRoomId, studentId, day1, 4);
        studentDailyNoteDao.upsertRating(institutionId, classRoomId, studentId, day2, 5);

        String token = login("13900008001", "password");

        mockMvc.perform(get("/api/students/" + studentId + "/monthly-stats")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.completedDays").value(1))
                .andExpect(jsonPath("$.incompleteDays").value(1))
                .andExpect(jsonPath("$.dailyRates.length()").value(2))
                .andExpect(jsonPath("$.dailyRates[0].date").value(day1.toString()))
                .andExpect(jsonPath("$.dailyRates[0].rate").value(1.0))
                .andExpect(jsonPath("$.dailyRates[1].date").value(day2.toString()))
                .andExpect(jsonPath("$.dailyRates[1].rate").value(0.5))
                .andExpect(jsonPath("$.averageRating").value(4.5))
                .andExpect(jsonPath("$.dailyRatings.length()").value(2))
                .andExpect(jsonPath("$.dailyRatings[0].date").value(day1.toString()))
                .andExpect(jsonPath("$.dailyRatings[0].rating").value(4))
                .andExpect(jsonPath("$.dailyRatings[1].rating").value(5))
                .andExpect(jsonPath("$.days.length()").value(2))
                .andExpect(jsonPath("$.days[0].date").value(day1.toString()))
                .andExpect(jsonPath("$.days[0].tasks.length()").value(2))
                .andExpect(jsonPath("$.days[0].rating").value(4))
                .andExpect(jsonPath("$.days[1].tasks[0].completed").value(true))
                .andExpect(jsonPath("$.days[1].tasks[1].completed").value(false));
    }

    @Test
    void teacherCannotViewAnotherTeachersStudentStats() throws Exception {
        Long institutionId = institutionDao.insert("月度统计控制器测试机构B");
        Long teacherAId = teacherDao.insert(new Teacher(null, institutionId, "13900008002",
                passwordEncoder.encode("password-a"), Role.TEACHER, false, null));
        Long teacherBId = teacherDao.insert(new Teacher(null, institutionId, "13900008003",
                passwordEncoder.encode("password-b"), Role.TEACHER, false, null));
        Long classRoomAId = classRoomDao.insert(new ClassRoom(null, institutionId, teacherAId, "A班", null));
        Long studentAId = studentDao.insert(new Student(null, institutionId, classRoomAId, "小明", "三年级2班",
                true, null, null));

        String tokenB = login("13900008003", "password-b");

        mockMvc.perform(get("/api/students/" + studentAId + "/monthly-stats")
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
