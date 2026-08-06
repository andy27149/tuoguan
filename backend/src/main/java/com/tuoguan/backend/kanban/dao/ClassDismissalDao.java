package com.tuoguan.backend.kanban.dao;

import com.tuoguan.backend.kanban.domain.ClassDismissal;

import java.time.LocalDate;
import java.util.Optional;

public interface ClassDismissalDao {

    Long insert(ClassDismissal classDismissal);

    Optional<ClassDismissal> findByClassRoomIdAndDate(Long classRoomId, LocalDate date);

    void deleteByClassRoomIdAndDate(Long classRoomId, LocalDate date);
}
