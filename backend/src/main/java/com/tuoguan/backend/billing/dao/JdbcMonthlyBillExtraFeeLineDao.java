package com.tuoguan.backend.billing.dao;

import com.tuoguan.backend.billing.domain.MonthlyBillExtraFeeLine;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;

@Repository
public class JdbcMonthlyBillExtraFeeLineDao implements MonthlyBillExtraFeeLineDao {

    private static final RowMapper<MonthlyBillExtraFeeLine> ROW_MAPPER = (rs, rowNum) -> new MonthlyBillExtraFeeLine(
            rs.getLong("id"),
            rs.getLong("monthly_bill_id"),
            rs.getString("name"),
            rs.getBigDecimal("price_per_lesson"),
            (Integer) rs.getObject("lesson_count"),
            rs.getBigDecimal("amount"));

    private final JdbcTemplate jdbcTemplate;

    public JdbcMonthlyBillExtraFeeLineDao(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public List<MonthlyBillExtraFeeLine> findAllByBillId(Long monthlyBillId) {
        return jdbcTemplate.query(
                "SELECT id, monthly_bill_id, name, price_per_lesson, lesson_count, amount "
                        + "FROM monthly_bill_extra_fee_line WHERE monthly_bill_id = ? ORDER BY id",
                ROW_MAPPER, monthlyBillId);
    }

    @Override
    public void insert(Long monthlyBillId, String name, BigDecimal pricePerLesson, int lessonCount,
                        BigDecimal amount) {
        jdbcTemplate.update(
                "INSERT INTO monthly_bill_extra_fee_line "
                        + "(monthly_bill_id, name, price_per_lesson, lesson_count, amount) VALUES (?, ?, ?, ?, ?)",
                monthlyBillId, name, pricePerLesson, lessonCount, amount);
    }

    @Override
    public void deleteAllByBillId(Long monthlyBillId) {
        jdbcTemplate.update("DELETE FROM monthly_bill_extra_fee_line WHERE monthly_bill_id = ?", monthlyBillId);
    }
}
