package com.tuoguan.backend.course.service;

import com.tuoguan.backend.course.dao.CourseMealRecordDao;
import com.tuoguan.backend.course.web.CourseNotEnrolledException;
import com.tuoguan.backend.course.web.OffCampusMealNotApplicableException;
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
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// 纯单元测试：覆盖 setMeal/clearMeal 的资格校验和并发去重兜底（code review 发现：
// check-then-insert 对 uq_course_meal_record_student_date 唯一约束不是原子的，两个
// 老师几乎同时给同一个学生记用餐会双双通过 isPresent() 检查），不经过 Spring/MySQL
// 整条链路。
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
    private static final Long STUDENT_ID = 100L;
    private static final LocalDate DATE = LocalDate.of(2024, 1, 2);

    private TeachingUnit stubCourse() {
        TeachingUnit course = new TeachingUnit(COURSE_ID, 1L, TEACHER_ID, "书法课", BillingMode.LESSON_COUNT, 45,
                null, true, null);
        when(courseService.getOwnedByTeacher(TEACHER_ID, COURSE_ID)).thenReturn(course);
        return course;
    }

    private void stubEnrolledStudent(Long teachingUnitId) {
        when(enrollmentDao.findByStudentIdAndTeachingUnitId(STUDENT_ID, COURSE_ID))
                .thenReturn(Optional.of(new StudentUnitEnrollment(null, 1L, STUDENT_ID, COURSE_ID, true, null)));
        when(studentDao.findById(STUDENT_ID))
                .thenReturn(Optional.of(new Student(STUDENT_ID, 1L, teachingUnitId, "小明", null, true, null, null)));
    }

    @Test
    void setMealInsertsWhenNotYetRecorded() {
        stubCourse();
        stubEnrolledStudent(null);
        when(mealRecordDao.findByStudentIdAndDate(STUDENT_ID, DATE)).thenReturn(Optional.empty());

        newService().setMeal(TEACHER_ID, COURSE_ID, STUDENT_ID, DATE);

        verify(mealRecordDao).insert(any());
    }

    @Test
    void setMealIsIdempotentWhenAlreadyRecorded() {
        stubCourse();
        stubEnrolledStudent(null);
        when(mealRecordDao.findByStudentIdAndDate(STUDENT_ID, DATE))
                .thenReturn(Optional.of(new com.tuoguan.backend.course.domain.CourseMealRecord(
                        1L, 1L, STUDENT_ID, DATE, TEACHER_ID, null)));

        newService().setMeal(TEACHER_ID, COURSE_ID, STUDENT_ID, DATE);

        verify(mealRecordDao, never()).insert(any());
    }

    @Test
    void concurrentDuplicateInsertIsSilentlySwallowedInsteadOfPropagatingAsAnError() {
        stubCourse();
        stubEnrolledStudent(null);
        // isPresent() 检查没看到记录（另一个请求还没提交完），但真正 insert 时撞上了
        // 另一个请求已经抢先插入成功的同一行，唯一约束抛出异常。
        when(mealRecordDao.findByStudentIdAndDate(STUDENT_ID, DATE)).thenReturn(Optional.empty());
        org.mockito.Mockito.doThrow(new DataIntegrityViolationException("duplicate"))
                .when(mealRecordDao).insert(any());

        newService().setMeal(TEACHER_ID, COURSE_ID, STUDENT_ID, DATE);
        // 没有抛出异常就是通过——老师点一下用餐图标不应该看到 500。
    }

    @Test
    void setMealRejectsStudentWhoAlreadyHasATeachingUnit() {
        stubCourse();
        stubEnrolledStudent(999L);

        assertThatThrownBy(() -> newService().setMeal(TEACHER_ID, COURSE_ID, STUDENT_ID, DATE))
                .isInstanceOf(OffCampusMealNotApplicableException.class);
        verify(mealRecordDao, never()).insert(any());
    }

    @Test
    void setMealRejectsStudentNotEnrolledInTheCourse() {
        stubCourse();
        when(enrollmentDao.findByStudentIdAndTeachingUnitId(STUDENT_ID, COURSE_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> newService().setMeal(TEACHER_ID, COURSE_ID, STUDENT_ID, DATE))
                .isInstanceOf(CourseNotEnrolledException.class);
    }

    @Test
    void clearMealDeletesTheRecordForEligibleStudent() {
        stubCourse();
        stubEnrolledStudent(null);

        newService().clearMeal(TEACHER_ID, COURSE_ID, STUDENT_ID, DATE);

        verify(mealRecordDao).delete(STUDENT_ID, DATE);
    }

    @Test
    void clearMealRejectsStudentWhoAlreadyHasATeachingUnit() {
        stubCourse();
        stubEnrolledStudent(999L);

        assertThatThrownBy(() -> newService().clearMeal(TEACHER_ID, COURSE_ID, STUDENT_ID, DATE))
                .isInstanceOf(OffCampusMealNotApplicableException.class);
        verify(mealRecordDao, never()).delete(any(), any());
    }
}
