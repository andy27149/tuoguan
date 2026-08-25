package com.tuoguan.backend.share.web;

import com.tuoguan.backend.auth.dao.InstitutionDao;
import com.tuoguan.backend.auth.dao.TeacherDao;
import com.tuoguan.backend.auth.domain.Role;
import com.tuoguan.backend.auth.domain.Teacher;
import com.tuoguan.backend.kanban.dao.DailyTaskDao;
import com.tuoguan.backend.kanban.dao.StudentDailyNoteDao;
import com.tuoguan.backend.kanban.dao.StudentPickupCheckinDao;
import com.tuoguan.backend.kanban.domain.DailyTask;
import com.tuoguan.backend.roster.dao.ClassRoomDao;
import com.tuoguan.backend.roster.dao.StudentDao;
import com.tuoguan.backend.roster.domain.ClassRoom;
import com.tuoguan.backend.roster.domain.Student;
import com.tuoguan.backend.support.IntegrationTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDate;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class PublicShareControllerTest extends IntegrationTestBase {

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
    private StudentPickupCheckinDao studentPickupCheckinDao;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Test
    void anonymousVisitorCanViewShareByToken() throws Exception {
        Long institutionId = institutionDao.insert("公开分享测试机构A");
        Long teacherId = teacherDao.insert(new Teacher(null, institutionId, "13900009001",
                passwordEncoder.encode("password"), Role.TEACHER, false, null));
        Long classRoomId = classRoomDao.insert(new ClassRoom(null, institutionId, teacherId, "托管班", null));
        Long studentId = studentDao.insert(new Student(null, institutionId, classRoomId, "小美", "三年级1班",
                true, null, null));

        LocalDate today = LocalDate.now();
        dailyTaskDao.insert(new DailyTask(null, institutionId, classRoomId, studentId, today, null,
                "数学", "口算练习", false, true, null));
        studentDailyNoteDao.upsertRating(institutionId, classRoomId, studentId, today, 5);
        studentPickupCheckinDao.upsert(institutionId, classRoomId, studentId, today, "妈妈", "17:45");

        String shareToken = studentDao.findShareToken(studentId);

        mockMvc.perform(get("/api/public/share/" + shareToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.studentName").value("小美"))
                .andExpect(jsonPath("$.schoolClassName").value("三年级1班"))
                .andExpect(jsonPath("$.stats.completedDays").value(1))
                .andExpect(jsonPath("$.stats.averageRating").value(5.0))
                .andExpect(jsonPath("$.stats.days[0].pickedUpBy").value("妈妈"))
                .andExpect(jsonPath("$.stats.days[0].pickedUpAt").value("17:45"));
    }

    @Test
    void unknownShareTokenReturnsNotFound() throws Exception {
        mockMvc.perform(get("/api/public/share/does-not-exist"))
                .andExpect(status().isNotFound());
    }

    @Test
    void protectedEndpointsStillRequireAuthentication() throws Exception {
        mockMvc.perform(get("/api/classes/1/students"))
                .andExpect(status().isUnauthorized());
    }
}
