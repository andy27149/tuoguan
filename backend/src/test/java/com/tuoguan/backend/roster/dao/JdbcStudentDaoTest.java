package com.tuoguan.backend.roster.dao;

import com.tuoguan.backend.auth.dao.InstitutionDao;
import com.tuoguan.backend.auth.dao.TeacherDao;
import com.tuoguan.backend.auth.domain.Role;
import com.tuoguan.backend.auth.domain.Teacher;
import com.tuoguan.backend.roster.domain.ClassRoom;
import com.tuoguan.backend.roster.domain.Student;
import com.tuoguan.backend.support.IntegrationTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class JdbcStudentDaoTest extends IntegrationTestBase {

    @Autowired
    private InstitutionDao institutionDao;

    @Autowired
    private TeacherDao teacherDao;

    @Autowired
    private ClassRoomDao classRoomDao;

    @Autowired
    private StudentDao studentDao;

    private Long createClassRoom(String institutionName, String phone) {
        Long institutionId = institutionDao.insert(institutionName);
        Long teacherId = teacherDao.insert(new Teacher(null, institutionId, phone, "hash",
                Role.TEACHER, false, null));
        return classRoomDao.insert(new ClassRoom(null, institutionId, teacherId, "托管班", null));
    }

    @Test
    void insertAndFindByIdRoundTrips() {
        Long classRoomId = createClassRoom("学生测试机构A", "13900002001");
        Long institutionId = classRoomDao.findById(classRoomId).orElseThrow().institutionId();
        Student student = new Student(null, institutionId, classRoomId, "小明", "三年级2班", true, null);

        Long id = studentDao.insert(student);

        Optional<Student> found = studentDao.findById(id);
        assertThat(found).isPresent();
        assertThat(found.get().name()).isEqualTo("小明");
        assertThat(found.get().schoolClassName()).isEqualTo("三年级2班");
        assertThat(found.get().enrolled()).isTrue();
    }

    @Test
    void findAllByClassRoomIdOnlyReturnsOwnStudents() {
        Long classRoomAId = createClassRoom("学生测试机构B", "13900002002");
        Long classRoomBId = createClassRoom("学生测试机构C", "13900002003");
        Long institutionAId = classRoomDao.findById(classRoomAId).orElseThrow().institutionId();
        Long institutionBId = classRoomDao.findById(classRoomBId).orElseThrow().institutionId();
        studentDao.insert(new Student(null, institutionAId, classRoomAId, "小红", "四年级1班", true, null));
        studentDao.insert(new Student(null, institutionBId, classRoomBId, "小刚", "五年级1班", true, null));

        List<Student> found = studentDao.findAllByClassRoomId(classRoomAId);

        assertThat(found).hasSize(1);
        assertThat(found.get(0).name()).isEqualTo("小红");
    }

    @Test
    void updateChangesNameSchoolClassAndEnrollment() {
        Long classRoomId = createClassRoom("学生测试机构D", "13900002004");
        Long institutionId = classRoomDao.findById(classRoomId).orElseThrow().institutionId();
        Long id = studentDao.insert(new Student(null, institutionId, classRoomId, "小李", "六年级1班", true, null));
        Student existing = studentDao.findById(id).orElseThrow();

        studentDao.update(new Student(existing.id(), existing.institutionId(), existing.classRoomId(),
                "小李四", "六年级2班", false, existing.createdAt()));

        Student updated = studentDao.findById(id).orElseThrow();
        assertThat(updated.name()).isEqualTo("小李四");
        assertThat(updated.schoolClassName()).isEqualTo("六年级2班");
        assertThat(updated.enrolled()).isFalse();
    }

    @Test
    void deleteByIdRemovesStudent() {
        Long classRoomId = createClassRoom("学生测试机构E", "13900002005");
        Long institutionId = classRoomDao.findById(classRoomId).orElseThrow().institutionId();
        Long id = studentDao.insert(new Student(null, institutionId, classRoomId, "小王", "一年级1班", true, null));

        studentDao.deleteById(id);

        assertThat(studentDao.findById(id)).isEmpty();
    }
}
