package com.tuoguan.backend.course.web;

import com.tuoguan.backend.admin.web.CourseConsumptionSummaryRow;
import com.tuoguan.backend.auth.security.TeacherPrincipal;
import com.tuoguan.backend.billing.service.BillGenerationService;
import com.tuoguan.backend.course.service.CourseAccountService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.YearMonth;
import java.util.List;

@RestController
public class AdminStudentAccountController {

    private final CourseAccountService courseAccountService;
    private final BillGenerationService billGenerationService;

    public AdminStudentAccountController(CourseAccountService courseAccountService,
                                          BillGenerationService billGenerationService) {
        this.courseAccountService = courseAccountService;
        this.billGenerationService = billGenerationService;
    }

    @PostMapping("/api/admin/students/{id}/recharges")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMIN')")
    public RechargeRecordResponse recharge(@AuthenticationPrincipal TeacherPrincipal principal,
                                            @PathVariable Long id, @Valid @RequestBody RechargeRequest request) {
        return RechargeRecordResponse.from(courseAccountService.recharge(principal.institutionId(), id,
                principal.teacherId(), request.courseId(), request.lessonCount(), request.note()));
    }

    @GetMapping("/api/admin/students/{id}/course-statement")
    @PreAuthorize("hasRole('ADMIN')")
    public StudentCourseStatement statement(@AuthenticationPrincipal TeacherPrincipal principal,
                                             @PathVariable Long id) {
        return courseAccountService.getStatement(principal.institutionId(), id);
    }

    @GetMapping("/api/admin/students/{id}/course-consumption")
    @PreAuthorize("hasRole('ADMIN')")
    public List<CourseConsumptionSummaryRow> courseConsumption(@AuthenticationPrincipal TeacherPrincipal principal,
                                                                 @PathVariable Long id, @RequestParam String month) {
        return billGenerationService.listCourseConsumptionForMonth(principal.institutionId(), id,
                YearMonth.parse(month));
    }
}
