package com.tuoguan.backend.course.web;

import com.tuoguan.backend.auth.security.TeacherPrincipal;
import com.tuoguan.backend.course.service.CourseConsumptionService;
import com.tuoguan.backend.course.service.CourseEnrollmentService;
import com.tuoguan.backend.course.service.CourseService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
public class CourseController {

    private final CourseService courseService;
    private final CourseEnrollmentService courseEnrollmentService;
    private final CourseConsumptionService courseConsumptionService;

    public CourseController(CourseService courseService, CourseEnrollmentService courseEnrollmentService,
                             CourseConsumptionService courseConsumptionService) {
        this.courseService = courseService;
        this.courseEnrollmentService = courseEnrollmentService;
        this.courseConsumptionService = courseConsumptionService;
    }

    @GetMapping("/api/courses")
    public List<CourseResponse> list(@AuthenticationPrincipal TeacherPrincipal principal) {
        return courseService.listForTeacher(principal.teacherId()).stream()
                .map(CourseResponse::from)
                .toList();
    }

    @PostMapping("/api/courses/{id}/enrollments")
    @ResponseStatus(HttpStatus.CREATED)
    public void enroll(@AuthenticationPrincipal TeacherPrincipal principal, @PathVariable Long id,
                        @Valid @RequestBody EnrollStudentRequest request) {
        courseEnrollmentService.enrollExistingStudent(principal.teacherId(), id, request.studentId());
    }

    @DeleteMapping("/api/courses/{id}/enrollments/{studentId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void unenroll(@AuthenticationPrincipal TeacherPrincipal principal, @PathVariable Long id,
                          @PathVariable Long studentId) {
        courseEnrollmentService.unenroll(principal.teacherId(), id, studentId);
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
}
