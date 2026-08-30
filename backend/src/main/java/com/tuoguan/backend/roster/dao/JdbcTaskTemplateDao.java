package com.tuoguan.backend.roster.dao;

import com.tuoguan.backend.roster.domain.TaskTemplate;
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
public class JdbcTaskTemplateDao implements TaskTemplateDao {

    private static final RowMapper<TaskTemplate> ROW_MAPPER = (rs, rowNum) -> new TaskTemplate(
            rs.getLong("id"),
            rs.getLong("institution_id"),
            rs.getString("subject"),
            rs.getString("name"),
            rs.getTimestamp("created_at").toInstant(),
            rs.getBoolean("archived"));

    private final JdbcTemplate jdbcTemplate;

    public JdbcTaskTemplateDao(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public Long insert(TaskTemplate taskTemplate) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(
                    "INSERT INTO task_template (institution_id, subject, name) VALUES (?, ?, ?)",
                    Statement.RETURN_GENERATED_KEYS);
            ps.setLong(1, taskTemplate.institutionId());
            ps.setString(2, taskTemplate.subject());
            ps.setString(3, taskTemplate.name());
            return ps;
        }, keyHolder);
        return keyHolder.getKey().longValue();
    }

    @Override
    public Optional<TaskTemplate> findById(Long id) {
        List<TaskTemplate> results = jdbcTemplate.query(
                "SELECT id, institution_id, subject, name, created_at, archived FROM task_template WHERE id = ?",
                ROW_MAPPER, id);
        return results.stream().findFirst();
    }

    @Override
    public List<TaskTemplate> findAllByInstitutionId(Long institutionId) {
        return jdbcTemplate.query(
                "SELECT id, institution_id, subject, name, created_at, archived FROM task_template "
                        + "WHERE institution_id = ? AND archived = FALSE ORDER BY id",
                ROW_MAPPER, institutionId);
    }

    @Override
    public void archiveById(Long id) {
        jdbcTemplate.update("UPDATE task_template SET archived = TRUE WHERE id = ?", id);
    }
}
