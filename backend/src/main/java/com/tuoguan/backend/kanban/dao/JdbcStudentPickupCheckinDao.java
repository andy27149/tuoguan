package com.tuoguan.backend.kanban.dao;

import com.tuoguan.backend.kanban.domain.StudentPickupCheckin;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public class JdbcStudentPickupCheckinDao implements StudentPickupCheckinDao {

    private static final RowMapper<StudentPickupCheckin> ROW_MAPPER = (rs, rowNum) -> new StudentPickupCheckin(
            rs.getLong("id"),
            rs.getLong("institution_id"),
            rs.getLong("class_room_id"),
            rs.getLong("student_id"),
            rs.getDate("checkin_date").toLocalDate(),
            rs.getString("picked_up_by"),
            rs.getString("picked_up_at"),
            rs.getTimestamp("created_at").toInstant(),
            rs.getTimestamp("updated_at").toInstant());

    private final JdbcTemplate jdbcTemplate;

    public JdbcStudentPickupCheckinDao(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public List<StudentPickupCheckin> findAllByClassRoomIdAndDate(Long classRoomId, LocalDate date) {
        return jdbcTemplate.query(
                "SELECT id, institution_id, class_room_id, student_id, checkin_date, picked_up_by, picked_up_at, "
                        + "created_at, updated_at FROM student_pickup_checkin "
                        + "WHERE class_room_id = ? AND checkin_date = ? ORDER BY id",
                ROW_MAPPER, classRoomId, java.sql.Date.valueOf(date));
    }

    @Override
    public List<StudentPickupCheckin> findAllByStudentIdAndDateRange(Long studentId, LocalDate start, LocalDate end) {
        return jdbcTemplate.query(
                "SELECT id, institution_id, class_room_id, student_id, checkin_date, picked_up_by, picked_up_at, "
                        + "created_at, updated_at FROM student_pickup_checkin "
                        + "WHERE student_id = ? AND checkin_date BETWEEN ? AND ? ORDER BY checkin_date",
                ROW_MAPPER, studentId, java.sql.Date.valueOf(start), java.sql.Date.valueOf(end));
    }

    @Override
    public void upsert(Long institutionId, Long classRoomId, Long studentId, LocalDate date, String pickedUpBy,
                        String pickedUpAt) {
        jdbcTemplate.update(
                "INSERT INTO student_pickup_checkin (institution_id, class_room_id, student_id, checkin_date, "
                        + "picked_up_by, picked_up_at) VALUES (?, ?, ?, ?, ?, ?) "
                        + "ON DUPLICATE KEY UPDATE picked_up_by = VALUES(picked_up_by), "
                        + "picked_up_at = VALUES(picked_up_at)",
                institutionId, classRoomId, studentId, java.sql.Date.valueOf(date), pickedUpBy, pickedUpAt);
    }
}
