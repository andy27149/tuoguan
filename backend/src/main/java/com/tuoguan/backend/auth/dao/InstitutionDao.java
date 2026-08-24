package com.tuoguan.backend.auth.dao;

import com.tuoguan.backend.auth.domain.Institution;

import java.util.List;
import java.util.Optional;

public interface InstitutionDao {

    Long insert(String name);

    Optional<Institution> findById(Long id);

    List<Institution> findAll();
}
