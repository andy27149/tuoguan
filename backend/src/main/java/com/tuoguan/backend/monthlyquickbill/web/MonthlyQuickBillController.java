package com.tuoguan.backend.monthlyquickbill.web;

import com.tuoguan.backend.auth.security.TeacherPrincipal;
import com.tuoguan.backend.monthlyquickbill.service.MonthlyQuickBillService;
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

import java.util.List;

// 月度批量录入+账单生成的入口：运营人员选一个已有班级，按学生填"本月用餐天数/课外课节数/
// 请假天数"几个聚合数字，不需要逐日打卡。生成账单复用现有 AdminClassBillingController
// 的 /api/admin/classes/{classId}/bills/generate，这里不重复实现。
@RestController
@PreAuthorize("hasRole('ADMIN')")
public class MonthlyQuickBillController {

    private final MonthlyQuickBillService monthlyQuickBillService;

    public MonthlyQuickBillController(MonthlyQuickBillService monthlyQuickBillService) {
        this.monthlyQuickBillService = monthlyQuickBillService;
    }

    @GetMapping("/api/admin/monthly-quick-bill/classes/{classId}/students")
    public List<QuickBillStudentResponse> listStudents(@AuthenticationPrincipal TeacherPrincipal principal,
                                                         @PathVariable Long classId, @RequestParam String month) {
        return monthlyQuickBillService.listStudents(principal.institutionId(), classId, month);
    }

    @PostMapping("/api/admin/monthly-quick-bill/students/{studentId}/submit")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void submit(@AuthenticationPrincipal TeacherPrincipal principal, @PathVariable Long studentId,
                        @Valid @RequestBody QuickBillSubmitRequest request) {
        monthlyQuickBillService.submit(principal.institutionId(), studentId, request);
    }
}
