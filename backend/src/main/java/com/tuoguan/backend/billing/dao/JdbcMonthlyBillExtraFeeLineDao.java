package com.tuoguan.backend.billing.dao;

import com.tuoguan.backend.billing.domain.MonthlyBillExtraFeeLine;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.List;

@Repository
public class JdbcMonthlyBillExtraFeeLineDao implements MonthlyBillExtraFeeLineDao {

    private final JdbcTemplate jdbcTemplate;
    private final MonthlyBillExtraFeeLineDateDao extraFeeLineDateDao;

    public JdbcMonthlyBillExtraFeeLineDao(JdbcTemplate jdbcTemplate,
                                           MonthlyBillExtraFeeLineDateDao extraFeeLineDateDao) {
        this.jdbcTemplate = jdbcTemplate;
        this.extraFeeLineDateDao = extraFeeLineDateDao;
    }

    private RowMapper<MonthlyBillExtraFeeLine> rowMapper() {
        return (rs, rowNum) -> new MonthlyBillExtraFeeLine(
                rs.getLong("id"),
                rs.getLong("monthly_bill_id"),
                rs.getString("name"),
                rs.getBigDecimal("price_per_lesson"),
                (Integer) rs.getObject("lesson_count"),
                rs.getBigDecimal("amount"),
                extraFeeLineDateDao.findAllByLineId(rs.getLong("id")));
    }

    @Override
    public List<MonthlyBillExtraFeeLine> findAllByBillId(Long monthlyBillId) {
        return jdbcTemplate.query(
                "SELECT id, monthly_bill_id, name, price_per_lesson, lesson_count, amount "
                        + "FROM monthly_bill_extra_fee_line WHERE monthly_bill_id = ? ORDER BY id",
                rowMapper(), monthlyBillId);
    }

    @Override
    public Long insert(Long monthlyBillId, String name, BigDecimal pricePerLesson, int lessonCount,
                        BigDecimal amount) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(
                    "INSERT INTO monthly_bill_extra_fee_line "
                            + "(monthly_bill_id, name, price_per_lesson, lesson_count, amount) VALUES (?, ?, ?, ?, ?)",
                    Statement.RETURN_GENERATED_KEYS);
            ps.setLong(1, monthlyBillId);
            ps.setString(2, name);
            ps.setBigDecimal(3, pricePerLesson);
            ps.setInt(4, lessonCount);
            ps.setBigDecimal(5, amount);
            return ps;
        }, keyHolder);
        return keyHolder.getKey().longValue();
    }

    @Override
    public void deleteAllByBillId(Long monthlyBillId) {
        jdbcTemplate.update("DELETE FROM monthly_bill_extra_fee_line WHERE monthly_bill_id = ?", monthlyBillId);
    }
}
