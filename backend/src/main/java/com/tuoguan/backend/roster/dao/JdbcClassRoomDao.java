package com.tuoguan.backend.roster.dao;

import com.tuoguan.backend.roster.domain.ClassRoom;
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
public class JdbcClassRoomDao implements ClassRoomDao {

    private static final RowMapper<ClassRoom> ROW_MAPPER = (rs, rowNum) -> new ClassRoom(
            rs.getLong("id"),
            rs.getLong("institution_id"),
            rs.getLong("teacher_id"),
            rs.getString("name"),
            rs.getTimestamp("created_at").toInstant());

    private final JdbcTemplate jdbcTemplate;

    public JdbcClassRoomDao(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public Long insert(ClassRoom classRoom) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(
                    "INSERT INTO class_room (institution_id, teacher_id, name) VALUES (?, ?, ?)",
                    Statement.RETURN_GENERATED_KEYS);
            ps.setLong(1, classRoom.institutionId());
            ps.setLong(2, classRoom.teacherId());
            ps.setString(3, classRoom.name());
            return ps;
        }, keyHolder);
        return keyHolder.getKey().longValue();
    }

    @Override
    public Optional<ClassRoom> findById(Long id) {
        List<ClassRoom> results = jdbcTemplate.query(
                "SELECT id, institution_id, teacher_id, name, created_at FROM class_room WHERE id = ?",
                ROW_MAPPER, id);
        return results.stream().findFirst();
    }

    @Override
    public List<ClassRoom> findAllByTeacherId(Long teacherId) {
        return jdbcTemplate.query(
                "SELECT id, institution_id, teacher_id, name, created_at FROM class_room "
                        + "WHERE teacher_id = ? ORDER BY id",
                ROW_MAPPER, teacherId);
    }

    @Override
    public List<ClassRoom> findAllByInstitutionId(Long institutionId) {
        return jdbcTemplate.query(
                "SELECT id, institution_id, teacher_id, name, created_at FROM class_room "
                        + "WHERE institution_id = ? ORDER BY id",
                ROW_MAPPER, institutionId);
    }
}
