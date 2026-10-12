package com.tuoguan.backend.share.dao;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Date;
import java.time.LocalDate;

@Repository
public class JdbcStudentShareViewLogDao implements StudentShareViewLogDao {

    private final JdbcTemplate jdbcTemplate;

    public JdbcStudentShareViewLogDao(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void recordView(Long institutionId, Long studentId, LocalDate viewDate) {
        jdbcTemplate.update(
                "INSERT IGNORE INTO student_share_view_log (institution_id, student_id, view_date) "
                        + "VALUES (?, ?, ?)",
                institutionId, studentId, Date.valueOf(viewDate));
    }

    @Override
    public int countDistinctStudentsOn(Long institutionId, LocalDate viewDate) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM student_share_view_log WHERE institution_id = ? AND view_date = ?",
                Integer.class, institutionId, Date.valueOf(viewDate));
        return count != null ? count : 0;
    }
}
