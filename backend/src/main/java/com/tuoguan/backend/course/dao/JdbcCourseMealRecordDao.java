package com.tuoguan.backend.course.dao;

import com.tuoguan.backend.course.domain.CourseMealRecord;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public class JdbcCourseMealRecordDao implements CourseMealRecordDao {

    private static final RowMapper<CourseMealRecord> ROW_MAPPER = (rs, rowNum) -> new CourseMealRecord(
            rs.getLong("id"),
            rs.getLong("institution_id"),
            rs.getLong("student_id"),
            rs.getDate("meal_date").toLocalDate(),
            rs.getLong("recorded_by_teacher_id"),
            rs.getTimestamp("created_at").toInstant());

    private static final String SELECT_COLUMNS =
            "id, institution_id, student_id, meal_date, recorded_by_teacher_id, created_at";

    private final JdbcTemplate jdbcTemplate;

    public JdbcCourseMealRecordDao(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public Long insert(CourseMealRecord record) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(
                    "INSERT INTO course_meal_record (institution_id, student_id, meal_date, recorded_by_teacher_id) "
                            + "VALUES (?, ?, ?, ?)",
                    Statement.RETURN_GENERATED_KEYS);
            ps.setLong(1, record.institutionId());
            ps.setLong(2, record.studentId());
            ps.setDate(3, java.sql.Date.valueOf(record.mealDate()));
            ps.setLong(4, record.recordedByTeacherId());
            return ps;
        }, keyHolder);
        return keyHolder.getKey().longValue();
    }

    @Override
    public Optional<CourseMealRecord> findByStudentIdAndDate(Long studentId, LocalDate date) {
        List<CourseMealRecord> results = jdbcTemplate.query(
                "SELECT " + SELECT_COLUMNS + " FROM course_meal_record WHERE student_id = ? AND meal_date = ?",
                ROW_MAPPER, studentId, java.sql.Date.valueOf(date));
        return results.stream().findFirst();
    }

    @Override
    public List<CourseMealRecord> findAllByStudentIdAndDateRange(Long studentId, LocalDate start, LocalDate end) {
        return jdbcTemplate.query(
                "SELECT " + SELECT_COLUMNS + " FROM course_meal_record WHERE student_id = ? "
                        + "AND meal_date BETWEEN ? AND ? ORDER BY meal_date",
                ROW_MAPPER, studentId, java.sql.Date.valueOf(start), java.sql.Date.valueOf(end));
    }

    @Override
    public void delete(Long studentId, LocalDate date) {
        jdbcTemplate.update("DELETE FROM course_meal_record WHERE student_id = ? AND meal_date = ?",
                studentId, java.sql.Date.valueOf(date));
    }
}
