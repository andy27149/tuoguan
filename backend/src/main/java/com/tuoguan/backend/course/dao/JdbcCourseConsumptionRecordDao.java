package com.tuoguan.backend.course.dao;

import com.tuoguan.backend.course.domain.CourseConsumptionRecord;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.List;

@Repository
public class JdbcCourseConsumptionRecordDao implements CourseConsumptionRecordDao {

    private static final RowMapper<CourseConsumptionRecord> ROW_MAPPER = (rs, rowNum) -> new CourseConsumptionRecord(
            rs.getLong("id"),
            rs.getLong("institution_id"),
            rs.getLong("student_id"),
            rs.getLong("course_id"),
            rs.getDate("consumption_date").toLocalDate(),
            rs.getBigDecimal("price_snapshot"),
            rs.getLong("recorded_by_teacher_id"),
            rs.getTimestamp("created_at").toInstant());

    private final JdbcTemplate jdbcTemplate;

    public JdbcCourseConsumptionRecordDao(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public Long insert(CourseConsumptionRecord record) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(
                    "INSERT INTO course_consumption_record (institution_id, student_id, course_id, "
                            + "consumption_date, price_snapshot, recorded_by_teacher_id) VALUES (?, ?, ?, ?, ?, ?)",
                    Statement.RETURN_GENERATED_KEYS);
            ps.setLong(1, record.institutionId());
            ps.setLong(2, record.studentId());
            ps.setLong(3, record.courseId());
            ps.setDate(4, java.sql.Date.valueOf(record.consumptionDate()));
            ps.setBigDecimal(5, record.priceSnapshot());
            ps.setLong(6, record.recordedByTeacherId());
            return ps;
        }, keyHolder);
        return keyHolder.getKey().longValue();
    }

    @Override
    public List<CourseConsumptionRecord> findAllByStudentIdAndCourseIdAndDate(Long studentId, Long courseId,
                                                                               LocalDate date) {
        return jdbcTemplate.query(
                "SELECT id, institution_id, student_id, course_id, consumption_date, price_snapshot, "
                        + "recorded_by_teacher_id, created_at FROM course_consumption_record "
                        + "WHERE student_id = ? AND course_id = ? AND consumption_date = ? ORDER BY id",
                ROW_MAPPER, studentId, courseId, date);
    }

    @Override
    public List<CourseConsumptionRecord> findAllByStudentIdAndDateRange(Long studentId, LocalDate start,
                                                                         LocalDate end) {
        return jdbcTemplate.query(
                "SELECT id, institution_id, student_id, course_id, consumption_date, price_snapshot, "
                        + "recorded_by_teacher_id, created_at FROM course_consumption_record "
                        + "WHERE student_id = ? AND consumption_date BETWEEN ? AND ? ORDER BY consumption_date",
                ROW_MAPPER, studentId, start, end);
    }

    @Override
    public List<CourseConsumptionRecord> findAllByStudentId(Long studentId) {
        return jdbcTemplate.query(
                "SELECT id, institution_id, student_id, course_id, consumption_date, price_snapshot, "
                        + "recorded_by_teacher_id, created_at FROM course_consumption_record "
                        + "WHERE student_id = ? ORDER BY consumption_date DESC, id DESC",
                ROW_MAPPER, studentId);
    }

    @Override
    public BigDecimal sumByStudentId(Long studentId) {
        BigDecimal sum = jdbcTemplate.queryForObject(
                "SELECT COALESCE(SUM(price_snapshot), 0) FROM course_consumption_record WHERE student_id = ?",
                BigDecimal.class, studentId);
        return sum != null ? sum : BigDecimal.ZERO;
    }

    @Override
    public void deleteAllByStudentId(Long studentId) {
        jdbcTemplate.update("DELETE FROM course_consumption_record WHERE student_id = ?", studentId);
    }

    @Override
    public void deleteAllByCourseId(Long courseId) {
        jdbcTemplate.update("DELETE FROM course_consumption_record WHERE course_id = ?", courseId);
    }
}
