package com.tuoguan.backend.kanban.dao;

import com.tuoguan.backend.kanban.domain.StudentMealRecord;

import java.time.LocalDate;
import java.util.List;

public interface StudentMealRecordDao {

    List<StudentMealRecord> findAllByTeachingUnitIdAndDate(Long teachingUnitId, LocalDate date);

    List<StudentMealRecord> findAllByStudentIdAndDateRange(Long studentId, LocalDate start, LocalDate end);

    void upsert(Long institutionId, Long teachingUnitId, Long studentId, LocalDate date);

    void clear(Long studentId, LocalDate date);

    void deleteAllByTeachingUnitId(Long teachingUnitId);
}
