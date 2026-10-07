package com.tuoguan.backend.billing.dao;

import com.tuoguan.backend.billing.domain.MonthlyBillLeaveLine;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public class JdbcMonthlyBillLeaveLineDao implements MonthlyBillLeaveLineDao {

    private static final RowMapper<MonthlyBillLeaveLine> ROW_MAPPER = (rs, rowNum) -> new MonthlyBillLeaveLine(
            rs.getLong("id"),
            rs.getLong("monthly_bill_id"),
            rs.getDate("leave_date").toLocalDate(),
            rs.getString("reason"));

    private final JdbcTemplate jdbcTemplate;

    public JdbcMonthlyBillLeaveLineDao(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public List<MonthlyBillLeaveLine> findAllByBillId(Long monthlyBillId) {
        return jdbcTemplate.query(
                "SELECT id, monthly_bill_id, leave_date, reason FROM monthly_bill_leave_line "
                        + "WHERE monthly_bill_id = ? ORDER BY leave_date",
                ROW_MAPPER, monthlyBillId);
    }

    @Override
    public void insert(Long monthlyBillId, LocalDate leaveDate, String reason) {
        jdbcTemplate.update(
                "INSERT INTO monthly_bill_leave_line (monthly_bill_id, leave_date, reason) VALUES (?, ?, ?)",
                monthlyBillId, java.sql.Date.valueOf(leaveDate), reason);
    }

    @Override
    public void deleteAllByBillId(Long monthlyBillId) {
        jdbcTemplate.update("DELETE FROM monthly_bill_leave_line WHERE monthly_bill_id = ?", monthlyBillId);
    }
}
