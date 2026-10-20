package com.tuoguan.backend.course.service;

import com.tuoguan.backend.auth.dao.InstitutionDao;
import com.tuoguan.backend.auth.dao.TeacherDao;
import com.tuoguan.backend.auth.domain.Role;
import com.tuoguan.backend.auth.domain.Teacher;
import com.tuoguan.backend.billing.dao.MonthlyBillDao;
import com.tuoguan.backend.billing.domain.MonthlyBill;
import com.tuoguan.backend.billing.service.BillGenerationService;
import com.tuoguan.backend.course.dao.CourseConsumptionRecordDao;
import com.tuoguan.backend.course.dao.CourseRechargeRecordDao;
import com.tuoguan.backend.roster.dao.StudentDao;
import com.tuoguan.backend.roster.domain.Student;
import com.tuoguan.backend.support.IntegrationTestBase;
import com.tuoguan.backend.unit.dao.StudentUnitEnrollmentDao;
import com.tuoguan.backend.unit.dao.TeachingUnitDao;
import com.tuoguan.backend.unit.domain.BillingMode;
import com.tuoguan.backend.unit.domain.StudentUnitEnrollment;
import com.tuoguan.backend.unit.domain.TeachingUnit;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

// 产品诊断 #50：并发/竞态条件测试完全空白。这里验证一个真实场景——老师点"确认消课"时
// 网络卡顿手滑连点两下，或者前端重试机制导致同一次点名被发送了两次请求，几乎同时到达
// 服务端。recordBatchConsumption 的"查这条记录存不存在→不存在就插入"是查后插，两步
// 之间没有数据库唯一约束兜底（course_consumption_record 表没有 (student_id, course_id,
// consumption_date) 唯一键），纯靠应用层这个检查防重复；用 CountDownLatch 逼真两个线程
// 真正同时执行，而不是先后执行，才能测出这类查后插竞态——顺序执行测不出来。
class CourseConsumptionConcurrencyTest extends IntegrationTestBase {

    @Autowired
    private InstitutionDao institutionDao;

    @Autowired
    private TeacherDao teacherDao;

    @Autowired
    private TeachingUnitDao teachingUnitDao;

    @Autowired
    private StudentDao studentDao;

    @Autowired
    private StudentUnitEnrollmentDao enrollmentDao;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private CourseConsumptionService courseConsumptionService;

    @Autowired
    private CourseConsumptionRecordDao consumptionRecordDao;

    @Autowired
    private BillGenerationService billGenerationService;

    @Autowired
    private MonthlyBillDao monthlyBillDao;

    @Autowired
    private CourseAccountService courseAccountService;

    @Autowired
    private CourseRechargeRecordDao rechargeRecordDao;

    // 和上面两条不同，充值记录本来就不该去重——同一个学生给同一门课连续充两次值是
    // 完全合法的正常操作，这里没有"唯一键"或"查后插"这类容易起竞态的逻辑，纯粹是
    // 两条独立 INSERT。这条测试验证的是更基础的不变量：两个真正同时发生的并发写入
    // 不会因为某种共享可变状态（缓存、连接复用出错等）互相覆盖、丢失其中一条——
    // 这是 #50 里唯一一条预期且确实稳定通过的并发测试，作为另外两条"发现问题"的
    // 测试之外的正面补充。
    @Test
    void concurrentRechargesForSameStudentBothPersist() throws Exception {
        Long institutionId = institutionDao.insert("充值并发测试机构");
        Long teacherId = teacherDao.insert(new Teacher(null, institutionId, uniquePhone(),
                passwordEncoder.encode("password"), Role.TEACHER, false, null));
        Long courseId = teachingUnitDao.insert(new TeachingUnit(null, institutionId, teacherId, "数学课",
                BillingMode.LESSON_COUNT, 45, new BigDecimal("50.00"), true, null));
        Long studentId = studentDao.insert(new Student(null, institutionId, null, "小充", null, true, null, null));

        int threadCount = 2;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch ready = new CountDownLatch(threadCount);
        CountDownLatch start = new CountDownLatch(1);

        Callable<Long> recharge = () -> {
            ready.countDown();
            start.await();
            return courseAccountService.recharge(institutionId, studentId, teacherId, courseId, 2, "并发充值")
                    .id();
        };

        Future<Long> first = executor.submit(recharge);
        Future<Long> second = executor.submit(recharge);

        ready.await();
        start.countDown();
        Long firstId = first.get(10, TimeUnit.SECONDS);
        Long secondId = second.get(10, TimeUnit.SECONDS);
        executor.shutdown();
        assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue();

        assertThat(firstId).as("两次并发充值应该各自产生独立的记录").isNotEqualTo(secondId);
        assertThat(rechargeRecordDao.findAllByStudentId(studentId))
                .as("两条并发充值都应该成功持久化，不应该有一条被覆盖/丢失")
                .hasSize(2);
    }

    // 本来预期这条是"对照组"——monthly_bill 表有 (student_id, bill_month) 唯一键
    // （uq_monthly_bill_student_month），generateBill 用的是 INSERT ... ON DUPLICATE
    // KEY UPDATE 的原子 upsert 而不是"查后插"，理论上并发应该安全。实测却稳定复现了
    // 另一个问题（3/3 次）：JdbcMonthlyBillDao.upsert 的 INSERT ... ON DUPLICATE KEY
    // UPDATE 语句本身没有抛异常，但紧接着在同一个事务里按 (student_id, bill_month)
    // 查回这一行时查不到，抛 `IllegalStateException: Bill not found after upsert`。
    // 没有深入定位到 InnoDB 层面的确切机制（怀疑是两个事务并发撞同一个唯一键时的
    // 锁等待/死锁与这个"写完立刻在同一事务里查回"模式产生了某种交互），但现象本身
    // 稳定可复现，记录为诊断文档 #76，和 #75 一样先只记录、不在这个"补测试覆盖"的
    // PR 里顺带修复。
    @Disabled("已知并发问题，见诊断文档 #76，待单独定位根因并修复后移除此注解")
    @Test
    void concurrentGenerateBillForSameStudentAndMonthProducesExactlyOneBill() throws Exception {
        Long institutionId = institutionDao.insert("账单并发测试机构");
        Long teacherId = teacherDao.insert(new Teacher(null, institutionId, uniquePhone(),
                passwordEncoder.encode("password"), Role.TEACHER, false, null));
        Long classRoomId = teachingUnitDao.insert(new TeachingUnit(null, institutionId, teacherId, "并发测试班",
                BillingMode.MONTHLY, null, null, true, null));
        billGenerationService.upsertBillingRate(institutionId, classRoomId,
                new BigDecimal("500.00"), new BigDecimal("10.00"));
        Long studentId = studentDao.insert(
                new Student(null, institutionId, classRoomId, "小账", "并发测试班", true, null, null));
        YearMonth month = YearMonth.of(2030, 4);

        int threadCount = 2;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch ready = new CountDownLatch(threadCount);
        CountDownLatch start = new CountDownLatch(1);

        Callable<Long> generate = () -> {
            ready.countDown();
            start.await();
            return billGenerationService.generateBill(institutionId, studentId, month).id();
        };

        Future<Long> first = executor.submit(generate);
        Future<Long> second = executor.submit(generate);

        ready.await();
        start.countDown();
        Long firstBillId = first.get(10, TimeUnit.SECONDS);
        Long secondBillId = second.get(10, TimeUnit.SECONDS);
        executor.shutdown();
        assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue();

        assertThat(secondBillId).as("两次并发生成应该落到同一条账单上，而不是各自产生独立的账单")
                .isEqualTo(firstBillId);
        List<MonthlyBill> bills = monthlyBillDao.findAllByStudentId(studentId);
        assertThat(bills).as("数据库里该学生该月的账单应该只有一条").hasSize(1);
    }

    // 这条测试目前会稳定失败（3/3 次复现，不是偶发）：确认了一个真实竞态 bug，详见
    // docs/功能全景与产品诊断.md #75。用户决定本次只记录问题、不在这个"补测试覆盖"
    // 的 PR 里顺带修复（修复涉及加锁或加唯一约束，应该单独评审）。先用 @Disabled
    // 把断言钉住并说明原因，而不是让它一直红——等 #75 修复时去掉这行 @Disabled，
    // 这条测试就是现成的回归验证。
    @Disabled("已知竞态 bug，见诊断文档 #75，待单独修复后移除此注解")
    @Test
    void concurrentDoubleSubmissionOfSameRollCallDoesNotCreateDuplicateConsumptionRecords() throws Exception {
        Long institutionId = institutionDao.insert("消课并发测试机构");
        Long teacherId = teacherDao.insert(new Teacher(null, institutionId, uniquePhone(),
                passwordEncoder.encode("password"), Role.TEACHER, false, null));
        Long courseId = teachingUnitDao.insert(new TeachingUnit(null, institutionId, teacherId, "数学课",
                BillingMode.LESSON_COUNT, 45, new BigDecimal("50.00"), true, null));
        Long studentId = studentDao.insert(new Student(null, institutionId, null, "小并", null, true, null, null));
        enrollmentDao.insert(new StudentUnitEnrollment(null, institutionId, studentId, courseId, true, null));
        LocalDate date = LocalDate.of(2030, 3, 15);

        int threadCount = 2;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch ready = new CountDownLatch(threadCount);
        CountDownLatch start = new CountDownLatch(1);

        Callable<Integer> submitRollCall = () -> {
            ready.countDown();
            start.await();
            return courseConsumptionService
                    .recordBatchConsumption(teacherId, courseId, date, List.of(studentId))
                    .size();
        };

        Future<Integer> first = executor.submit(submitRollCall);
        Future<Integer> second = executor.submit(submitRollCall);

        ready.await();
        start.countDown();
        int firstCreated = first.get(10, TimeUnit.SECONDS);
        int secondCreated = second.get(10, TimeUnit.SECONDS);
        executor.shutdown();
        assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue();

        List<com.tuoguan.backend.course.domain.CourseConsumptionRecord> allRecords = consumptionRecordDao
                .findAllByStudentIdAndTeachingUnitIdAndDate(studentId, courseId, date);
        assertThat(allRecords)
                .as("同一学生同一天同一门课的消课记录，不应该因为两个几乎同时到达的重复请求而出现两条——"
                        + "否则家长会被多扣一次课时费。两次调用总共声称创建了 %d+%d 条，数据库里实际是 %d 条",
                        firstCreated, secondCreated, allRecords.size())
                .hasSize(1);
    }
}
