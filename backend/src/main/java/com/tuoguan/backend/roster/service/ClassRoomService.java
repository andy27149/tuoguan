package com.tuoguan.backend.roster.service;

import com.tuoguan.backend.roster.web.DuplicateClassNameException;
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

    public TeachingUnit create(Long teacherId, Long institutionId, String name) {
        boolean duplicate = teachingUnitDao.findAllByTeacherId(teacherId).stream()
                .filter(u -> u.billingMode() == BillingMode.MONTHLY)
                .anyMatch(c -> c.name().equals(name));
        if (duplicate) {
            throw new DuplicateClassNameException("Class name already exists: " + name);
        }
        Long id = teachingUnitDao.insert(
                new TeachingUnit(null, institutionId, teacherId, name, BillingMode.MONTHLY, null, null, true, null));
        return teachingUnitDao.findById(id).orElseThrow(() -> new NotFoundException("Class not found: " + id));
    }
}
