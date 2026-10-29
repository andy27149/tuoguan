package com.tuoguan.backend.course.service;

import com.tuoguan.backend.course.dao.CourseMealRecordDao;
import com.tuoguan.backend.roster.dao.StudentDao;
import com.tuoguan.backend.roster.domain.Student;
import com.tuoguan.backend.unit.dao.StudentUnitEnrollmentDao;
import com.tuoguan.backend.unit.domain.BillingMode;
import com.tuoguan.backend.unit.domain.StudentUnitEnrollment;
import com.tuoguan.backend.unit.domain.TeachingUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

// 纯单元测试：只测 recordBatchMeal 的并发去重兜底（code review 发现：check-then-insert
// 对 uq_course_meal_record_student_date 唯一约束不是原子的，两个老师当天几乎同时给
// 同一个学生记用餐会双双通过 isPresent() 检查，第二次 insert 撞唯一约束抛
// DataIntegrityViolationException），不经过 Spring/MySQL 整条链路。
@ExtendWith(MockitoExtension.class)
class CourseConsumptionServiceMealUnitTest {

    @Mock
    private CourseService courseService;
    @Mock
    private StudentDao studentDao;
    @Mock
    private StudentUnitEnrollmentDao enrollmentDao;
    @Mock
    private CourseMealRecordDao mealRecordDao;

    // 构造 service 的实例必须放在每个 @Test 方法执行时（而不是字段初始化器里）——
    // 字段初始化器在 MockitoExtension 把 @Mock 字段真正赋值之前就已经跑完，此时拿到
    // 的还是 null，会在真正调用时抛 NPE。
    private CourseConsumptionService newService() {
        return new CourseConsumptionService(courseService, studentDao, enrollmentDao, null, null, mealRecordDao);
    }

    private static final Long TEACHER_ID = 1L;
    private static final Long COURSE_ID = 10L;
    private static final Long OFF_CAMPUS_STUDENT_ID = 100L;
    private static final LocalDate DATE = LocalDate.of(2024, 1, 2);

    private void stubCourseAndRoster() {
        TeachingUnit course = new TeachingUnit(COURSE_ID, 1L, TEACHER_ID, "书法课", BillingMode.LESSON_COUNT, 45,
                null, true, null);
        when(courseService.getOwnedByTeacher(TEACHER_ID, COURSE_ID)).thenReturn(course);
        when(enrollmentDao.findAllByTeachingUnitId(COURSE_ID)).thenReturn(
                List.of(new StudentUnitEnrollment(null, 1L, OFF_CAMPUS_STUDENT_ID, COURSE_ID, true, null)));
        when(studentDao.findById(OFF_CAMPUS_STUDENT_ID)).thenReturn(
                Optional.of(new Student(OFF_CAMPUS_STUDENT_ID, 1L, null, "小明", null, true, null, null)));
    }

    @Test
    void concurrentDuplicateInsertIsSilentlySkippedInsteadOfPropagatingAsAnError() {
        stubCourseAndRoster();
        // isPresent() 检查没看到记录（另一个请求还没提交完），但真正 insert 时撞上了
        // 另一个请求已经抢先插入成功的同一行，唯一约束抛出异常。
        when(mealRecordDao.findByStudentIdAndDate(OFF_CAMPUS_STUDENT_ID, DATE)).thenReturn(Optional.empty());
        org.mockito.Mockito.doThrow(new DataIntegrityViolationException("duplicate"))
                .when(mealRecordDao).insert(any());

        int recordedCount = newService().recordBatchMeal(TEACHER_ID, COURSE_ID, DATE, List.of(OFF_CAMPUS_STUDENT_ID));

        assertThat(recordedCount).isEqualTo(0);
    }

    @Test
    void recordsNormallyWhenNoConcurrentConflictOccurs() {
        stubCourseAndRoster();
        when(mealRecordDao.findByStudentIdAndDate(OFF_CAMPUS_STUDENT_ID, DATE)).thenReturn(Optional.empty());

        int recordedCount = newService().recordBatchMeal(TEACHER_ID, COURSE_ID, DATE, List.of(OFF_CAMPUS_STUDENT_ID));

        assertThat(recordedCount).isEqualTo(1);
    }
}
