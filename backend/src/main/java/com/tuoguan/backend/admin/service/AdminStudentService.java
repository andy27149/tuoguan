package com.tuoguan.backend.admin.service;

import com.tuoguan.backend.admin.web.AdminStudentResponse;
import com.tuoguan.backend.roster.dao.ClassRoomDao;
import com.tuoguan.backend.roster.dao.StudentDao;
import com.tuoguan.backend.roster.domain.ClassRoom;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class AdminStudentService {

    private final StudentDao studentDao;
    private final ClassRoomDao classRoomDao;

    public AdminStudentService(StudentDao studentDao, ClassRoomDao classRoomDao) {
        this.studentDao = studentDao;
        this.classRoomDao = classRoomDao;
    }

    public List<AdminStudentResponse> listStudents(Long institutionId) {
        Map<Long, ClassRoom> classRoomById = classRoomDao.findAllByInstitutionId(institutionId).stream()
                .collect(Collectors.toMap(ClassRoom::id, c -> c));
        return studentDao.findAllByInstitutionId(institutionId).stream()
                .map(student -> {
                    ClassRoom classRoom = student.classRoomId() != null ? classRoomById.get(student.classRoomId())
                            : null;
                    return new AdminStudentResponse(student.id(), student.name(), student.schoolClassName(),
                            student.classRoomId(), classRoom != null ? classRoom.name() : null,
                            student.classRoomId() == null);
                })
                .toList();
    }
}
