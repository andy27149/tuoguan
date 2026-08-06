package com.tuoguan.backend.roster.web;

import com.tuoguan.backend.auth.security.TeacherPrincipal;
import com.tuoguan.backend.roster.service.ClassRoomService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
public class ClassRoomController {

    private final ClassRoomService classRoomService;

    public ClassRoomController(ClassRoomService classRoomService) {
        this.classRoomService = classRoomService;
    }

    @GetMapping("/api/classes")
    public List<ClassRoomResponse> list(@AuthenticationPrincipal TeacherPrincipal principal) {
        return classRoomService.listForTeacher(principal.teacherId()).stream()
                .map(ClassRoomResponse::from)
                .toList();
    }
}
