package com.tuoguan.backend.roster.service;

import com.tuoguan.backend.roster.dao.TaskTemplateDao;
import com.tuoguan.backend.roster.domain.TaskTemplate;
import com.tuoguan.backend.roster.web.NotFoundException;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class TaskTemplateService {

    private final TaskTemplateDao taskTemplateDao;

    public TaskTemplateService(TaskTemplateDao taskTemplateDao) {
        this.taskTemplateDao = taskTemplateDao;
    }

    public TaskTemplate create(Long institutionId, String subject, String name) {
        TaskTemplate taskTemplate = new TaskTemplate(null, institutionId, subject, name, null);
        Long id = taskTemplateDao.insert(taskTemplate);
        return taskTemplateDao.findById(id)
                .orElseThrow(() -> new IllegalStateException("Task template not found after insert: " + id));
    }

    public List<TaskTemplate> list(Long institutionId) {
        return taskTemplateDao.findAllByInstitutionId(institutionId);
    }

    public void delete(Long institutionId, Long taskTemplateId) {
        TaskTemplate taskTemplate = taskTemplateDao.findById(taskTemplateId)
                .filter(t -> t.institutionId().equals(institutionId))
                .orElseThrow(() -> new NotFoundException("Task template not found: " + taskTemplateId));
        taskTemplateDao.deleteById(taskTemplate.id());
    }
}
