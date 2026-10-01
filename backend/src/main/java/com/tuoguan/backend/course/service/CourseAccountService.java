package com.tuoguan.backend.course.service;

import com.tuoguan.backend.auth.dao.TeacherDao;
import com.tuoguan.backend.auth.domain.Teacher;
import com.tuoguan.backend.course.dao.CourseConsumptionRecordDao;
import com.tuoguan.backend.course.dao.CourseDao;
import com.tuoguan.backend.course.dao.CourseRechargeRecordDao;
import com.tuoguan.backend.course.domain.Course;
import com.tuoguan.backend.course.domain.CourseConsumptionRecord;
import com.tuoguan.backend.course.domain.CourseRechargeRecord;
import com.tuoguan.backend.course.web.ConsumptionRecordResponse;
import com.tuoguan.backend.course.web.CourseBalanceRow;
import com.tuoguan.backend.course.web.RechargeNotAllowedException;
import com.tuoguan.backend.course.web.RechargeRecordResponse;
import com.tuoguan.backend.course.web.StudentCourseStatement;
import com.tuoguan.backend.roster.dao.StudentDao;
import com.tuoguan.backend.roster.domain.Student;
import com.tuoguan.backend.roster.web.NotFoundException;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class CourseAccountService {

    private final StudentDao studentDao;
    private final CourseDao courseDao;
    private final TeacherDao teacherDao;
    private final CourseRechargeRecordDao rechargeRecordDao;
    private final CourseConsumptionRecordDao consumptionRecordDao;

    public CourseAccountService(StudentDao studentDao, CourseDao courseDao, TeacherDao teacherDao,
                                 CourseRechargeRecordDao rechargeRecordDao,
                                 CourseConsumptionRecordDao consumptionRecordDao) {
        this.studentDao = studentDao;
        this.courseDao = courseDao;
        this.teacherDao = teacherDao;
        this.rechargeRecordDao = rechargeRecordDao;
        this.consumptionRecordDao = consumptionRecordDao;
    }

    public CourseRechargeRecord recharge(Long institutionId, Long studentId, Long recordedByTeacherId,
                                          Long courseId, Integer lessonCount, String note) {
        Student student = requireStudentInInstitution(institutionId, studentId);
        if (student.classRoomId() != null) {
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
        recharges.forEach(r -> courseIds.add(r.courseId()));
        consumptions.forEach(c -> courseIds.add(c.courseId()));

        List<CourseBalanceRow> balances = new ArrayList<>();
        for (Long courseId : courseIds) {
            String courseName = resolveCourseName(courseNames, courseId);
            int lessonsRecharged = recharges.stream()
                    .filter(r -> r.courseId().equals(courseId))
                    .mapToInt(CourseRechargeRecord::lessonCount)
                    .sum();
            int lessonsConsumed = (int) consumptions.stream()
                    .filter(c -> c.courseId().equals(courseId))
                    .count();
            balances.add(new CourseBalanceRow(courseId, courseName, lessonsRecharged, lessonsConsumed,
                    lessonsRecharged - lessonsConsumed));
        }

        List<RechargeRecordResponse> rechargeResponses = recharges.stream()
                .map(r -> RechargeRecordResponse.from(r, resolveCourseName(courseNames, r.courseId())))
                .toList();
        List<ConsumptionRecordResponse> consumptionResponses = consumptions.stream()
                .map(c -> ConsumptionRecordResponse.from(c, resolveCourseName(courseNames, c.courseId()),
                        resolveTeacherName(teacherNames, c.recordedByTeacherId())))
                .toList();

        return new StudentCourseStatement(balances, rechargeResponses, consumptionResponses);
    }

    private String resolveCourseName(Map<Long, String> cache, Long courseId) {
        return cache.computeIfAbsent(courseId,
                id -> courseDao.findById(id).map(Course::name).orElse(null));
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

    private Course requireCourseInInstitution(Long institutionId, Long courseId) {
        Course course = courseDao.findById(courseId)
                .orElseThrow(() -> new NotFoundException("Course not found: " + courseId));
        if (!course.institutionId().equals(institutionId)) {
            throw new NotFoundException("Course not found: " + courseId);
        }
        return course;
    }
}
