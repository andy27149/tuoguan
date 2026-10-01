package com.tuoguan.backend.course.service;

import com.tuoguan.backend.course.dao.CourseDao;
import com.tuoguan.backend.course.domain.Course;
import com.tuoguan.backend.course.web.DuplicateCourseNameException;
import com.tuoguan.backend.roster.web.NotFoundException;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class CourseService {

    private final CourseDao courseDao;

    public CourseService(CourseDao courseDao) {
        this.courseDao = courseDao;
    }

    public List<Course> listForTeacher(Long teacherId) {
        return courseDao.findAllByTeacherId(teacherId);
    }

    public Course getOwnedByTeacher(Long teacherId, Long courseId) {
        return courseDao.findById(courseId)
                .filter(c -> c.teacherId().equals(teacherId))
                .orElseThrow(() -> new NotFoundException("Course not found: " + courseId));
    }

    public Course create(Long teacherId, Long institutionId, String name, int lessonDurationMinutes) {
        boolean duplicate = courseDao.findAllByTeacherId(teacherId).stream()
                .anyMatch(c -> c.name().equals(name));
        if (duplicate) {
            throw new DuplicateCourseNameException("Course name already exists: " + name);
        }
        Long id = courseDao.insert(new Course(null, institutionId, teacherId, name, null, lessonDurationMinutes,
                true, null));
        return courseDao.findById(id).orElseThrow(() -> new NotFoundException("Course not found: " + id));
    }
}
