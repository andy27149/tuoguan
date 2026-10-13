package com.tuoguan.backend.billing.dao;

import java.time.LocalDate;
import java.util.List;

public interface MonthlyBillExtraFeeLineDateDao {

    List<LocalDate> findAllByLineId(Long extraFeeLineId);

    void insert(Long extraFeeLineId, LocalDate consumptionDate);
}
