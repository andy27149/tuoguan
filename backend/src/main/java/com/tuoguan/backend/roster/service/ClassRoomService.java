package com.tuoguan.backend.roster.service;

import com.tuoguan.backend.roster.dao.ClassRoomDao;
import com.tuoguan.backend.roster.domain.ClassRoom;
import com.tuoguan.backend.roster.web.DuplicateClassNameException;
import com.tuoguan.backend.roster.web.NotFoundException;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ClassRoomService {

    private final ClassRoomDao classRoomDao;

    public ClassRoomService(ClassRoomDao classRoomDao) {
        this.classRoomDao = classRoomDao;
    }

    public List<ClassRoom> listForTeacher(Long teacherId) {
        return classRoomDao.findAllByTeacherId(teacherId);
    }

    public ClassRoom getOwnedByTeacher(Long teacherId, Long classRoomId) {
        return classRoomDao.findById(classRoomId)
                .filter(c -> c.teacherId().equals(teacherId))
                .orElseThrow(() -> new NotFoundException("Class not found: " + classRoomId));
    }

    public ClassRoom create(Long teacherId, Long institutionId, String name) {
        boolean duplicate = classRoomDao.findAllByTeacherId(teacherId).stream()
                .anyMatch(c -> c.name().equals(name));
        if (duplicate) {
            throw new DuplicateClassNameException("Class name already exists: " + name);
        }
        Long id = classRoomDao.insert(new ClassRoom(null, institutionId, teacherId, name, null));
        return classRoomDao.findById(id).orElseThrow(() -> new NotFoundException("Class not found: " + id));
    }
}
