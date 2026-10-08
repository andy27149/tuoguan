# 今日运营快照 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a `GET /api/admin/overview/today` endpoint returning today's institution-wide custody attendance/meal/leave counts, for the "今日运营快照" card on the new admin overview page.

**Architecture:** Extend the existing `AdminStatsService` (which already has a near-identical per-class loop in `getDashboard`/`buildSummary`) with a new `getTodaySnapshot` method that sums arrival/meal/leave record counts across every MONTHLY teaching unit in the institution. Wire it into `AdminOverviewController` (created/extended by sibling plans) as a new `@GetMapping`.

**Tech Stack:** Spring Boot, Spring MVC, JDBC-backed DAOs (no SQL changes needed — reuses existing per-teaching-unit query methods), JUnit 5 + MockMvc integration tests (`IntegrationTestBase`).

**Spec:** `docs/superpowers/specs/2026-10-08-机构管理员总览页-design.md`, section "4. 今日运营快照".

## Global Constraints

- "今日" always means the server's current date (`LocalDate.now()`) — no date query parameter, no historical lookback. This is a deliberate scope limit from the spec, not an oversight.
- Only `BillingMode.MONTHLY` teaching units (custody classes) are included — pure off-campus courses never contribute to this card's counts.
- This plan does not touch the "在读规模" card — that's a separate sibling plan modifying the same `AdminStatsService`/`AdminOverviewController` files independently. Do not reference or depend on it; if those files already contain its changes when this plan executes, add alongside them without altering them.
- `/api/admin/overview/*` is a brand-new controller path, distinct from the existing `/api/admin/dashboard` endpoint in `AdminStatsController` (that one is for the unrelated "任务完成情况" feature).

## Review Focus

- Summing across multiple teaching units must actually sum (accumulate), not overwrite on each loop iteration — an institution with two custody classes both having activity today must report the combined total, not just one class's numbers.
- A disabled (`enrolled = false`) student must not inflate `totalCustodyStudentCount`, even though the existing `buildSummary` precedent this plan follows does not filter arrival/meal/leave *record* counts by enrollment status (only the student-count denominator is filtered) — this mirrors current codebase behavior exactly, not a gap to close.
- An institution with MONTHLY teaching units that currently have zero enrolled students must return all-zero counts, not throw.
- An institution with zero MONTHLY teaching units at all (e.g. only pure off-campus courses, or nothing yet) must return an all-zero `TodaySnapshot`, not an error.
- A non-ADMIN caller (e.g. a TEACHER) must get a 401/403, matching the existing auth convention on sibling admin-overview endpoints.

---

### Task 1: `getTodaySnapshot` service method + `/today` endpoint

**Files:**
- Modify: `backend/src/main/java/com/tuoguan/backend/admin/service/AdminStatsService.java`
- Create: `backend/src/main/java/com/tuoguan/backend/admin/web/TodaySnapshot.java`
- Modify: `backend/src/main/java/com/tuoguan/backend/admin/web/AdminOverviewController.java` (created by an earlier sibling plan — read its current contents first; if it already has an `AdminStatsService` field from the sibling "enrollment" plan, reuse that field and skip re-adding it)
- Test: `backend/src/test/java/com/tuoguan/backend/admin/web/AdminOverviewControllerTest.java` (created/extended by earlier sibling plans — read its current contents first and add your test methods alongside whatever is already there, matching its existing style)

**Interfaces:**
- Consumes: `TeachingUnitDao.findAllByInstitutionId(Long) -> List<TeachingUnit>`, `StudentDao.findAllByTeachingUnitId(Long) -> List<Student>`, `StudentArrivalCheckinDao.findAllByTeachingUnitIdAndDate(Long, LocalDate) -> List<StudentArrivalCheckin>`, `StudentMealRecordDao.findAllByTeachingUnitIdAndDate(Long, LocalDate) -> List<StudentMealRecord>`, `StudentLeaveRecordDao.findAllByTeachingUnitIdAndDate(Long, LocalDate) -> List<StudentLeaveRecord>` — all pre-existing. `TeacherPrincipal.institutionId() -> Long` — pre-existing, used by every admin controller.
- Produces: `TodaySnapshot(int arrivedCount, int mealCount, int leaveCount, int totalCustodyStudentCount)` (record, public fields via accessors) and `AdminStatsService.getTodaySnapshot(Long institutionId, LocalDate date) -> TodaySnapshot`, consumed only by this task's own controller method — no sibling plan depends on this signature.

- [ ] **Step 1: Write the failing tests**

Append these methods inside the existing `AdminOverviewControllerTest` class (read the file first; if it doesn't exist yet at execution time, create it fresh with this header, copying the exact import/helper style of `backend/src/test/java/com/tuoguan/backend/admin/web/AdminBillOverviewControllerTest.java` — `extends IntegrationTestBase`, `@Autowired InstitutionDao/TeacherDao/TeachingUnitDao/StudentDao/PasswordEncoder`, the inherited `login(phone, password)` helper, `mockMvc.perform(get(...)).header("Authorization", "Bearer " + token)`, `jsonPath` assertions):

```java
import com.tuoguan.backend.kanban.dao.StudentArrivalCheckinDao;
import com.tuoguan.backend.kanban.dao.StudentMealRecordDao;
import com.tuoguan.backend.billing.dao.StudentLeaveRecordDao;

import java.time.LocalDate;

// ... inside the test class, alongside existing @Autowired fields:

    @Autowired
    private StudentArrivalCheckinDao studentArrivalCheckinDao;

    @Autowired
    private StudentMealRecordDao studentMealRecordDao;

    @Autowired
    private StudentLeaveRecordDao studentLeaveRecordDao;

    @Test
    void todaySnapshotCountsArrivalMealLeaveForOneCustodyClass() throws Exception {
        Long institutionId = institutionDao.insert("今日快照测试机构A");
        teacherDao.insert(new Teacher(null, institutionId, "13900015001",
                passwordEncoder.encode("admin-password"), Role.ADMIN, false, null));
        Long teacherId = teacherDao.insert(new Teacher(null, institutionId, "13900015002",
                passwordEncoder.encode("teacher-password"), Role.TEACHER, false, null));
        Long classRoomId = teachingUnitDao.insert(new TeachingUnit(null, institutionId, teacherId, "快照一班",
                BillingMode.MONTHLY, null, null, true, null));
        Long student1Id = studentDao.insert(new Student(null, institutionId, classRoomId, "学生甲", "一班", true, null, null));
        Long student2Id = studentDao.insert(new Student(null, institutionId, classRoomId, "学生乙", "一班", true, null, null));
        LocalDate today = LocalDate.now();

        studentArrivalCheckinDao.upsert(institutionId, classRoomId, student1Id, today, "08:00");
        studentMealRecordDao.upsert(institutionId, classRoomId, student1Id, today);

        String adminToken = login("13900015001", "admin-password");

        mockMvc.perform(get("/api/admin/overview/today")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.arrivedCount").value(1))
                .andExpect(jsonPath("$.mealCount").value(1))
                .andExpect(jsonPath("$.leaveCount").value(0))
                .andExpect(jsonPath("$.totalCustodyStudentCount").value(2));
    }

    @Test
    void todaySnapshotSumsAcrossMultipleCustodyClasses() throws Exception {
        Long institutionId = institutionDao.insert("今日快照测试机构B");
        teacherDao.insert(new Teacher(null, institutionId, "13900015010",
                passwordEncoder.encode("admin-password"), Role.ADMIN, false, null));
        Long teacherAId = teacherDao.insert(new Teacher(null, institutionId, "13900015011",
                passwordEncoder.encode("teacher-password-a"), Role.TEACHER, false, null));
        Long teacherBId = teacherDao.insert(new Teacher(null, institutionId, "13900015012",
                passwordEncoder.encode("teacher-password-b"), Role.TEACHER, false, null));
        Long classAId = teachingUnitDao.insert(new TeachingUnit(null, institutionId, teacherAId, "快照A班",
                BillingMode.MONTHLY, null, null, true, null));
        Long classBId = teachingUnitDao.insert(new TeachingUnit(null, institutionId, teacherBId, "快照B班",
                BillingMode.MONTHLY, null, null, true, null));
        Long studentAId = studentDao.insert(new Student(null, institutionId, classAId, "甲班学生", "一班", true, null, null));
        Long studentBId = studentDao.insert(new Student(null, institutionId, classBId, "乙班学生", "一班", true, null, null));
        LocalDate today = LocalDate.now();

        studentArrivalCheckinDao.upsert(institutionId, classAId, studentAId, today, "08:00");
        studentArrivalCheckinDao.upsert(institutionId, classBId, studentBId, today, "08:30");
        studentLeaveRecordDao.upsert(institutionId, studentBId, classBId, today, "发烧");

        String adminToken = login("13900015010", "admin-password");

        mockMvc.perform(get("/api/admin/overview/today")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.arrivedCount").value(2))
                .andExpect(jsonPath("$.mealCount").value(0))
                .andExpect(jsonPath("$.leaveCount").value(1))
                .andExpect(jsonPath("$.totalCustodyStudentCount").value(2));
    }

    @Test
    void todaySnapshotExcludesDisabledStudentsFromTotalCustodyCount() throws Exception {
        Long institutionId = institutionDao.insert("今日快照测试机构C");
        teacherDao.insert(new Teacher(null, institutionId, "13900015020",
                passwordEncoder.encode("admin-password"), Role.ADMIN, false, null));
        Long teacherId = teacherDao.insert(new Teacher(null, institutionId, "13900015021",
                passwordEncoder.encode("teacher-password"), Role.TEACHER, false, null));
        Long classRoomId = teachingUnitDao.insert(new TeachingUnit(null, institutionId, teacherId, "快照C班",
                BillingMode.MONTHLY, null, null, true, null));
        studentDao.insert(new Student(null, institutionId, classRoomId, "在读学生", "一班", true, null, null));
        studentDao.insert(new Student(null, institutionId, classRoomId, "停用学生", "一班", false, null, null));

        String adminToken = login("13900015020", "admin-password");

        mockMvc.perform(get("/api/admin/overview/today")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalCustodyStudentCount").value(1))
                .andExpect(jsonPath("$.arrivedCount").value(0));
    }

    @Test
    void todaySnapshotForEmptyInstitutionReturnsAllZeros() throws Exception {
        Long institutionId = institutionDao.insert("今日快照测试机构D");
        teacherDao.insert(new Teacher(null, institutionId, "13900015030",
                passwordEncoder.encode("admin-password"), Role.ADMIN, false, null));
        String adminToken = login("13900015030", "admin-password");

        mockMvc.perform(get("/api/admin/overview/today")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.arrivedCount").value(0))
                .andExpect(jsonPath("$.mealCount").value(0))
                .andExpect(jsonPath("$.leaveCount").value(0))
                .andExpect(jsonPath("$.totalCustodyStudentCount").value(0));
    }

    @Test
    void todaySnapshotForClassWithNoStudentsYetReturnsAllZerosWithoutThrowing() throws Exception {
        // 覆盖"机构下有托管班，但这个班还没有学生"这个场景——跟上一个测试（机构里
        // 一个托管班都没有）是不同的代码路径：这里 for 循环会真的跑一轮，只是内部
        // 的学生列表和签到/用餐/请假列表都是空的，必须确认不会抛异常、不会把 null
        // 当成 0 处理错。
        Long institutionId = institutionDao.insert("今日快照测试机构F");
        teacherDao.insert(new Teacher(null, institutionId, "13900015050",
                passwordEncoder.encode("admin-password"), Role.ADMIN, false, null));
        Long teacherId = teacherDao.insert(new Teacher(null, institutionId, "13900015051",
                passwordEncoder.encode("teacher-password"), Role.TEACHER, false, null));
        teachingUnitDao.insert(new TeachingUnit(null, institutionId, teacherId, "空班",
                BillingMode.MONTHLY, null, null, true, null));
        String adminToken = login("13900015050", "admin-password");

        mockMvc.perform(get("/api/admin/overview/today")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.arrivedCount").value(0))
                .andExpect(jsonPath("$.mealCount").value(0))
                .andExpect(jsonPath("$.leaveCount").value(0))
                .andExpect(jsonPath("$.totalCustodyStudentCount").value(0));
    }

    @Test
    void todaySnapshotRejectsNonAdminCaller() throws Exception {
        Long institutionId = institutionDao.insert("今日快照测试机构E");
        teacherDao.insert(new Teacher(null, institutionId, "13900015040",
                passwordEncoder.encode("teacher-password"), Role.TEACHER, false, null));
        String teacherToken = login("13900015040", "teacher-password");

        mockMvc.perform(get("/api/admin/overview/today")
                        .header("Authorization", "Bearer " + teacherToken))
                .andExpect(status().isForbidden());
    }
```

(If the real auth rejection in this codebase returns 401 instead of 403 for a wrong-role token — check the exact status `AdminBillOverviewControllerTest` or another existing admin controller test asserts for a non-ADMIN caller before finalizing this step, and match that exact status rather than assuming 403.)

- [ ] **Step 2: Run the tests to verify they fail**

Run: `cd backend && mvn test -Dtest=AdminOverviewControllerTest`
Expected: compilation failure (`TodaySnapshot` doesn't exist, `getTodaySnapshot` doesn't exist, `/api/admin/overview/today` returns 404) — or if sibling plans' tests already exist in this file and pass, your five new test methods specifically fail/error.

- [ ] **Step 3: Create the `TodaySnapshot` DTO**

Create `backend/src/main/java/com/tuoguan/backend/admin/web/TodaySnapshot.java`:

```java
package com.tuoguan.backend.admin.web;

public record TodaySnapshot(int arrivedCount, int mealCount, int leaveCount, int totalCustodyStudentCount) {
}
```

- [ ] **Step 4: Add `getTodaySnapshot` to `AdminStatsService`**

Read `backend/src/main/java/com/tuoguan/backend/admin/service/AdminStatsService.java` first. At the time this plan was written, its full contents were:

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

    public List<ClassSummary> getDashboard(Long institutionId, LocalDate date) { /* unchanged */ }

    private ClassSummary buildSummary(TeachingUnit teachingUnit, LocalDate date) { /* unchanged */ }
}
```

If a sibling plan ("在读规模") already ran and added a `TeacherDao` field + `getEnrollmentSummary` method, leave those completely untouched and just add your own field/constructor-param/method alongside them — append your two new constructor parameters after whatever is already there, don't reorder existing ones.

Add two new fields and constructor parameters:

```java
import com.tuoguan.backend.kanban.dao.StudentMealRecordDao;
import com.tuoguan.backend.billing.dao.StudentLeaveRecordDao;

// ... as new fields, alongside the existing four (or five, if the enrollment sibling plan already added TeacherDao):
    private final StudentMealRecordDao studentMealRecordDao;
    private final StudentLeaveRecordDao studentLeaveRecordDao;
```

Updated constructor (append the two new parameters at the end of whatever parameter list currently exists, and add the two matching `this.x = x;` assignments):

```java
    public AdminStatsService(TeachingUnitDao teachingUnitDao, StudentDao studentDao, DailyTaskDao dailyTaskDao,
                              StudentArrivalCheckinDao studentArrivalCheckinDao, StudentMealRecordDao studentMealRecordDao,
                              StudentLeaveRecordDao studentLeaveRecordDao) {
        this.teachingUnitDao = teachingUnitDao;
        this.studentDao = studentDao;
        this.dailyTaskDao = dailyTaskDao;
        this.studentArrivalCheckinDao = studentArrivalCheckinDao;
        this.studentMealRecordDao = studentMealRecordDao;
        this.studentLeaveRecordDao = studentLeaveRecordDao;
    }
```

Add the new method anywhere in the class body (e.g. right after `getDashboard`):

```java
    public TodaySnapshot getTodaySnapshot(Long institutionId, LocalDate date) {
        List<TeachingUnit> custodyUnits = teachingUnitDao.findAllByInstitutionId(institutionId).stream()
                .filter(unit -> unit.billingMode() == BillingMode.MONTHLY)
                .toList();

        int arrivedCount = 0;
        int mealCount = 0;
        int leaveCount = 0;
        int totalCustodyStudentCount = 0;

        for (TeachingUnit unit : custodyUnits) {
            totalCustodyStudentCount += (int) studentDao.findAllByTeachingUnitId(unit.id()).stream()
                    .filter(Student::enrolled)
                    .count();
            arrivedCount += studentArrivalCheckinDao.findAllByTeachingUnitIdAndDate(unit.id(), date).size();
            mealCount += studentMealRecordDao.findAllByTeachingUnitIdAndDate(unit.id(), date).size();
            leaveCount += studentLeaveRecordDao.findAllByTeachingUnitIdAndDate(unit.id(), date).size();
        }

        return new TodaySnapshot(arrivedCount, mealCount, leaveCount, totalCustodyStudentCount);
    }
```

Add the import `import com.tuoguan.backend.admin.web.TodaySnapshot;` alongside the existing `AdminDashboardResponse.ClassSummary` import.

- [ ] **Step 5: Wire the endpoint into `AdminOverviewController`**

Read `backend/src/main/java/com/tuoguan/backend/admin/web/AdminOverviewController.java` first — it was created by an earlier sibling plan with at least a `CourseAccountService` field and a `/low-balance` endpoint, and may already have a `BillGenerationService` field (from the "unpaid-bills"/"revenue" sibling plans) and/or an `AdminStatsService` field (from the "在读规模" sibling plan, if it ran first). If `AdminStatsService` is **not yet** a constructor parameter, add it:

```java
import com.tuoguan.backend.admin.service.AdminStatsService;

// new field, alongside whatever existing ones are there:
    private final AdminStatsService adminStatsService;

// append to the constructor parameter list and assign it:
        this.adminStatsService = adminStatsService;
```

If `AdminStatsService adminStatsService` is **already** a constructor parameter (the "在读规模" sibling plan ran first), reuse that existing field — do not add a second one.

Add this method to the class body:

```java
    @GetMapping("/today")
    public TodaySnapshot today(@AuthenticationPrincipal TeacherPrincipal principal) {
        return adminStatsService.getTodaySnapshot(principal.institutionId(), LocalDate.now());
    }
```

Add `import java.time.LocalDate;` to the file if it isn't already imported.

- [ ] **Step 6: Run the tests to verify they pass**

Run: `cd backend && mvn test -Dtest=AdminOverviewControllerTest`
Expected: all tests in the class pass, including your five new ones (BUILD SUCCESS).

- [ ] **Step 7: Commit**

```bash
git add backend/src/main/java/com/tuoguan/backend/admin/service/AdminStatsService.java \
        backend/src/main/java/com/tuoguan/backend/admin/web/TodaySnapshot.java \
        backend/src/main/java/com/tuoguan/backend/admin/web/AdminOverviewController.java \
        backend/src/test/java/com/tuoguan/backend/admin/web/AdminOverviewControllerTest.java
git commit -m "feat: add today's operational snapshot endpoint for admin overview page"
```
