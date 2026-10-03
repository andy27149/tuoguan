package com.tuoguan.backend.roster.service;

import com.tuoguan.backend.roster.web.NotFoundException;
import com.tuoguan.backend.unit.dao.TeachingUnitDao;
import com.tuoguan.backend.unit.domain.BillingMode;
import com.tuoguan.backend.unit.domain.TeachingUnit;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ClassRoomService {

    private final TeachingUnitDao teachingUnitDao;

    public ClassRoomService(TeachingUnitDao teachingUnitDao) {
        this.teachingUnitDao = teachingUnitDao;
    }

    public List<TeachingUnit> listForTeacher(Long teacherId) {
        return teachingUnitDao.findAllByTeacherId(teacherId).stream()
                .filter(u -> u.billingMode() == BillingMode.MONTHLY)
                .toList();
    }

    public TeachingUnit getOwnedByTeacher(Long teacherId, Long classRoomId) {
        return teachingUnitDao.findById(classRoomId)
                .filter(c -> c.teacherId().equals(teacherId))
                .filter(c -> c.billingMode() == BillingMode.MONTHLY)
                .orElseThrow(() -> new NotFoundException("Class not found: " + classRoomId));
    }
}
