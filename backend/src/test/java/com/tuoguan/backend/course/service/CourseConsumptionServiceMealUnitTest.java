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

import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// 纯单元测试：覆盖 setMeal/clearMeal 的资格校验（只对报了这门课、且没有托管班的
// 学生生效）。幂等/并发安全由 CourseMealRecordDao.upsert 的原子 INSERT ... ON
// DUPLICATE KEY UPDATE 保证（跟 kanban 模块 StudentMealRecordDao.upsert 同款写法），
// 不需要在 service 层另外测——这正是改用 upsert 替代"先查后插+catch 唯一约束冲突"
// 换来的好处：不再需要专门写测试验证"吞异常"这件事本身是否正确。
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

    private void stubCourse() {
        TeachingUnit course = new TeachingUnit(COURSE_ID, 1L, TEACHER_ID, "书法课", BillingMode.LESSON_COUNT, 45,
                null, true, null);
        when(courseService.getOwnedByTeacher(TEACHER_ID, COURSE_ID)).thenReturn(course);
    }

    private void stubEnrolledStudent(Long teachingUnitId) {
        when(enrollmentDao.findByStudentIdAndTeachingUnitId(STUDENT_ID, COURSE_ID))
                .thenReturn(Optional.of(new StudentUnitEnrollment(null, 1L, STUDENT_ID, COURSE_ID, true, null)));
        when(studentDao.findById(STUDENT_ID))
                .thenReturn(Optional.of(new Student(STUDENT_ID, 1L, teachingUnitId, "小明", null, true, null, null)));
    }

    @Test
    void setMealUpsertsForAnEligibleOffCampusStudent() {
        stubCourse();
        stubEnrolledStudent(null);

        newService().setMeal(TEACHER_ID, COURSE_ID, STUDENT_ID, DATE);

        verify(mealRecordDao).upsert(any());
    }

    @Test
    void setMealRejectsStudentWhoAlreadyHasATeachingUnit() {
        stubCourse();
        stubEnrolledStudent(999L);

        assertThatThrownBy(() -> newService().setMeal(TEACHER_ID, COURSE_ID, STUDENT_ID, DATE))
                .isInstanceOf(OffCampusMealNotApplicableException.class);
        verify(mealRecordDao, never()).upsert(any());
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
