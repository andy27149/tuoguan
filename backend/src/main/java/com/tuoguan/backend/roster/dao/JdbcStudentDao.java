package com.tuoguan.backend.roster.dao;

import com.tuoguan.backend.roster.domain.Student;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class JdbcStudentDao implements StudentDao {

    private static final RowMapper<Student> ROW_MAPPER = (rs, rowNum) -> new Student(
            rs.getLong("id"),
            rs.getLong("institution_id"),
            rs.getObject("teaching_unit_id", Long.class),
            rs.getString("name"),
            rs.getString("school_class_name"),
            rs.getBoolean("enrolled"),
            rs.getString("avatar_object_key"),
            rs.getTimestamp("created_at").toInstant());

    private final JdbcTemplate jdbcTemplate;

    public JdbcStudentDao(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public Long insert(Student student) {
        String shareToken = UUID.randomUUID().toString().replace("-", "");
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(
                    "INSERT INTO student (institution_id, teaching_unit_id, name, school_class_name, enrolled, share_token) "
                            + "VALUES (?, ?, ?, ?, ?, ?)",
                    Statement.RETURN_GENERATED_KEYS);
            ps.setLong(1, student.institutionId());
            ps.setObject(2, student.teachingUnitId());
            ps.setString(3, student.name());
            ps.setString(4, student.schoolClassName());
            ps.setBoolean(5, student.enrolled());
            ps.setString(6, shareToken);
            return ps;
        }, keyHolder);
        return keyHolder.getKey().longValue();
    }

    @Override
    public Optional<Student> findById(Long id) {
        List<Student> results = jdbcTemplate.query(
                "SELECT id, institution_id, teaching_unit_id, name, school_class_name, enrolled, avatar_object_key, created_at "
                        + "FROM student WHERE id = ?",
                ROW_MAPPER, id);
        return results.stream().findFirst();
    }

    @Override
    public List<Student> findAllByTeachingUnitId(Long teachingUnitId) {
        return jdbcTemplate.query(
                "SELECT id, institution_id, teaching_unit_id, name, school_class_name, enrolled, avatar_object_key, created_at "
                        + "FROM student WHERE teaching_unit_id = ? ORDER BY id",
                ROW_MAPPER, teachingUnitId);
    }

    @Override
    public List<Student> findAllByInstitutionId(Long institutionId) {
        return jdbcTemplate.query(
                "SELECT id, institution_id, teaching_unit_id, name, school_class_name, enrolled, avatar_object_key, created_at "
                        + "FROM student WHERE institution_id = ? ORDER BY id",
                ROW_MAPPER, institutionId);
    }

    @Override
    public void update(Student student) {
        jdbcTemplate.update(
                "UPDATE student SET name = ?, school_class_name = ?, enrolled = ? WHERE id = ?",
                student.name(), student.schoolClassName(), student.enrolled(), student.id());
    }

    @Override
    public void updateAvatarObjectKey(Long id, String avatarObjectKey) {
        jdbcTemplate.update("UPDATE student SET avatar_object_key = ? WHERE id = ?", avatarObjectKey, id);
    }

    @Override
    public Optional<Student> findByShareToken(String shareToken) {
        List<Student> results = jdbcTemplate.query(
                "SELECT id, institution_id, teaching_unit_id, name, school_class_name, enrolled, avatar_object_key, created_at "
                        + "FROM student WHERE share_token = ?",
                ROW_MAPPER, shareToken);
        return results.stream().findFirst();
    }

    @Override
    public String findShareToken(Long studentId) {
        return jdbcTemplate.queryForObject("SELECT share_token FROM student WHERE id = ?", String.class, studentId);
    }

    @Override
    public void deleteAllByTeachingUnitId(Long teachingUnitId) {
        jdbcTemplate.update("DELETE FROM student WHERE teaching_unit_id = ?", teachingUnitId);
    }

    @Override
    public void deleteAllByStudentId(Long studentId) {
        jdbcTemplate.update("DELETE FROM student WHERE id = ?", studentId);
    }
}
