package com.tuoguan.backend.kanban.service;

import com.tuoguan.backend.kanban.dao.ClassDismissalDao;
import com.tuoguan.backend.kanban.domain.ClassDismissal;
import com.tuoguan.backend.roster.domain.ClassRoom;
import com.tuoguan.backend.roster.service.ClassRoomService;
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

    public void dismiss(Long teacherId, Long classRoomId, LocalDate date) {
        ClassRoom classRoom = classRoomService.getOwnedByTeacher(teacherId, classRoomId);
        if (classDismissalDao.findByClassRoomIdAndDate(classRoomId, date).isEmpty()) {
            classDismissalDao.insert(new ClassDismissal(null, classRoom.institutionId(), classRoomId, date, null));
        }
    }

    public void undoDismiss(Long teacherId, Long classRoomId, LocalDate date) {
        classRoomService.getOwnedByTeacher(teacherId, classRoomId);
        classDismissalDao.deleteByClassRoomIdAndDate(classRoomId, date);
    }

    public boolean isDismissed(Long teacherId, Long classRoomId, LocalDate date) {
        classRoomService.getOwnedByTeacher(teacherId, classRoomId);
        return classDismissalDao.findByClassRoomIdAndDate(classRoomId, date).isPresent();
    }
}
