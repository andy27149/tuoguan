package com.tuoguan.backend.course.service;

import com.tuoguan.backend.auth.dao.TeacherDao;
import com.tuoguan.backend.auth.domain.Teacher;
import com.tuoguan.backend.course.web.AdminCourseResponse;
import com.tuoguan.backend.course.web.DuplicateCourseNameException;
import com.tuoguan.backend.roster.web.NotFoundException;
import com.tuoguan.backend.unit.dao.TeachingUnitDao;
import com.tuoguan.backend.unit.domain.BillingMode;
import com.tuoguan.backend.unit.domain.TeachingUnit;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class AdminCourseService {

    private final TeachingUnitDao teachingUnitDao;
    private final TeacherDao teacherDao;

    public AdminCourseService(TeachingUnitDao teachingUnitDao, TeacherDao teacherDao) {
        this.teachingUnitDao = teachingUnitDao;
        this.teacherDao = teacherDao;
    }

    public List<AdminCourseResponse> listCourses(Long institutionId) {
        Map<Long, Teacher> teacherById = teacherDao.findAllByInstitutionId(institutionId).stream()
                .collect(Collectors.toMap(Teacher::id, t -> t));
        return teachingUnitDao.findAllByInstitutionId(institutionId).stream()
                .filter(c -> c.billingMode() == BillingMode.LESSON_COUNT)
                .map(c -> {
                    Teacher teacher = teacherById.get(c.teacherId());
                    return AdminCourseResponse.from(c, teacher != null ? teacher.name() : "-",
                            teacher != null ? teacher.phone() : "-");
                })
                .toList();
    }

    public AdminCourseResponse createCourse(Long institutionId, String name, int lessonDurationMinutes,
                                             Long teacherId, BigDecimal pricePerLesson) {
        Teacher teacher = teacherDao.findById(teacherId)
                .orElseThrow(() -> new NotFoundException("Teacher not found: " + teacherId));
        if (!teacher.institutionId().equals(institutionId)) {
            throw new NotFoundException("Teacher not found: " + teacherId);
        }
        boolean duplicate = teachingUnitDao.findAllByInstitutionId(institutionId).stream()
                .filter(c -> c.billingMode() == BillingMode.LESSON_COUNT)
                .anyMatch(c -> c.name().equals(name));
        if (duplicate) {
            throw new DuplicateCourseNameException("Course name already exists: " + name);
        }
        Long id = teachingUnitDao.insert(new TeachingUnit(null, institutionId, teacherId, name,
                BillingMode.LESSON_COUNT, lessonDurationMinutes, pricePerLesson, true, null));
        TeachingUnit created = teachingUnitDao.findById(id)
                .orElseThrow(() -> new NotFoundException("Course not found: " + id));
        return AdminCourseResponse.from(created, teacher.name(), teacher.phone());
    }

    public void setPrice(Long institutionId, Long courseId, BigDecimal pricePerLesson) {
        requireCourseInInstitution(institutionId, courseId);
        teachingUnitDao.setPrice(courseId, pricePerLesson);
    }

    public void setActive(Long institutionId, Long courseId, boolean active) {
        requireCourseInInstitution(institutionId, courseId);
        teachingUnitDao.setActive(courseId, active);
    }

    public void reassignTeacher(Long institutionId, Long courseId, Long teacherId) {
        requireCourseInInstitution(institutionId, courseId);
        Teacher teacher = teacherDao.findById(teacherId)
                .orElseThrow(() -> new NotFoundException("Teacher not found: " + teacherId));
        if (!teacher.institutionId().equals(institutionId)) {
            throw new NotFoundException("Teacher not found: " + teacherId);
        }
        teachingUnitDao.reassignTeacher(courseId, teacherId);
    }

    private TeachingUnit requireCourseInInstitution(Long institutionId, Long courseId) {
        TeachingUnit course = teachingUnitDao.findById(courseId)
                .filter(c -> c.billingMode() == BillingMode.LESSON_COUNT)
                .orElseThrow(() -> new NotFoundException("Course not found: " + courseId));
        if (!course.institutionId().equals(institutionId)) {
            throw new NotFoundException("Course not found: " + courseId);
        }
        return course;
    }
}
