package com.tuoguan.backend.kanban.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tuoguan.backend.auth.dao.InstitutionDao;
import com.tuoguan.backend.auth.dao.TeacherDao;
import com.tuoguan.backend.auth.domain.Role;
import com.tuoguan.backend.auth.domain.Teacher;
import com.tuoguan.backend.auth.web.LoginResponse;
import com.tuoguan.backend.roster.dao.StudentDao;
import com.tuoguan.backend.roster.dao.TaskTemplateDao;
import com.tuoguan.backend.roster.domain.Student;
import com.tuoguan.backend.roster.domain.TaskTemplate;
import com.tuoguan.backend.support.IntegrationTestBase;
import com.tuoguan.backend.unit.dao.TeachingUnitDao;
import com.tuoguan.backend.unit.domain.BillingMode;
import com.tuoguan.backend.unit.domain.TeachingUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MvcResult;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class DailyTaskControllerTest extends IntegrationTestBase {

    @Autowired
    private InstitutionDao institutionDao;

    @Autowired
    private TeacherDao teacherDao;

    @Autowired
    private TeachingUnitDao teachingUnitDao;

    @Autowired
    private StudentDao studentDao;

    @Autowired
    private TaskTemplateDao taskTemplateDao;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void batchAssignCreatesTaskOnlyForEnrolledStudents() throws Exception {
        Long institutionId = institutionDao.insert("每日任务控制器测试机构A");
        Long teacherId = teacherDao.insert(new Teacher(null, institutionId, "13900005001",
                passwordEncoder.encode("password"), Role.TEACHER, false, null));
        Long classRoomId = teachingUnitDao.insert(new TeachingUnit(null, institutionId, teacherId, "托管班",
                BillingMode.MONTHLY, null, null, true, null));
        studentDao.insert(new Student(null, institutionId, classRoomId, "小明", "三年级2班", true, null, null));
        studentDao.insert(new Student(null, institutionId, classRoomId, "小红", "三年级3班", true, null, null));
        Long droppedId = studentDao.insert(new Student(null, institutionId, classRoomId, "小刚", "四年级1班",
                true, null, null));
        studentDao.update(new Student(droppedId, institutionId, classRoomId, "小刚", "四年级1班", false, null, null));
        Long templateId = taskTemplateDao.insert(
                new TaskTemplate(null, institutionId, teacherId, "数学", "口算练习", null, false));
        String token = login("13900005001", "password");

        MvcResult result = mockMvc.perform(post("/api/classes/" + classRoomId + "/daily-tasks/batch")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"taskTemplateIds\":[" + templateId + "],\"date\":\"2026-08-06\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        List<?> created = objectMapper.readValue(result.getResponse().getContentAsString(), List.class);
        assertThat(created).hasSize(2);

        mockMvc.perform(get("/api/classes/" + classRoomId + "/daily-tasks?date=2026-08-06")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].subject").value("数学"))
                .andExpect(jsonPath("$[0].completed").value(false));
    }

    @Test
    void addForStudentSyncsToSameSchoolClassPeersOnlyAndIsIndependentlyRevocable() throws Exception {
        Long institutionId = institutionDao.insert("每日任务控制器测试机构B");
        Long teacherId = teacherDao.insert(new Teacher(null, institutionId, "13900005002",
                passwordEncoder.encode("password"), Role.TEACHER, false, null));
        Long classRoomId = teachingUnitDao.insert(new TeachingUnit(null, institutionId, teacherId, "托管班",
                BillingMode.MONTHLY, null, null, true, null));
        Long studentAId = studentDao.insert(new Student(null, institutionId, classRoomId, "小明", "三年级2班",
                true, null, null));
        Long studentBId = studentDao.insert(new Student(null, institutionId, classRoomId, "小红", "三年级2班",
                true, null, null));
        studentDao.insert(new Student(null, institutionId, classRoomId, "小刚", "四年级1班", true, null, null));
        String token = login("13900005002", "password");

        mockMvc.perform(post("/api/students/" + studentAId + "/daily-tasks")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"subject\":\"语文\",\"name\":\"阅读打卡\",\"date\":\"2026-08-06\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.studentId").value(studentAId))
                .andExpect(jsonPath("$.custom").value(true));

        MvcResult listResult = mockMvc.perform(get("/api/classes/" + classRoomId + "/daily-tasks?date=2026-08-06")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andReturn();
        List<DailyTaskResponse> tasks = objectMapper.readValue(listResult.getResponse().getContentAsString(),
                objectMapper.getTypeFactory().constructCollectionType(List.class, DailyTaskResponse.class));
        DailyTaskResponse studentBTask = tasks.stream()
                .filter(t -> t.studentId().equals(studentBId))
                .findFirst().orElseThrow();

        mockMvc.perform(delete("/api/daily-tasks/" + studentBTask.id())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/classes/" + classRoomId + "/daily-tasks?date=2026-08-06")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].studentId").value(studentAId));
    }

    @Test
    void addForStudentDoesNotCrashWhenAPeerHasNoSchoolClassNameAndTreatsNullAsItsOwnGroup() throws Exception {
        // 回归测试：学籍班是选填字段，peer.schoolClassName() 为 null 时，此前用
        // peer.schoolClassName().equals(...) 直接 NPE。改成 Objects.equals 后，
        // null 应被当作独立的"未分班"分组——与目标学生学籍班不同（非 null）的不广播，
        // 都是 null 的则视为同组会广播，和前端 groupBySchoolClass 的分组口径一致。
        Long institutionId = institutionDao.insert("每日任务控制器测试机构H");
        Long teacherId = teacherDao.insert(new Teacher(null, institutionId, "13900005020",
                passwordEncoder.encode("password"), Role.TEACHER, false, null));
        Long classRoomId = teachingUnitDao.insert(new TeachingUnit(null, institutionId, teacherId, "托管班",
                BillingMode.MONTHLY, null, null, true, null));
        Long studentAId = studentDao.insert(new Student(null, institutionId, classRoomId, "小明", "三年级2班",
                true, null, null));
        studentDao.insert(new Student(null, institutionId, classRoomId, "小刚", null, true, null, null));
        Long studentCId = studentDao.insert(new Student(null, institutionId, classRoomId, "小丽", null,
                true, null, null));
        String token = login("13900005020", "password");

        // 目标学生学籍班非空，班里有一个学籍班为空的同学：请求不应 NPE，空学籍班同学不应被广播。
        mockMvc.perform(post("/api/students/" + studentAId + "/daily-tasks")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"subject\":\"语文\",\"name\":\"阅读打卡\",\"date\":\"2026-08-06\"}"))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/classes/" + classRoomId + "/daily-tasks?date=2026-08-06")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].studentId").value(studentAId));

        // 目标学生自己学籍班也为空：和另一个学籍班同样为空的同学视为同组，应被广播。
        mockMvc.perform(post("/api/students/" + studentCId + "/daily-tasks")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"subject\":\"数学\",\"name\":\"口算练习\",\"date\":\"2026-08-07\"}"))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/classes/" + classRoomId + "/daily-tasks?date=2026-08-07")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    void completingATaskUpdatesItsStatus() throws Exception {
        Long institutionId = institutionDao.insert("每日任务控制器测试机构C");
        Long teacherId = teacherDao.insert(new Teacher(null, institutionId, "13900005003",
                passwordEncoder.encode("password"), Role.TEACHER, false, null));
        Long classRoomId = teachingUnitDao.insert(new TeachingUnit(null, institutionId, teacherId, "托管班",
                BillingMode.MONTHLY, null, null, true, null));
        Long studentId = studentDao.insert(new Student(null, institutionId, classRoomId, "小明", "三年级2班",
                true, null, null));
        String token = login("13900005003", "password");

        MvcResult createResult = mockMvc.perform(post("/api/students/" + studentId + "/daily-tasks")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"subject\":\"语文\",\"name\":\"阅读打卡\",\"date\":\"2026-08-06\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        DailyTaskResponse created = objectMapper.readValue(
                createResult.getResponse().getContentAsString(), DailyTaskResponse.class);

        mockMvc.perform(patch("/api/daily-tasks/" + created.id())
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"completed\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.completed").value(true));
    }

    @Test
    void templateFromAnotherInstitutionIsRejectedOnBatchAssign() throws Exception {
        Long institutionAId = institutionDao.insert("每日任务控制器测试机构D");
        Long teacherAId = teacherDao.insert(new Teacher(null, institutionAId, "13900005004",
                passwordEncoder.encode("password"), Role.TEACHER, false, null));
        Long classRoomAId = teachingUnitDao.insert(new TeachingUnit(null, institutionAId, teacherAId, "A班",
                BillingMode.MONTHLY, null, null, true, null));
        Long institutionBId = institutionDao.insert("每日任务控制器测试机构E");
        Long teacherBId = teacherDao.insert(new Teacher(null, institutionBId, "13900005007",
                passwordEncoder.encode("password"), Role.TEACHER, false, null));
        Long foreignTemplateId = taskTemplateDao.insert(
                new TaskTemplate(null, institutionBId, teacherBId, "数学", "口算练习", null, false));
        String tokenA = login("13900005004", "password");

        mockMvc.perform(post("/api/classes/" + classRoomAId + "/daily-tasks/batch")
                        .header("Authorization", "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"taskTemplateIds\":[" + foreignTemplateId + "],\"date\":\"2026-08-06\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void teacherCannotOperateOnAnotherTeachersClassOrStudentDailyTasks() throws Exception {
        Long institutionId = institutionDao.insert("每日任务控制器测试机构F");
        Long teacherAId = teacherDao.insert(new Teacher(null, institutionId, "13900005005",
                passwordEncoder.encode("password-a"), Role.TEACHER, false, null));
        Long teacherBId = teacherDao.insert(new Teacher(null, institutionId, "13900005006",
                passwordEncoder.encode("password-b"), Role.TEACHER, false, null));
        Long classRoomAId = teachingUnitDao.insert(new TeachingUnit(null, institutionId, teacherAId, "A班",
                BillingMode.MONTHLY, null, null, true, null));
        Long studentAId = studentDao.insert(new Student(null, institutionId, classRoomAId, "小明", "三年级2班",
                true, null, null));
        Long templateId = taskTemplateDao.insert(
                new TaskTemplate(null, institutionId, teacherAId, "数学", "口算练习", null, false));

        String tokenA = login("13900005005", "password-a");
        String tokenB = login("13900005006", "password-b");

        mockMvc.perform(post("/api/classes/" + classRoomAId + "/daily-tasks/batch")
                        .header("Authorization", "Bearer " + tokenB)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"taskTemplateIds\":[" + templateId + "],\"date\":\"2026-08-06\"}"))
                .andExpect(status().isNotFound());

        mockMvc.perform(post("/api/students/" + studentAId + "/daily-tasks")
                        .header("Authorization", "Bearer " + tokenB)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"subject\":\"语文\",\"name\":\"阅读打卡\",\"date\":\"2026-08-06\"}"))
                .andExpect(status().isNotFound());

        mockMvc.perform(get("/api/classes/" + classRoomAId + "/daily-tasks?date=2026-08-06")
                        .header("Authorization", "Bearer " + tokenB))
                .andExpect(status().isNotFound());

        MvcResult createResult = mockMvc.perform(post("/api/students/" + studentAId + "/daily-tasks")
                        .header("Authorization", "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"subject\":\"语文\",\"name\":\"阅读打卡\",\"date\":\"2026-08-06\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        DailyTaskResponse created = objectMapper.readValue(
                createResult.getResponse().getContentAsString(), DailyTaskResponse.class);

        mockMvc.perform(patch("/api/daily-tasks/" + created.id())
                        .header("Authorization", "Bearer " + tokenB)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"completed\":true}"))
                .andExpect(status().isNotFound());

        mockMvc.perform(delete("/api/daily-tasks/" + created.id())
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
