package com.tuoguan.backend.admin.web;

import com.tuoguan.backend.admin.service.AdminClassRoomService;
import com.tuoguan.backend.auth.security.TeacherPrincipal;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
public class AdminClassRoomController {

    private final AdminClassRoomService adminClassRoomService;

    public AdminClassRoomController(AdminClassRoomService adminClassRoomService) {
        this.adminClassRoomService = adminClassRoomService;
    }

    @GetMapping("/api/admin/classes")
    @PreAuthorize("hasRole('ADMIN')")
    public List<AdminClassRoomResponse> list(@AuthenticationPrincipal TeacherPrincipal principal) {
        return adminClassRoomService.listClassRooms(principal.institutionId());
    }
}
