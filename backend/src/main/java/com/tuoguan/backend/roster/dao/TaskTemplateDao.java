package com.tuoguan.backend.roster.dao;

import com.tuoguan.backend.roster.domain.TaskTemplate;

import java.util.List;
import java.util.Optional;

public interface TaskTemplateDao {

    Long insert(TaskTemplate taskTemplate);

    Optional<TaskTemplate> findById(Long id);

    List<TaskTemplate> findAllByInstitutionId(Long institutionId);

    void deleteById(Long id);
}
