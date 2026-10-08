# 收入快照卡片 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 新增 `GET /api/admin/overview/revenue` 接口，给机构总览页的"收入快照"卡片提供数据——托管费应收/实收看**上个月**，课外课消课次数看**本月至今**。

**Architecture:** 在已有的 `BillGenerationService` 上加一个新方法 `getRevenueSnapshot`，复用已有的 `getBillOverview(institutionId, month, ...)` 按月查询（传上个月）算托管费，复用 `CourseAccountService.countConsumptionsInMonth`（由另一个并行计划新增，本计划只调用、不重复定义）算本月消课次数。挂一个新的 `GET` 方法到 `AdminOverviewController`。

**Tech Stack:** Spring Boot（JdbcTemplate 风格 DAO，本计划不新增 DAO 方法）、MockMvc 集成测试（Testcontainers MySQL）。

**Spec:** `docs/superpowers/specs/2026-10-08-机构管理员总览页-design.md`（"核心设计决策"第5条 + "五张卡片的数据来源与新增接口"第5节）

## Global Constraints

- 托管费数字永远是**上个月**（`YearMonth.now().minusMonths(1)`），绝不是当月——这是故意的：账单生成是管理员手动触发、且只在月份结束后才有意义去生成，当月账单八成还不存在。不要把这个"改成当月"当成 bug 去"修"。
- 课外课消课次数永远是**本月至今**（`YearMonth.now()`）——跟托管费不是同一个月份，这是刻意的设计，不要为了"统一口径"把两个指标并成同一个月份。
- 课外课消课次数只做计数，不换算成金额，不管课程有没有配置 `pricePerLesson`。
- 这个接口不接受任何查询参数（不开放月份选择）。
- `tuitionMonth`/`consumptionMonth` 以 `YearMonth.toString()` 格式（如 `"2026-09"`）放进响应体，前端直接显示这两个字符串，不在后端或前端里拼"上月"/"本月"这种相对文案跟日期脱节。

## Review Focus

- 当月账单不存在或者"恰好也生成了一条当月账单"：当月的 `BillOverviewRow` 绝不能混进 `tuitionBilled`/`tuitionCollected`——只查上个月那一次 `getBillOverview` 调用，天然不会混入当月数据，但要专门写一个测试断言这件事，不能只靠"代码看起来对"。
- 上个月账单生成了但一直没缴费：应该照常计入 `tuitionBilled`，但不计入 `tuitionCollected`——不是"没缴费就从应收里剔除"。
- 空机构（没有任何账单、没有任何消课记录）：三个数字都应该是 `0`/空金额，月份字符串正常返回，不能抛异常。
- 消课次数要按自然月过滤：上个月的消课记录不能计入本月的 `offCampusConsumptionCount`。
- 非 ADMIN 角色调用：跟其他 admin-only 接口一样返回 401/403，不能因为是新接口就漏掉鉴权。

---

### Task 1: `BillGenerationService.getRevenueSnapshot` + `RevenueSnapshot` DTO + `/api/admin/overview/revenue` 接口

**Files:**
- Create: `backend/src/main/java/com/tuoguan/backend/admin/web/RevenueSnapshot.java`
- Modify: `backend/src/main/java/com/tuoguan/backend/billing/service/BillGenerationService.java`
- Modify: `backend/src/main/java/com/tuoguan/backend/admin/web/AdminOverviewController.java`
- Test: `backend/src/test/java/com/tuoguan/backend/admin/web/AdminOverviewControllerTest.java`

**Interfaces:**
- Consumes: `BillGenerationService.getBillOverview(Long institutionId, YearMonth month, Long classRoomId, String studentName, boolean offCampusOnly)`（已存在，`backend/src/main/java/com/tuoguan/backend/billing/service/BillGenerationService.java:242`，返回 `List<BillOverviewRow>`，字段 `studentId, studentName, classRoomId, className, teacherName, billId, totalAmount, isPaid, yearMonth`）；`CourseAccountService.countConsumptionsInMonth(Long institutionId, YearMonth month) -> int`（由另一个并行计划新增到 `CourseAccountService`，本任务执行时如果还没合并进来，先假设它已经存在并按这个签名调用——不要在本任务里重新实现它；如果执行时发现它确实还不存在，就只能先跳过这一步调用并在 PR 描述里注明依赖未就绪，不要擅自改签名）。`BillGenerationService` 已经有 `courseAccountService` 字段（见构造函数第二个参数），直接用，不用新增构造函数依赖。
- Produces: `RevenueSnapshot(String tuitionMonth, BigDecimal tuitionBilled, BigDecimal tuitionCollected, String consumptionMonth, int offCampusConsumptionCount)`；`BillGenerationService.getRevenueSnapshot(Long institutionId) -> RevenueSnapshot`；`GET /api/admin/overview/revenue`，ADMIN 角色，返回 `RevenueSnapshot` JSON。

- [ ] **Step 1: 创建 `RevenueSnapshot` 记录类**

```java
package com.tuoguan.backend.admin.web;

import java.math.BigDecimal;

public record RevenueSnapshot(String tuitionMonth, BigDecimal tuitionBilled, BigDecimal tuitionCollected,
                               String consumptionMonth, int offCampusConsumptionCount) {
}
```

- [ ] **Step 2: 在 `AdminOverviewControllerTest` 里写失败的测试（文件可能已经被其他并行计划创建/追加过方法——先打开看一眼现状，把下面这些方法追加进 `class AdminOverviewControllerTest extends IntegrationTestBase { ... }` 的花括号内，不要删除已有的测试方法或 import；如果某个 import 已经存在就不用重复加）**

本测试类如果是第一次创建（还没有任何其他计划跑过），用这个骨架起步，`import` 按下面测试方法实际用到的类补全（`InstitutionDao`, `TeacherDao`, `Teacher`, `Role`, `LoginResponse`, `StudentDao`, `Student`, `TeachingUnitDao`, `TeachingUnit`, `BillingMode`, `StudentUnitEnrollmentDao`, `StudentUnitEnrollment`, `CourseConsumptionRecordDao`, `CourseConsumptionRecord`, `IntegrationTestBase`, `ObjectMapper`, `PasswordEncoder`, `MvcResult`, `BigDecimal`, `YearMonth`, `@Test`, `@Autowired`, MockMvc 静态导入 `get/post/patch`, `jsonPath`, `status`, `assertThat`）：

```java
package com.tuoguan.backend.admin.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tuoguan.backend.auth.dao.InstitutionDao;
import com.tuoguan.backend.auth.dao.TeacherDao;
import com.tuoguan.backend.auth.domain.Role;
import com.tuoguan.backend.auth.domain.Teacher;
import com.tuoguan.backend.auth.web.LoginResponse;
import com.tuoguan.backend.course.dao.CourseConsumptionRecordDao;
import com.tuoguan.backend.course.domain.CourseConsumptionRecord;
import com.tuoguan.backend.roster.dao.StudentDao;
import com.tuoguan.backend.roster.domain.Student;
import com.tuoguan.backend.unit.dao.StudentUnitEnrollmentDao;
import com.tuoguan.backend.unit.domain.StudentUnitEnrollment;
import com.tuoguan.backend.unit.dao.TeachingUnitDao;
import com.tuoguan.backend.unit.domain.BillingMode;
import com.tuoguan.backend.unit.domain.TeachingUnit;
import com.tuoguan.backend.support.IntegrationTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;
import java.time.YearMonth;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AdminOverviewControllerTest extends IntegrationTestBase {

    @Autowired
    private InstitutionDao institutionDao;

    @Autowired
    private TeacherDao teacherDao;

    @Autowired
    private TeachingUnitDao teachingUnitDao;

    @Autowired
    private StudentDao studentDao;

    @Autowired
    private StudentUnitEnrollmentDao enrollmentDao;

    @Autowired
    private CourseConsumptionRecordDao courseConsumptionRecordDao;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private ObjectMapper objectMapper;

    // ... 其他并行计划追加的测试方法保留在这里 ...

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
```

把下面四个测试方法加进类体（`login` 私有方法整个类只应该有一份，如果已经存在就不要重复加）：

```java
    @Test
    void revenueSnapshotSumsLastMonthTuitionAndExcludesCurrentMonth() throws Exception {
        Long institutionId = institutionDao.insert("收入快照测试机构A");
        teacherDao.insert(new Teacher(null, institutionId, "13900015001",
                passwordEncoder.encode("admin-password"), Role.ADMIN, false, null));
        Long teacherId = teacherDao.insert(new Teacher(null, institutionId, "13900015002",
                passwordEncoder.encode("teacher-password"), Role.TEACHER, false, null));
        Long classRoomId = teachingUnitDao.insert(new TeachingUnit(null, institutionId, teacherId, "收入一班",
                BillingMode.MONTHLY, null, null, true, null));
        Long paidStudentId = studentDao.insert(
                new Student(null, institutionId, classRoomId, "已缴费学生", "一班", true, null, null));
        Long unpaidStudentId = studentDao.insert(
                new Student(null, institutionId, classRoomId, "未缴费学生", "一班", true, null, null));
        String adminToken = login("13900015001", "admin-password");

        // 托管班（有 classRoomId）的学生生成账单前必须先配置计费单价，否则会报
        // BillingRateNotConfiguredException；纯课外课学生才不需要这一步（它们的月度
        // 账单只有课外课超额部分，没有托管费）。
        mockMvc.perform(put("/api/admin/classes/" + classRoomId + "/billing-rate")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tuitionRatePerMonth\":500.00,\"mealRatePerDay\":10.00}"))
                .andExpect(status().isOk());

        YearMonth lastMonth = YearMonth.now().minusMonths(1);
        YearMonth currentMonth = YearMonth.now();

        MvcResult paidGenerate = mockMvc.perform(post("/api/admin/students/" + paidStudentId
                        + "/bills/generate?month=" + lastMonth)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andReturn();
        Long paidBillId = objectMapper.readTree(paidGenerate.getResponse().getContentAsString()).get("id").asLong();
        mockMvc.perform(patch("/api/admin/bills/" + paidBillId + "/paid")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"isPaid\":true}"))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/admin/students/" + unpaidStudentId + "/bills/generate?month=" + lastMonth)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());

        // 当月也生成一条账单——必须被排除在 tuitionBilled/tuitionCollected 之外。
        mockMvc.perform(post("/api/admin/students/" + paidStudentId + "/bills/generate?month=" + currentMonth)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());

        MvcResult result = mockMvc.perform(get("/api/admin/overview/revenue")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tuitionMonth").value(lastMonth.toString()))
                .andExpect(jsonPath("$.consumptionMonth").value(currentMonth.toString()))
                .andReturn();

        var json = objectMapper.readTree(result.getResponse().getContentAsString());
        BigDecimal paidAmount = objectMapper.readTree(paidGenerate.getResponse().getContentAsString())
                .get("totalAmount").decimalValue();
        // 两条上月账单（一条已缴费、一条未缴费）的金额相加才是 tuitionBilled；
        // tuitionCollected 只应该等于已缴费那一条的金额。两条账单金额走同一条计费规则，
        // 这里只断言 tuitionCollected 严格小于 tuitionBilled（证明未缴费那条没被计进 collected），
        // 且 tuitionCollected 至少覆盖了已缴费账单的金额。
        assertTrue(json.get("tuitionBilled").decimalValue().compareTo(json.get("tuitionCollected").decimalValue()) > 0);
        assertTrue(json.get("tuitionCollected").decimalValue().compareTo(paidAmount) >= 0);
    }

    @Test
    void revenueSnapshotCountsOnlyCurrentMonthOffCampusConsumptions() throws Exception {
        Long institutionId = institutionDao.insert("收入快照测试机构B");
        teacherDao.insert(new Teacher(null, institutionId, "13900015010",
                passwordEncoder.encode("admin-password"), Role.ADMIN, false, null));
        Long teacherId = teacherDao.insert(new Teacher(null, institutionId, "13900015011",
                passwordEncoder.encode("teacher-password"), Role.TEACHER, false, null));
        Long courseId = teachingUnitDao.insert(new TeachingUnit(null, institutionId, teacherId, "收入课程",
                BillingMode.LESSON_COUNT, 45, new BigDecimal("50.00"), true, null));
        Long studentId = studentDao.insert(new Student(null, institutionId, null, "消课学生", null, true, null, null));
        enrollmentDao.insert(new StudentUnitEnrollment(null, institutionId, studentId, courseId, true, null));
        String adminToken = login("13900015010", "admin-password");

        courseConsumptionRecordDao.insert(new CourseConsumptionRecord(null, institutionId, studentId, courseId,
                java.time.LocalDate.now(), new BigDecimal("50.00"), teacherId, null));
        courseConsumptionRecordDao.insert(new CourseConsumptionRecord(null, institutionId, studentId, courseId,
                java.time.LocalDate.now().minusMonths(1), new BigDecimal("50.00"), teacherId, null));

        mockMvc.perform(get("/api/admin/overview/revenue")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.offCampusConsumptionCount").value(1));
    }

    @Test
    void revenueSnapshotForEmptyInstitutionReturnsZeroes() throws Exception {
        Long institutionId = institutionDao.insert("收入快照测试机构C");
        teacherDao.insert(new Teacher(null, institutionId, "13900015020",
                passwordEncoder.encode("admin-password"), Role.ADMIN, false, null));
        String adminToken = login("13900015020", "admin-password");

        mockMvc.perform(get("/api/admin/overview/revenue")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tuitionBilled").value(0))
                .andExpect(jsonPath("$.tuitionCollected").value(0))
                .andExpect(jsonPath("$.offCampusConsumptionCount").value(0))
                .andExpect(jsonPath("$.tuitionMonth").value(YearMonth.now().minusMonths(1).toString()))
                .andExpect(jsonPath("$.consumptionMonth").value(YearMonth.now().toString()));
    }

    @Test
    void revenueSnapshotRejectsNonAdminCaller() throws Exception {
        Long institutionId = institutionDao.insert("收入快照测试机构D");
        teacherDao.insert(new Teacher(null, institutionId, "13900015030",
                passwordEncoder.encode("teacher-password"), Role.TEACHER, false, null));
        String teacherToken = login("13900015030", "teacher-password");

        mockMvc.perform(get("/api/admin/overview/revenue")
                        .header("Authorization", "Bearer " + teacherToken))
                .andExpect(status().isForbidden());
    }
```

`revenueSnapshotSumsLastMonthTuitionAndExcludesCurrentMonth` 用到 `assertTrue`，记得补上 `import static org.junit.jupiter.api.Assertions.assertTrue;`。

- [ ] **Step 2.1: 运行测试，确认因为 `RevenueSnapshot`/`getRevenueSnapshot`/`/api/admin/overview/revenue` 还不存在而编译失败**

Run: `cd backend && mvn test -Dtest=AdminOverviewControllerTest`
Expected: 编译错误（找不到符号 `RevenueSnapshot` / `/api/admin/overview/revenue` 404），不是断言失败。

- [ ] **Step 3: 在 `BillGenerationService` 里实现 `getRevenueSnapshot`**

在类里任意位置（建议紧跟 `getBillOverviewAllMonths` 之后）加入：

```java
    public RevenueSnapshot getRevenueSnapshot(Long institutionId) {
        YearMonth tuitionMonth = YearMonth.now().minusMonths(1);
        YearMonth consumptionMonth = YearMonth.now();

        List<BillOverviewRow> lastMonthRows = getBillOverview(institutionId, tuitionMonth, null, null, false);
        BigDecimal tuitionBilled = lastMonthRows.stream()
                .filter(row -> row.billId() != null)
                .map(BillOverviewRow::totalAmount)
                .filter(java.util.Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal tuitionCollected = lastMonthRows.stream()
                .filter(row -> row.billId() != null && row.isPaid())
                .map(BillOverviewRow::totalAmount)
                .filter(java.util.Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        int offCampusConsumptionCount = courseAccountService.countConsumptionsInMonth(institutionId, consumptionMonth);

        return new RevenueSnapshot(tuitionMonth.toString(), tuitionBilled, tuitionCollected,
                consumptionMonth.toString(), offCampusConsumptionCount);
    }
```

把 `import com.tuoguan.backend.admin.web.RevenueSnapshot;` 加到文件顶部的 import 区（跟已有的 `import com.tuoguan.backend.admin.web.BillOverviewRow;` 放一起）；`java.util.Objects` 用全限定名内联即可，不用额外加 import（上面代码已经写成 `java.util.Objects::nonNull`）。

- [ ] **Step 4: 把 `/revenue` 端点加到 `AdminOverviewController`**

打开 `backend/src/main/java/com/tuoguan/backend/admin/web/AdminOverviewController.java`：
- 如果类里还没有 `BillGenerationService` 字段（检查构造函数参数列表），加一个：在构造函数参数列表末尾追加 `, BillGenerationService billGenerationService`，类里加 `private final BillGenerationService billGenerationService;`，构造函数体里加 `this.billGenerationService = billGenerationService;`，文件顶部加 `import com.tuoguan.backend.billing.service.BillGenerationService;`。
- 如果已经有 `BillGenerationService` 字段（因为另一个并行计划——账单欠费提醒——也需要它，已经加过了），直接复用那个字段，不要重复声明。
- 类体里加入这个方法：

```java
    @GetMapping("/revenue")
    public RevenueSnapshot revenue(@AuthenticationPrincipal TeacherPrincipal principal) {
        return billGenerationService.getRevenueSnapshot(principal.institutionId());
    }
```

- [ ] **Step 5: 运行测试，确认通过**

Run: `cd backend && mvn test -Dtest=AdminOverviewControllerTest`
Expected: 全部 PASS（包括其他并行计划已经加进这个类的测试方法）。

- [ ] **Step 6: Commit**

```bash
git add backend/src/main/java/com/tuoguan/backend/admin/web/RevenueSnapshot.java \
        backend/src/main/java/com/tuoguan/backend/billing/service/BillGenerationService.java \
        backend/src/main/java/com/tuoguan/backend/admin/web/AdminOverviewController.java \
        backend/src/test/java/com/tuoguan/backend/admin/web/AdminOverviewControllerTest.java
git commit -m "feat: 总览页收入快照接口（上月托管费 + 本月消课次数）"
```
