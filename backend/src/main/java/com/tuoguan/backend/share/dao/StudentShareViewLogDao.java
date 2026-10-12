package com.tuoguan.backend.share.dao;

import java.time.LocalDate;

public interface StudentShareViewLogDao {

    void recordView(Long institutionId, Long studentId, LocalDate viewDate);

    int countDistinctStudentsOn(Long institutionId, LocalDate viewDate);
}
