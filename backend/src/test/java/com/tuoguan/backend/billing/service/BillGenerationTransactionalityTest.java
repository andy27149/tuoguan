package com.tuoguan.backend.billing.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tuoguan.backend.auth.dao.InstitutionDao;
import com.tuoguan.backend.auth.dao.TeacherDao;
import com.tuoguan.backend.auth.domain.Role;
import com.tuoguan.backend.auth.domain.Teacher;
import com.tuoguan.backend.auth.web.LoginResponse;
import com.tuoguan.backend.billing.dao.MonthlyBillDao;
import com.tuoguan.backend.billing.dao.MonthlyBillMealLineDao;
import com.tuoguan.backend.roster.dao.StudentDao;
import com.tuoguan.backend.roster.domain.Student;
import com.tuoguan.backend.support.IntegrationTestBase;
import com.tuoguan.backend.unit.dao.TeachingUnitDao;
import com.tuoguan.backend.unit.domain.BillingMode;
import com.tuoguan.backend.unit.domain.TeachingUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MvcResult;

import java.time.YearMonth;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// 诊断 #33：generateBillsForInstitution/generateBillsForClass 在同一个类内部直接调用
// generateBill(...)，Spring AOP 代理拦截不到类内自调用，@Transactional 对这条路径实际
// 不生效——单学生账单生成（走 Controller -> 代理）天生没有这个问题，这里专门验证"批量
// 生成中途失败"这个唯一会暴露问题的场景。
class BillGenerationTransactionalityTest extends IntegrationTestBase {

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

    @Autowired
    private BillGenerationService billGenerationService;

    @Autowired
    private MonthlyBillDao monthlyBillDao;

    @SpyBean
    private MonthlyBillMealLineDao monthlyBillMealLineDao;

    @Test
    void bulkGenerationRollsBackPartialWritesWhenOneStudentFailsMidway() throws Exception {
        Long institutionId = institutionDao.insert("账单事务性测试机构");
        String adminPhone = uniquePhone();
        String teacherPhone = uniquePhone();
        teacherDao.insert(new Teacher(null, institutionId, adminPhone,
                passwordEncoder.encode("admin-password"), Role.ADMIN, false, null));
        Long teacherId = teacherDao.insert(new Teacher(null, institutionId, teacherPhone,
                passwordEncoder.encode("teacher-password"), Role.TEACHER, false, null));
        Long classRoomId = teachingUnitDao.insert(new TeachingUnit(null, institutionId, teacherId, "事务测试班",
                BillingMode.MONTHLY, null, null, true, null));
        Long studentId = studentDao.insert(
                new Student(null, institutionId, classRoomId, "事务测试生", "一班", true, null, null));

        String adminToken = login(adminPhone, "admin-password");
        String teacherToken = login(teacherPhone, "teacher-password");

        mockMvc.perform(put("/api/admin/classes/" + classRoomId + "/billing-rate")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tuitionRatePerMonth\":500.00,\"mealRatePerDay\":10.00}"))
                .andExpect(status().isOk());
        // 必须有至少一条用餐记录，才会触发 monthlyBillMealLineDao.insert（下面强制抛异常
        // 的那一步），模拟"账单主表已经 upsert 完成、后续明细行写入才失败"的中途失败场景。
        // 标记用餐前必须先有到校记录（ArrivalRequiredException）。
        mockMvc.perform(patch("/api/students/" + studentId + "/arrival")
                        .header("Authorization", "Bearer " + teacherToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"date\":\"2024-01-08\",\"arrivedAt\":\"08:00\"}"))
                .andExpect(status().isNoContent());
        mockMvc.perform(patch("/api/students/" + studentId + "/meal")
                        .header("Authorization", "Bearer " + teacherToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"date\":\"2024-01-08\"}"))
                .andExpect(status().isNoContent());

        doThrow(new RuntimeException("模拟用餐明细行写入失败"))
                .when(monthlyBillMealLineDao).insert(anyLong(), any());

        YearMonth month = YearMonth.of(2024, 1);
        assertThatThrownBy(() -> billGenerationService.generateBillsForInstitution(
                institutionId, month, null, null, false))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("模拟用餐明细行写入失败");

        // 核心断言：generateBill 内部先 upsert 账单主表，再写用餐明细行；用餐明细行写入
        // 失败时，如果 @Transactional 真的生效，整个方法应该回滚，账单主表也不应该落库。
        // 这条断言在修复前会失败（账单主表残留，证明没有事务保护），修复后应该通过。
        Optional<com.tuoguan.backend.billing.domain.MonthlyBill> bill =
                monthlyBillDao.findByStudentIdAndYearMonth(studentId, month);
        assertThat(bill).as("批量生成中途失败后，账单主表不应该残留部分写入的数据").isEmpty();
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
