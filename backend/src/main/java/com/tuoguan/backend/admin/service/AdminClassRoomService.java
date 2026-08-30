package com.tuoguan.backend.admin.service;

import com.tuoguan.backend.admin.web.AdminClassRoomResponse;
import com.tuoguan.backend.auth.dao.TeacherDao;
import com.tuoguan.backend.auth.domain.Teacher;
import com.tuoguan.backend.roster.dao.ClassRoomDao;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class AdminClassRoomService {

    private final ClassRoomDao classRoomDao;
    private final TeacherDao teacherDao;

    public AdminClassRoomService(ClassRoomDao classRoomDao, TeacherDao teacherDao) {
        this.classRoomDao = classRoomDao;
        this.teacherDao = teacherDao;
    }

    public List<AdminClassRoomResponse> listClassRooms(Long institutionId) {
        Map<Long, String> phoneByTeacherId = teacherDao.findAllByInstitutionId(institutionId).stream()
                .collect(Collectors.toMap(Teacher::id, Teacher::phone));
        return classRoomDao.findAllByInstitutionId(institutionId).stream()
                .map(c -> new AdminClassRoomResponse(c.id(), c.name(), c.teacherId(),
                        phoneByTeacherId.getOrDefault(c.teacherId(), "-")))
                .toList();
    }
}
