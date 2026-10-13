package com.tuoguan.backend.billing.dao;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public class JdbcMonthlyBillExtraFeeLineDateDao implements MonthlyBillExtraFeeLineDateDao {

    private static final RowMapper<LocalDate> ROW_MAPPER =
            (rs, rowNum) -> rs.getDate("consumption_date").toLocalDate();

    private final JdbcTemplate jdbcTemplate;

    public JdbcMonthlyBillExtraFeeLineDateDao(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public List<LocalDate> findAllByLineId(Long extraFeeLineId) {
        return jdbcTemplate.query(
                "SELECT consumption_date FROM monthly_bill_extra_fee_line_date "
                        + "WHERE extra_fee_line_id = ? ORDER BY consumption_date",
                ROW_MAPPER, extraFeeLineId);
    }

    @Override
    public void insert(Long extraFeeLineId, LocalDate consumptionDate) {
        jdbcTemplate.update(
                "INSERT INTO monthly_bill_extra_fee_line_date (extra_fee_line_id, consumption_date) VALUES (?, ?)",
                extraFeeLineId, java.sql.Date.valueOf(consumptionDate));
    }
}
