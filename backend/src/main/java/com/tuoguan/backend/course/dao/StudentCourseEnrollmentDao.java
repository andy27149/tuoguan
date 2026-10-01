package com.tuoguan.backend.course.dao;

import com.tuoguan.backend.course.domain.StudentCourseEnrollment;

import java.util.List;
import java.util.Optional;

public interface StudentCourseEnrollmentDao {

    Long insert(StudentCourseEnrollment enrollment);

    Optional<StudentCourseEnrollment> findByStudentIdAndCourseId(Long studentId, Long courseId);

    List<StudentCourseEnrollment> findAllByCourseId(Long courseId);

    List<StudentCourseEnrollment> findAllByStudentId(Long studentId);

    void setActive(Long id, boolean active);

    void deleteAllByStudentId(Long studentId);

    void deleteAllByCourseId(Long courseId);
}
