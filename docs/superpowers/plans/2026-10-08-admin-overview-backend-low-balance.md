# 课时不足预警（Service + Controller + 测试）Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 实现"课时不足预警"卡片的服务端能力——机构范围内批量算出哪些 (学生, 课程) 组合课时余额不足，通过一个新接口 `GET /api/admin/overview/low-balance` 暴露给前端。

**Architecture:** 在已有的 `CourseAccountService` 里新增两个方法（`getLowBalanceEntries`、`countConsumptionsInMonth`），复用它已经注入好的 DAO；新建 `AdminOverviewController` 作为这次"机构总览页"所有卡片共用的控制器入口（本计划只加一个端点，其余端点由其他计划分别追加）。

**Tech Stack:** Spring Boot（Java 17 records）、Spring MVC、MockMvc 集成测试（`IntegrationTestBase`，真实 MySQL via Testcontainers）。

**Spec:** `docs/superpowers/specs/2026-10-08-机构管理员总览页-design.md`（section "1. 课时不足预警"）

## Global Constraints

- 阈值固定写死为 `3`，不做机构级可配置项（这是 spec 的明确取舍）。
- 本计划只做后端，不涉及任何前端文件。
- 新端点路径前缀是 `/api/admin/overview/*`，跟已有的 `/api/admin/dashboard`（`AdminStatsController`，"任务完成情况"用的）是两个完全不同的概念，不要混淆或复用那个路径。
- `CourseRechargeRecordDao.findAllByInstitutionId(Long institutionId)` 和 `CourseConsumptionRecordDao.findAllByInstitutionId(Long institutionId)` 这两个方法由另一个独立计划
  （`docs/superpowers/plans/2026-10-08-admin-overview-backend-dao-infra.md`）负责新增——执行本计划时如果这两个方法还不存在，先去执行那个计划，不要在本计划里重新定义它们。
- `AdminOverviewController` 这个文件在本计划执行前不存在，本计划负责从零创建它；后续还有其他独立计划会各自往这同一个文件里追加一个构造函数依赖和一个 `@GetMapping` 方法——执行到那些计划时，在已有基础上追加，不要覆盖本计划写的内容。

## Review Focus

- 有充值但从未消课的课程，不能仅因为"存在充值记录"就被纳入预警——只有 `balance <= threshold` 才算，充值 10 消课 0（余额 10）必须被排除。
- 同一个学生报了两门不同课程，两门课程的余额互相独立，必须各自出现一行，不能被合并成一行或互相覆盖。
- 机构隔离：A 机构的低余额学生绝不能出现在 B 机构管理员查到的结果里。
- 空机构（完全没有充值/消课记录）返回空数组，不能抛异常或 500。
- 非 ADMIN 角色（比如 TEACHER）调用这个接口必须被拒绝（403），不能因为是新接口就漏掉鉴权注解。

---

### Task 1: LowBalanceRow DTO + CourseAccountService 两个新方法 + AdminOverviewController + 集成测试

**Files:**
- Create: `backend/src/main/java/com/tuoguan/backend/admin/web/LowBalanceRow.java`
- Create: `backend/src/main/java/com/tuoguan/backend/admin/web/AdminOverviewController.java`
- Modify: `backend/src/main/java/com/tuoguan/backend/course/service/CourseAccountService.java`
- Create: `backend/src/test/java/com/tuoguan/backend/admin/web/AdminOverviewControllerTest.java`

**Interfaces:**
- Consumes: `CourseRechargeRecordDao.findAllByInstitutionId(Long institutionId) -> List<CourseRechargeRecord>`、`CourseConsumptionRecordDao.findAllByInstitutionId(Long institutionId) -> List<CourseConsumptionRecord>`（由 sibling 计划 `2026-10-08-admin-overview-backend-dao-infra.md` 提供，执行本任务前确认这两个方法已存在，不存在就先去跑那个计划）。`CourseAccountService` 已有的构造函数字段：`studentDao`（`StudentDao.findById(Long) -> Optional<Student>`）、`teachingUnitDao`（`TeachingUnitDao.findById(Long) -> Optional<TeachingUnit>`）、`rechargeRecordDao`、`consumptionRecordDao`。
- Produces: `CourseAccountService.getLowBalanceEntries(Long institutionId, int threshold) -> List<LowBalanceRow>`、`CourseAccountService.countConsumptionsInMonth(Long institutionId, YearMonth month) -> int`（后者由另一个独立计划"收入快照"消费，本任务只需要实现并测试它，不需要接入调用方）。`GET /api/admin/overview/low-balance` 返回 `List<LowBalanceRow>`，`LowBalanceRow(Long studentId, String studentName, Long courseId, String courseName, int balance)`。

- [ ] **Step 1: 写失败的集成测试（这个阶段代码还编译不过，因为 `LowBalanceRow`/`AdminOverviewController`/两个新 service 方法都还不存在——这是预期的"失败"形式，Java 下一个新功能的集成测试先行通常表现为编译错误而不是断言失败）**

创建 `backend/src/test/java/com/tuoguan/backend/admin/web/AdminOverviewControllerTest.java`：

```java
package com.tuoguan.backend.admin.web;

import com.tuoguan.backend.auth.dao.InstitutionDao;
import com.tuoguan.backend.auth.dao.TeacherDao;
import com.tuoguan.backend.auth.domain.Role;
import com.tuoguan.backend.auth.domain.Teacher;
import com.tuoguan.backend.course.dao.CourseConsumptionRecordDao;
import com.tuoguan.backend.course.dao.CourseRechargeRecordDao;
import com.tuoguan.backend.course.domain.CourseConsumptionRecord;
import com.tuoguan.backend.course.domain.CourseRechargeRecord;
import com.tuoguan.backend.roster.dao.StudentDao;
import com.tuoguan.backend.roster.domain.Student;
import com.tuoguan.backend.unit.dao.TeachingUnitDao;
import com.tuoguan.backend.unit.domain.BillingMode;
import com.tuoguan.backend.unit.domain.TeachingUnit;
import com.tuoguan.backend.support.IntegrationTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDate;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
    private CourseRechargeRecordDao courseRechargeRecordDao;

    @Autowired
    private CourseConsumptionRecordDao courseConsumptionRecordDao;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Test
    void lowBalanceIncludesStudentBelowThresholdAndExcludesStudentAtOrAboveIt() throws Exception {
        Long institutionId = institutionDao.insert("总览课时预警测试机构A");
        teacherDao.insert(new Teacher(null, institutionId, "13900015001",
                passwordEncoder.encode("admin-password"), Role.ADMIN, false, null));
        Long teacherId = teacherDao.insert(new Teacher(null, institutionId, "13900015002",
                passwordEncoder.encode("teacher-password"), Role.TEACHER, false, null));
        Long courseId = teachingUnitDao.insert(new TeachingUnit(null, institutionId, teacherId, "数学课",
                BillingMode.LESSON_COUNT, 60, null, true, null));
        Long lowBalanceStudentId = studentDao.insert(
                new Student(null, institutionId, null, "低余额学生", null, true, null, null));
        Long healthyStudentId = studentDao.insert(
                new Student(null, institutionId, null, "余额充足学生", null, true, null, null));
        // 低余额学生：充值 2，消课 0，余额 2（< 3，应该出现）。
        courseRechargeRecordDao.insert(new CourseRechargeRecord(null, institutionId, lowBalanceStudentId, courseId,
                2, null, teacherId, null));
        // 余额充足学生：充值 5，消课 0，余额 5（>= 3，不应该出现）。
        courseRechargeRecordDao.insert(new CourseRechargeRecord(null, institutionId, healthyStudentId, courseId,
                5, null, teacherId, null));
        String adminToken = login("13900015001", "admin-password");

        mockMvc.perform(get("/api/admin/overview/low-balance")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.studentId == " + lowBalanceStudentId + ")].balance").value(
                        org.hamcrest.Matchers.contains(2)))
                .andExpect(jsonPath("$[?(@.studentId == " + healthyStudentId + ")]").isEmpty());
    }

    @Test
    void lowBalanceIncludesZeroAndNegativeBalances() throws Exception {
        Long institutionId = institutionDao.insert("总览课时预警测试机构B");
        teacherDao.insert(new Teacher(null, institutionId, "13900015010",
                passwordEncoder.encode("admin-password"), Role.ADMIN, false, null));
        Long teacherId = teacherDao.insert(new Teacher(null, institutionId, "13900015011",
                passwordEncoder.encode("teacher-password"), Role.TEACHER, false, null));
        Long courseId = teachingUnitDao.insert(new TeachingUnit(null, institutionId, teacherId, "英语课",
                BillingMode.LESSON_COUNT, 60, null, true, null));
        Long studentId = studentDao.insert(new Student(null, institutionId, null, "欠费学生", null, true, null, null));
        // 充值 1，消课 2，余额 -1。
        courseRechargeRecordDao.insert(new CourseRechargeRecord(null, institutionId, studentId, courseId,
                1, null, teacherId, null));
        courseConsumptionRecordDao.insert(new CourseConsumptionRecord(null, institutionId, studentId, courseId,
                LocalDate.now(), null, teacherId, null));
        courseConsumptionRecordDao.insert(new CourseConsumptionRecord(null, institutionId, studentId, courseId,
                LocalDate.now().minusDays(1), null, teacherId, null));
        String adminToken = login("13900015010", "admin-password");

        mockMvc.perform(get("/api/admin/overview/low-balance")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.studentId == " + studentId + ")].balance").value(
                        org.hamcrest.Matchers.contains(-1)));
    }

    @Test
    void lowBalanceListsTwoCoursesForTheSameStudentAsSeparateRows() throws Exception {
        Long institutionId = institutionDao.insert("总览课时预警测试机构C");
        teacherDao.insert(new Teacher(null, institutionId, "13900015020",
                passwordEncoder.encode("admin-password"), Role.ADMIN, false, null));
        Long teacherId = teacherDao.insert(new Teacher(null, institutionId, "13900015021",
                passwordEncoder.encode("teacher-password"), Role.TEACHER, false, null));
        Long courseAId = teachingUnitDao.insert(new TeachingUnit(null, institutionId, teacherId, "语文课",
                BillingMode.LESSON_COUNT, 60, null, true, null));
        Long courseBId = teachingUnitDao.insert(new TeachingUnit(null, institutionId, teacherId, "数学课",
                BillingMode.LESSON_COUNT, 60, null, true, null));
        Long studentId = studentDao.insert(new Student(null, institutionId, null, "双课程学生", null, true, null, null));
        courseRechargeRecordDao.insert(new CourseRechargeRecord(null, institutionId, studentId, courseAId,
                1, null, teacherId, null));
        courseRechargeRecordDao.insert(new CourseRechargeRecord(null, institutionId, studentId, courseBId,
                2, null, teacherId, null));
        String adminToken = login("13900015020", "admin-password");

        mockMvc.perform(get("/api/admin/overview/low-balance")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.courseId == " + courseAId + ")].balance").value(
                        org.hamcrest.Matchers.contains(1)))
                .andExpect(jsonPath("$[?(@.courseId == " + courseBId + ")].balance").value(
                        org.hamcrest.Matchers.contains(2)));
    }

    @Test
    void lowBalanceReturnsEmptyListForInstitutionWithNoRecords() throws Exception {
        Long institutionId = institutionDao.insert("总览课时预警测试机构D");
        teacherDao.insert(new Teacher(null, institutionId, "13900015030",
                passwordEncoder.encode("admin-password"), Role.ADMIN, false, null));
        String adminToken = login("13900015030", "admin-password");

        mockMvc.perform(get("/api/admin/overview/low-balance")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    void lowBalanceNeverLeaksAnotherInstitutionsStudents() throws Exception {
        Long institutionAId = institutionDao.insert("总览课时预警测试机构E");
        Long institutionBId = institutionDao.insert("总览课时预警测试机构F");
        teacherDao.insert(new Teacher(null, institutionAId, "13900015040",
                passwordEncoder.encode("admin-password"), Role.ADMIN, false, null));
        Long teacherBId = teacherDao.insert(new Teacher(null, institutionBId, "13900015041",
                passwordEncoder.encode("teacher-password"), Role.TEACHER, false, null));
        Long courseBId = teachingUnitDao.insert(new TeachingUnit(null, institutionBId, teacherBId, "B机构课程",
                BillingMode.LESSON_COUNT, 60, null, true, null));
        Long studentBId = studentDao.insert(
                new Student(null, institutionBId, null, "B机构低余额学生", null, true, null, null));
        courseRechargeRecordDao.insert(new CourseRechargeRecord(null, institutionBId, studentBId, courseBId,
                1, null, teacherBId, null));
        String adminAToken = login("13900015040", "admin-password");

        mockMvc.perform(get("/api/admin/overview/low-balance")
                        .header("Authorization", "Bearer " + adminAToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    void nonAdminIsForbiddenFromLowBalance() throws Exception {
        Long institutionId = institutionDao.insert("总览课时预警测试机构G");
        teacherDao.insert(new Teacher(null, institutionId, "13900015050",
                passwordEncoder.encode("teacher-password"), Role.TEACHER, false, null));
        String teacherToken = login("13900015050", "teacher-password");

        mockMvc.perform(get("/api/admin/overview/low-balance")
                        .header("Authorization", "Bearer " + teacherToken))
                .andExpect(status().isForbidden());
    }
}
```

- [ ] **Step 2: 运行测试，确认编译失败（`LowBalanceRow`、`AdminOverviewController`、`getLowBalanceEntries` 都还不存在）**

Run: `cd backend && mvn -q -Dtest=AdminOverviewControllerTest test-compile`
Expected: FAIL，报错找不到符号 `AdminOverviewController`（还没创建）

- [ ] **Step 3: 新建 `LowBalanceRow` DTO**

创建 `backend/src/main/java/com/tuoguan/backend/admin/web/LowBalanceRow.java`：

```java
package com.tuoguan.backend.admin.web;

public record LowBalanceRow(Long studentId, String studentName, Long courseId, String courseName, int balance) {
}
```

- [ ] **Step 4: 在 `CourseAccountService` 里新增两个方法**

打开 `backend/src/main/java/com/tuoguan/backend/course/service/CourseAccountService.java`，在 `getStatement` 方法后面（任意位置，不需要紧跟其后，只要在类体内）新增：

```java
    public List<LowBalanceRow> getLowBalanceEntries(Long institutionId, int threshold) {
        List<CourseRechargeRecord> recharges = rechargeRecordDao.findAllByInstitutionId(institutionId);
        List<CourseConsumptionRecord> consumptions = consumptionRecordDao.findAllByInstitutionId(institutionId);

        record StudentCourseKey(Long studentId, Long teachingUnitId) {
        }

        Set<StudentCourseKey> keys = new LinkedHashSet<>();
        recharges.forEach(r -> keys.add(new StudentCourseKey(r.studentId(), r.teachingUnitId())));
        consumptions.forEach(c -> keys.add(new StudentCourseKey(c.studentId(), c.teachingUnitId())));

        Map<Long, String> studentNames = new HashMap<>();
        Map<Long, String> courseNames = new HashMap<>();

        List<LowBalanceRow> rows = new ArrayList<>();
        for (StudentCourseKey key : keys) {
            int recharged = recharges.stream()
                    .filter(r -> r.studentId().equals(key.studentId()) && r.teachingUnitId().equals(key.teachingUnitId()))
                    .mapToInt(CourseRechargeRecord::lessonCount)
                    .sum();
            int consumed = (int) consumptions.stream()
                    .filter(c -> c.studentId().equals(key.studentId()) && c.teachingUnitId().equals(key.teachingUnitId()))
                    .count();
            int balance = recharged - consumed;
            if (balance > threshold) {
                continue;
            }
            String studentName = studentNames.computeIfAbsent(key.studentId(),
                    id -> studentDao.findById(id).map(Student::name).orElse("-"));
            String courseName = courseNames.computeIfAbsent(key.teachingUnitId(),
                    id -> teachingUnitDao.findById(id).map(TeachingUnit::name).orElse("-"));
            rows.add(new LowBalanceRow(key.studentId(), studentName, key.teachingUnitId(), courseName, balance));
        }

        return rows.stream()
                .sorted(Comparator.comparingInt(LowBalanceRow::balance))
                .toList();
    }

    public int countConsumptionsInMonth(Long institutionId, YearMonth month) {
        return (int) consumptionRecordDao.findAllByInstitutionId(institutionId).stream()
                .filter(c -> YearMonth.from(c.consumptionDate()).equals(month))
                .count();
    }
```

文件顶部已经 import 了 `java.util.ArrayList`、`java.util.Comparator`、`java.util.HashMap`、`java.util.LinkedHashSet`、`java.util.List`、`java.util.Map`、`java.util.Set`（已验证，不要重复添加）。只需要新增这两行：

```java
import com.tuoguan.backend.admin.web.LowBalanceRow;
import java.time.YearMonth;
```

- [ ] **Step 5: 新建 `AdminOverviewController`**

创建 `backend/src/main/java/com/tuoguan/backend/admin/web/AdminOverviewController.java`：

```java
package com.tuoguan.backend.admin.web;

import com.tuoguan.backend.auth.security.TeacherPrincipal;
import com.tuoguan.backend.course.service.CourseAccountService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/admin/overview")
@PreAuthorize("hasRole('ADMIN')")
public class AdminOverviewController {

    private final CourseAccountService courseAccountService;

    public AdminOverviewController(CourseAccountService courseAccountService) {
        this.courseAccountService = courseAccountService;
    }

    @GetMapping("/low-balance")
    public List<LowBalanceRow> lowBalance(@AuthenticationPrincipal TeacherPrincipal principal) {
        return courseAccountService.getLowBalanceEntries(principal.institutionId(), 3);
    }
}
```

- [ ] **Step 6: 跑测试，确认全部通过**

Run（需要 colima/Testcontainers 环境变量，参考 `README.md` "常用命令"一节）：
```bash
cd backend
export DOCKER_HOST=unix:///Users/<用户名>/.colima/default/docker.sock
export DOCKER_API_VERSION=1.44
export TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE=/var/run/docker.sock
export JAVA_TOOL_OPTIONS="-Dapi.version=1.44"
mvn -q -Dtest=AdminOverviewControllerTest test
```
Expected: `Tests run: 6, Failures: 0, Errors: 0`

- [ ] **Step 7: Commit**

```bash
git add backend/src/main/java/com/tuoguan/backend/admin/web/LowBalanceRow.java \
        backend/src/main/java/com/tuoguan/backend/admin/web/AdminOverviewController.java \
        backend/src/main/java/com/tuoguan/backend/course/service/CourseAccountService.java \
        backend/src/test/java/com/tuoguan/backend/admin/web/AdminOverviewControllerTest.java
git commit -m "feat: 机构总览页课时不足预警接口"
```
