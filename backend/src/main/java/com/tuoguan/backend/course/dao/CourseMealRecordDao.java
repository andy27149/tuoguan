package com.tuoguan.backend.course.dao;

import com.tuoguan.backend.course.domain.CourseMealRecord;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface CourseMealRecordDao {

    Long insert(CourseMealRecord record);

    Optional<CourseMealRecord> findByStudentIdAndDate(Long studentId, LocalDate date);

    List<CourseMealRecord> findAllByStudentIdAndDateRange(Long studentId, LocalDate start, LocalDate end);
}
