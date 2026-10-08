# 课程充值/消课记录机构范围查询 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add institution-wide bulk query methods to `CourseRechargeRecordDao` and `CourseConsumptionRecordDao` so a later plan can aggregate course balances across an entire institution in two queries instead of looping per student.

**Architecture:** Two new one-line SQL methods, `WHERE institution_id = ?` instead of `WHERE student_id = ?`, added alongside the existing per-student methods in the same DAO interfaces/impls. No new abstractions, no service/controller changes — this plan is pure data-access surface for a sibling plan to consume.

**Tech Stack:** Spring JDBC (`JdbcTemplate`), MySQL, JUnit 5 + Testcontainers (`IntegrationTestBase`).

**Spec:** `docs/superpowers/specs/2026-10-08-机构管理员总览页-design.md` (section "1. 课时不足预警" — this plan covers only the DAO-infra half of that section; a separate sibling plan covers `CourseAccountService.getLowBalanceEntries`/`countConsumptionsInMonth` and the controller endpoint that consume these two new methods).

## Global Constraints

- Both new methods are plain `WHERE institution_id = ? ORDER BY id` queries — no filtering, no date range, no threshold logic. Any such logic belongs in the service layer that calls these methods (a separate plan), not here.
- Reuse the exact existing `ROW_MAPPER` in each JDBC impl — do not write a second mapper.
- This plan does not touch `CourseAccountService`, `AdminOverviewController`, or any DTO — those are a sibling plan's scope.

## Review Focus

- Institution isolation: a query for institution A's `findAllByInstitutionId` must never return institution B's rows. This is the only behavior in this plan with any real risk (the SQL has no branching logic otherwise), and it gets a dedicated test in Task 1.

---

### Task 1: Add `findAllByInstitutionId` to both course record DAOs, with an institution-isolation test

**Files:**
- Modify: `backend/src/main/java/com/tuoguan/backend/course/dao/CourseRechargeRecordDao.java`
- Modify: `backend/src/main/java/com/tuoguan/backend/course/dao/JdbcCourseRechargeRecordDao.java`
- Modify: `backend/src/main/java/com/tuoguan/backend/course/dao/CourseConsumptionRecordDao.java`
- Modify: `backend/src/main/java/com/tuoguan/backend/course/dao/JdbcCourseConsumptionRecordDao.java`
- Test: `backend/src/test/java/com/tuoguan/backend/course/dao/CourseRecordInstitutionQueriesTest.java` (new file)

**Interfaces:**
- Consumes: nothing new — uses existing `CourseRechargeRecord(Long id, Long institutionId, Long studentId, Long teachingUnitId, Integer lessonCount, String note, Long recordedByTeacherId, Instant createdAt)` and `CourseConsumptionRecord(Long id, Long institutionId, Long studentId, Long teachingUnitId, LocalDate consumptionDate, BigDecimal priceSnapshot, Long recordedByTeacherId, Instant createdAt)` domain records, both already defined in `com.tuoguan.backend.course.domain`.
- Produces: `CourseRechargeRecordDao.findAllByInstitutionId(Long institutionId): List<CourseRechargeRecord>` and `CourseConsumptionRecordDao.findAllByInstitutionId(Long institutionId): List<CourseConsumptionRecord>` — a sibling plan (`CourseAccountService.getLowBalanceEntries`/`countConsumptionsInMonth`) will call these two methods by these exact names and signatures.

- [ ] **Step 1: Write the failing test**

Create `backend/src/test/java/com/tuoguan/backend/course/dao/CourseRecordInstitutionQueriesTest.java`:

```java
package com.tuoguan.backend.course.dao;

import com.tuoguan.backend.auth.dao.InstitutionDao;
import com.tuoguan.backend.auth.dao.TeacherDao;
import com.tuoguan.backend.auth.domain.Role;
import com.tuoguan.backend.auth.domain.Teacher;
import com.tuoguan.backend.course.domain.CourseConsumptionRecord;
import com.tuoguan.backend.course.domain.CourseRechargeRecord;
import com.tuoguan.backend.roster.dao.StudentDao;
import com.tuoguan.backend.roster.domain.Student;
import com.tuoguan.backend.support.IntegrationTestBase;
import com.tuoguan.backend.unit.dao.TeachingUnitDao;
import com.tuoguan.backend.unit.domain.BillingMode;
import com.tuoguan.backend.unit.domain.TeachingUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CourseRecordInstitutionQueriesTest extends IntegrationTestBase {

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
    private CourseRechargeRecordDao courseRechargeRecordDao;

    @Autowired
    private CourseConsumptionRecordDao courseConsumptionRecordDao;

    @Test
    void findAllByInstitutionIdReturnsOnlyThatInstitutionsRecordsForBothDaos() {
        Long institutionAId = institutionDao.insert("机构范围查询测试机构A");
        Long teacherAId = teacherDao.insert(new Teacher(null, institutionAId, "13900015001",
                passwordEncoder.encode("password"), Role.TEACHER, false, null));
        Long courseAId = teachingUnitDao.insert(new TeachingUnit(null, institutionAId, teacherAId, "课程A",
                BillingMode.LESSON_COUNT, null, null, true, null));
        Long studentAId = studentDao.insert(new Student(null, institutionAId, courseAId, "学生A", null, true, null, null));

        Long institutionBId = institutionDao.insert("机构范围查询测试机构B");
        Long teacherBId = teacherDao.insert(new Teacher(null, institutionBId, "13900015002",
                passwordEncoder.encode("password"), Role.TEACHER, false, null));
        Long courseBId = teachingUnitDao.insert(new TeachingUnit(null, institutionBId, teacherBId, "课程B",
                BillingMode.LESSON_COUNT, null, null, true, null));
        Long studentBId = studentDao.insert(new Student(null, institutionBId, courseBId, "学生B", null, true, null, null));

        Long rechargeAId = courseRechargeRecordDao.insert(
                new CourseRechargeRecord(null, institutionAId, studentAId, courseAId, 5, null, teacherAId, null));
        courseRechargeRecordDao.insert(
                new CourseRechargeRecord(null, institutionBId, studentBId, courseBId, 5, null, teacherBId, null));

        Long consumptionAId = courseConsumptionRecordDao.insert(
                new CourseConsumptionRecord(null, institutionAId, studentAId, courseAId, LocalDate.now(),
                        BigDecimal.valueOf(50), teacherAId, null));
        courseConsumptionRecordDao.insert(
                new CourseConsumptionRecord(null, institutionBId, studentBId, courseBId, LocalDate.now(),
                        BigDecimal.valueOf(50), teacherBId, null));

        List<CourseRechargeRecord> rechargesA = courseRechargeRecordDao.findAllByInstitutionId(institutionAId);
        assertThat(rechargesA).hasSize(1);
        assertThat(rechargesA.get(0).id()).isEqualTo(rechargeAId);
        assertThat(rechargesA.get(0).institutionId()).isEqualTo(institutionAId);

        List<CourseConsumptionRecord> consumptionsA = courseConsumptionRecordDao.findAllByInstitutionId(institutionAId);
        assertThat(consumptionsA).hasSize(1);
        assertThat(consumptionsA.get(0).id()).isEqualTo(consumptionAId);
        assertThat(consumptionsA.get(0).institutionId()).isEqualTo(institutionAId);
    }
}
```

- [ ] **Step 2: Run the test to verify it fails to compile**

Run: `cd backend && mvn -q compile test-compile`
Expected: compile error — `findAllByInstitutionId` is not a member of `CourseRechargeRecordDao`/`CourseConsumptionRecordDao`.

- [ ] **Step 3: Add the method to `CourseRechargeRecordDao` and its JDBC impl**

In `backend/src/main/java/com/tuoguan/backend/course/dao/CourseRechargeRecordDao.java`, add this line inside the interface, after `findAllByStudentId`:

```java
    List<CourseRechargeRecord> findAllByInstitutionId(Long institutionId);
```

In `backend/src/main/java/com/tuoguan/backend/course/dao/JdbcCourseRechargeRecordDao.java`, add this method right after the existing `findAllByStudentId` method (same file, same class, reuses the existing `ROW_MAPPER` field already declared at the top of the class — do not duplicate it):

```java
    @Override
    public List<CourseRechargeRecord> findAllByInstitutionId(Long institutionId) {
        return jdbcTemplate.query(
                "SELECT id, institution_id, student_id, teaching_unit_id, lesson_count, note, recorded_by_teacher_id, "
                        + "created_at FROM course_recharge_record WHERE institution_id = ? ORDER BY id",
                ROW_MAPPER, institutionId);
    }
```

- [ ] **Step 4: Add the method to `CourseConsumptionRecordDao` and its JDBC impl**

In `backend/src/main/java/com/tuoguan/backend/course/dao/CourseConsumptionRecordDao.java`, add this line inside the interface, after `findAllByStudentId`:

```java
    List<CourseConsumptionRecord> findAllByInstitutionId(Long institutionId);
```

In `backend/src/main/java/com/tuoguan/backend/course/dao/JdbcCourseConsumptionRecordDao.java`, add this method right after the existing `findAllByStudentId` method (reuses the existing `ROW_MAPPER` field already declared at the top of the class):

```java
    @Override
    public List<CourseConsumptionRecord> findAllByInstitutionId(Long institutionId) {
        return jdbcTemplate.query(
                "SELECT id, institution_id, student_id, teaching_unit_id, consumption_date, price_snapshot, "
                        + "recorded_by_teacher_id, created_at FROM course_consumption_record "
                        + "WHERE institution_id = ? ORDER BY id",
                ROW_MAPPER, institutionId);
    }
```

- [ ] **Step 5: Run the test to verify it passes**

Run (with the colima Testcontainers env vars this repo's README documents — `DOCKER_HOST`, `DOCKER_API_VERSION=1.44`, `TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE`, `JAVA_TOOL_OPTIONS="-Dapi.version=1.44"` — already required for every integration test in this codebase, not specific to this plan):
`cd backend && mvn -q -Dtest=CourseRecordInstitutionQueriesTest test`
Expected: `Tests run: 1, Failures: 0, Errors: 0`.

- [ ] **Step 6: Commit**

```bash
git add backend/src/main/java/com/tuoguan/backend/course/dao/CourseRechargeRecordDao.java \
        backend/src/main/java/com/tuoguan/backend/course/dao/JdbcCourseRechargeRecordDao.java \
        backend/src/main/java/com/tuoguan/backend/course/dao/CourseConsumptionRecordDao.java \
        backend/src/main/java/com/tuoguan/backend/course/dao/JdbcCourseConsumptionRecordDao.java \
        backend/src/test/java/com/tuoguan/backend/course/dao/CourseRecordInstitutionQueriesTest.java
git commit -m "feat: add institution-wide bulk queries to course recharge/consumption DAOs"
```
