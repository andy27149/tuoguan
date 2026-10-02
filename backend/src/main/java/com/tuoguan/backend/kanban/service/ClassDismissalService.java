package com.tuoguan.backend.kanban.service;

import com.tuoguan.backend.kanban.dao.ClassDismissalDao;
import com.tuoguan.backend.kanban.domain.ClassDismissal;
import com.tuoguan.backend.roster.service.ClassRoomService;
import com.tuoguan.backend.unit.domain.TeachingUnit;
import org.springframework.stereotype.Service;

import java.time.LocalDate;

@Service
public class ClassDismissalService {

    private final ClassDismissalDao classDismissalDao;
    private final ClassRoomService classRoomService;

    public ClassDismissalService(ClassDismissalDao classDismissalDao, ClassRoomService classRoomService) {
        this.classDismissalDao = classDismissalDao;
        this.classRoomService = classRoomService;
    }

    public void dismiss(Long teacherId, Long teachingUnitId, LocalDate date) {
        TeachingUnit teachingUnit = classRoomService.getOwnedByTeacher(teacherId, teachingUnitId);
        if (classDismissalDao.findByTeachingUnitIdAndDate(teachingUnitId, date).isEmpty()) {
            classDismissalDao.insert(new ClassDismissal(null, teachingUnit.institutionId(), teachingUnitId, date, null));
        }
    }

    public void undoDismiss(Long teacherId, Long teachingUnitId, LocalDate date) {
        classRoomService.getOwnedByTeacher(teacherId, teachingUnitId);
        classDismissalDao.deleteByTeachingUnitIdAndDate(teachingUnitId, date);
    }

    public boolean isDismissed(Long teacherId, Long teachingUnitId, LocalDate date) {
        classRoomService.getOwnedByTeacher(teacherId, teachingUnitId);
        return classDismissalDao.findByTeachingUnitIdAndDate(teachingUnitId, date).isPresent();
    }
}
