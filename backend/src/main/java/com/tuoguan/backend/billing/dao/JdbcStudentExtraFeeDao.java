package com.tuoguan.backend.billing.dao;

import com.tuoguan.backend.billing.domain.StudentExtraFee;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.List;
import java.util.Optional;

@Repository
public class JdbcStudentExtraFeeDao implements StudentExtraFeeDao {

    private static final RowMapper<StudentExtraFee> ROW_MAPPER = (rs, rowNum) -> new StudentExtraFee(
            rs.getLong("id"),
            rs.getLong("institution_id"),
            rs.getLong("student_id"),
            rs.getString("name"),
            rs.getBigDecimal("price_per_lesson"),
            rs.getTimestamp("created_at").toInstant());

    private final JdbcTemplate jdbcTemplate;

    public JdbcStudentExtraFeeDao(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public List<StudentExtraFee> findAllByStudentId(Long studentId) {
        return jdbcTemplate.query(
                "SELECT id, institution_id, student_id, name, price_per_lesson, created_at "
                        + "FROM student_extra_fee WHERE student_id = ? ORDER BY id",
                ROW_MAPPER, studentId);
    }

    @Override
    public Optional<StudentExtraFee> findById(Long id) {
        List<StudentExtraFee> results = jdbcTemplate.query(
                "SELECT id, institution_id, student_id, name, price_per_lesson, created_at "
                        + "FROM student_extra_fee WHERE id = ?",
                ROW_MAPPER, id);
        return results.stream().findFirst();
    }

    @Override
    public Long insert(Long institutionId, Long studentId, String name, BigDecimal pricePerLesson) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(
                    "INSERT INTO student_extra_fee (institution_id, student_id, name, price_per_lesson) "
                            + "VALUES (?, ?, ?, ?)",
                    Statement.RETURN_GENERATED_KEYS);
            ps.setLong(1, institutionId);
            ps.setLong(2, studentId);
            ps.setString(3, name);
            ps.setBigDecimal(4, pricePerLesson);
            return ps;
        }, keyHolder);
        return keyHolder.getKey().longValue();
    }

    @Override
    public void deleteById(Long id) {
        jdbcTemplate.update("DELETE FROM student_extra_fee WHERE id = ?", id);
    }

    @Override
    public void deleteAllByStudentId(Long studentId) {
        jdbcTemplate.update("DELETE FROM student_extra_fee WHERE student_id = ?", studentId);
    }
}
