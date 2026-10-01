package com.tuoguan.backend.course.service;

import com.tuoguan.backend.auth.dao.TeacherDao;
import com.tuoguan.backend.auth.domain.Teacher;
import com.tuoguan.backend.course.dao.CourseDao;
import com.tuoguan.backend.course.domain.Course;
import com.tuoguan.backend.course.web.AdminCourseResponse;
import com.tuoguan.backend.roster.web.NotFoundException;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class AdminCourseService {

    private final CourseDao courseDao;
    private final TeacherDao teacherDao;

    public AdminCourseService(CourseDao courseDao, TeacherDao teacherDao) {
        this.courseDao = courseDao;
        this.teacherDao = teacherDao;
    }

    public List<AdminCourseResponse> listCourses(Long institutionId) {
        Map<Long, Teacher> teacherById = teacherDao.findAllByInstitutionId(institutionId).stream()
                .collect(Collectors.toMap(Teacher::id, t -> t));
        return courseDao.findAllByInstitutionId(institutionId).stream()
                .map(c -> {
                    Teacher teacher = teacherById.get(c.teacherId());
                    return AdminCourseResponse.from(c, teacher != null ? teacher.name() : "-",
                            teacher != null ? teacher.phone() : "-");
                })
                .toList();
    }

    public void setPrice(Long institutionId, Long courseId, BigDecimal pricePerLesson) {
        requireCourseInInstitution(institutionId, courseId);
        courseDao.setPrice(courseId, pricePerLesson);
    }

    public void setActive(Long institutionId, Long courseId, boolean active) {
        requireCourseInInstitution(institutionId, courseId);
        courseDao.setActive(courseId, active);
    }

    public void reassignTeacher(Long institutionId, Long courseId, Long teacherId) {
        requireCourseInInstitution(institutionId, courseId);
        Teacher teacher = teacherDao.findById(teacherId)
                .orElseThrow(() -> new NotFoundException("Teacher not found: " + teacherId));
        if (!teacher.institutionId().equals(institutionId)) {
            throw new NotFoundException("Teacher not found: " + teacherId);
        }
        courseDao.reassignTeacher(courseId, teacherId);
    }

    private Course requireCourseInInstitution(Long institutionId, Long courseId) {
        Course course = courseDao.findById(courseId)
                .orElseThrow(() -> new NotFoundException("Course not found: " + courseId));
        if (!course.institutionId().equals(institutionId)) {
            throw new NotFoundException("Course not found: " + courseId);
        }
        return course;
    }
}
