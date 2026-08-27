package com.tuoguan.backend.kanban.dao;

import com.tuoguan.backend.kanban.domain.StudentArrivalCheckin;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public class JdbcStudentArrivalCheckinDao implements StudentArrivalCheckinDao {

    private static final RowMapper<StudentArrivalCheckin> ROW_MAPPER = (rs, rowNum) -> new StudentArrivalCheckin(
            rs.getLong("id"),
            rs.getLong("institution_id"),
            rs.getLong("class_room_id"),
            rs.getLong("student_id"),
            rs.getDate("checkin_date").toLocalDate(),
            rs.getString("arrived_at"),
            rs.getTimestamp("created_at").toInstant(),
            rs.getTimestamp("updated_at").toInstant());

    private final JdbcTemplate jdbcTemplate;

    public JdbcStudentArrivalCheckinDao(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public List<StudentArrivalCheckin> findAllByClassRoomIdAndDate(Long classRoomId, LocalDate date) {
        return jdbcTemplate.query(
                "SELECT id, institution_id, class_room_id, student_id, checkin_date, arrived_at, "
                        + "created_at, updated_at FROM student_arrival_checkin "
                        + "WHERE class_room_id = ? AND checkin_date = ? ORDER BY id",
                ROW_MAPPER, classRoomId, java.sql.Date.valueOf(date));
    }

    @Override
    public List<StudentArrivalCheckin> findAllByStudentIdAndDateRange(Long studentId, LocalDate start, LocalDate end) {
        return jdbcTemplate.query(
                "SELECT id, institution_id, class_room_id, student_id, checkin_date, arrived_at, "
                        + "created_at, updated_at FROM student_arrival_checkin "
                        + "WHERE student_id = ? AND checkin_date BETWEEN ? AND ? ORDER BY checkin_date",
                ROW_MAPPER, studentId, java.sql.Date.valueOf(start), java.sql.Date.valueOf(end));
    }

    @Override
    public void upsert(Long institutionId, Long classRoomId, Long studentId, LocalDate date, String arrivedAt) {
        jdbcTemplate.update(
                "INSERT INTO student_arrival_checkin (institution_id, class_room_id, student_id, checkin_date, "
                        + "arrived_at) VALUES (?, ?, ?, ?, ?) "
                        + "ON DUPLICATE KEY UPDATE arrived_at = VALUES(arrived_at)",
                institutionId, classRoomId, studentId, java.sql.Date.valueOf(date), arrivedAt);
    }

    @Override
    public void clear(Long studentId, LocalDate date) {
        jdbcTemplate.update(
                "DELETE FROM student_arrival_checkin WHERE student_id = ? AND checkin_date = ?",
                studentId, java.sql.Date.valueOf(date));
    }
}
