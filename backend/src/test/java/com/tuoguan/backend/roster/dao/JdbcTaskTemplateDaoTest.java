package com.tuoguan.backend.roster.dao;

import com.tuoguan.backend.auth.dao.InstitutionDao;
import com.tuoguan.backend.auth.dao.TeacherDao;
import com.tuoguan.backend.auth.domain.Role;
import com.tuoguan.backend.auth.domain.Teacher;
import com.tuoguan.backend.roster.domain.TaskTemplate;
import com.tuoguan.backend.support.IntegrationTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class JdbcTaskTemplateDaoTest extends IntegrationTestBase {

    @Autowired
    private InstitutionDao institutionDao;

    @Autowired
    private TeacherDao teacherDao;

    @Autowired
    private TaskTemplateDao taskTemplateDao;

    @Test
    void insertAndFindByIdRoundTrips() {
        Long institutionId = institutionDao.insert("任务库测试机构A");
        Long teacherId = teacherDao.insert(new Teacher(null, institutionId, "13800007501",
                "hash", Role.TEACHER, false, null));
        TaskTemplate taskTemplate = new TaskTemplate(null, institutionId, teacherId, "数学", "口算练习", null, false);

        Long id = taskTemplateDao.insert(taskTemplate);

        Optional<TaskTemplate> found = taskTemplateDao.findById(id);
        assertThat(found).isPresent();
        assertThat(found.get().subject()).isEqualTo("数学");
        assertThat(found.get().name()).isEqualTo("口算练习");
        assertThat(found.get().institutionId()).isEqualTo(institutionId);
        assertThat(found.get().teacherId()).isEqualTo(teacherId);
    }

    @Test
    void findAllByInstitutionIdAndTeacherIdOnlyReturnsOwnTemplates() {
        Long institutionAId = institutionDao.insert("任务库测试机构B");
        Long institutionBId = institutionDao.insert("任务库测试机构C");
        Long teacherAId = teacherDao.insert(new Teacher(null, institutionAId, "13800007502",
                "hash", Role.TEACHER, false, null));
        Long teacherA2Id = teacherDao.insert(new Teacher(null, institutionAId, "13800007503",
                "hash", Role.TEACHER, false, null));
        Long teacherBId = teacherDao.insert(new Teacher(null, institutionBId, "13800007504",
                "hash", Role.TEACHER, false, null));
        taskTemplateDao.insert(new TaskTemplate(null, institutionAId, teacherAId, "语文", "背诵古诗", null, false));
        taskTemplateDao.insert(new TaskTemplate(null, institutionAId, teacherA2Id, "数学", "口算练习", null, false));
        taskTemplateDao.insert(new TaskTemplate(null, institutionBId, teacherBId, "英语", "单词听写", null, false));

        List<TaskTemplate> found = taskTemplateDao.findAllByInstitutionIdAndTeacherId(institutionAId, teacherAId);

        assertThat(found).hasSize(1);
        assertThat(found.get(0).subject()).isEqualTo("语文");
    }

    @Test
    void archiveByIdHidesTemplateFromListingButKeepsRecord() {
        Long institutionId = institutionDao.insert("任务库测试机构D");
        Long teacherId = teacherDao.insert(new Teacher(null, institutionId, "13800007505",
                "hash", Role.TEACHER, false, null));
        Long id = taskTemplateDao.insert(new TaskTemplate(null, institutionId, teacherId, "数学", "错题整理", null, false));

        taskTemplateDao.archiveById(id);

        assertThat(taskTemplateDao.findById(id)).isPresent();
        assertThat(taskTemplateDao.findById(id).get().archived()).isTrue();
        assertThat(taskTemplateDao.findAllByInstitutionIdAndTeacherId(institutionId, teacherId)).isEmpty();
    }
}
