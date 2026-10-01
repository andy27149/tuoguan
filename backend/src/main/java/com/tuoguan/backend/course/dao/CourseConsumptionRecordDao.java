package com.tuoguan.backend.course.dao;

import com.tuoguan.backend.course.domain.CourseConsumptionRecord;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public interface CourseConsumptionRecordDao {

    Long insert(CourseConsumptionRecord record);

    List<CourseConsumptionRecord> findAllByStudentIdAndCourseIdAndDate(Long studentId, Long courseId, LocalDate date);

    List<CourseConsumptionRecord> findAllByStudentIdAndDateRange(Long studentId, LocalDate start, LocalDate end);

    List<CourseConsumptionRecord> findAllByStudentId(Long studentId);

    BigDecimal sumByStudentId(Long studentId);

    void deleteAllByStudentId(Long studentId);

    void deleteAllByCourseId(Long courseId);
}
