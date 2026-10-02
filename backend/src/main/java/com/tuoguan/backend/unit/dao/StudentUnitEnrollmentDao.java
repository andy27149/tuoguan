package com.tuoguan.backend.unit.dao;

import com.tuoguan.backend.unit.domain.StudentUnitEnrollment;

import java.util.List;
import java.util.Optional;

public interface StudentUnitEnrollmentDao {

    Long insert(StudentUnitEnrollment enrollment);

    Optional<StudentUnitEnrollment> findByStudentIdAndTeachingUnitId(Long studentId, Long teachingUnitId);

    List<StudentUnitEnrollment> findAllByTeachingUnitId(Long teachingUnitId);

    List<StudentUnitEnrollment> findAllByStudentId(Long studentId);

    void setActive(Long id, boolean active);

    void deleteAllByStudentId(Long studentId);

    void deleteAllByTeachingUnitId(Long teachingUnitId);
}
