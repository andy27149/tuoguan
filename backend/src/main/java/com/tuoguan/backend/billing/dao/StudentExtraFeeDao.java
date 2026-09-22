package com.tuoguan.backend.billing.dao;

import com.tuoguan.backend.billing.domain.StudentExtraFee;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

public interface StudentExtraFeeDao {

    List<StudentExtraFee> findAllByStudentId(Long studentId);

    Optional<StudentExtraFee> findById(Long id);

    Long insert(Long institutionId, Long studentId, String name, BigDecimal pricePerLesson);

    void deleteById(Long id);

    void deleteAllByStudentId(Long studentId);
}
