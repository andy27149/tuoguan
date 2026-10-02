package com.tuoguan.backend.unit.dao;

import com.tuoguan.backend.unit.domain.TeachingUnit;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

public interface TeachingUnitDao {

    Long insert(TeachingUnit teachingUnit);

    Optional<TeachingUnit> findById(Long id);

    List<TeachingUnit> findAllByTeacherId(Long teacherId);

    List<TeachingUnit> findAllByInstitutionId(Long institutionId);

    void deleteById(Long id);

    void updateNameAndTeacher(Long id, String name, Long teacherId);

    void reassignTeacher(Long id, Long teacherId);

    void reassignAllTeacher(Long oldTeacherId, Long newTeacherId);

    void setPrice(Long id, BigDecimal pricePerLesson);

    void setActive(Long id, boolean active);
}
