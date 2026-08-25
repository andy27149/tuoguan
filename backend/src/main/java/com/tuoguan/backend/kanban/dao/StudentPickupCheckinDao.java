package com.tuoguan.backend.kanban.dao;

import com.tuoguan.backend.kanban.domain.StudentPickupCheckin;

import java.time.LocalDate;
import java.util.List;

public interface StudentPickupCheckinDao {

    List<StudentPickupCheckin> findAllByClassRoomIdAndDate(Long classRoomId, LocalDate date);

    List<StudentPickupCheckin> findAllByStudentIdAndDateRange(Long studentId, LocalDate start, LocalDate end);

    void upsert(Long institutionId, Long classRoomId, Long studentId, LocalDate date, String pickedUpBy,
                String pickedUpAt);
}
