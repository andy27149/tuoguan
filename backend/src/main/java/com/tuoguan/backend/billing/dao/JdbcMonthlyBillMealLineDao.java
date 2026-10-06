package com.tuoguan.backend.billing.dao;

import com.tuoguan.backend.billing.domain.MonthlyBillMealLine;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public class JdbcMonthlyBillMealLineDao implements MonthlyBillMealLineDao {

    private static final RowMapper<MonthlyBillMealLine> ROW_MAPPER = (rs, rowNum) -> new MonthlyBillMealLine(
            rs.getLong("id"),
            rs.getLong("monthly_bill_id"),
            rs.getDate("meal_date").toLocalDate());

    private final JdbcTemplate jdbcTemplate;

    public JdbcMonthlyBillMealLineDao(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public List<MonthlyBillMealLine> findAllByBillId(Long monthlyBillId) {
        return jdbcTemplate.query(
                "SELECT id, monthly_bill_id, meal_date FROM monthly_bill_meal_line "
                        + "WHERE monthly_bill_id = ? ORDER BY meal_date",
                ROW_MAPPER, monthlyBillId);
    }

    @Override
    public void insert(Long monthlyBillId, LocalDate mealDate) {
        jdbcTemplate.update(
                "INSERT INTO monthly_bill_meal_line (monthly_bill_id, meal_date) VALUES (?, ?)",
                monthlyBillId, java.sql.Date.valueOf(mealDate));
    }

    @Override
    public void deleteAllByBillId(Long monthlyBillId) {
        jdbcTemplate.update("DELETE FROM monthly_bill_meal_line WHERE monthly_bill_id = ?", monthlyBillId);
    }
}
