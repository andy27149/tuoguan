package com.tuoguan.backend.roster.dao;

import com.tuoguan.backend.roster.domain.ClassRoom;

import java.util.List;
import java.util.Optional;

public interface ClassRoomDao {

    Long insert(ClassRoom classRoom);

    Optional<ClassRoom> findById(Long id);

    List<ClassRoom> findAllByTeacherId(Long teacherId);

    List<ClassRoom> findAllByInstitutionId(Long institutionId);
}
