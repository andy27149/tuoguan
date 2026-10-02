package com.tuoguan.backend.course.service;

import com.tuoguan.backend.roster.web.NotFoundException;
import com.tuoguan.backend.unit.dao.TeachingUnitDao;
import com.tuoguan.backend.unit.domain.BillingMode;
import com.tuoguan.backend.unit.domain.TeachingUnit;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class CourseService {

    private final TeachingUnitDao teachingUnitDao;

    public CourseService(TeachingUnitDao teachingUnitDao) {
        this.teachingUnitDao = teachingUnitDao;
    }

    public List<TeachingUnit> listForTeacher(Long teacherId) {
        return teachingUnitDao.findAllByTeacherId(teacherId).stream()
                .filter(u -> u.billingMode() == BillingMode.LESSON_COUNT)
                .toList();
    }

    public TeachingUnit getOwnedByTeacher(Long teacherId, Long courseId) {
        return teachingUnitDao.findById(courseId)
                .filter(c -> c.teacherId().equals(teacherId))
                .filter(c -> c.billingMode() == BillingMode.LESSON_COUNT)
                .orElseThrow(() -> new NotFoundException("Course not found: " + courseId));
    }

}
