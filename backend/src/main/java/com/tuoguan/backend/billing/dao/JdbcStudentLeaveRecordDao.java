package com.tuoguan.backend.billing.dao;

import com.tuoguan.backend.billing.domain.StudentLeaveRecord;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public class JdbcStudentLeaveRecordDao implements StudentLeaveRecordDao {

    private static final RowMapper<StudentLeaveRecord> ROW_MAPPER = (rs, rowNum) -> new StudentLeaveRecord(
            rs.getLong("id"),
            rs.getLong("institution_id"),
            rs.getLong("student_id"),
            rs.getLong("teaching_unit_id"),
            rs.getDate("leave_date").toLocalDate(),
            rs.getString("reason"),
            rs.getTimestamp("created_at").toInstant());

    private final JdbcTemplate jdbcTemplate;

    public JdbcStudentLeaveRecordDao(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public List<StudentLeaveRecord> findAllByStudentIdAndDateRange(Long studentId, LocalDate start, LocalDate end) {
        return jdbcTemplate.query(
                "SELECT id, institution_id, student_id, teaching_unit_id, leave_date, reason, created_at "
                        + "FROM student_leave_record WHERE student_id = ? AND leave_date BETWEEN ? AND ? "
                        + "ORDER BY leave_date",
                ROW_MAPPER, studentId, java.sql.Date.valueOf(start), java.sql.Date.valueOf(end));
    }

    @Override
    public int countByStudentIdAndDateRange(Long studentId, LocalDate start, LocalDate end) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM student_leave_record WHERE student_id = ? AND leave_date BETWEEN ? AND ?",
                Integer.class, studentId, java.sql.Date.valueOf(start), java.sql.Date.valueOf(end));
        return count != null ? count : 0;
    }

    @Override
    public void upsert(Long institutionId, Long studentId, Long teachingUnitId, LocalDate leaveDate, String reason) {
        jdbcTemplate.update(
                "INSERT INTO student_leave_record (institution_id, student_id, teaching_unit_id, leave_date, reason) "
                        + "VALUES (?, ?, ?, ?, ?) ON DUPLICATE KEY UPDATE reason = VALUES(reason)",
                institutionId, studentId, teachingUnitId, java.sql.Date.valueOf(leaveDate), reason);
    }

    @Override
    public void deleteByStudentIdAndDate(Long studentId, LocalDate leaveDate) {
        jdbcTemplate.update(
                "DELETE FROM student_leave_record WHERE student_id = ? AND leave_date = ?",
                studentId, java.sql.Date.valueOf(leaveDate));
    }

    @Override
    public void deleteAllByTeachingUnitId(Long teachingUnitId) {
        jdbcTemplate.update("DELETE FROM student_leave_record WHERE teaching_unit_id = ?", teachingUnitId);
    }
}
