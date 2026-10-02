package com.tuoguan.backend.billing.dao;

import com.tuoguan.backend.billing.domain.ClassBillingRate;

import java.math.BigDecimal;
import java.util.Optional;

public interface ClassBillingRateDao {

    Optional<ClassBillingRate> findByTeachingUnitId(Long teachingUnitId);

    void upsert(Long institutionId, Long teachingUnitId, BigDecimal tuitionRatePerMonth, BigDecimal mealRatePerDay);

    void deleteAllByTeachingUnitId(Long teachingUnitId);
}
