package com.tuoguan.backend.billing.dao;

import com.tuoguan.backend.billing.domain.MonthlyBillLeaveLine;

import java.time.LocalDate;
import java.util.List;

public interface MonthlyBillLeaveLineDao {

    List<MonthlyBillLeaveLine> findAllByBillId(Long monthlyBillId);

    void insert(Long monthlyBillId, LocalDate leaveDate, String reason);

    void deleteAllByBillId(Long monthlyBillId);
}
