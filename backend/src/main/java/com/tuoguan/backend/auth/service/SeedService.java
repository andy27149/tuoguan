package com.tuoguan.backend.auth.service;

import com.tuoguan.backend.auth.dao.InstitutionDao;
import com.tuoguan.backend.auth.dao.TeacherDao;
import com.tuoguan.backend.auth.domain.Role;
import com.tuoguan.backend.auth.domain.Teacher;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class SeedService {

    private final InstitutionDao institutionDao;
    private final TeacherDao teacherDao;
    private final PasswordEncoder passwordEncoder;

    public SeedService(InstitutionDao institutionDao, TeacherDao teacherDao, PasswordEncoder passwordEncoder) {
        this.institutionDao = institutionDao;
        this.teacherDao = teacherDao;
        this.passwordEncoder = passwordEncoder;
    }

    public void seed(String institutionName, String phone, String password) {
        if (teacherDao.findByPhone(phone).isPresent()) {
            throw new IllegalStateException("Teacher with phone already exists: " + phone);
        }
        Long institutionId = institutionDao.insert(institutionName);
        Teacher teacher = new Teacher(null, institutionId, phone, passwordEncoder.encode(password),
                Role.ADMIN, true, null);
        teacherDao.insert(teacher);
    }
}
