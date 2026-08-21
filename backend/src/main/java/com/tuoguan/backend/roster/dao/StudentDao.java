package com.tuoguan.backend.roster.dao;

import com.tuoguan.backend.roster.domain.Student;

import java.util.List;
import java.util.Optional;

public interface StudentDao {

    Long insert(Student student);

    Optional<Student> findById(Long id);

    List<Student> findAllByClassRoomId(Long classRoomId);

    void update(Student student);

    void updateAvatarObjectKey(Long id, String avatarObjectKey);

    void deleteById(Long id);
}
