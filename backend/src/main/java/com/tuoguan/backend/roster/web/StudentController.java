package com.tuoguan.backend.roster.web;

import com.tuoguan.backend.auth.security.TeacherPrincipal;
import com.tuoguan.backend.roster.domain.Student;
import com.tuoguan.backend.roster.service.StudentService;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;

@RestController
public class StudentController {

    private final StudentService studentService;

    public StudentController(StudentService studentService) {
        this.studentService = studentService;
    }

    @PostMapping("/api/classes/{classId}/students")
    @ResponseStatus(HttpStatus.CREATED)
    public StudentResponse create(@AuthenticationPrincipal TeacherPrincipal principal,
                                   @PathVariable Long classId,
                                   @RequestBody CreateStudentRequest request) {
        return toResponse(
                studentService.create(principal.teacherId(), classId, request.name(), request.schoolClassName()));
    }

    @GetMapping("/api/classes/{classId}/students")
    public List<StudentResponse> list(@AuthenticationPrincipal TeacherPrincipal principal,
                                       @PathVariable Long classId) {
        return studentService.list(principal.teacherId(), classId).stream()
                .map(this::toResponse)
                .toList();
    }

    @PutMapping("/api/students/{id}")
    public StudentResponse update(@AuthenticationPrincipal TeacherPrincipal principal,
                                   @PathVariable Long id,
                                   @RequestBody UpdateStudentRequest request) {
        return toResponse(studentService.update(principal.teacherId(), id,
                request.name(), request.schoolClassName(), request.enrolled()));
    }

    @PostMapping("/api/students/{id}/avatar")
    public StudentResponse uploadAvatar(@AuthenticationPrincipal TeacherPrincipal principal,
                                         @PathVariable Long id,
                                         @RequestParam("file") MultipartFile file) {
        byte[] content;
        try {
            content = file.getBytes();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return toResponse(studentService.updateAvatar(principal.teacherId(), id, file.getContentType(), content));
    }

    private StudentResponse toResponse(Student student) {
        return StudentResponse.from(student, studentService.avatarUrl(student));
    }
}
