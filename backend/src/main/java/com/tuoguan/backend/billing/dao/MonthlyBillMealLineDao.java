package com.tuoguan.backend.billing.dao;

import com.tuoguan.backend.billing.domain.MonthlyBillMealLine;

import java.time.LocalDate;
import java.util.List;

public interface MonthlyBillMealLineDao {

    List<MonthlyBillMealLine> findAllByBillId(Long monthlyBillId);

    void insert(Long monthlyBillId, LocalDate mealDate);

    void deleteAllByBillId(Long monthlyBillId);
}
