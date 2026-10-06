package com.tuoguan.backend.kanban.dao;

import com.tuoguan.backend.kanban.domain.StudentMealRecord;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public class JdbcStudentMealRecordDao implements StudentMealRecordDao {

    private static final RowMapper<StudentMealRecord> ROW_MAPPER = (rs, rowNum) -> new StudentMealRecord(
            rs.getLong("id"),
            rs.getLong("institution_id"),
            rs.getLong("teaching_unit_id"),
            rs.getLong("student_id"),
            rs.getDate("meal_date").toLocalDate(),
            rs.getTimestamp("created_at").toInstant());

    private final JdbcTemplate jdbcTemplate;

    public JdbcStudentMealRecordDao(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public List<StudentMealRecord> findAllByTeachingUnitIdAndDate(Long teachingUnitId, LocalDate date) {
        return jdbcTemplate.query(
                "SELECT id, institution_id, teaching_unit_id, student_id, meal_date, created_at "
                        + "FROM student_meal_record WHERE teaching_unit_id = ? AND meal_date = ? ORDER BY id",
                ROW_MAPPER, teachingUnitId, java.sql.Date.valueOf(date));
    }

    @Override
    public List<StudentMealRecord> findAllByStudentIdAndDateRange(Long studentId, LocalDate start, LocalDate end) {
        return jdbcTemplate.query(
                "SELECT id, institution_id, teaching_unit_id, student_id, meal_date, created_at "
                        + "FROM student_meal_record WHERE student_id = ? AND meal_date BETWEEN ? AND ? "
                        + "ORDER BY meal_date",
                ROW_MAPPER, studentId, java.sql.Date.valueOf(start), java.sql.Date.valueOf(end));
    }

    @Override
    public void upsert(Long institutionId, Long teachingUnitId, Long studentId, LocalDate date) {
        jdbcTemplate.update(
                "INSERT INTO student_meal_record (institution_id, teaching_unit_id, student_id, meal_date) "
                        + "VALUES (?, ?, ?, ?) ON DUPLICATE KEY UPDATE meal_date = VALUES(meal_date)",
                institutionId, teachingUnitId, studentId, java.sql.Date.valueOf(date));
    }

    @Override
    public void clear(Long studentId, LocalDate date) {
        jdbcTemplate.update(
                "DELETE FROM student_meal_record WHERE student_id = ? AND meal_date = ?",
                studentId, java.sql.Date.valueOf(date));
    }

    @Override
    public void deleteAllByTeachingUnitId(Long teachingUnitId) {
        jdbcTemplate.update("DELETE FROM student_meal_record WHERE teaching_unit_id = ?", teachingUnitId);
    }
}
