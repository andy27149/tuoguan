package com.tuoguan.backend.kanban.dao;

import com.tuoguan.backend.kanban.domain.StudentDailyNote;

import java.time.LocalDate;
import java.util.List;

public interface StudentDailyNoteDao {

    List<StudentDailyNote> findAllByClassRoomIdAndDate(Long classRoomId, LocalDate date);

    void upsertRating(Long institutionId, Long classRoomId, Long studentId, LocalDate date, int rating);

    void upsertComment(Long institutionId, Long classRoomId, Long studentId, LocalDate date, String comment);
}
