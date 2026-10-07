package com.tuoguan.backend.billing.dao;

import com.tuoguan.backend.billing.domain.StudentLeaveRecord;

import java.time.LocalDate;
import java.util.List;

public interface StudentLeaveRecordDao {

    List<StudentLeaveRecord> findAllByTeachingUnitIdAndDate(Long teachingUnitId, LocalDate date);

    List<StudentLeaveRecord> findAllByStudentIdAndDateRange(Long studentId, LocalDate start, LocalDate end);

    int countByStudentIdAndDateRange(Long studentId, LocalDate start, LocalDate end);

    void upsert(Long institutionId, Long studentId, Long teachingUnitId, LocalDate leaveDate, String reason);

    void deleteByStudentIdAndDate(Long studentId, LocalDate leaveDate);

    void deleteAllByTeachingUnitId(Long teachingUnitId);
}
