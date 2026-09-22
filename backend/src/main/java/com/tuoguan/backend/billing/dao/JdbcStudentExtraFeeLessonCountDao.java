package com.tuoguan.backend.billing.dao;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public class JdbcStudentExtraFeeLessonCountDao implements StudentExtraFeeLessonCountDao {

    private final JdbcTemplate jdbcTemplate;

    public JdbcStudentExtraFeeLessonCountDao(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public Optional<Integer> findLessonCount(Long studentExtraFeeId, String billMonth) {
        List<Integer> results = jdbcTemplate.query(
                "SELECT lesson_count FROM student_extra_fee_lesson_count "
                        + "WHERE student_extra_fee_id = ? AND bill_month = ?",
                (rs, rowNum) -> rs.getInt("lesson_count"), studentExtraFeeId, billMonth);
        return results.stream().findFirst();
    }

    @Override
    public void upsert(Long institutionId, Long studentExtraFeeId, String billMonth, int lessonCount) {
        jdbcTemplate.update(
                "INSERT INTO student_extra_fee_lesson_count "
                        + "(institution_id, student_extra_fee_id, bill_month, lesson_count) VALUES (?, ?, ?, ?) "
                        + "ON DUPLICATE KEY UPDATE lesson_count = VALUES(lesson_count)",
                institutionId, studentExtraFeeId, billMonth, lessonCount);
    }
}
