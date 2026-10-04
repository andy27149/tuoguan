package com.tuoguan.backend.billing.dao;

import com.tuoguan.backend.billing.domain.MonthlyBill;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.List;
import java.util.Optional;

public interface MonthlyBillDao {

    Optional<MonthlyBill> findById(Long id);

    Optional<MonthlyBill> findByStudentIdAndYearMonth(Long studentId, YearMonth yearMonth);

    List<MonthlyBill> findAllByTeachingUnitIdAndYearMonth(Long teachingUnitId, YearMonth yearMonth);

    List<MonthlyBill> findAllByTeachingUnitId(Long teachingUnitId);

    List<MonthlyBill> findAllByStudentId(Long studentId);

    Long upsert(Long institutionId, Long studentId, Long teachingUnitId, YearMonth yearMonth, int totalWeekdays,
                int leaveDays, int attendanceDays, BigDecimal tuitionAmount, BigDecimal mealAmount,
                BigDecimal extraFeeTotal, BigDecimal totalAmount);

    void deleteAllByTeachingUnitId(Long teachingUnitId);

    void setPaid(Long billId, boolean isPaid);
}
