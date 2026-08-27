package com.tuoguan.backend.admin.service;

import com.tuoguan.backend.admin.web.DuplicatePhoneException;
import com.tuoguan.backend.auth.dao.TeacherDao;
import com.tuoguan.backend.auth.domain.Role;
import com.tuoguan.backend.auth.domain.Teacher;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class AdminTeacherService {

    private final TeacherDao teacherDao;
    private final PasswordEncoder passwordEncoder;

    public AdminTeacherService(TeacherDao teacherDao, PasswordEncoder passwordEncoder) {
        this.teacherDao = teacherDao;
        this.passwordEncoder = passwordEncoder;
    }

    public Teacher createTeacher(Long institutionId, String phone, String name, String initialPassword) {
        if (teacherDao.findByPhone(phone).isPresent()) {
            throw new DuplicatePhoneException("Phone already registered: " + phone);
        }
        Long id = teacherDao.insert(new Teacher(null, institutionId, phone, name,
                passwordEncoder.encode(initialPassword), Role.TEACHER, true, null));
        return teacherDao.findById(id)
                .orElseThrow(() -> new IllegalStateException("Teacher not found after insert: " + id));
    }

    public List<Teacher> listTeachers(Long institutionId) {
        return teacherDao.findAllByInstitutionId(institutionId);
    }
}
