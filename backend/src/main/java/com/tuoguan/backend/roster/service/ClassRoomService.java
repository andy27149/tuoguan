package com.tuoguan.backend.roster.service;

import com.tuoguan.backend.roster.dao.ClassRoomDao;
import com.tuoguan.backend.roster.domain.ClassRoom;
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
}
