package com.tuoguan.backend.course.dao;

import com.tuoguan.backend.course.domain.Course;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Types;
import java.util.List;
import java.util.Optional;

@Repository
public class JdbcCourseDao implements CourseDao {

    private static final RowMapper<Course> ROW_MAPPER = (rs, rowNum) -> new Course(
            rs.getLong("id"),
            rs.getLong("institution_id"),
            rs.getLong("teacher_id"),
            rs.getString("name"),
            rs.getBigDecimal("price_per_lesson"),
            rs.getInt("lesson_duration_minutes"),
            rs.getBoolean("active"),
            rs.getTimestamp("created_at").toInstant());

    private final JdbcTemplate jdbcTemplate;

    public JdbcCourseDao(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public Long insert(Course course) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(
                    "INSERT INTO course (institution_id, teacher_id, name, price_per_lesson, "
                            + "lesson_duration_minutes, active) VALUES (?, ?, ?, ?, ?, ?)",
                    Statement.RETURN_GENERATED_KEYS);
            ps.setLong(1, course.institutionId());
            ps.setLong(2, course.teacherId());
            ps.setString(3, course.name());
            if (course.pricePerLesson() != null) {
                ps.setBigDecimal(4, course.pricePerLesson());
            } else {
                ps.setNull(4, Types.DECIMAL);
            }
            ps.setInt(5, course.lessonDurationMinutes());
            ps.setBoolean(6, course.active());
            return ps;
        }, keyHolder);
        return keyHolder.getKey().longValue();
    }

    @Override
    public Optional<Course> findById(Long id) {
        List<Course> results = jdbcTemplate.query(
                "SELECT id, institution_id, teacher_id, name, price_per_lesson, lesson_duration_minutes, active, "
                        + "created_at FROM course WHERE id = ?",
                ROW_MAPPER, id);
        return results.stream().findFirst();
    }

    @Override
    public List<Course> findAllByTeacherId(Long teacherId) {
        return jdbcTemplate.query(
                "SELECT id, institution_id, teacher_id, name, price_per_lesson, lesson_duration_minutes, active, "
                        + "created_at FROM course WHERE teacher_id = ? ORDER BY id",
                ROW_MAPPER, teacherId);
    }

    @Override
    public List<Course> findAllByInstitutionId(Long institutionId) {
        return jdbcTemplate.query(
                "SELECT id, institution_id, teacher_id, name, price_per_lesson, lesson_duration_minutes, active, "
                        + "created_at FROM course WHERE institution_id = ? ORDER BY id",
                ROW_MAPPER, institutionId);
    }

    @Override
    public void setPrice(Long id, BigDecimal pricePerLesson) {
        jdbcTemplate.update("UPDATE course SET price_per_lesson = ? WHERE id = ?", pricePerLesson, id);
    }

    @Override
    public void setActive(Long id, boolean active) {
        jdbcTemplate.update("UPDATE course SET active = ? WHERE id = ?", active, id);
    }

    @Override
    public void reassignTeacher(Long courseId, Long teacherId) {
        jdbcTemplate.update("UPDATE course SET teacher_id = ? WHERE id = ?", teacherId, courseId);
    }

    @Override
    public void reassignAllTeacher(Long oldTeacherId, Long newTeacherId) {
        jdbcTemplate.update("UPDATE course SET teacher_id = ? WHERE teacher_id = ?", newTeacherId, oldTeacherId);
    }

    @Override
    public void deleteById(Long id) {
        jdbcTemplate.update("DELETE FROM course WHERE id = ?", id);
    }
}
