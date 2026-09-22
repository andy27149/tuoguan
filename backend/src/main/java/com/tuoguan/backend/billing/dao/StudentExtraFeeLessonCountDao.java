package com.tuoguan.backend.billing.dao;

import java.util.Optional;

public interface StudentExtraFeeLessonCountDao {

    Optional<Integer> findLessonCount(Long studentExtraFeeId, String billMonth);

    void upsert(Long institutionId, Long studentExtraFeeId, String billMonth, int lessonCount);
}
