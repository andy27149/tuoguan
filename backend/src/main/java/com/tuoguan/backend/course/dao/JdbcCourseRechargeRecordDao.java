package com.tuoguan.backend.course.dao;

import com.tuoguan.backend.course.domain.CourseRechargeRecord;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.List;

@Repository
public class JdbcCourseRechargeRecordDao implements CourseRechargeRecordDao {

    private static final RowMapper<CourseRechargeRecord> ROW_MAPPER = (rs, rowNum) -> new CourseRechargeRecord(
            rs.getLong("id"),
            rs.getLong("institution_id"),
            rs.getLong("student_id"),
            rs.getLong("teaching_unit_id"),
            rs.getInt("lesson_count"),
            rs.getString("note"),
            rs.getLong("recorded_by_teacher_id"),
            rs.getTimestamp("created_at").toInstant());

    private final JdbcTemplate jdbcTemplate;

    public JdbcCourseRechargeRecordDao(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public Long insert(CourseRechargeRecord record) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(
                    "INSERT INTO course_recharge_record (institution_id, student_id, teaching_unit_id, lesson_count, "
                            + "note, recorded_by_teacher_id) VALUES (?, ?, ?, ?, ?, ?)",
                    Statement.RETURN_GENERATED_KEYS);
            ps.setLong(1, record.institutionId());
            ps.setLong(2, record.studentId());
            ps.setLong(3, record.teachingUnitId());
            ps.setInt(4, record.lessonCount());
            ps.setString(5, record.note());
            ps.setLong(6, record.recordedByTeacherId());
            return ps;
        }, keyHolder);
        return keyHolder.getKey().longValue();
    }

    @Override
    public List<CourseRechargeRecord> findAllByStudentId(Long studentId) {
        return jdbcTemplate.query(
                "SELECT id, institution_id, student_id, teaching_unit_id, lesson_count, note, recorded_by_teacher_id, "
                        + "created_at FROM course_recharge_record WHERE student_id = ? ORDER BY id DESC",
                ROW_MAPPER, studentId);
    }

    @Override
    public List<CourseRechargeRecord> findAllByInstitutionId(Long institutionId) {
        return jdbcTemplate.query(
                "SELECT id, institution_id, student_id, teaching_unit_id, lesson_count, note, recorded_by_teacher_id, "
                        + "created_at FROM course_recharge_record WHERE institution_id = ? ORDER BY id",
                ROW_MAPPER, institutionId);
    }

    @Override
    public void deleteAllByStudentId(Long studentId) {
        jdbcTemplate.update("DELETE FROM course_recharge_record WHERE student_id = ?", studentId);
    }

    @Override
    public void deleteAllByTeachingUnitId(Long teachingUnitId) {
        jdbcTemplate.update("DELETE FROM course_recharge_record WHERE teaching_unit_id = ?", teachingUnitId);
    }
}
