package com.tuoguan.backend.course.dao;

import com.tuoguan.backend.course.domain.CourseRechargeRecord;

import java.util.List;

public interface CourseRechargeRecordDao {

    Long insert(CourseRechargeRecord record);

    List<CourseRechargeRecord> findAllByStudentId(Long studentId);

    List<CourseRechargeRecord> findAllByInstitutionId(Long institutionId);

    void deleteAllByStudentId(Long studentId);

    void deleteAllByTeachingUnitId(Long teachingUnitId);
}
