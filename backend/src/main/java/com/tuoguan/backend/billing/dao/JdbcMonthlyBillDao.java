package com.tuoguan.backend.billing.dao;

import com.tuoguan.backend.billing.domain.MonthlyBill;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.List;
import java.util.Optional;

@Repository
public class JdbcMonthlyBillDao implements MonthlyBillDao {

    private static final RowMapper<MonthlyBill> ROW_MAPPER = (rs, rowNum) -> new MonthlyBill(
            rs.getLong("id"),
            rs.getLong("institution_id"),
            rs.getLong("student_id"),
            rs.getLong("teaching_unit_id"),
            YearMonth.parse(rs.getString("bill_month")),
            rs.getInt("total_weekdays"),
            rs.getInt("leave_days"),
            rs.getInt("attendance_days"),
            rs.getBigDecimal("tuition_amount"),
            rs.getBigDecimal("meal_amount"),
            rs.getBigDecimal("extra_fee_total"),
            rs.getBigDecimal("total_amount"),
            rs.getBoolean("is_paid"),
            rs.getTimestamp("generated_at").toInstant(),
            List.of());

    private static final String SELECT_COLUMNS = "SELECT id, institution_id, student_id, teaching_unit_id, bill_month, "
            + "total_weekdays, leave_days, attendance_days, tuition_amount, meal_amount, extra_fee_total, "
            + "total_amount, is_paid, generated_at FROM monthly_bill";

    private final JdbcTemplate jdbcTemplate;

    public JdbcMonthlyBillDao(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public Optional<MonthlyBill> findById(Long id) {
        List<MonthlyBill> results = jdbcTemplate.query(SELECT_COLUMNS + " WHERE id = ?", ROW_MAPPER, id);
        return results.stream().findFirst();
    }

    @Override
    public Optional<MonthlyBill> findByStudentIdAndYearMonth(Long studentId, YearMonth yearMonth) {
        List<MonthlyBill> results = jdbcTemplate.query(SELECT_COLUMNS + " WHERE student_id = ? AND bill_month = ?",
                ROW_MAPPER, studentId, yearMonth.toString());
        return results.stream().findFirst();
    }

    @Override
    public List<MonthlyBill> findAllByTeachingUnitIdAndYearMonth(Long teachingUnitId, YearMonth yearMonth) {
        return jdbcTemplate.query(
                SELECT_COLUMNS + " WHERE teaching_unit_id = ? AND bill_month = ? ORDER BY student_id",
                ROW_MAPPER, teachingUnitId, yearMonth.toString());
    }

    @Override
    public List<MonthlyBill> findAllByTeachingUnitId(Long teachingUnitId) {
        return jdbcTemplate.query(
                SELECT_COLUMNS + " WHERE teaching_unit_id = ? ORDER BY bill_month DESC, student_id",
                ROW_MAPPER, teachingUnitId);
    }

    @Override
    public Long upsert(Long institutionId, Long studentId, Long teachingUnitId, YearMonth yearMonth,
                        int totalWeekdays, int leaveDays, int attendanceDays, BigDecimal tuitionAmount,
                        BigDecimal mealAmount, BigDecimal extraFeeTotal, BigDecimal totalAmount) {
        // MySQL 驱动在 ON DUPLICATE KEY UPDATE 命中更新分支时，Statement.RETURN_GENERATED_KEYS
        // 可能返回 2 条 key 记录，导致 KeyHolder.getKey() 抛 InvalidDataAccessApiUsageException，
        // 因此改为 upsert 后按唯一键 (student_id, bill_month) 查回 id，不依赖生成的主键。
        jdbcTemplate.update(
                "INSERT INTO monthly_bill (institution_id, student_id, teaching_unit_id, bill_month, "
                        + "total_weekdays, leave_days, attendance_days, tuition_amount, meal_amount, "
                        + "extra_fee_total, total_amount) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) "
                        + "ON DUPLICATE KEY UPDATE total_weekdays = VALUES(total_weekdays), "
                        + "leave_days = VALUES(leave_days), attendance_days = VALUES(attendance_days), "
                        + "tuition_amount = VALUES(tuition_amount), meal_amount = VALUES(meal_amount), "
                        + "extra_fee_total = VALUES(extra_fee_total), total_amount = VALUES(total_amount), "
                        + "generated_at = CURRENT_TIMESTAMP",
                institutionId, studentId, teachingUnitId, yearMonth.toString(), totalWeekdays, leaveDays,
                attendanceDays, tuitionAmount, mealAmount, extraFeeTotal, totalAmount);
        return findByStudentIdAndYearMonth(studentId, yearMonth)
                .orElseThrow(() -> new IllegalStateException(
                        "Bill not found after upsert: studentId=" + studentId + ", month=" + yearMonth))
                .id();
    }

    @Override
    public void deleteAllByTeachingUnitId(Long teachingUnitId) {
        jdbcTemplate.update("DELETE FROM monthly_bill WHERE teaching_unit_id = ?", teachingUnitId);
    }

    @Override
    public void setPaid(Long billId, boolean isPaid) {
        jdbcTemplate.update("UPDATE monthly_bill SET is_paid = ? WHERE id = ?", isPaid, billId);
    }
}
