package com.tuoguan.backend.auth.dao;

import com.tuoguan.backend.auth.domain.Teacher;

import java.util.Optional;

public interface TeacherDao {

    Long insert(Teacher teacher);

    Optional<Teacher> findByPhone(String phone);

    Optional<Teacher> findById(Long id);

    void updatePassword(Long teacherId, String newPasswordHash);
}
