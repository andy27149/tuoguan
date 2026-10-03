package com.tuoguan.backend.course.service;

import com.tuoguan.backend.auth.dao.TeacherDao;
import com.tuoguan.backend.auth.domain.Teacher;
import com.tuoguan.backend.course.dao.CourseConsumptionRecordDao;
import com.tuoguan.backend.course.dao.CourseRechargeRecordDao;
import com.tuoguan.backend.course.domain.CourseConsumptionRecord;
import com.tuoguan.backend.course.domain.CourseRechargeRecord;
import com.tuoguan.backend.course.web.ConsumptionRecordResponse;
import com.tuoguan.backend.course.web.CourseActivityRow;
import com.tuoguan.backend.course.web.CourseBalanceRow;
import com.tuoguan.backend.course.web.RechargeNotAllowedException;
import com.tuoguan.backend.course.web.RechargeRecordResponse;
import com.tuoguan.backend.course.web.StudentCourseStatement;
import com.tuoguan.backend.roster.dao.StudentDao;
import com.tuoguan.backend.roster.domain.Student;
import com.tuoguan.backend.roster.web.NotFoundException;
import com.tuoguan.backend.unit.dao.TeachingUnitDao;
import com.tuoguan.backend.unit.domain.BillingMode;
import com.tuoguan.backend.unit.domain.TeachingUnit;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class CourseAccountService {

    private final StudentDao studentDao;
    private final TeachingUnitDao teachingUnitDao;
    private final TeacherDao teacherDao;
    private final CourseRechargeRecordDao rechargeRecordDao;
    private final CourseConsumptionRecordDao consumptionRecordDao;

    public CourseAccountService(StudentDao studentDao, TeachingUnitDao teachingUnitDao, TeacherDao teacherDao,
                                 CourseRechargeRecordDao rechargeRecordDao,
                                 CourseConsumptionRecordDao consumptionRecordDao) {
        this.studentDao = studentDao;
        this.teachingUnitDao = teachingUnitDao;
        this.teacherDao = teacherDao;
        this.rechargeRecordDao = rechargeRecordDao;
        this.consumptionRecordDao = consumptionRecordDao;
    }

    public CourseRechargeRecord recharge(Long institutionId, Long studentId, Long recordedByTeacherId,
                                          Long courseId, Integer lessonCount, String note) {
        Student student = requireStudentInInstitution(institutionId, studentId);
        if (student.teachingUnitId() != null) {
            throw new RechargeNotAllowedException("Student is enrolled in a class room: " + studentId);
        }
        requireCourseInInstitution(institutionId, courseId);
        Long id = rechargeRecordDao.insert(new CourseRechargeRecord(null, institutionId, studentId, courseId,
                lessonCount, note, recordedByTeacherId, null));
        return rechargeRecordDao.findAllByStudentId(studentId).stream()
                .filter(r -> r.id().equals(id))
                .findFirst()
                .orElseThrow(() -> new NotFoundException("Recharge record not found: " + id));
    }

    public StudentCourseStatement getStatement(Long institutionId, Long studentId) {
        requireStudentInInstitution(institutionId, studentId);
        List<CourseRechargeRecord> recharges = rechargeRecordDao.findAllByStudentId(studentId);
        List<CourseConsumptionRecord> consumptions = consumptionRecordDao.findAllByStudentId(studentId);

        Map<Long, String> courseNames = new HashMap<>();
        Map<Long, String> teacherNames = new HashMap<>();

        Set<Long> courseIds = new LinkedHashSet<>();
        recharges.forEach(r -> courseIds.add(r.teachingUnitId()));
        consumptions.forEach(c -> courseIds.add(c.teachingUnitId()));

        List<CourseBalanceRow> balances = new ArrayList<>();
        for (Long courseId : courseIds) {
            String courseName = resolveCourseName(courseNames, courseId);
            int lessonsRecharged = recharges.stream()
                    .filter(r -> r.teachingUnitId().equals(courseId))
                    .mapToInt(CourseRechargeRecord::lessonCount)
                    .sum();
            int lessonsConsumed = (int) consumptions.stream()
                    .filter(c -> c.teachingUnitId().equals(courseId))
                    .count();
            balances.add(new CourseBalanceRow(courseId, courseName, lessonsRecharged, lessonsConsumed,
                    lessonsRecharged - lessonsConsumed));
        }

        List<RechargeRecordResponse> rechargeResponses = recharges.stream()
                .map(r -> RechargeRecordResponse.from(r, resolveCourseName(courseNames, r.teachingUnitId())))
                .toList();
        List<ConsumptionRecordResponse> consumptionResponses = consumptions.stream()
                .map(c -> ConsumptionRecordResponse.from(c, resolveCourseName(courseNames, c.teachingUnitId()),
                        resolveTeacherName(teacherNames, c.recordedByTeacherId())))
                .toList();

        return new StudentCourseStatement(balances, rechargeResponses, consumptionResponses);
    }

    // 供托管班学生（同时报名课外课）的家长分享页使用：只给课程名+最近消课日期，不带
    // 充值/余额——这类学生的课外课费用走托管月度账单附加费，从不预充值，余额概念不成立。
    public List<CourseActivityRow> getCourseActivity(Long institutionId, Long studentId) {
        requireStudentInInstitution(institutionId, studentId);
        List<CourseConsumptionRecord> consumptions = consumptionRecordDao.findAllByStudentId(studentId);

        Map<Long, List<LocalDate>> datesByCourse = new LinkedHashMap<>();
        for (CourseConsumptionRecord record : consumptions) {
            datesByCourse.computeIfAbsent(record.teachingUnitId(), id -> new ArrayList<>())
                    .add(record.consumptionDate());
        }

        Map<Long, String> courseNames = new HashMap<>();
        List<CourseActivityRow> rows = new ArrayList<>();
        for (Map.Entry<Long, List<LocalDate>> entry : datesByCourse.entrySet()) {
            List<LocalDate> recentDates = entry.getValue().stream()
                    .sorted(Comparator.reverseOrder())
                    .limit(5)
                    .toList();
            rows.add(new CourseActivityRow(entry.getKey(), resolveCourseName(courseNames, entry.getKey()),
                    recentDates));
        }
        return rows;
    }

    private String resolveCourseName(Map<Long, String> cache, Long courseId) {
        return cache.computeIfAbsent(courseId,
                id -> teachingUnitDao.findById(id).map(TeachingUnit::name).orElse(null));
    }

    private String resolveTeacherName(Map<Long, String> cache, Long teacherId) {
        return cache.computeIfAbsent(teacherId,
                id -> teacherDao.findById(id).map(Teacher::name).orElse(null));
    }

    private Student requireStudentInInstitution(Long institutionId, Long studentId) {
        Student student = studentDao.findById(studentId)
                .orElseThrow(() -> new NotFoundException("Student not found: " + studentId));
        if (!student.institutionId().equals(institutionId)) {
            throw new NotFoundException("Student not found: " + studentId);
        }
        return student;
    }

    private TeachingUnit requireCourseInInstitution(Long institutionId, Long courseId) {
        TeachingUnit course = teachingUnitDao.findById(courseId)
                .filter(c -> c.billingMode() == BillingMode.LESSON_COUNT)
                .orElseThrow(() -> new NotFoundException("Course not found: " + courseId));
        if (!course.institutionId().equals(institutionId)) {
            throw new NotFoundException("Course not found: " + courseId);
        }
        return course;
    }
}
