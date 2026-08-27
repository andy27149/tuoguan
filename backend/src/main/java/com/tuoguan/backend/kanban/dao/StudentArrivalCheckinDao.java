package com.tuoguan.backend.kanban.dao;

import com.tuoguan.backend.kanban.domain.StudentArrivalCheckin;

import java.time.LocalDate;
import java.util.List;

public interface StudentArrivalCheckinDao {

    List<StudentArrivalCheckin> findAllByClassRoomIdAndDate(Long classRoomId, LocalDate date);

    List<StudentArrivalCheckin> findAllByStudentIdAndDateRange(Long studentId, LocalDate start, LocalDate end);

    void upsert(Long institutionId, Long classRoomId, Long studentId, LocalDate date, String arrivedAt);

    void clear(Long studentId, LocalDate date);
}
