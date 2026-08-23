package com.tuoguan.backend.kanban.dao;

import com.tuoguan.backend.kanban.domain.DailyTask;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Types;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public class JdbcDailyTaskDao implements DailyTaskDao {

    private static final RowMapper<DailyTask> ROW_MAPPER = (rs, rowNum) -> new DailyTask(
            rs.getLong("id"),
            rs.getLong("institution_id"),
            rs.getLong("class_room_id"),
            rs.getLong("student_id"),
            rs.getDate("task_date").toLocalDate(),
            rs.getObject("task_template_id", Long.class),
            rs.getString("subject"),
            rs.getString("name"),
            rs.getBoolean("is_custom"),
            rs.getBoolean("completed"),
            rs.getTimestamp("created_at").toInstant());

    private final JdbcTemplate jdbcTemplate;

    public JdbcDailyTaskDao(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public Long insert(DailyTask dailyTask) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(
                    "INSERT INTO daily_task (institution_id, class_room_id, student_id, task_date, "
                            + "task_template_id, subject, name, is_custom, completed) "
                            + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
                    Statement.RETURN_GENERATED_KEYS);
            ps.setLong(1, dailyTask.institutionId());
            ps.setLong(2, dailyTask.classRoomId());
            ps.setLong(3, dailyTask.studentId());
            ps.setDate(4, java.sql.Date.valueOf(dailyTask.taskDate()));
            if (dailyTask.taskTemplateId() != null) {
                ps.setLong(5, dailyTask.taskTemplateId());
            } else {
                ps.setNull(5, Types.BIGINT);
            }
            ps.setString(6, dailyTask.subject());
            ps.setString(7, dailyTask.name());
            ps.setBoolean(8, dailyTask.custom());
            ps.setBoolean(9, dailyTask.completed());
            return ps;
        }, keyHolder);
        return keyHolder.getKey().longValue();
    }

    @Override
    public Optional<DailyTask> findById(Long id) {
        List<DailyTask> results = jdbcTemplate.query(
                "SELECT id, institution_id, class_room_id, student_id, task_date, task_template_id, subject, "
                        + "name, is_custom, completed, created_at FROM daily_task WHERE id = ?",
                ROW_MAPPER, id);
        return results.stream().findFirst();
    }

    @Override
    public List<DailyTask> findAllByClassRoomIdAndDate(Long classRoomId, LocalDate date) {
        return jdbcTemplate.query(
                "SELECT id, institution_id, class_room_id, student_id, task_date, task_template_id, subject, "
                        + "name, is_custom, completed, created_at FROM daily_task "
                        + "WHERE class_room_id = ? AND task_date = ? ORDER BY id",
                ROW_MAPPER, classRoomId, java.sql.Date.valueOf(date));
    }

    @Override
    public List<DailyTask> findAllByStudentIdAndDateRange(Long studentId, LocalDate start, LocalDate end) {
        return jdbcTemplate.query(
                "SELECT id, institution_id, class_room_id, student_id, task_date, task_template_id, subject, "
                        + "name, is_custom, completed, created_at FROM daily_task "
                        + "WHERE student_id = ? AND task_date BETWEEN ? AND ? ORDER BY task_date",
                ROW_MAPPER, studentId, java.sql.Date.valueOf(start), java.sql.Date.valueOf(end));
    }

    @Override
    public void updateCompleted(Long id, boolean completed) {
        jdbcTemplate.update("UPDATE daily_task SET completed = ? WHERE id = ?", completed, id);
    }

    @Override
    public void deleteById(Long id) {
        jdbcTemplate.update("DELETE FROM daily_task WHERE id = ?", id);
    }
}
