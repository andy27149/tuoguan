package com.tuoguan.backend.unit.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tuoguan.backend.auth.dao.InstitutionDao;
import com.tuoguan.backend.auth.dao.TeacherDao;
import com.tuoguan.backend.auth.domain.Role;
import com.tuoguan.backend.auth.domain.Teacher;
import com.tuoguan.backend.auth.web.LoginResponse;
import com.tuoguan.backend.roster.dao.StudentDao;
import com.tuoguan.backend.roster.domain.Student;
import com.tuoguan.backend.support.IntegrationTestBase;
import com.tuoguan.backend.unit.dao.TeachingUnitDao;
import com.tuoguan.backend.unit.domain.BillingMode;
import com.tuoguan.backend.unit.domain.TeachingUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AnnualPromotionControllerTest extends IntegrationTestBase {

    private static final String ANNUAL_PROMOTION_URL = "/api/admin/teaching-units/annual-promotion";

    @Autowired
    private InstitutionDao institutionDao;

    @Autowired
    private TeacherDao teacherDao;

    @Autowired
    private TeachingUnitDao teachingUnitDao;

    @Autowired
    private StudentDao studentDao;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void promotingFullChainInShuffledOrderProducesCorrectFinalPlacement() throws Exception {
        Fixture f = setupInstitution("学年晋级测试机构A");
        Long unit1 = monthlyUnit(f, "1年级托管班");
        Long unit2 = monthlyUnit(f, "2年级托管班");
        Long unit3 = monthlyUnit(f, "3年级托管班");
        Long unit4 = monthlyUnit(f, "4年级托管班");
        Long s1 = student(f, unit1, "原1年级生");
        Long s2 = student(f, unit2, "原2年级生");
        Long s3 = student(f, unit3, "原3年级生");
        Long s4 = student(f, unit4, "原4年级生");

        // 刻意打乱提交顺序：3→4 排在最前面，正确执行顺序必须由后端拓扑排序推导出来，
        // 而不是依赖管理员按班级从高到低提交。
        mockMvc.perform(post(ANNUAL_PROMOTION_URL)
                        .header("Authorization", "Bearer " + f.adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request(
                                step(unit3, "MOVE_TO_UNIT", unit4),
                                step(unit1, "MOVE_TO_UNIT", unit2),
                                step(unit4, "DEACTIVATE", null),
                                step(unit2, "MOVE_TO_UNIT", unit3))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalMovedCount").value(4));

        assertThat(studentDao.findById(s1).orElseThrow().teachingUnitId()).isEqualTo(unit2);
        assertThat(studentDao.findById(s2).orElseThrow().teachingUnitId()).isEqualTo(unit3);
        assertThat(studentDao.findById(s3).orElseThrow().teachingUnitId()).isEqualTo(unit4);
        Student deactivated = studentDao.findById(s4).orElseThrow();
        assertThat(deactivated.enrolled()).isFalse();
        assertThat(deactivated.teachingUnitId()).isNull();
        // 关键回归点：4年级班最终只有"原3年级学生"一人，没有混入"原4年级学生"——
        // 这直接证明顺序保证来自后端算法而非提交顺序。
        assertThat(studentDao.findAllByTeachingUnitId(unit4)).extracting(Student::id).containsExactly(s3);
    }

    @Test
    void promotingSameChainInAscendingOrderProducesSameResult() throws Exception {
        Fixture f = setupInstitution("学年晋级测试机构B");
        Long unit1 = monthlyUnit(f, "1年级托管班");
        Long unit2 = monthlyUnit(f, "2年级托管班");
        Long unit3 = monthlyUnit(f, "3年级托管班");
        Long unit4 = monthlyUnit(f, "4年级托管班");
        Long s1 = student(f, unit1, "原1年级生");
        Long s2 = student(f, unit2, "原2年级生");
        Long s3 = student(f, unit3, "原3年级生");
        Long s4 = student(f, unit4, "原4年级生");

        mockMvc.perform(post(ANNUAL_PROMOTION_URL)
                        .header("Authorization", "Bearer " + f.adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request(
                                step(unit1, "MOVE_TO_UNIT", unit2),
                                step(unit2, "MOVE_TO_UNIT", unit3),
                                step(unit3, "MOVE_TO_UNIT", unit4),
                                step(unit4, "DEACTIVATE", null))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalMovedCount").value(4));

        assertThat(studentDao.findById(s1).orElseThrow().teachingUnitId()).isEqualTo(unit2);
        assertThat(studentDao.findById(s2).orElseThrow().teachingUnitId()).isEqualTo(unit3);
        assertThat(studentDao.findById(s3).orElseThrow().teachingUnitId()).isEqualTo(unit4);
        assertThat(studentDao.findById(s4).orElseThrow().enrolled()).isFalse();
    }

    @Test
    void offCampusOutcomeClearsTeachingUnitButKeepsEnrolledTrue() throws Exception {
        Fixture f = setupInstitution("学年晋级测试机构C");
        Long unit = monthlyUnit(f, "毕业班");
        Long s = student(f, unit, "毕业生");

        mockMvc.perform(post(ANNUAL_PROMOTION_URL)
                        .header("Authorization", "Bearer " + f.adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request(step(unit, "OFF_CAMPUS", null))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.steps[0].outcome").value("OFF_CAMPUS"))
                .andExpect(jsonPath("$.steps[0].movedCount").value(1));

        Student updated = studentDao.findById(s).orElseThrow();
        assertThat(updated.teachingUnitId()).isNull();
        assertThat(updated.enrolled()).isTrue();
    }

    @Test
    void deactivateOutcomeClearsTeachingUnitAndSetsEnrolledFalse() throws Exception {
        Fixture f = setupInstitution("学年晋级测试机构D");
        Long unit = monthlyUnit(f, "毕业班");
        Long s = student(f, unit, "毕业生");

        mockMvc.perform(post(ANNUAL_PROMOTION_URL)
                        .header("Authorization", "Bearer " + f.adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request(step(unit, "DEACTIVATE", null))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.steps[0].outcome").value("DEACTIVATE"))
                .andExpect(jsonPath("$.steps[0].movedCount").value(1));

        Student updated = studentDao.findById(s).orElseThrow();
        assertThat(updated.teachingUnitId()).isNull();
        assertThat(updated.enrolled()).isFalse();
    }

    @Test
    void sourceUnitWithZeroEnrolledStudentsSucceedsWithZeroMovedCount() throws Exception {
        Fixture f = setupInstitution("学年晋级测试机构E");
        Long unit = monthlyUnit(f, "空班");

        mockMvc.perform(post(ANNUAL_PROMOTION_URL)
                        .header("Authorization", "Bearer " + f.adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request(step(unit, "OFF_CAMPUS", null))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalMovedCount").value(0))
                .andExpect(jsonPath("$.steps[0].movedCount").value(0));
    }

    @Test
    void onlyEnrolledStudentsAreMovedDisabledStudentsUntouched() throws Exception {
        Fixture f = setupInstitution("学年晋级测试机构F");
        Long source = monthlyUnit(f, "源班级");
        Long target = monthlyUnit(f, "目标班级");
        Long enrolledStudent = student(f, source, "在读生");
        Long disabledStudent = disabledStudent(f, source, "已停用生");

        mockMvc.perform(post(ANNUAL_PROMOTION_URL)
                        .header("Authorization", "Bearer " + f.adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request(step(source, "MOVE_TO_UNIT", target))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.steps[0].movedCount").value(1));

        assertThat(studentDao.findById(enrolledStudent).orElseThrow().teachingUnitId()).isEqualTo(target);
        assertThat(studentDao.findById(disabledStudent).orElseThrow().teachingUnitId()).isEqualTo(source);
    }

    @Test
    void rejectsTwoCycleAndDoesNotMoveAnyStudent() throws Exception {
        Fixture f = setupInstitution("学年晋级测试机构G");
        Long unitA = monthlyUnit(f, "A班");
        Long unitB = monthlyUnit(f, "B班");
        Long sA = student(f, unitA, "A生");
        Long sB = student(f, unitB, "B生");

        mockMvc.perform(post(ANNUAL_PROMOTION_URL)
                        .header("Authorization", "Bearer " + f.adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request(
                                step(unitA, "MOVE_TO_UNIT", unitB),
                                step(unitB, "MOVE_TO_UNIT", unitA))))
                .andExpect(status().isBadRequest());

        assertThat(studentDao.findById(sA).orElseThrow().teachingUnitId()).isEqualTo(unitA);
        assertThat(studentDao.findById(sB).orElseThrow().teachingUnitId()).isEqualTo(unitB);
    }

    @Test
    void rejectsThreeCycleAndDoesNotMoveAnyStudent() throws Exception {
        Fixture f = setupInstitution("学年晋级测试机构H");
        Long unitA = monthlyUnit(f, "A班");
        Long unitB = monthlyUnit(f, "B班");
        Long unitC = monthlyUnit(f, "C班");
        Long sA = student(f, unitA, "A生");
        Long sB = student(f, unitB, "B生");
        Long sC = student(f, unitC, "C生");

        mockMvc.perform(post(ANNUAL_PROMOTION_URL)
                        .header("Authorization", "Bearer " + f.adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request(
                                step(unitA, "MOVE_TO_UNIT", unitB),
                                step(unitB, "MOVE_TO_UNIT", unitC),
                                step(unitC, "MOVE_TO_UNIT", unitA))))
                .andExpect(status().isBadRequest());

        assertThat(studentDao.findById(sA).orElseThrow().teachingUnitId()).isEqualTo(unitA);
        assertThat(studentDao.findById(sB).orElseThrow().teachingUnitId()).isEqualTo(unitB);
        assertThat(studentDao.findById(sC).orElseThrow().teachingUnitId()).isEqualTo(unitC);
    }

    @Test
    void rejectsSelfTargetEqualsSource() throws Exception {
        Fixture f = setupInstitution("学年晋级测试机构I");
        Long unit = monthlyUnit(f, "班级");

        mockMvc.perform(post(ANNUAL_PROMOTION_URL)
                        .header("Authorization", "Bearer " + f.adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request(step(unit, "MOVE_TO_UNIT", unit))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void rejectsDuplicateSourceUnit() throws Exception {
        Fixture f = setupInstitution("学年晋级测试机构J");
        Long unit1 = monthlyUnit(f, "1班");
        Long unit2 = monthlyUnit(f, "2班");
        Long unit3 = monthlyUnit(f, "3班");

        mockMvc.perform(post(ANNUAL_PROMOTION_URL)
                        .header("Authorization", "Bearer " + f.adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request(
                                step(unit1, "MOVE_TO_UNIT", unit2),
                                step(unit1, "MOVE_TO_UNIT", unit3))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void rejectsSourceTeachingUnitFromAnotherInstitution() throws Exception {
        Fixture f = setupInstitution("学年晋级测试机构K1");
        Fixture other = setupInstitution("学年晋级测试机构K2");
        Long outsiderUnit = monthlyUnit(other, "别的机构班");
        Long target = monthlyUnit(f, "目标班");

        mockMvc.perform(post(ANNUAL_PROMOTION_URL)
                        .header("Authorization", "Bearer " + f.adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request(step(outsiderUnit, "MOVE_TO_UNIT", target))))
                .andExpect(status().isNotFound());
    }

    @Test
    void rejectsTargetTeachingUnitFromAnotherInstitution() throws Exception {
        Fixture f = setupInstitution("学年晋级测试机构L1");
        Fixture other = setupInstitution("学年晋级测试机构L2");
        Long source = monthlyUnit(f, "源班");
        Long outsiderUnit = monthlyUnit(other, "别的机构班");

        mockMvc.perform(post(ANNUAL_PROMOTION_URL)
                        .header("Authorization", "Bearer " + f.adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request(step(source, "MOVE_TO_UNIT", outsiderUnit))))
                .andExpect(status().isNotFound());
    }

    @Test
    void rejectsNonMonthlySourceUnit() throws Exception {
        Fixture f = setupInstitution("学年晋级测试机构M");
        Long course = lessonCountUnit(f, "课外课");
        Long target = monthlyUnit(f, "目标班");

        mockMvc.perform(post(ANNUAL_PROMOTION_URL)
                        .header("Authorization", "Bearer " + f.adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request(step(course, "MOVE_TO_UNIT", target))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void rejectsNonMonthlyTargetUnit() throws Exception {
        Fixture f = setupInstitution("学年晋级测试机构N");
        Long source = monthlyUnit(f, "源班");
        Long course = lessonCountUnit(f, "课外课");

        mockMvc.perform(post(ANNUAL_PROMOTION_URL)
                        .header("Authorization", "Bearer " + f.adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request(step(source, "MOVE_TO_UNIT", course))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void rejectsOffCampusStepWithNonNullTarget() throws Exception {
        Fixture f = setupInstitution("学年晋级测试机构O");
        Long source = monthlyUnit(f, "源班");
        Long target = monthlyUnit(f, "目标班");

        mockMvc.perform(post(ANNUAL_PROMOTION_URL)
                        .header("Authorization", "Bearer " + f.adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request(step(source, "OFF_CAMPUS", target))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void rejectsDeactivateStepWithNonNullTarget() throws Exception {
        Fixture f = setupInstitution("学年晋级测试机构P");
        Long source = monthlyUnit(f, "源班");
        Long target = monthlyUnit(f, "目标班");

        mockMvc.perform(post(ANNUAL_PROMOTION_URL)
                        .header("Authorization", "Bearer " + f.adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request(step(source, "DEACTIVATE", target))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void nonAdminTeacherIsForbiddenFromRunningAnnualPromotion() throws Exception {
        Long institutionId = institutionDao.insert("学年晋级测试机构Q");
        String teacherPhone = uniquePhone();
        Long teacherId = teacherDao.insert(new Teacher(null, institutionId, teacherPhone,
                passwordEncoder.encode("password"), Role.TEACHER, false, null));
        Long unit = teachingUnitDao.insert(new TeachingUnit(null, institutionId, teacherId, "班",
                BillingMode.MONTHLY, null, null, true, null));
        String token = login(teacherPhone, "password");

        mockMvc.perform(post(ANNUAL_PROMOTION_URL)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request(step(unit, "OFF_CAMPUS", null))))
                .andExpect(status().isForbidden());
    }

    @Test
    void successfulPromotionRecordsAuditLog() throws Exception {
        Fixture f = setupInstitution("学年晋级测试机构R");
        Long unit = monthlyUnit(f, "班");

        mockMvc.perform(post(ANNUAL_PROMOTION_URL)
                        .header("Authorization", "Bearer " + f.adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request(step(unit, "OFF_CAMPUS", null))))
                .andExpect(status().isOk());

        Integer auditCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM audit_log WHERE action = 'TEACHING_UNIT_ANNUAL_PROMOTION' AND target_id = ?",
                Integer.class, f.institutionId());
        assertThat(auditCount).isEqualTo(1);
    }

    private record Fixture(Long institutionId, Long teacherId, String adminToken) {
    }

    private Fixture setupInstitution(String institutionName) throws Exception {
        Long institutionId = institutionDao.insert(institutionName);
        String adminPhone = uniquePhone();
        teacherDao.insert(new Teacher(null, institutionId, adminPhone,
                passwordEncoder.encode("password"), Role.ADMIN, false, null));
        Long teacherId = teacherDao.insert(new Teacher(null, institutionId, uniquePhone(),
                passwordEncoder.encode("teacher-password"), Role.TEACHER, false, null));
        String token = login(adminPhone, "password");
        return new Fixture(institutionId, teacherId, token);
    }

    private Long monthlyUnit(Fixture f, String name) {
        return teachingUnitDao.insert(new TeachingUnit(null, f.institutionId(), f.teacherId(), name,
                BillingMode.MONTHLY, null, null, true, null));
    }

    private Long lessonCountUnit(Fixture f, String name) {
        return teachingUnitDao.insert(new TeachingUnit(null, f.institutionId(), f.teacherId(), name,
                BillingMode.LESSON_COUNT, 45, null, true, null));
    }

    private Long student(Fixture f, Long unitId, String name) {
        return studentDao.insert(new Student(null, f.institutionId(), unitId, name, "一班", true, null, null));
    }

    private Long disabledStudent(Fixture f, Long unitId, String name) {
        return studentDao.insert(new Student(null, f.institutionId(), unitId, name, "一班", false, null, null));
    }

    private String step(Long sourceId, String outcome, Long targetId) {
        return "{\"sourceTeachingUnitId\":" + sourceId + ",\"outcome\":\"" + outcome + "\""
                + (targetId != null ? ",\"targetTeachingUnitId\":" + targetId : "") + "}";
    }

    private String request(String... steps) {
        return "{\"steps\":[" + String.join(",", steps) + "]}";
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
