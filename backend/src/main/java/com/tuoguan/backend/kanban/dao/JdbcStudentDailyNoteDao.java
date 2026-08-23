package com.tuoguan.backend.kanban.dao;

import com.tuoguan.backend.kanban.domain.StudentDailyNote;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public class JdbcStudentDailyNoteDao implements StudentDailyNoteDao {

    private static final RowMapper<StudentDailyNote> ROW_MAPPER = (rs, rowNum) -> new StudentDailyNote(
            rs.getLong("id"),
            rs.getLong("institution_id"),
            rs.getLong("class_room_id"),
            rs.getLong("student_id"),
            rs.getDate("note_date").toLocalDate(),
            rs.getInt("rating"),
            rs.getString("comment"),
            rs.getTimestamp("created_at").toInstant(),
            rs.getTimestamp("updated_at").toInstant());

    private final JdbcTemplate jdbcTemplate;

    public JdbcStudentDailyNoteDao(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public List<StudentDailyNote> findAllByClassRoomIdAndDate(Long classRoomId, LocalDate date) {
        return jdbcTemplate.query(
                "SELECT id, institution_id, class_room_id, student_id, note_date, rating, comment, created_at, "
                        + "updated_at FROM student_daily_note WHERE class_room_id = ? AND note_date = ? ORDER BY id",
                ROW_MAPPER, classRoomId, java.sql.Date.valueOf(date));
    }

    @Override
    public void upsertRating(Long institutionId, Long classRoomId, Long studentId, LocalDate date, int rating) {
        jdbcTemplate.update(
                "INSERT INTO student_daily_note (institution_id, class_room_id, student_id, note_date, rating, "
                        + "comment) VALUES (?, ?, ?, ?, ?, '') "
                        + "ON DUPLICATE KEY UPDATE rating = VALUES(rating)",
                institutionId, classRoomId, studentId, java.sql.Date.valueOf(date), rating);
    }

    @Override
    public void upsertComment(Long institutionId, Long classRoomId, Long studentId, LocalDate date, String comment) {
        jdbcTemplate.update(
                "INSERT INTO student_daily_note (institution_id, class_room_id, student_id, note_date, rating, "
                        + "comment) VALUES (?, ?, ?, ?, 0, ?) "
                        + "ON DUPLICATE KEY UPDATE comment = VALUES(comment)",
                institutionId, classRoomId, studentId, java.sql.Date.valueOf(date), comment);
    }
}
