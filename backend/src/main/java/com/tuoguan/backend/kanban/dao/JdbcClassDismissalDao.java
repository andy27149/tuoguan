package com.tuoguan.backend.kanban.dao;

import com.tuoguan.backend.kanban.domain.ClassDismissal;
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
public class JdbcClassDismissalDao implements ClassDismissalDao {

    private static final RowMapper<ClassDismissal> ROW_MAPPER = (rs, rowNum) -> new ClassDismissal(
            rs.getLong("id"),
            rs.getLong("institution_id"),
            rs.getLong("class_room_id"),
            rs.getDate("dismissal_date").toLocalDate(),
            rs.getTimestamp("created_at").toInstant());

    private final JdbcTemplate jdbcTemplate;

    public JdbcClassDismissalDao(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public Long insert(ClassDismissal classDismissal) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(
                    "INSERT INTO class_dismissal (institution_id, class_room_id, dismissal_date) VALUES (?, ?, ?)",
                    Statement.RETURN_GENERATED_KEYS);
            ps.setLong(1, classDismissal.institutionId());
            ps.setLong(2, classDismissal.classRoomId());
            ps.setDate(3, java.sql.Date.valueOf(classDismissal.dismissalDate()));
            return ps;
        }, keyHolder);
        return keyHolder.getKey().longValue();
    }

    @Override
    public Optional<ClassDismissal> findByClassRoomIdAndDate(Long classRoomId, LocalDate date) {
        List<ClassDismissal> results = jdbcTemplate.query(
                "SELECT id, institution_id, class_room_id, dismissal_date, created_at FROM class_dismissal "
                        + "WHERE class_room_id = ? AND dismissal_date = ?",
                ROW_MAPPER, classRoomId, java.sql.Date.valueOf(date));
        return results.stream().findFirst();
    }

    @Override
    public void deleteByClassRoomIdAndDate(Long classRoomId, LocalDate date) {
        jdbcTemplate.update("DELETE FROM class_dismissal WHERE class_room_id = ? AND dismissal_date = ?",
                classRoomId, java.sql.Date.valueOf(date));
    }
}
