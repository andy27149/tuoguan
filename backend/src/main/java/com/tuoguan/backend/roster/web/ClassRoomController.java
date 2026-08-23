package com.tuoguan.backend.roster.web;

import com.tuoguan.backend.auth.security.TeacherPrincipal;
import com.tuoguan.backend.roster.service.ClassRoomService;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
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

    @PostMapping("/api/classes")
    @ResponseStatus(HttpStatus.CREATED)
    public ClassRoomResponse create(@AuthenticationPrincipal TeacherPrincipal principal,
                                     @RequestBody CreateClassRequest request) {
        return ClassRoomResponse.from(
                classRoomService.create(principal.teacherId(), principal.institutionId(), request.name()));
    }
}
