package com.tuoguan.backend.kanban.dao;

import com.tuoguan.backend.kanban.domain.ClassDismissal;

import java.time.LocalDate;
import java.util.Optional;

public interface ClassDismissalDao {

    Long insert(ClassDismissal classDismissal);

    Optional<ClassDismissal> findByTeachingUnitIdAndDate(Long teachingUnitId, LocalDate date);

    void deleteByTeachingUnitIdAndDate(Long teachingUnitId, LocalDate date);

    void deleteAllByTeachingUnitId(Long teachingUnitId);
}
