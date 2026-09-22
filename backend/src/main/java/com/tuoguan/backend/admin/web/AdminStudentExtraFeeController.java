package com.tuoguan.backend.admin.web;

import com.tuoguan.backend.auth.security.TeacherPrincipal;
import com.tuoguan.backend.billing.domain.StudentExtraFee;
import com.tuoguan.backend.billing.service.BillGenerationService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.YearMonth;
import java.util.List;

@RestController
@RequestMapping("/api/admin/students/{studentId}/extra-fees")
@PreAuthorize("hasRole('ADMIN')")
public class AdminStudentExtraFeeController {

    private final BillGenerationService billGenerationService;

    public AdminStudentExtraFeeController(BillGenerationService billGenerationService) {
        this.billGenerationService = billGenerationService;
    }

    @GetMapping
    public List<StudentExtraFeeRow> list(@AuthenticationPrincipal TeacherPrincipal principal,
                                          @PathVariable Long studentId, @RequestParam String month) {
        return billGenerationService.listExtraFeesForMonth(principal.institutionId(), studentId,
                YearMonth.parse(month));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public StudentExtraFee add(@AuthenticationPrincipal TeacherPrincipal principal, @PathVariable Long studentId,
                                @Valid @RequestBody AddExtraFeeRequest request) {
        return billGenerationService.addExtraFee(principal.institutionId(), studentId, request.name(),
                request.pricePerLesson());
    }

    @PutMapping("/{feeId}/lesson-count")
    public StudentExtraFeeRow setLessonCount(@AuthenticationPrincipal TeacherPrincipal principal,
                                              @PathVariable Long studentId, @PathVariable Long feeId,
                                              @RequestParam String month,
                                              @Valid @RequestBody SetExtraFeeLessonCountRequest request) {
        return billGenerationService.setExtraFeeLessonCount(principal.institutionId(), studentId, feeId,
                YearMonth.parse(month), request.lessonCount());
    }

    @DeleteMapping("/{feeId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal TeacherPrincipal principal, @PathVariable Long studentId,
                        @PathVariable Long feeId) {
        billGenerationService.deleteExtraFee(principal.institutionId(), studentId, feeId);
    }
}
