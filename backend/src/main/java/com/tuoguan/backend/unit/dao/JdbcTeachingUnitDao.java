package com.tuoguan.backend.unit.dao;

import com.tuoguan.backend.unit.domain.BillingMode;
import com.tuoguan.backend.unit.domain.TeachingUnit;
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
public class JdbcTeachingUnitDao implements TeachingUnitDao {

    private static final RowMapper<TeachingUnit> ROW_MAPPER = (rs, rowNum) -> new TeachingUnit(
            rs.getLong("id"),
            rs.getLong("institution_id"),
            rs.getLong("teacher_id"),
            rs.getString("name"),
            BillingMode.valueOf(rs.getString("billing_mode")),
            (Integer) rs.getObject("lesson_duration_minutes"),
            rs.getBigDecimal("price_per_lesson"),
            rs.getBoolean("active"),
            rs.getTimestamp("created_at").toInstant());

    private final JdbcTemplate jdbcTemplate;

    public JdbcTeachingUnitDao(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public Long insert(TeachingUnit teachingUnit) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(
                    "INSERT INTO teaching_unit (institution_id, teacher_id, name, billing_mode, "
                            + "lesson_duration_minutes, price_per_lesson, active) VALUES (?, ?, ?, ?, ?, ?, ?)",
                    Statement.RETURN_GENERATED_KEYS);
            ps.setLong(1, teachingUnit.institutionId());
            ps.setLong(2, teachingUnit.teacherId());
            ps.setString(3, teachingUnit.name());
            ps.setString(4, teachingUnit.billingMode().name());
            if (teachingUnit.lessonDurationMinutes() != null) {
                ps.setInt(5, teachingUnit.lessonDurationMinutes());
            } else {
                ps.setNull(5, Types.INTEGER);
            }
            if (teachingUnit.pricePerLesson() != null) {
                ps.setBigDecimal(6, teachingUnit.pricePerLesson());
            } else {
                ps.setNull(6, Types.DECIMAL);
            }
            ps.setBoolean(7, teachingUnit.active());
            return ps;
        }, keyHolder);
        return keyHolder.getKey().longValue();
    }

    @Override
    public Optional<TeachingUnit> findById(Long id) {
        List<TeachingUnit> results = jdbcTemplate.query(
                "SELECT id, institution_id, teacher_id, name, billing_mode, lesson_duration_minutes, "
                        + "price_per_lesson, active, created_at FROM teaching_unit WHERE id = ?",
                ROW_MAPPER, id);
        return results.stream().findFirst();
    }

    @Override
    public List<TeachingUnit> findAllByTeacherId(Long teacherId) {
        return jdbcTemplate.query(
                "SELECT id, institution_id, teacher_id, name, billing_mode, lesson_duration_minutes, "
                        + "price_per_lesson, active, created_at FROM teaching_unit WHERE teacher_id = ? ORDER BY id",
                ROW_MAPPER, teacherId);
    }

    @Override
    public List<TeachingUnit> findAllByInstitutionId(Long institutionId) {
        return jdbcTemplate.query(
                "SELECT id, institution_id, teacher_id, name, billing_mode, lesson_duration_minutes, "
                        + "price_per_lesson, active, created_at FROM teaching_unit WHERE institution_id = ? "
                        + "ORDER BY id",
                ROW_MAPPER, institutionId);
    }

    @Override
    public void deleteById(Long id) {
        jdbcTemplate.update("DELETE FROM teaching_unit WHERE id = ?", id);
    }

    @Override
    public void updateNameAndTeacher(Long id, String name, Long teacherId) {
        jdbcTemplate.update("UPDATE teaching_unit SET name = ?, teacher_id = ? WHERE id = ?",
                name, teacherId, id);
    }

    @Override
    public void reassignTeacher(Long id, Long teacherId) {
        jdbcTemplate.update("UPDATE teaching_unit SET teacher_id = ? WHERE id = ?", teacherId, id);
    }

    @Override
    public void reassignAllTeacher(Long oldTeacherId, Long newTeacherId) {
        jdbcTemplate.update("UPDATE teaching_unit SET teacher_id = ? WHERE teacher_id = ?",
                newTeacherId, oldTeacherId);
    }

    @Override
    public void setPrice(Long id, BigDecimal pricePerLesson) {
        jdbcTemplate.update("UPDATE teaching_unit SET price_per_lesson = ? WHERE id = ?", pricePerLesson, id);
    }

    @Override
    public void setActive(Long id, boolean active) {
        jdbcTemplate.update("UPDATE teaching_unit SET active = ? WHERE id = ?", active, id);
    }
}
