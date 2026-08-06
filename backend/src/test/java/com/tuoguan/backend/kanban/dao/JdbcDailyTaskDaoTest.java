package com.tuoguan.backend.kanban.dao;

import com.tuoguan.backend.auth.dao.InstitutionDao;
import com.tuoguan.backend.auth.dao.TeacherDao;
import com.tuoguan.backend.auth.domain.Role;
import com.tuoguan.backend.auth.domain.Teacher;
import com.tuoguan.backend.kanban.domain.DailyTask;
import com.tuoguan.backend.roster.dao.ClassRoomDao;
import com.tuoguan.backend.roster.dao.StudentDao;
import com.tuoguan.backend.roster.domain.ClassRoom;
import com.tuoguan.backend.roster.domain.Student;
import com.tuoguan.backend.support.IntegrationTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class JdbcDailyTaskDaoTest extends IntegrationTestBase {

    @Autowired
    private InstitutionDao institutionDao;

    @Autowired
    private TeacherDao teacherDao;

    @Autowired
    private ClassRoomDao classRoomDao;

    @Autowired
    private StudentDao studentDao;

    @Autowired
    private DailyTaskDao dailyTaskDao;

    private Long createStudent(String institutionName, String phone) {
        Long institutionId = institutionDao.insert(institutionName);
        Long teacherId = teacherDao.insert(new Teacher(null, institutionId, phone, "hash",
                Role.TEACHER, false, null));
        Long classRoomId = classRoomDao.insert(new ClassRoom(null, institutionId, teacherId, "托管班", null));
        return studentDao.insert(new Student(null, institutionId, classRoomId, "小明", "三年级2班", true, null));
    }

    @Test
    void insertAndFindByIdRoundTrips() {
        Long studentId = createStudent("每日任务测试机构A", "13900003001");
        Student student = studentDao.findById(studentId).orElseThrow();
        DailyTask dailyTask = new DailyTask(null, student.institutionId(), student.classRoomId(), studentId,
                LocalDate.of(2026, 8, 6), null, "数学", "口算练习", false, false, null);

        Long id = dailyTaskDao.insert(dailyTask);

        Optional<DailyTask> found = dailyTaskDao.findById(id);
        assertThat(found).isPresent();
        assertThat(found.get().subject()).isEqualTo("数学");
        assertThat(found.get().name()).isEqualTo("口算练习");
        assertThat(found.get().taskDate()).isEqualTo(LocalDate.of(2026, 8, 6));
        assertThat(found.get().completed()).isFalse();
    }

    @Test
    void findAllByClassRoomIdAndDateOnlyReturnsMatchingRows() {
        Long studentId = createStudent("每日任务测试机构B", "13900003002");
        Student student = studentDao.findById(studentId).orElseThrow();
        dailyTaskDao.insert(new DailyTask(null, student.institutionId(), student.classRoomId(), studentId,
                LocalDate.of(2026, 8, 6), null, "数学", "口算练习", false, false, null));
        dailyTaskDao.insert(new DailyTask(null, student.institutionId(), student.classRoomId(), studentId,
                LocalDate.of(2026, 8, 5), null, "语文", "背诵古诗", false, false, null));

        List<DailyTask> found = dailyTaskDao.findAllByClassRoomIdAndDate(
                student.classRoomId(), LocalDate.of(2026, 8, 6));

        assertThat(found).hasSize(1);
        assertThat(found.get(0).name()).isEqualTo("口算练习");
    }

    @Test
    void updateCompletedChangesFlag() {
        Long studentId = createStudent("每日任务测试机构C", "13900003003");
        Student student = studentDao.findById(studentId).orElseThrow();
        Long id = dailyTaskDao.insert(new DailyTask(null, student.institutionId(), student.classRoomId(), studentId,
                LocalDate.of(2026, 8, 6), null, "数学", "口算练习", false, false, null));

        dailyTaskDao.updateCompleted(id, true);

        assertThat(dailyTaskDao.findById(id).orElseThrow().completed()).isTrue();
    }

    @Test
    void deleteByIdRemovesRow() {
        Long studentId = createStudent("每日任务测试机构D", "13900003004");
        Student student = studentDao.findById(studentId).orElseThrow();
        Long id = dailyTaskDao.insert(new DailyTask(null, student.institutionId(), student.classRoomId(), studentId,
                LocalDate.of(2026, 8, 6), null, "数学", "口算练习", false, false, null));

        dailyTaskDao.deleteById(id);

        assertThat(dailyTaskDao.findById(id)).isEmpty();
    }
}
