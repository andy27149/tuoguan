package com.tuoguan.backend.admin.service;

import com.tuoguan.backend.admin.web.AdminClassRoomResponse;
import com.tuoguan.backend.auth.dao.TeacherDao;
import com.tuoguan.backend.auth.domain.Teacher;
import com.tuoguan.backend.roster.dao.ClassRoomDao;
import com.tuoguan.backend.roster.domain.ClassRoom;
import com.tuoguan.backend.roster.service.ClassRoomService;
import com.tuoguan.backend.roster.web.NotFoundException;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class AdminClassRoomService {

    private final ClassRoomService classRoomService;
    private final ClassRoomDao classRoomDao;
    private final TeacherDao teacherDao;

    public AdminClassRoomService(ClassRoomService classRoomService, ClassRoomDao classRoomDao, TeacherDao teacherDao) {
        this.classRoomService = classRoomService;
        this.classRoomDao = classRoomDao;
        this.teacherDao = teacherDao;
    }

    public AdminClassRoomResponse createClassRoom(Long adminInstitutionId, Long teacherId, String name) {
        Teacher teacher = teacherDao.findById(teacherId)
                .filter(t -> t.institutionId().equals(adminInstitutionId))
                .orElseThrow(() -> new NotFoundException("Teacher not found: " + teacherId));
        ClassRoom classRoom = classRoomService.create(teacherId, adminInstitutionId, name);
        return new AdminClassRoomResponse(classRoom.id(), classRoom.name(), teacherId, teacher.phone());
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
