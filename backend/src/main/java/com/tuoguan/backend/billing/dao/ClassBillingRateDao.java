package com.tuoguan.backend.billing.dao;

import com.tuoguan.backend.billing.domain.ClassBillingRate;

import java.math.BigDecimal;
import java.util.Optional;

public interface ClassBillingRateDao {

    Optional<ClassBillingRate> findByClassRoomId(Long classRoomId);

    void upsert(Long institutionId, Long classRoomId, BigDecimal tuitionRatePerMonth, BigDecimal mealRatePerDay);

    void deleteAllByClassRoomId(Long classRoomId);
}
