package com.tuoguan.backend.course.dao;

import com.tuoguan.backend.course.domain.StudentCourseEnrollment;
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
public class JdbcStudentCourseEnrollmentDao implements StudentCourseEnrollmentDao {

    private static final RowMapper<StudentCourseEnrollment> ROW_MAPPER = (rs, rowNum) -> new StudentCourseEnrollment(
            rs.getLong("id"),
            rs.getLong("institution_id"),
            rs.getLong("student_id"),
            rs.getLong("course_id"),
            rs.getBoolean("active"),
            rs.getTimestamp("created_at").toInstant());

    private final JdbcTemplate jdbcTemplate;

    public JdbcStudentCourseEnrollmentDao(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public Long insert(StudentCourseEnrollment enrollment) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(
                    "INSERT INTO student_course_enrollment (institution_id, student_id, course_id, active) "
                            + "VALUES (?, ?, ?, ?)",
                    Statement.RETURN_GENERATED_KEYS);
            ps.setLong(1, enrollment.institutionId());
            ps.setLong(2, enrollment.studentId());
            ps.setLong(3, enrollment.courseId());
            ps.setBoolean(4, enrollment.active());
            return ps;
        }, keyHolder);
        return keyHolder.getKey().longValue();
    }

    @Override
    public Optional<StudentCourseEnrollment> findByStudentIdAndCourseId(Long studentId, Long courseId) {
        List<StudentCourseEnrollment> results = jdbcTemplate.query(
                "SELECT id, institution_id, student_id, course_id, active, created_at "
                        + "FROM student_course_enrollment WHERE student_id = ? AND course_id = ?",
                ROW_MAPPER, studentId, courseId);
        return results.stream().findFirst();
    }

    @Override
    public List<StudentCourseEnrollment> findAllByCourseId(Long courseId) {
        return jdbcTemplate.query(
                "SELECT id, institution_id, student_id, course_id, active, created_at "
                        + "FROM student_course_enrollment WHERE course_id = ? ORDER BY id",
                ROW_MAPPER, courseId);
    }

    @Override
    public List<StudentCourseEnrollment> findAllByStudentId(Long studentId) {
        return jdbcTemplate.query(
                "SELECT id, institution_id, student_id, course_id, active, created_at "
                        + "FROM student_course_enrollment WHERE student_id = ? ORDER BY id",
                ROW_MAPPER, studentId);
    }

    @Override
    public void setActive(Long id, boolean active) {
        jdbcTemplate.update("UPDATE student_course_enrollment SET active = ? WHERE id = ?", active, id);
    }

    @Override
    public void deleteAllByStudentId(Long studentId) {
        jdbcTemplate.update("DELETE FROM student_course_enrollment WHERE student_id = ?", studentId);
    }

    @Override
    public void deleteAllByCourseId(Long courseId) {
        jdbcTemplate.update("DELETE FROM student_course_enrollment WHERE course_id = ?", courseId);
    }
}
