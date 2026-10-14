package com.tuoguan.backend.billing.service;

import com.tuoguan.backend.auth.dao.InstitutionDao;
import com.tuoguan.backend.auth.dao.TeacherDao;
import com.tuoguan.backend.auth.domain.Role;
import com.tuoguan.backend.auth.domain.Teacher;
import com.tuoguan.backend.auth.web.LoginResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tuoguan.backend.billing.dao.MonthlyBillDao;
import com.tuoguan.backend.billing.domain.MonthlyBill;
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

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// 产品反馈：部分机构月初预收「下个月」托管费，同时后收「上个月」实际发生的餐费/课外课费，
// 两者合并成一张账单。开启 Institution.billTuitionInAdvance 后，BillGenerationService
// 应该把餐费/课外课费/请假统计的取数月份往前倒一个月，托管费单价仍按账单的 yearMonth
// （管理员选的那个预收月份）计算，不受影响。
class BillGenerationPrepayTuitionTest extends IntegrationTestBase {

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
    private MonthlyBillDao monthlyBillDao;

    @Test
    void generateBillUsesPreviousMonthActualsForMealFeeWhenTuitionBilledInAdvance() throws Exception {
        Long institutionId = institutionDao.insert("托管费提前收取测试机构");
        institutionDao.updateBillTuitionInAdvance(institutionId, true);
        String adminPhone = uniquePhone();
        String teacherPhone = uniquePhone();
        teacherDao.insert(new Teacher(null, institutionId, adminPhone,
                passwordEncoder.encode("admin-password"), Role.ADMIN, false, null));
        Long teacherId = teacherDao.insert(new Teacher(null, institutionId, teacherPhone,
                passwordEncoder.encode("teacher-password"), Role.TEACHER, false, null));
        Long classRoomId = teachingUnitDao.insert(new TeachingUnit(null, institutionId, teacherId, "预收托管班",
                BillingMode.MONTHLY, null, null, true, null));
        Long studentId = studentDao.insert(
                new Student(null, institutionId, classRoomId, "预收测试生", "一班", true, null, null));

        String adminToken = login(adminPhone, "admin-password");
        String teacherToken = login(teacherPhone, "teacher-password");

        mockMvc.perform(put("/api/admin/classes/" + classRoomId + "/billing-rate")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tuitionRatePerMonth\":500.00,\"mealRatePerDay\":10.00}"))
                .andExpect(status().isOk());

        // 1 月份实际发生：到校 + 用餐各一次（标记用餐前必须先有到校记录）。
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

        // 生成 2 月账单：托管费按 2 月预收；餐费应该用 1 月实际发生的那一条，而不是
        // 2 月（2 月还没有任何用餐记录）。
        MvcResult result = mockMvc.perform(post("/api/admin/students/" + studentId + "/bills/generate?month=2024-02")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andReturn();
        Long billId = objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asLong();

        Optional<MonthlyBill> bill = monthlyBillDao.findByStudentIdAndYearMonth(studentId, YearMonth.of(2024, 2));
        assertThat(bill).isPresent();
        assertThat(bill.get().id()).isEqualTo(billId);
        assertThat(bill.get().yearMonth()).isEqualTo(YearMonth.of(2024, 2));
        assertThat(bill.get().tuitionAmount()).isEqualByComparingTo(BigDecimal.valueOf(500.00));
        assertThat(bill.get().mealAmount()).isEqualByComparingTo(BigDecimal.valueOf(10.00));
        assertThat(bill.get().totalAmount()).isEqualByComparingTo(BigDecimal.valueOf(510.00));

        // 不开这个开关时行为必须保持不变：同一批数据生成 1 月账单，餐费正常按 1 月本月计算。
        institutionDao.updateBillTuitionInAdvance(institutionId, false);
        mockMvc.perform(post("/api/admin/students/" + studentId + "/bills/generate?month=2024-01")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());
        Optional<MonthlyBill> januaryBill =
                monthlyBillDao.findByStudentIdAndYearMonth(studentId, YearMonth.of(2024, 1));
        assertThat(januaryBill).isPresent();
        assertThat(januaryBill.get().mealAmount()).isEqualByComparingTo(BigDecimal.valueOf(10.00));
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
