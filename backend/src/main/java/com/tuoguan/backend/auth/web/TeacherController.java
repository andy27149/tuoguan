package com.tuoguan.backend.auth.web;

import com.tuoguan.backend.auth.dao.TeacherDao;
import com.tuoguan.backend.auth.domain.Teacher;
import com.tuoguan.backend.auth.security.TeacherPrincipal;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class TeacherController {

    private final TeacherDao teacherDao;

    public TeacherController(TeacherDao teacherDao) {
        this.teacherDao = teacherDao;
    }

    @GetMapping("/api/teachers/me")
    public TeacherMeResponse me(@AuthenticationPrincipal TeacherPrincipal principal) {
        Teacher teacher = teacherDao.findById(principal.teacherId())
                .orElseThrow(() -> new IllegalStateException("Authenticated teacher not found: " + principal.teacherId()));
        return new TeacherMeResponse(teacher.id(), teacher.phone(), teacher.institutionId(), teacher.role());
    }
}
