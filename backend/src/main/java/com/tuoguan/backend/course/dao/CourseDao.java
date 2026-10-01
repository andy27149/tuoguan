package com.tuoguan.backend.course.dao;

import com.tuoguan.backend.course.domain.Course;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

public interface CourseDao {

    Long insert(Course course);

    Optional<Course> findById(Long id);

    List<Course> findAllByTeacherId(Long teacherId);

    List<Course> findAllByInstitutionId(Long institutionId);

    void setPrice(Long id, BigDecimal pricePerLesson);

    void setActive(Long id, boolean active);

    void reassignTeacher(Long courseId, Long teacherId);

    void reassignAllTeacher(Long oldTeacherId, Long newTeacherId);

    void deleteById(Long id);
}
