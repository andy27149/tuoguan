package com.tuoguan.backend.kanban.dao;

import com.tuoguan.backend.kanban.domain.StudentDailyNote;

import java.time.LocalDate;
import java.util.List;

public interface StudentDailyNoteDao {

    List<StudentDailyNote> findAllByTeachingUnitIdAndDate(Long teachingUnitId, LocalDate date);

    List<StudentDailyNote> findAllByStudentIdAndDateRange(Long studentId, LocalDate start, LocalDate end);

    void upsertRating(Long institutionId, Long teachingUnitId, Long studentId, LocalDate date, int rating);

    void upsertComment(Long institutionId, Long teachingUnitId, Long studentId, LocalDate date, String comment);

    void deleteAllByTeachingUnitId(Long teachingUnitId);
}
