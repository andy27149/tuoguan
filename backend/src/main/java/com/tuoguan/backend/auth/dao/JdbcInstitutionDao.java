package com.tuoguan.backend.auth.dao;

import com.tuoguan.backend.auth.domain.Institution;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.List;
import java.util.Optional;

@Repository
public class JdbcInstitutionDao implements InstitutionDao {

    private final JdbcTemplate jdbcTemplate;

    public JdbcInstitutionDao(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public Long insert(String name) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(
                    "INSERT INTO institution (name) VALUES (?)",
                    Statement.RETURN_GENERATED_KEYS);
            ps.setString(1, name);
            return ps;
        }, keyHolder);
        return keyHolder.getKey().longValue();
    }

    @Override
    public Optional<Institution> findById(Long id) {
        List<Institution> results = jdbcTemplate.query(
                "SELECT id, name, created_at FROM institution WHERE id = ?",
                (rs, rowNum) -> new Institution(
                        rs.getLong("id"),
                        rs.getString("name"),
                        rs.getTimestamp("created_at").toInstant()),
                id);
        return results.stream().findFirst();
    }

    @Override
    public List<Institution> findAll() {
        return jdbcTemplate.query(
                "SELECT id, name, created_at FROM institution ORDER BY created_at",
                (rs, rowNum) -> new Institution(
                        rs.getLong("id"),
                        rs.getString("name"),
                        rs.getTimestamp("created_at").toInstant()));
    }
}
