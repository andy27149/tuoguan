package com.tuoguan.backend.kanban.web;

import com.tuoguan.backend.kanban.domain.StudentDailyNote;

public record StudentDailyNoteResponse(Long studentId, int rating, String comment) {

    public static StudentDailyNoteResponse from(StudentDailyNote note) {
        return new StudentDailyNoteResponse(note.studentId(), note.rating(), note.comment());
    }
}
