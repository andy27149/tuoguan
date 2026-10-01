package com.tuoguan.backend.admin.service;

import com.tuoguan.backend.admin.web.AdminStudentResponse;
import com.tuoguan.backend.course.dao.CourseDao;
import com.tuoguan.backend.course.dao.StudentCourseEnrollmentDao;
import com.tuoguan.backend.course.domain.Course;
import com.tuoguan.backend.course.domain.StudentCourseEnrollment;
import com.tuoguan.backend.roster.dao.ClassRoomDao;
import com.tuoguan.backend.roster.dao.StudentDao;
import com.tuoguan.backend.roster.domain.ClassRoom;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
public class AdminStudentService {

    private final StudentDao studentDao;
    private final ClassRoomDao classRoomDao;
    private final StudentCourseEnrollmentDao enrollmentDao;
    private final CourseDao courseDao;

    public AdminStudentService(StudentDao studentDao, ClassRoomDao classRoomDao,
                                StudentCourseEnrollmentDao enrollmentDao, CourseDao courseDao) {
        this.studentDao = studentDao;
        this.classRoomDao = classRoomDao;
        this.enrollmentDao = enrollmentDao;
        this.courseDao = courseDao;
    }

    public List<AdminStudentResponse> listStudents(Long institutionId) {
        Map<Long, ClassRoom> classRoomById = classRoomDao.findAllByInstitutionId(institutionId).stream()
                .collect(Collectors.toMap(ClassRoom::id, c -> c));
        Map<Long, String> courseNameById = courseDao.findAllByInstitutionId(institutionId).stream()
                .collect(Collectors.toMap(Course::id, Course::name));
        return studentDao.findAllByInstitutionId(institutionId).stream()
                .map(student -> {
                    ClassRoom classRoom = student.classRoomId() != null ? classRoomById.get(student.classRoomId())
                            : null;
                    List<String> enrolledCourseNames = enrollmentDao.findAllByStudentId(student.id()).stream()
                            .filter(StudentCourseEnrollment::active)
                            .map(e -> courseNameById.get(e.courseId()))
                            .filter(Objects::nonNull)
                            .toList();
                    return new AdminStudentResponse(student.id(), student.name(), student.schoolClassName(),
                            student.classRoomId(), classRoom != null ? classRoom.name() : null,
                            student.classRoomId() == null, enrolledCourseNames);
                })
                .toList();
    }
}
