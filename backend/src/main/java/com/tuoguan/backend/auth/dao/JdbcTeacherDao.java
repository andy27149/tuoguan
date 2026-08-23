package com.tuoguan.backend.auth.dao;

import com.tuoguan.backend.auth.domain.Role;
import com.tuoguan.backend.auth.domain.Teacher;
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
public class JdbcTeacherDao implements TeacherDao {

    private static final RowMapper<Teacher> ROW_MAPPER = (rs, rowNum) -> new Teacher(
            rs.getLong("id"),
            rs.getLong("institution_id"),
            rs.getString("phone"),
            rs.getString("password_hash"),
            Role.valueOf(rs.getString("role")),
            rs.getBoolean("must_change_password"),
            rs.getTimestamp("created_at").toInstant());

    private final JdbcTemplate jdbcTemplate;

    public JdbcTeacherDao(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public Long insert(Teacher teacher) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(
                    "INSERT INTO teacher (institution_id, phone, password_hash, role, must_change_password) "
                            + "VALUES (?, ?, ?, ?, ?)",
                    Statement.RETURN_GENERATED_KEYS);
            ps.setLong(1, teacher.institutionId());
            ps.setString(2, teacher.phone());
            ps.setString(3, teacher.passwordHash());
            ps.setString(4, teacher.role().name());
            ps.setBoolean(5, teacher.mustChangePassword());
            return ps;
        }, keyHolder);
        return keyHolder.getKey().longValue();
    }

    @Override
    public Optional<Teacher> findByPhone(String phone) {
        List<Teacher> results = jdbcTemplate.query(
                "SELECT id, institution_id, phone, password_hash, role, must_change_password, created_at "
                        + "FROM teacher WHERE phone = ?",
                ROW_MAPPER, phone);
        return results.stream().findFirst();
    }

    @Override
    public Optional<Teacher> findById(Long id) {
        List<Teacher> results = jdbcTemplate.query(
                "SELECT id, institution_id, phone, password_hash, role, must_change_password, created_at "
                        + "FROM teacher WHERE id = ?",
                ROW_MAPPER, id);
        return results.stream().findFirst();
    }

    @Override
    public List<Teacher> findAllByInstitutionId(Long institutionId) {
        return jdbcTemplate.query(
                "SELECT id, institution_id, phone, password_hash, role, must_change_password, created_at "
                        + "FROM teacher WHERE institution_id = ? ORDER BY id",
                ROW_MAPPER, institutionId);
    }

    @Override
    public void updatePassword(Long teacherId, String newPasswordHash) {
        jdbcTemplate.update(
                "UPDATE teacher SET password_hash = ?, must_change_password = FALSE WHERE id = ?",
                newPasswordHash, teacherId);
    }
}
