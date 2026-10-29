package com.tuoguan.backend.course.dao;

import com.tuoguan.backend.course.domain.CourseMealRecord;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface CourseMealRecordDao {

    // 原子的 INSERT ... ON DUPLICATE KEY UPDATE，跟 kanban 模块
    // StudentMealRecordDao.upsert 同款写法——不需要先查是否存在再插入，天然对
    // uq_course_meal_record_student_date 并发安全，不会出现"查的时候没有、插的
    // 时候撞唯一约束"这种竞态窗口。
    void upsert(CourseMealRecord record);

    Optional<CourseMealRecord> findByStudentIdAndDate(Long studentId, LocalDate date);

    List<CourseMealRecord> findAllByStudentIdAndDateRange(Long studentId, LocalDate start, LocalDate end);

    void delete(Long studentId, LocalDate date);
}
