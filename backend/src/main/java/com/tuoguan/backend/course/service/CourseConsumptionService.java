package com.tuoguan.backend.course.service;

import com.tuoguan.backend.course.dao.CourseConsumptionRecordDao;
import com.tuoguan.backend.course.domain.CourseConsumptionRecord;
import com.tuoguan.backend.course.web.CourseNotEnrolledException;
import com.tuoguan.backend.course.web.CoursePriceNotConfiguredException;
import com.tuoguan.backend.course.web.CourseRosterEntry;
import com.tuoguan.backend.course.web.DuplicateConsumptionException;
import com.tuoguan.backend.roster.dao.StudentDao;
import com.tuoguan.backend.roster.domain.Student;
import com.tuoguan.backend.roster.web.NotFoundException;
import com.tuoguan.backend.unit.dao.StudentUnitEnrollmentDao;
import com.tuoguan.backend.unit.domain.StudentUnitEnrollment;
import com.tuoguan.backend.unit.domain.TeachingUnit;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;

@Service
public class CourseConsumptionService {

    private final CourseService courseService;
    private final StudentDao studentDao;
    private final StudentUnitEnrollmentDao enrollmentDao;
    private final CourseConsumptionRecordDao consumptionRecordDao;

    public CourseConsumptionService(CourseService courseService, StudentDao studentDao,
                                     StudentUnitEnrollmentDao enrollmentDao,
                                     CourseConsumptionRecordDao consumptionRecordDao) {
        this.courseService = courseService;
        this.studentDao = studentDao;
        this.enrollmentDao = enrollmentDao;
        this.consumptionRecordDao = consumptionRecordDao;
    }

    public List<CourseRosterEntry> listRosterForCourse(Long teacherId, Long courseId) {
        TeachingUnit teachingUnit = courseService.getOwnedByTeacher(teacherId, courseId);
        return enrollmentDao.findAllByTeachingUnitId(teachingUnit.id()).stream()
                .filter(StudentUnitEnrollment::active)
                .map(enrollment -> {
                    Student student = studentDao.findById(enrollment.studentId())
                            .orElseThrow(() -> new IllegalStateException("Student not found: "
                                    + enrollment.studentId()));
                    return CourseRosterEntry.from(student);
                })
                .toList();
    }

    public CourseConsumptionRecord recordConsumption(Long teacherId, Long courseId, Long studentId, LocalDate date,
                                                       boolean confirm) {
        TeachingUnit teachingUnit = courseService.getOwnedByTeacher(teacherId, courseId);
        if (teachingUnit.pricePerLesson() == null) {
            throw new CoursePriceNotConfiguredException("Course price not configured: " + courseId);
        }
        enrollmentDao.findByStudentIdAndTeachingUnitId(studentId, teachingUnit.id())
                .filter(StudentUnitEnrollment::active)
                .orElseThrow(() -> new CourseNotEnrolledException("Student not enrolled in course: " + studentId));

        List<CourseConsumptionRecord> existing = consumptionRecordDao
                .findAllByStudentIdAndTeachingUnitIdAndDate(studentId, teachingUnit.id(), date);
        if (!existing.isEmpty() && !confirm) {
            throw new DuplicateConsumptionException("Consumption already recorded for this date: " + date);
        }

        Long id = consumptionRecordDao.insert(new CourseConsumptionRecord(null, teachingUnit.institutionId(),
                studentId, teachingUnit.id(), date, teachingUnit.pricePerLesson(), teacherId, null));
        return consumptionRecordDao.findAllByStudentIdAndTeachingUnitIdAndDate(studentId, teachingUnit.id(), date)
                .stream()
                .filter(r -> r.id().equals(id))
                .findFirst()
                .orElseThrow(() -> new NotFoundException("Consumption record not found: " + id));
    }

    public List<CourseConsumptionRecord> recordBatchConsumption(Long teacherId, Long courseId, LocalDate date,
                                                                  List<Long> presentStudentIds) {
        TeachingUnit teachingUnit = courseService.getOwnedByTeacher(teacherId, courseId);
        if (teachingUnit.pricePerLesson() == null) {
            throw new CoursePriceNotConfiguredException("Course price not configured: " + courseId);
        }

        List<Long> enrolledStudentIds = enrollmentDao.findAllByTeachingUnitId(teachingUnit.id()).stream()
                .filter(StudentUnitEnrollment::active)
                .map(StudentUnitEnrollment::studentId)
                .toList();

        return presentStudentIds.stream()
                .filter(enrolledStudentIds::contains)
                .filter(studentId -> consumptionRecordDao
                        .findAllByStudentIdAndTeachingUnitIdAndDate(studentId, teachingUnit.id(), date).isEmpty())
                .map(studentId -> {
                    Long id = consumptionRecordDao.insert(new CourseConsumptionRecord(null,
                            teachingUnit.institutionId(), studentId, teachingUnit.id(), date,
                            teachingUnit.pricePerLesson(), teacherId, null));
                    return consumptionRecordDao
                            .findAllByStudentIdAndTeachingUnitIdAndDate(studentId, teachingUnit.id(), date)
                            .stream()
                            .filter(r -> r.id().equals(id))
                            .findFirst()
                            .orElseThrow(() -> new NotFoundException("Consumption record not found: " + id));
                })
                .toList();
    }
}
