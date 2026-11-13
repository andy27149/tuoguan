package com.tuoguan.backend.monthlyquickbill.dao;

import com.tuoguan.backend.monthlyquickbill.domain.MonthlyQuickBillRecord;
import com.tuoguan.backend.monthlyquickbill.domain.QuickBillRecordType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.Date;
import java.time.LocalDate;
import java.util.List;

@Repository
public class JdbcMonthlyQuickBillRecordDao implements MonthlyQuickBillRecordDao {

    private static final RowMapper<MonthlyQuickBillRecord> ROW_MAPPER = (rs, rowNum) -> new MonthlyQuickBillRecord(
            rs.getLong("id"),
            rs.getLong("institution_id"),
            rs.getLong("student_id"),
            QuickBillRecordType.valueOf(rs.getString("record_type")),
            rs.getDate("record_date").toLocalDate(),
            rs.getTimestamp("created_at").toInstant());

    private final JdbcTemplate jdbcTemplate;

    public JdbcMonthlyQuickBillRecordDao(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void insert(Long institutionId, Long studentId, QuickBillRecordType recordType, LocalDate recordDate) {
        jdbcTemplate.update(
                "INSERT INTO monthly_quick_bill_record (institution_id, student_id, record_type, record_date) "
                        + "VALUES (?, ?, ?, ?)",
                institutionId, studentId, recordType.name(), Date.valueOf(recordDate));
    }

    @Override
    public List<MonthlyQuickBillRecord> findAllByStudentIdAndDateRange(Long studentId, LocalDate start,
                                                                        LocalDate end) {
        return jdbcTemplate.query(
                "SELECT id, institution_id, student_id, record_type, record_date, created_at "
                        + "FROM monthly_quick_bill_record WHERE student_id = ? AND record_date BETWEEN ? AND ? "
                        + "ORDER BY record_date",
                ROW_MAPPER, studentId, Date.valueOf(start), Date.valueOf(end));
    }

    @Override
    public void deleteAllByStudentIdAndDateRange(Long studentId, LocalDate start, LocalDate end) {
        jdbcTemplate.update(
                "DELETE FROM monthly_quick_bill_record WHERE student_id = ? AND record_date BETWEEN ? AND ?",
                studentId, Date.valueOf(start), Date.valueOf(end));
    }
}
