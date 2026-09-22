package com.tuoguan.backend.billing.dao;

import com.tuoguan.backend.billing.domain.ClassBillingRate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

@Repository
public class JdbcClassBillingRateDao implements ClassBillingRateDao {

    private static final RowMapper<ClassBillingRate> ROW_MAPPER = (rs, rowNum) -> new ClassBillingRate(
            rs.getLong("id"),
            rs.getLong("institution_id"),
            rs.getLong("class_room_id"),
            rs.getBigDecimal("tuition_rate_per_month"),
            rs.getBigDecimal("meal_rate_per_day"),
            rs.getTimestamp("updated_at").toInstant());

    private final JdbcTemplate jdbcTemplate;

    public JdbcClassBillingRateDao(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public Optional<ClassBillingRate> findByClassRoomId(Long classRoomId) {
        List<ClassBillingRate> results = jdbcTemplate.query(
                "SELECT id, institution_id, class_room_id, tuition_rate_per_month, meal_rate_per_day, updated_at "
                        + "FROM class_billing_rate WHERE class_room_id = ?",
                ROW_MAPPER, classRoomId);
        return results.stream().findFirst();
    }

    @Override
    public void upsert(Long institutionId, Long classRoomId, BigDecimal tuitionRatePerMonth, BigDecimal mealRatePerDay) {
        jdbcTemplate.update(
                "INSERT INTO class_billing_rate (institution_id, class_room_id, tuition_rate_per_month, "
                        + "meal_rate_per_day) VALUES (?, ?, ?, ?) "
                        + "ON DUPLICATE KEY UPDATE tuition_rate_per_month = VALUES(tuition_rate_per_month), "
                        + "meal_rate_per_day = VALUES(meal_rate_per_day)",
                institutionId, classRoomId, tuitionRatePerMonth, mealRatePerDay);
    }

    @Override
    public void deleteAllByClassRoomId(Long classRoomId) {
        jdbcTemplate.update("DELETE FROM class_billing_rate WHERE class_room_id = ?", classRoomId);
    }
}
