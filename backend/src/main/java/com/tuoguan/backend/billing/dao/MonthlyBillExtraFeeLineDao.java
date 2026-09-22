package com.tuoguan.backend.billing.dao;

import com.tuoguan.backend.billing.domain.MonthlyBillExtraFeeLine;

import java.math.BigDecimal;
import java.util.List;

public interface MonthlyBillExtraFeeLineDao {

    List<MonthlyBillExtraFeeLine> findAllByBillId(Long monthlyBillId);

    void insert(Long monthlyBillId, String name, BigDecimal pricePerLesson, int lessonCount, BigDecimal amount);

    void deleteAllByBillId(Long monthlyBillId);
}
