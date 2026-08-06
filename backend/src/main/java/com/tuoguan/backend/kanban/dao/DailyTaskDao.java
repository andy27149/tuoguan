package com.tuoguan.backend.kanban.dao;

import com.tuoguan.backend.kanban.domain.DailyTask;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface DailyTaskDao {

    Long insert(DailyTask dailyTask);

    Optional<DailyTask> findById(Long id);

    List<DailyTask> findAllByClassRoomIdAndDate(Long classRoomId, LocalDate date);

    void updateCompleted(Long id, boolean completed);

    void deleteById(Long id);
}
