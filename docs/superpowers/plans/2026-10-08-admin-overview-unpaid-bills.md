# 机构总览页 · 账单欠费提醒 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a backend endpoint that summarizes every unpaid monthly bill across all months for an institution (no month filter), so the admin overview page's "账单欠费提醒" card can show it.

**Architecture:** Reuse the already-existing `BillGenerationService.getBillOverviewAllMonths(...)` query (it already returns one row per generated bill across every month, plus a sentinel row for students with no bill at all). Add one new service method that filters that result down to unpaid bills and sums them, two new DTO records, and one new `@GetMapping` on the already-existing `AdminOverviewController`.

**Tech Stack:** Spring Boot, Spring MVC, JUnit 5 + MockMvc integration tests (Testcontainers MySQL/MinIO via `IntegrationTestBase`).

**Spec:** `docs/superpowers/specs/2026-10-08-机构管理员总览页-design.md` (section "2. 账单欠费提醒"; read the correction note near the top of that file first — this card's scope changed from "current month only" to "all months" partway through the design discussion, and this plan implements the corrected, final version).

## Global Constraints

- No month query parameter on `/api/admin/overview/unpaid-bills` — the endpoint always returns every unpaid bill regardless of month. This reverses an earlier design draft that scoped it to the current month; do not reintroduce a month filter.
- `/api/admin/overview/*` is a separate path namespace from the existing `/api/admin/dashboard` (that one belongs to the unrelated "任务完成情况" feature in `AdminStatsController` — don't touch it).
- This plan is backend-only. No frontend changes.
- `AdminOverviewController` already exists (created by a separate, earlier plan) with one `CourseAccountService` constructor parameter and one `@GetMapping("/low-balance")` method. This plan **modifies** that file — see Task 2's "assumed current state" note for what to do if the live file differs from what's documented here.

## Review Focus

- A student with a `billId == null` sentinel row (never generated a bill at all) must never be counted as "unpaid" — only rows with a real generated bill that is unpaid count.
- The same student owing money for two different months must appear as two separate rows, each with its own `yearMonth` — this is the entire point of the "all months" redesign, so it needs a dedicated test, not just incidental coverage.
- A bill that has been explicitly marked paid (`PATCH /api/admin/bills/{billId}/paid`) must disappear from the result immediately.
- `count` and `totalAmount` on the summary object must always agree with the `rows` list actually returned — a careless implementation could compute the summary fields from a different (unfiltered) list than the one it returns.
- Institution isolation: an unpaid bill belonging to a different institution must never leak into another institution's admin's result.

---

### Task 1: `UnpaidBillRow` and `UnpaidBillSummary` DTOs

**Files:**
- Create: `backend/src/main/java/com/tuoguan/backend/admin/web/UnpaidBillRow.java`
- Create: `backend/src/main/java/com/tuoguan/backend/admin/web/UnpaidBillSummary.java`
- Test: none (plain records, covered indirectly by Task 3's integration test)

**Interfaces:**
- Produces: `UnpaidBillRow(Long studentId, String studentName, String className, String yearMonth, BigDecimal totalAmount)`, `UnpaidBillSummary(int count, BigDecimal totalAmount, List<UnpaidBillRow> rows)` — Task 2 constructs these, Task 3's test asserts their JSON shape.

- [ ] **Step 1: Create `UnpaidBillRow`**

```java
package com.tuoguan.backend.admin.web;

import java.math.BigDecimal;

public record UnpaidBillRow(Long studentId, String studentName, String className, String yearMonth,
                             BigDecimal totalAmount) {
}
```

- [ ] **Step 2: Create `UnpaidBillSummary`**

```java
package com.tuoguan.backend.admin.web;

import java.math.BigDecimal;
import java.util.List;

public record UnpaidBillSummary(int count, BigDecimal totalAmount, List<UnpaidBillRow> rows) {
}
```

- [ ] **Step 3: Compile to catch typos**

Run: `cd backend && mvn -q compile`
Expected: BUILD SUCCESS (these are standalone records with no logic yet, so this just checks syntax).

- [ ] **Step 4: Commit**

```bash
git add backend/src/main/java/com/tuoguan/backend/admin/web/UnpaidBillRow.java backend/src/main/java/com/tuoguan/backend/admin/web/UnpaidBillSummary.java
git commit -m "feat: add UnpaidBillRow/UnpaidBillSummary DTOs for admin overview"
```

---

### Task 2: `BillGenerationService.getUnpaidBillSummary` + controller endpoint

**Files:**
- Modify: `backend/src/main/java/com/tuoguan/backend/billing/service/BillGenerationService.java`
- Modify: `backend/src/main/java/com/tuoguan/backend/admin/web/AdminOverviewController.java`
- Test: `backend/src/test/java/com/tuoguan/backend/admin/web/AdminOverviewControllerTest.java` (modify — this file already exists from an earlier plan with one test method covering `/low-balance`; you are adding test methods to the same class)

**Interfaces:**
- Consumes: `BillGenerationService.getBillOverviewAllMonths(Long institutionId, Long classRoomId, String studentName, boolean offCampusOnly)` → `List<BillOverviewRow>` (already exists, unchanged). `BillOverviewRow(Long studentId, String studentName, Long classRoomId, String className, String teacherName, Long billId, BigDecimal totalAmount, boolean isPaid, String yearMonth)` (already exists, unchanged).
- Produces: `BillGenerationService.getUnpaidBillSummary(Long institutionId)` → `UnpaidBillSummary`. `GET /api/admin/overview/unpaid-bills` → `UnpaidBillSummary` JSON.

**Assumed current state of `AdminOverviewController.java`** (if it looks different when you actually open it — e.g. another plan already added a different `@GetMapping` method — add your method and constructor param alongside what's there; do not delete or reorder anyone else's code):

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

- [ ] **Step 1: Write the failing integration test**

Add to `backend/src/test/java/com/tuoguan/backend/admin/web/AdminOverviewControllerTest.java` (if the class doesn't exist yet when you reach this task, create it following the exact style of `backend/src/test/java/com/tuoguan/backend/admin/web/AdminBillOverviewControllerTest.java` — same imports, same `extends IntegrationTestBase`, same private `login(phone, password)` helper copied verbatim from that file):

```java
@Test
void unpaidBillsSummaryListsEveryUnpaidMonthSeparatelyAndExcludesPaidAndUngenerated() throws Exception {
    Long institutionId = institutionDao.insert("总览欠费测试机构");
    teacherDao.insert(new Teacher(null, institutionId, "13900015001",
            passwordEncoder.encode("admin-password"), Role.ADMIN, false, null));
    String adminToken = login("13900015001", "admin-password");

    // 学生甲：两个月都没缴费，必须出现两行，各自标对月份。
    Long studentAId = studentDao.insert(new Student(null, institutionId, null, "学生甲", null, true, null, null));
    mockMvc.perform(post("/api/admin/students/" + studentAId + "/bills/generate?month=2024-01")
                    .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isOk());
    mockMvc.perform(post("/api/admin/students/" + studentAId + "/bills/generate?month=2024-02")
                    .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isOk());

    // 学生乙：账单已缴费，不该出现。
    Long studentBId = studentDao.insert(new Student(null, institutionId, null, "学生乙", null, true, null, null));
    MvcResult generateResult = mockMvc.perform(post("/api/admin/students/" + studentBId + "/bills/generate?month=2024-01")
                    .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isOk())
            .andReturn();
    Long paidBillId = objectMapper.readTree(generateResult.getResponse().getContentAsString()).get("id").asLong();
    mockMvc.perform(patch("/api/admin/bills/" + paidBillId + "/paid")
                    .header("Authorization", "Bearer " + adminToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"isPaid\":true}"))
            .andExpect(status().isOk());

    // 学生丙：从没生成过账单，不该出现。
    studentDao.insert(new Student(null, institutionId, null, "学生丙", null, true, null, null));

    MvcResult result = mockMvc.perform(get("/api/admin/overview/unpaid-bills")
                    .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isOk())
            .andReturn();
    String body = result.getResponse().getContentAsString();

    assertThat(objectMapper.readTree(body).get("count").asInt()).isEqualTo(2);
    assertThat(objectMapper.readTree(body).get("rows")).hasSize(2);
    assertThat(body).contains("\"yearMonth\":\"2024-01\"");
    assertThat(body).contains("\"yearMonth\":\"2024-02\"");
    assertThat(body).doesNotContain("学生乙");
    assertThat(body).doesNotContain("学生丙");
}

@Test
void unpaidBillsSummaryIsolatesByInstitution() throws Exception {
    Long institutionAId = institutionDao.insert("总览欠费隔离机构A");
    teacherDao.insert(new Teacher(null, institutionAId, "13900015002",
            passwordEncoder.encode("admin-password"), Role.ADMIN, false, null));
    String adminAToken = login("13900015002", "admin-password");

    Long institutionBId = institutionDao.insert("总览欠费隔离机构B");
    teacherDao.insert(new Teacher(null, institutionBId, "13900015003",
            passwordEncoder.encode("admin-password"), Role.ADMIN, false, null));
    String adminBToken = login("13900015003", "admin-password");
    Long studentBId = studentDao.insert(new Student(null, institutionBId, null, "机构B学生", null, true, null, null));
    mockMvc.perform(post("/api/admin/students/" + studentBId + "/bills/generate?month=2024-01")
                    .header("Authorization", "Bearer " + adminBToken))
            .andExpect(status().isOk());

    mockMvc.perform(get("/api/admin/overview/unpaid-bills")
                    .header("Authorization", "Bearer " + adminAToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.count").value(0))
            .andExpect(jsonPath("$.rows").isEmpty());
}

@Test
void unpaidBillsSummaryRequiresAdminRole() throws Exception {
    Long institutionId = institutionDao.insert("总览欠费鉴权测试机构");
    Long teacherId = teacherDao.insert(new Teacher(null, institutionId, "13900015004",
            passwordEncoder.encode("teacher-password"), Role.TEACHER, false, null));
    String teacherToken = login("13900015004", "teacher-password");

    mockMvc.perform(get("/api/admin/overview/unpaid-bills")
                    .header("Authorization", "Bearer " + teacherToken))
            .andExpect(status().isForbidden());
}
```

If creating the test class fresh, it needs these imports (copy from `AdminBillOverviewControllerTest.java`): `com.fasterxml.jackson.databind.ObjectMapper`, `InstitutionDao`, `TeacherDao`, `Role`, `Teacher`, `LoginResponse`, `StudentDao`, `Student`, `IntegrationTestBase`, `org.junit.jupiter.api.Test`, `@Autowired`, `MediaType`, `PasswordEncoder`, `MvcResult`, `static ... assertThat`, `static ... get/patch/post`, `static ... jsonPath/status`, plus `@Autowired private ObjectMapper objectMapper;` and the private `login` helper method (see Task 1 of the low-balance plan, or copy straight from `AdminBillOverviewControllerTest.java`).

- [ ] **Step 2: Run the tests to verify they fail**

Run (with the four Testcontainers env vars set — see `backend/README.md` "常用命令" section — `export DOCKER_HOST=unix:///Users/<you>/.colima/default/docker.sock DOCKER_API_VERSION=1.44 TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE=/var/run/docker.sock JAVA_TOOL_OPTIONS="-Dapi.version=1.44"` if running locally against colima):

`cd backend && mvn -q -Dtest=AdminOverviewControllerTest test`

Expected: FAIL — compile error, `getUnpaidBillSummary` and `/unpaid-bills` don't exist yet.

- [ ] **Step 3: Implement `getUnpaidBillSummary` in `BillGenerationService`**

Add this method anywhere among the other public methods in `backend/src/main/java/com/tuoguan/backend/billing/service/BillGenerationService.java` (e.g. directly after `getBillOverviewAllMonths`):

```java
public UnpaidBillSummary getUnpaidBillSummary(Long institutionId) {
    List<UnpaidBillRow> rows = getBillOverviewAllMonths(institutionId, null, null, false).stream()
            .filter(row -> row.billId() != null && !row.isPaid())
            .sorted(Comparator.comparing(BillOverviewRow::yearMonth).thenComparing(BillOverviewRow::studentName))
            .map(row -> new UnpaidBillRow(row.studentId(), row.studentName(), row.className(), row.yearMonth(),
                    row.totalAmount()))
            .toList();
    BigDecimal total = rows.stream()
            .map(UnpaidBillRow::totalAmount)
            .filter(java.util.Objects::nonNull)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    return new UnpaidBillSummary(rows.size(), total, rows);
}
```

Add the two new imports at the top of the file: `import com.tuoguan.backend.admin.web.UnpaidBillRow;` and `import com.tuoguan.backend.admin.web.UnpaidBillSummary;` (alongside the existing `import com.tuoguan.backend.admin.web.BillOverviewRow;` line). `Comparator` is already imported in this file.

- [ ] **Step 4: Add the controller endpoint**

Modify `backend/src/main/java/com/tuoguan/backend/admin/web/AdminOverviewController.java`: add the import, constructor parameter, field, and method shown below, keeping everything already in the file:

```java
import com.tuoguan.backend.billing.service.BillGenerationService;
```

```java
private final BillGenerationService billGenerationService;

public AdminOverviewController(CourseAccountService courseAccountService,
                                BillGenerationService billGenerationService) {
    this.courseAccountService = courseAccountService;
    this.billGenerationService = billGenerationService;
}
```

```java
@GetMapping("/unpaid-bills")
public UnpaidBillSummary unpaidBills(@AuthenticationPrincipal TeacherPrincipal principal) {
    return billGenerationService.getUnpaidBillSummary(principal.institutionId());
}
```

If the constructor already takes more than one parameter (because another plan's task ran first and added its own), add `BillGenerationService billGenerationService` as one more parameter and assign it in the body — don't replace the existing parameter list.

- [ ] **Step 5: Run the tests to verify they pass**

Run: `cd backend && mvn -q -Dtest=AdminOverviewControllerTest test`
Expected: PASS, all three new tests plus any pre-existing ones in the class green.

- [ ] **Step 6: Commit**

```bash
git add backend/src/main/java/com/tuoguan/backend/billing/service/BillGenerationService.java backend/src/main/java/com/tuoguan/backend/admin/web/AdminOverviewController.java backend/src/test/java/com/tuoguan/backend/admin/web/AdminOverviewControllerTest.java
git commit -m "feat: add unpaid-bills overview endpoint (all months, not just current)"
```

---

## Self-Review Notes (filled in by the plan author, not a step for the implementer)

- **Spec coverage:** Section "2. 账单欠费提醒" fully covered — all-months scope, `yearMonth` per row, count+total summary, new controller endpoint. The spec's note that this endpoint takes no month parameter is captured in Global Constraints.
- **Placeholder scan:** none found — every step has real code.
- **Type consistency:** `UnpaidBillRow`/`UnpaidBillSummary` field names and types match between Task 1 (definition) and Task 2 (construction and JSON assertions).
- **Review Focus coverage:** sentinel-row exclusion, paid-bill exclusion, multi-month same-student, summary/rows agreement, and institution isolation each have a dedicated assertion or test in Task 2.
