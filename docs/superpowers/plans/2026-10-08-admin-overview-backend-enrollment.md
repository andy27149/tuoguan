# 机构总览页 - 在读规模卡片 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 新增 `GET /api/admin/overview/enrollment` 接口，返回机构在读学生总数、托管班/纯课外课人数拆分、按老师分布，供"机构总览页"的"在读规模"卡片使用。

**Architecture:** 在已有的 `AdminStatsService` 里新增一个 `getEnrollmentSummary` 方法，纯 Java 内存聚合（遍历机构下全部学生+教学单元，按 `teachingUnitId`/`teacherId` 分组计数），不新增任何 DAO 方法，复用 `StudentDao.findAllByInstitutionId`/`TeachingUnitDao.findAllByInstitutionId` 已有的批量查询。`AdminOverviewController`（其他独立计划负责创建/追加）追加一个新端点。

**Tech Stack:** Spring Boot, Spring Security `@PreAuthorize`, MockMvc 集成测试（`IntegrationTestBase`）。

**Spec:** `docs/superpowers/specs/2026-10-08-机构管理员总览页-design.md`（第 "3. 在读规模" 节）

## Global Constraints

- 这个接口没有日期/月份参数——"在读规模"永远是当前在读学生状态的实时快照，不支持查历史某个时间点。
- 已停用（`enrolled=false`）的学生必须被排除在全部三个计数（`totalCount`/`custodyCount`/`offCampusOnlyCount`）和 `byTeacher` 之外。
- `AdminOverviewController` 这个文件可能已经被其他独立计划创建/修改过（它们各自追加自己的构造函数依赖和 `@GetMapping` 方法）。执行本计划的 Task 2 时，先读一遍这个文件的当前内容：如果 `AdminStatsService` 已经是构造函数参数（可能是"今日运营快照"那个姊妹计划先跑了），复用那个已有字段，不要重复添加第二个同类型字段；如果还没有，按文件里已有字段的写法追加。不管哪种情况，只需要追加本计划这一个 `@GetMapping("/enrollment")` 方法本体，不要动文件里其他端点。

## Review Focus

- 同一个老师名下有两个不同班级、两个班都有学生：`byTeacher` 里必须合并成这个老师的一条记录（人数相加），不能出现同一个老师名字出现两次——这是本次聚合逻辑里最容易写错的地方。
- `enrolled=false` 的学生必须不计入 `totalCount`/`custodyCount`/`offCampusOnlyCount` 三个顶层计数中的任何一个，也不出现在 `byTeacher` 的人数里。
- 跨机构隔离：另一个机构的学生/老师数据不能泄漏进本机构的统计结果。
- 空机构（没有任何学生）必须返回全零的汇总对象，而不是抛异常或返回 `null`。
- 纯课外课学生（`teachingUnitId == null`）不能被误判进 `custodyCount`，也不能在 `byTeacher` 聚合时导致空指针（遍历到 `teachingUnitId == null` 的学生时要跳过按老师分组这一步，但仍然要计入 `totalCount`/`offCampusOnlyCount`）。

---

### Task 1: 新增 DTO + `AdminStatsService.getEnrollmentSummary`

**Files:**
- Create: `backend/src/main/java/com/tuoguan/backend/admin/web/TeacherStudentCount.java`
- Create: `backend/src/main/java/com/tuoguan/backend/admin/web/EnrollmentSummary.java`
- Modify: `backend/src/main/java/com/tuoguan/backend/admin/service/AdminStatsService.java`
- Test: `backend/src/test/java/com/tuoguan/backend/admin/service/AdminStatsServiceTest.java`（新建——这个服务目前没有独立的单元/集成测试文件，现有覆盖都走 `AdminStatsControllerTest.java` 这条 HTTP 集成测试路径；这次给新方法单独建一个更贴近服务层的集成测试文件，因为聚合逻辑本身值得独立验证，不强制要求也通过 HTTP 再测一遍——Task 2 的 Controller 测试只做"接口确实把 service 返回的数据原样透出、权限校验生效"这一层，不重复造数据验证聚合细节）

**Interfaces:**
- Consumes: `StudentDao.findAllByInstitutionId(Long) -> List<Student>`（已存在）；`TeachingUnitDao.findAllByInstitutionId(Long) -> List<TeachingUnit>`（已存在）；`TeacherDao.findById(Long) -> Optional<Teacher>`（已存在，`Teacher.name()` 取名字）。
- Produces: `AdminStatsService.getEnrollmentSummary(Long institutionId) -> EnrollmentSummary`，供 Task 2 的 Controller 调用；`EnrollmentSummary(int totalCount, int custodyCount, int offCampusOnlyCount, List<TeacherStudentCount> byTeacher)`；`TeacherStudentCount(String teacherName, int studentCount)`。

- [ ] **Step 1: 写失败的集成测试**

创建 `backend/src/test/java/com/tuoguan/backend/admin/service/AdminStatsServiceTest.java`：

```java
package com.tuoguan.backend.admin.service;

import com.tuoguan.backend.admin.web.EnrollmentSummary;
import com.tuoguan.backend.admin.web.TeacherStudentCount;
import com.tuoguan.backend.auth.dao.InstitutionDao;
import com.tuoguan.backend.auth.dao.TeacherDao;
import com.tuoguan.backend.auth.domain.Role;
import com.tuoguan.backend.auth.domain.Teacher;
import com.tuoguan.backend.roster.dao.StudentDao;
import com.tuoguan.backend.roster.domain.Student;
import com.tuoguan.backend.unit.dao.TeachingUnitDao;
import com.tuoguan.backend.unit.domain.BillingMode;
import com.tuoguan.backend.unit.domain.TeachingUnit;
import com.tuoguan.backend.support.IntegrationTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

class AdminStatsServiceTest extends IntegrationTestBase {

    @Autowired
    private AdminStatsService adminStatsService;

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

    @Test
    void countsCustodyAndOffCampusStudentsSeparatelyAndGroupsCustodyStudentsByTeacher() {
        Long institutionId = institutionDao.insert("在读规模测试机构A");
        Long teacherAId = teacherDao.insert(new Teacher(null, institutionId, "13600006001", "A老师",
                passwordEncoder.encode("password"), Role.TEACHER, false, null));
        Long teacherBId = teacherDao.insert(new Teacher(null, institutionId, "13600006002", "B老师",
                passwordEncoder.encode("password"), Role.TEACHER, false, null));
        Long classAId = teachingUnitDao.insert(new TeachingUnit(null, institutionId, teacherAId, "A班",
                BillingMode.MONTHLY, null, null, true, null));
        Long classBId = teachingUnitDao.insert(new TeachingUnit(null, institutionId, teacherBId, "B班",
                BillingMode.MONTHLY, null, null, true, null));

        studentDao.insert(new Student(null, institutionId, classAId, "托管生甲", "一班", true, null, null));
        studentDao.insert(new Student(null, institutionId, classBId, "托管生乙", "二班", true, null, null));
        studentDao.insert(new Student(null, institutionId, null, "纯课外生", null, true, null, null));

        EnrollmentSummary summary = adminStatsService.getEnrollmentSummary(institutionId);

        assertThat(summary.totalCount()).isEqualTo(3);
        assertThat(summary.custodyCount()).isEqualTo(2);
        assertThat(summary.offCampusOnlyCount()).isEqualTo(1);
        assertThat(summary.byTeacher())
                .extracting(TeacherStudentCount::teacherName, TeacherStudentCount::studentCount)
                .containsExactlyInAnyOrder(tuple("A老师", 1), tuple("B老师", 1));
    }

    @Test
    void combinesStudentsFromTwoClassesOwnedByTheSameTeacherIntoOneEntry() {
        Long institutionId = institutionDao.insert("在读规模测试机构B");
        Long teacherId = teacherDao.insert(new Teacher(null, institutionId, "13600006003", "一人两班老师",
                passwordEncoder.encode("password"), Role.TEACHER, false, null));
        Long classAId = teachingUnitDao.insert(new TeachingUnit(null, institutionId, teacherId, "A班",
                BillingMode.MONTHLY, null, null, true, null));
        Long classBId = teachingUnitDao.insert(new TeachingUnit(null, institutionId, teacherId, "B班",
                BillingMode.MONTHLY, null, null, true, null));

        studentDao.insert(new Student(null, institutionId, classAId, "学生一", "一班", true, null, null));
        studentDao.insert(new Student(null, institutionId, classBId, "学生二", "二班", true, null, null));

        EnrollmentSummary summary = adminStatsService.getEnrollmentSummary(institutionId);

        assertThat(summary.byTeacher()).hasSize(1);
        assertThat(summary.byTeacher().get(0).teacherName()).isEqualTo("一人两班老师");
        assertThat(summary.byTeacher().get(0).studentCount()).isEqualTo(2);
    }

    @Test
    void excludesDisabledStudentsFromEveryCount() {
        Long institutionId = institutionDao.insert("在读规模测试机构C");
        Long teacherId = teacherDao.insert(new Teacher(null, institutionId, "13600006004", "老师",
                passwordEncoder.encode("password"), Role.TEACHER, false, null));
        Long classId = teachingUnitDao.insert(new TeachingUnit(null, institutionId, teacherId, "一班",
                BillingMode.MONTHLY, null, null, true, null));

        studentDao.insert(new Student(null, institutionId, classId, "在读生", "一班", true, null, null));
        studentDao.insert(new Student(null, institutionId, classId, "已停用生", "一班", false, null, null));

        EnrollmentSummary summary = adminStatsService.getEnrollmentSummary(institutionId);

        assertThat(summary.totalCount()).isEqualTo(1);
        assertThat(summary.custodyCount()).isEqualTo(1);
        assertThat(summary.byTeacher()).containsExactly(new TeacherStudentCount("老师", 1));
    }

    @Test
    void returnsAllZeroSummaryForAnEmptyInstitution() {
        Long institutionId = institutionDao.insert("在读规模测试机构D（空机构）");

        EnrollmentSummary summary = adminStatsService.getEnrollmentSummary(institutionId);

        assertThat(summary.totalCount()).isZero();
        assertThat(summary.custodyCount()).isZero();
        assertThat(summary.offCampusOnlyCount()).isZero();
        assertThat(summary.byTeacher()).isEmpty();
    }

    @Test
    void neverCountsAnotherInstitutionsStudents() {
        Long institutionAId = institutionDao.insert("在读规模测试机构E");
        Long institutionBId = institutionDao.insert("在读规模测试机构F");
        Long teacherBId = teacherDao.insert(new Teacher(null, institutionBId, "13600006005", "B机构老师",
                passwordEncoder.encode("password"), Role.TEACHER, false, null));
        Long classBId = teachingUnitDao.insert(new TeachingUnit(null, institutionBId, teacherBId, "B机构一班",
                BillingMode.MONTHLY, null, null, true, null));
        studentDao.insert(new Student(null, institutionBId, classBId, "B机构学生", "一班", true, null, null));

        EnrollmentSummary summary = adminStatsService.getEnrollmentSummary(institutionAId);

        assertThat(summary.totalCount()).isZero();
        assertThat(summary.byTeacher()).isEmpty();
    }
}
```

- [ ] **Step 2: 运行测试，确认因为符号不存在而编译失败**

Run: `cd backend && mvn -q -Dtest=AdminStatsServiceTest test-compile`
Expected: FAIL，报错找不到符号 `EnrollmentSummary`/`TeacherStudentCount`/`getEnrollmentSummary`（这三个都还没创建）

- [ ] **Step 3: 新增两个 DTO record**

创建 `backend/src/main/java/com/tuoguan/backend/admin/web/TeacherStudentCount.java`：

```java
package com.tuoguan.backend.admin.web;

public record TeacherStudentCount(String teacherName, int studentCount) {
}
```

创建 `backend/src/main/java/com/tuoguan/backend/admin/web/EnrollmentSummary.java`：

```java
package com.tuoguan.backend.admin.web;

import java.util.List;

public record EnrollmentSummary(int totalCount, int custodyCount, int offCampusOnlyCount,
                                 List<TeacherStudentCount> byTeacher) {
}
```

- [ ] **Step 4: 给 `AdminStatsService` 新增 `TeacherDao` 依赖和 `getEnrollmentSummary` 方法**

修改 `backend/src/main/java/com/tuoguan/backend/admin/service/AdminStatsService.java`。当前完整内容：

```java
package com.tuoguan.backend.admin.service;

import com.tuoguan.backend.admin.web.AdminDashboardResponse.ClassSummary;
import com.tuoguan.backend.kanban.dao.DailyTaskDao;
import com.tuoguan.backend.kanban.dao.StudentArrivalCheckinDao;
import com.tuoguan.backend.kanban.domain.DailyTask;
import com.tuoguan.backend.roster.dao.StudentDao;
import com.tuoguan.backend.roster.domain.Student;
import com.tuoguan.backend.unit.dao.TeachingUnitDao;
import com.tuoguan.backend.unit.domain.BillingMode;
import com.tuoguan.backend.unit.domain.TeachingUnit;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class AdminStatsService {

    private final TeachingUnitDao teachingUnitDao;
    private final StudentDao studentDao;
    private final DailyTaskDao dailyTaskDao;
    private final StudentArrivalCheckinDao studentArrivalCheckinDao;

    public AdminStatsService(TeachingUnitDao teachingUnitDao, StudentDao studentDao, DailyTaskDao dailyTaskDao,
                              StudentArrivalCheckinDao studentArrivalCheckinDao) {
        this.teachingUnitDao = teachingUnitDao;
        this.studentDao = studentDao;
        this.dailyTaskDao = dailyTaskDao;
        this.studentArrivalCheckinDao = studentArrivalCheckinDao;
    }

    public List<ClassSummary> getDashboard(Long institutionId, LocalDate date) {
        // ...unchanged, see file...
    }

    private ClassSummary buildSummary(TeachingUnit teachingUnit, LocalDate date) {
        // ...unchanged, see file...
    }
}
```

**先读一遍这个文件的当前实际内容**——如果一个独立的姊妹计划（"今日运营快照"）已经先跑过，这个文件可能已经多了 `StudentMealRecordDao`/`StudentLeaveRecordDao` 字段和一个 `getTodaySnapshot` 方法。如果是这样，完全不要动那部分，只在它的基础上追加你自己的改动。如果还是上面这个原始状态，按下面的方式改：

在字段声明处新增一行（放在 `studentArrivalCheckinDao` 后面）：
```java
    private final com.tuoguan.backend.auth.dao.TeacherDao teacherDao;
```
（实际写代码时改成规范的顶部 `import com.tuoguan.backend.auth.dao.TeacherDao;`，不要用这种内联全限定名——这里为了在这份计划文档里不占用一整块 import 列表才这样写，真正改文件时放进 import 区。同时需要 `import com.tuoguan.backend.auth.domain.Teacher;`。）

构造函数签名改成：
```java
    public AdminStatsService(TeachingUnitDao teachingUnitDao, StudentDao studentDao, DailyTaskDao dailyTaskDao,
                              StudentArrivalCheckinDao studentArrivalCheckinDao, TeacherDao teacherDao) {
        this.teachingUnitDao = teachingUnitDao;
        this.studentDao = studentDao;
        this.dailyTaskDao = dailyTaskDao;
        this.studentArrivalCheckinDao = studentArrivalCheckinDao;
        this.teacherDao = teacherDao;
    }
```

（如果姊妹计划已经把构造函数改成了别的参数顺序，把 `teacherDao` 追加在参数列表最后，对应地在构造函数体最后一行赋值，不要打乱已有参数的顺序。）

新增方法（放在 `getDashboard` 后面、`buildSummary` 前面均可）：

```java
    public EnrollmentSummary getEnrollmentSummary(Long institutionId) {
        List<Student> enrolledStudents = studentDao.findAllByInstitutionId(institutionId).stream()
                .filter(Student::enrolled)
                .toList();

        int custodyCount = (int) enrolledStudents.stream().filter(s -> s.teachingUnitId() != null).count();
        int offCampusOnlyCount = enrolledStudents.size() - custodyCount;

        Map<Long, TeachingUnit> unitsById = teachingUnitDao.findAllByInstitutionId(institutionId).stream()
                .collect(Collectors.toMap(TeachingUnit::id, u -> u));

        Map<Long, Long> studentCountByTeacherId = enrolledStudents.stream()
                .filter(s -> s.teachingUnitId() != null)
                .map(s -> unitsById.get(s.teachingUnitId()))
                .filter(unit -> unit != null)
                .collect(Collectors.groupingBy(TeachingUnit::teacherId, Collectors.counting()));

        List<TeacherStudentCount> byTeacher = studentCountByTeacherId.entrySet().stream()
                .map(entry -> new TeacherStudentCount(
                        teacherDao.findById(entry.getKey()).map(Teacher::name).orElse("-"),
                        entry.getValue().intValue()))
                .sorted(Comparator.comparingInt(TeacherStudentCount::studentCount).reversed())
                .toList();

        return new EnrollmentSummary(enrolledStudents.size(), custodyCount, offCampusOnlyCount, byTeacher);
    }
```

新增 import：`com.tuoguan.backend.admin.web.EnrollmentSummary`、`com.tuoguan.backend.admin.web.TeacherStudentCount`、`com.tuoguan.backend.auth.dao.TeacherDao`、`com.tuoguan.backend.auth.domain.Teacher`、`java.util.Comparator`（`Map`/`Collectors`/`List` 已经在文件里导入过）。

- [ ] **Step 5: 运行测试，确认通过**

Run: `cd backend && mvn -q -Dtest=AdminStatsServiceTest test`
Expected: PASS，5 个测试全过

- [ ] **Step 6: Commit**

```bash
cd backend && git add \
    src/main/java/com/tuoguan/backend/admin/web/TeacherStudentCount.java \
    src/main/java/com/tuoguan/backend/admin/web/EnrollmentSummary.java \
    src/main/java/com/tuoguan/backend/admin/service/AdminStatsService.java \
    src/test/java/com/tuoguan/backend/admin/service/AdminStatsServiceTest.java
git commit -m "feat: 新增在读规模统计 AdminStatsService.getEnrollmentSummary"
```

---

### Task 2: `AdminOverviewController` 追加 `/enrollment` 端点

**Files:**
- Modify: `backend/src/main/java/com/tuoguan/backend/admin/web/AdminOverviewController.java`
- Modify: `backend/src/test/java/com/tuoguan/backend/admin/web/AdminOverviewControllerTest.java`

**Interfaces:**
- Consumes: Task 1 产出的 `AdminStatsService.getEnrollmentSummary(Long) -> EnrollmentSummary`。
- Produces: `GET /api/admin/overview/enrollment -> EnrollmentSummary`（JSON）。

- [ ] **Step 1: 读取 `AdminOverviewController.java` 和 `AdminOverviewControllerTest.java` 的当前实际内容**

这两个文件由其他独立计划创建/追加，执行到本任务时它们大概率已经存在（可能已经有 `/low-balance`、`/unpaid-bills`、`/revenue` 里的一个或多个）。先读一遍，确认：

- 构造函数里是否已经有 `AdminStatsService adminStatsService` 这个字段/参数（如果"今日运营快照"那个姊妹计划先跑过，可能已经加过）。如果已经有，直接复用，不要重复添加。
- 如果还没有，在已有构造函数参数列表最后追加 `AdminStatsService adminStatsService`，对应新增 `private final AdminStatsService adminStatsService;` 字段和构造函数体里的赋值语句，新增 `import com.tuoguan.backend.admin.service.AdminStatsService;`。

- [ ] **Step 2: 写失败的集成测试**

在 `AdminOverviewControllerTest.java` 里追加（保留文件里已有的其他测试方法不动）：

```java
    @Test
    void enrollmentReportsCustodyAndOffCampusCounts() throws Exception {
        Long institutionId = institutionDao.insert("总览-在读规模测试机构");
        teacherDao.insert(new Teacher(null, institutionId, "13600007001",
                passwordEncoder.encode("admin-password"), Role.ADMIN, false, null));
        Long teacherId = teacherDao.insert(new Teacher(null, institutionId, "13600007002", "老师",
                passwordEncoder.encode("teacher-password"), Role.TEACHER, false, null));
        Long classId = teachingUnitDao.insert(new TeachingUnit(null, institutionId, teacherId, "一班",
                BillingMode.MONTHLY, null, null, true, null));
        studentDao.insert(new Student(null, institutionId, classId, "托管生", "一班", true, null, null));
        studentDao.insert(new Student(null, institutionId, null, "纯课外生", null, true, null, null));
        String adminToken = login("13600007001", "admin-password");

        mockMvc.perform(get("/api/admin/overview/enrollment")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalCount").value(2))
                .andExpect(jsonPath("$.custodyCount").value(1))
                .andExpect(jsonPath("$.offCampusOnlyCount").value(1))
                .andExpect(jsonPath("$.byTeacher[0].teacherName").value("老师"))
                .andExpect(jsonPath("$.byTeacher[0].studentCount").value(1));
    }

    @Test
    void nonAdminTeacherIsForbiddenFromViewingEnrollmentSummary() throws Exception {
        Long institutionId = institutionDao.insert("总览-在读规模权限测试机构");
        teacherDao.insert(new Teacher(null, institutionId, "13600007003",
                passwordEncoder.encode("teacher-password"), Role.TEACHER, false, null));
        String teacherToken = login("13600007003", "teacher-password");

        mockMvc.perform(get("/api/admin/overview/enrollment")
                        .header("Authorization", "Bearer " + teacherToken))
                .andExpect(status().isForbidden());
    }
```

（如果文件里还没有 `institutionDao`/`teacherDao`/`teachingUnitDao`/`studentDao`/`passwordEncoder` 这几个 `@Autowired` 字段或 `Role`/`Teacher`/`TeachingUnit`/`BillingMode`/`Student` 的 import，参照 `AdminBillOverviewControllerTest.java` 补全——但这几个字段大概率已经被更早跑过的姊妹计划加上了，先读文件确认，不要重复添加同名字段导致编译错误。）

- [ ] **Step 3: 运行测试，确认失败**

Run: `cd backend && mvn -q -Dtest=AdminOverviewControllerTest#enrollmentReportsCustodyAndOffCampusCounts test`
Expected: FAIL（404，端点还不存在，或编译失败如果 `adminStatsService` 字段还没加）

- [ ] **Step 4: 追加 `/enrollment` 端点**

在 `AdminOverviewController.java` 类体内追加（任意位置，不要动已有端点）：

```java
    @GetMapping("/enrollment")
    public EnrollmentSummary enrollment(@AuthenticationPrincipal TeacherPrincipal principal) {
        return adminStatsService.getEnrollmentSummary(principal.institutionId());
    }
```

- [ ] **Step 5: 运行测试，确认通过**

Run: `cd backend && mvn -q -Dtest=AdminOverviewControllerTest test`
Expected: PASS（本文件全部测试，包括其他姊妹计划已经加过的）

- [ ] **Step 6: Commit**

```bash
cd backend && git add \
    src/main/java/com/tuoguan/backend/admin/web/AdminOverviewController.java \
    src/test/java/com/tuoguan/backend/admin/web/AdminOverviewControllerTest.java
git commit -m "feat: 机构总览页新增在读规模接口 GET /api/admin/overview/enrollment"
```
