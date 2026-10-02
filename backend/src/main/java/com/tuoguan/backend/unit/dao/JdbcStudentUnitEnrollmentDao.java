package com.tuoguan.backend.unit.dao;

import com.tuoguan.backend.unit.domain.StudentUnitEnrollment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.List;
import java.util.Optional;

@Repository
public class JdbcStudentUnitEnrollmentDao implements StudentUnitEnrollmentDao {

    private static final RowMapper<StudentUnitEnrollment> ROW_MAPPER = (rs, rowNum) -> new StudentUnitEnrollment(
            rs.getLong("id"),
            rs.getLong("institution_id"),
            rs.getLong("student_id"),
            rs.getLong("teaching_unit_id"),
            rs.getBoolean("active"),
            rs.getTimestamp("created_at").toInstant());

    private final JdbcTemplate jdbcTemplate;

    public JdbcStudentUnitEnrollmentDao(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public Long insert(StudentUnitEnrollment enrollment) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(
                    "INSERT INTO student_unit_enrollment (institution_id, student_id, teaching_unit_id, active) "
                            + "VALUES (?, ?, ?, ?)",
                    Statement.RETURN_GENERATED_KEYS);
            ps.setLong(1, enrollment.institutionId());
            ps.setLong(2, enrollment.studentId());
            ps.setLong(3, enrollment.teachingUnitId());
            ps.setBoolean(4, enrollment.active());
            return ps;
        }, keyHolder);
        return keyHolder.getKey().longValue();
    }

    @Override
    public Optional<StudentUnitEnrollment> findByStudentIdAndTeachingUnitId(Long studentId, Long teachingUnitId) {
        List<StudentUnitEnrollment> results = jdbcTemplate.query(
                "SELECT id, institution_id, student_id, teaching_unit_id, active, created_at "
                        + "FROM student_unit_enrollment WHERE student_id = ? AND teaching_unit_id = ?",
                ROW_MAPPER, studentId, teachingUnitId);
        return results.stream().findFirst();
    }

    @Override
    public List<StudentUnitEnrollment> findAllByTeachingUnitId(Long teachingUnitId) {
        return jdbcTemplate.query(
                "SELECT id, institution_id, student_id, teaching_unit_id, active, created_at "
                        + "FROM student_unit_enrollment WHERE teaching_unit_id = ? ORDER BY id",
                ROW_MAPPER, teachingUnitId);
    }

    @Override
    public List<StudentUnitEnrollment> findAllByStudentId(Long studentId) {
        return jdbcTemplate.query(
                "SELECT id, institution_id, student_id, teaching_unit_id, active, created_at "
                        + "FROM student_unit_enrollment WHERE student_id = ? ORDER BY id",
                ROW_MAPPER, studentId);
    }

    @Override
    public void setActive(Long id, boolean active) {
        jdbcTemplate.update("UPDATE student_unit_enrollment SET active = ? WHERE id = ?", active, id);
    }

    @Override
    public void deleteAllByStudentId(Long studentId) {
        jdbcTemplate.update("DELETE FROM student_unit_enrollment WHERE student_id = ?", studentId);
    }

    @Override
    public void deleteAllByTeachingUnitId(Long teachingUnitId) {
        jdbcTemplate.update("DELETE FROM student_unit_enrollment WHERE teaching_unit_id = ?", teachingUnitId);
    }
}
