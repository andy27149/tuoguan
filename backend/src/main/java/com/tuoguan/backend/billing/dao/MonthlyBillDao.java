package com.tuoguan.backend.billing.dao;

import com.tuoguan.backend.billing.domain.MonthlyBill;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.List;
import java.util.Optional;

public interface MonthlyBillDao {

    Optional<MonthlyBill> findById(Long id);

    Optional<MonthlyBill> findByStudentIdAndYearMonth(Long studentId, YearMonth yearMonth);

    List<MonthlyBill> findAllByClassRoomIdAndYearMonth(Long classRoomId, YearMonth yearMonth);

    List<MonthlyBill> findAllByClassRoomId(Long classRoomId);

    Long upsert(Long institutionId, Long studentId, Long classRoomId, YearMonth yearMonth, int totalWeekdays,
                int leaveDays, int attendanceDays, BigDecimal tuitionAmount, BigDecimal mealAmount,
                BigDecimal extraFeeTotal, BigDecimal totalAmount);

    void deleteAllByClassRoomId(Long classRoomId);

    void setPaid(Long billId, boolean isPaid);
}
