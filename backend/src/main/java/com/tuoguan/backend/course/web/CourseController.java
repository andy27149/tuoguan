package com.tuoguan.backend.course.web;

import com.tuoguan.backend.auth.security.TeacherPrincipal;
import com.tuoguan.backend.course.service.CourseConsumptionService;
import com.tuoguan.backend.course.service.CourseService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

@RestController
public class CourseController {

    private final CourseService courseService;
    private final CourseConsumptionService courseConsumptionService;

    public CourseController(CourseService courseService, CourseConsumptionService courseConsumptionService) {
        this.courseService = courseService;
        this.courseConsumptionService = courseConsumptionService;
    }

    @GetMapping("/api/courses")
    public List<CourseResponse> list(@AuthenticationPrincipal TeacherPrincipal principal) {
        return courseService.listForTeacher(principal.teacherId()).stream()
                .map(CourseResponse::from)
                .toList();
    }

    @GetMapping("/api/courses/{id}/roster")
    public List<CourseRosterEntry> roster(@AuthenticationPrincipal TeacherPrincipal principal,
                                           @PathVariable Long id) {
        return courseConsumptionService.listRosterForCourse(principal.teacherId(), id);
    }

    @PostMapping("/api/courses/{id}/consumption")
    @ResponseStatus(HttpStatus.CREATED)
    public ConsumptionRecordResponse recordConsumption(@AuthenticationPrincipal TeacherPrincipal principal,
                                                         @PathVariable Long id,
                                                         @Valid @RequestBody RecordConsumptionRequest request) {
        return ConsumptionRecordResponse.from(courseConsumptionService.recordConsumption(principal.teacherId(), id,
                request.studentId(), request.date(), request.confirm()));
    }

    @PostMapping("/api/courses/{id}/consumption/batch")
    @ResponseStatus(HttpStatus.CREATED)
    public List<ConsumptionRecordResponse> recordBatchConsumption(@AuthenticationPrincipal TeacherPrincipal principal,
                                                                    @PathVariable Long id,
                                                                    @Valid @RequestBody BatchRecordConsumptionRequest request) {
        return courseConsumptionService
                .recordBatchConsumption(principal.teacherId(), id, request.date(), request.presentStudentIds())
                .stream()
                .map(ConsumptionRecordResponse::from)
                .toList();
    }

    // 只对纯课外课学生生效（双重身份学生的用餐走托管看板），点击立即生效，交互上
    // 和托管看板的"用餐"图标一致，不是先勾选再批量提交确认。
    @PatchMapping("/api/courses/{id}/students/{studentId}/meal")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void setMeal(@AuthenticationPrincipal TeacherPrincipal principal,
                         @PathVariable Long id,
                         @PathVariable Long studentId,
                         @Valid @RequestBody SetMealRequest request) {
        courseConsumptionService.setMeal(principal.teacherId(), id, studentId, request.date());
    }

    @DeleteMapping("/api/courses/{id}/students/{studentId}/meal")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void clearMeal(@AuthenticationPrincipal TeacherPrincipal principal,
                           @PathVariable Long id,
                           @PathVariable Long studentId,
                           @RequestParam LocalDate date) {
        courseConsumptionService.clearMeal(principal.teacherId(), id, studentId, date);
    }
}
