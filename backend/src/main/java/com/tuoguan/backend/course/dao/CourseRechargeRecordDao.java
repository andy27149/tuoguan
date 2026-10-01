package com.tuoguan.backend.course.dao;

import com.tuoguan.backend.course.domain.CourseRechargeRecord;

import java.util.List;

public interface CourseRechargeRecordDao {

    Long insert(CourseRechargeRecord record);

    List<CourseRechargeRecord> findAllByStudentId(Long studentId);

    void deleteAllByStudentId(Long studentId);
}
