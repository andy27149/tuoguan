package com.tuoguan.backend.monthlyquickbill.dao;

import com.tuoguan.backend.monthlyquickbill.domain.MonthlyQuickBillRecord;
import com.tuoguan.backend.monthlyquickbill.domain.QuickBillRecordType;

import java.time.LocalDate;
import java.util.List;

public interface MonthlyQuickBillRecordDao {

    void insert(Long institutionId, Long studentId, QuickBillRecordType recordType, LocalDate recordDate);

    List<MonthlyQuickBillRecord> findAllByStudentIdAndDateRange(Long studentId, LocalDate start, LocalDate end);

    void deleteAllByStudentIdAndDateRange(Long studentId, LocalDate start, LocalDate end);
}
