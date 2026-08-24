package com.tuoguan.backend.admin.web;

import com.tuoguan.backend.admin.service.AdminClassRoomService;
import com.tuoguan.backend.auth.security.TeacherPrincipal;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
public class AdminClassRoomController {

    private final AdminClassRoomService adminClassRoomService;

    public AdminClassRoomController(AdminClassRoomService adminClassRoomService) {
        this.adminClassRoomService = adminClassRoomService;
    }

    @PostMapping("/api/admin/classes")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMIN')")
    public AdminClassRoomResponse create(@AuthenticationPrincipal TeacherPrincipal principal,
                                          @Valid @RequestBody CreateAdminClassRequest request) {
        return adminClassRoomService.createClassRoom(principal.institutionId(), request.teacherId(), request.name());
    }

    @GetMapping("/api/admin/classes")
    @PreAuthorize("hasRole('ADMIN')")
    public List<AdminClassRoomResponse> list(@AuthenticationPrincipal TeacherPrincipal principal) {
        return adminClassRoomService.listClassRooms(principal.institutionId());
    }
}
