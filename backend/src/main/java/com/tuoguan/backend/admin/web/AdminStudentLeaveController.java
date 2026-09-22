package com.tuoguan.backend.admin.web;

import com.tuoguan.backend.auth.security.TeacherPrincipal;
import com.tuoguan.backend.billing.domain.StudentLeaveRecord;
import com.tuoguan.backend.billing.service.BillGenerationService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

@RestController
@RequestMapping("/api/admin/students/{studentId}/leave-records")
@PreAuthorize("hasRole('ADMIN')")
public class AdminStudentLeaveController {

    private final BillGenerationService billGenerationService;

    public AdminStudentLeaveController(BillGenerationService billGenerationService) {
        this.billGenerationService = billGenerationService;
    }

    @GetMapping
    public List<StudentLeaveRecord> list(@AuthenticationPrincipal TeacherPrincipal principal,
                                          @PathVariable Long studentId, @RequestParam String month) {
        return billGenerationService.listLeaveRecords(principal.institutionId(), studentId, YearMonth.parse(month));
    }

    @PostMapping
    public List<StudentLeaveRecord> register(@AuthenticationPrincipal TeacherPrincipal principal,
                                              @PathVariable Long studentId,
                                              @Valid @RequestBody LeaveRangeRequest request) {
        return billGenerationService.registerLeaveRange(principal.institutionId(), studentId, request.startDate(),
                request.endDate(), request.reason());
    }

    @DeleteMapping("/{date}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void cancel(@AuthenticationPrincipal TeacherPrincipal principal, @PathVariable Long studentId,
                        @PathVariable String date) {
        billGenerationService.cancelLeave(principal.institutionId(), studentId, LocalDate.parse(date));
    }
}
