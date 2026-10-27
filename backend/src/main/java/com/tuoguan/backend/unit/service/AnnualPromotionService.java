package com.tuoguan.backend.unit.service;

import com.tuoguan.backend.roster.dao.StudentDao;
import com.tuoguan.backend.roster.domain.Student;
import com.tuoguan.backend.roster.web.NotFoundException;
import com.tuoguan.backend.unit.dao.TeachingUnitDao;
import com.tuoguan.backend.unit.domain.BillingMode;
import com.tuoguan.backend.unit.domain.TeachingUnit;
import com.tuoguan.backend.unit.web.AnnualPromotionOutcome;
import com.tuoguan.backend.unit.web.AnnualPromotionRequest;
import com.tuoguan.backend.unit.web.AnnualPromotionResult;
import com.tuoguan.backend.unit.web.AnnualPromotionStepRequest;
import com.tuoguan.backend.unit.web.AnnualPromotionStepResult;
import com.tuoguan.backend.unit.web.InvalidTeachingUnitRequestException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

// 学年批量晋级：把一批"源托管班 -> 归宿（转入下一班级/转纯课外课/停用）"的步骤，按照
// 能保证正确性的顺序一次性执行。核心风险：如果步骤 A 的目标班级恰好是步骤 B 的源班级
// （A.target == B.source），必须先执行 B（把 B 源班级的原始学生清空搬走），再执行 A
// （把新学生搬进这个已经清空的班级），顺序反了会导致"新搬进来的学生被二次迁移"。
// 这里用 Kahn 拓扑排序从步骤集合里推导出保证正确的执行顺序，不依赖调用方的提交顺序。
@Service
public class AnnualPromotionService {

    private final TeachingUnitDao teachingUnitDao;
    private final StudentDao studentDao;

    public AnnualPromotionService(TeachingUnitDao teachingUnitDao, StudentDao studentDao) {
        this.teachingUnitDao = teachingUnitDao;
        this.studentDao = studentDao;
    }

    @Transactional
    public AnnualPromotionResult execute(Long institutionId, AnnualPromotionRequest request) {
        List<ValidatedStep> validated = validate(institutionId, request.steps());
        List<ValidatedStep> order = topologicalSort(validated);

        List<AnnualPromotionStepResult> results = new ArrayList<>();
        int total = 0;
        for (ValidatedStep step : order) {
            List<Student> affected = studentDao.findAllByTeachingUnitId(step.sourceUnit().id()).stream()
                    .filter(Student::enrolled)
                    .toList();
            for (Student student : affected) {
                studentDao.update(applyOutcome(student, step));
            }
            total += affected.size();
            results.add(new AnnualPromotionStepResult(step.sourceUnit().id(), step.sourceUnit().name(),
                    step.outcome(), step.targetUnit() != null ? step.targetUnit().id() : null,
                    step.targetUnit() != null ? step.targetUnit().name() : null, affected.size()));
        }
        return new AnnualPromotionResult(total, results);
    }

    private List<ValidatedStep> validate(Long institutionId, List<AnnualPromotionStepRequest> rawSteps) {
        List<ValidatedStep> result = new ArrayList<>();
        Set<Long> seenSources = new HashSet<>();
        for (AnnualPromotionStepRequest raw : rawSteps) {
            TeachingUnit source = requireMonthlyUnitInInstitution(institutionId, raw.sourceTeachingUnitId(), "源班级");
            if (!seenSources.add(source.id())) {
                throw new InvalidTeachingUnitRequestException("重复的源班级: " + source.name());
            }
            TeachingUnit target = null;
            if (raw.outcome() == AnnualPromotionOutcome.MOVE_TO_UNIT) {
                if (raw.targetTeachingUnitId() == null) {
                    throw new InvalidTeachingUnitRequestException("「" + source.name() + "」需要指定目标班级");
                }
                target = requireMonthlyUnitInInstitution(institutionId, raw.targetTeachingUnitId(), "目标班级");
                if (target.id().equals(source.id())) {
                    throw new InvalidTeachingUnitRequestException("源班级和目标班级不能相同: " + source.name());
                }
            } else if (raw.targetTeachingUnitId() != null) {
                throw new InvalidTeachingUnitRequestException(
                        "「" + source.name() + "」选择停用/转纯课外课时不应指定目标班级");
            }
            result.add(new ValidatedStep(source, raw.outcome(), target));
        }
        return result;
    }

    private TeachingUnit requireMonthlyUnitInInstitution(Long institutionId, Long unitId, String label) {
        TeachingUnit unit = teachingUnitDao.findById(unitId)
                .orElseThrow(() -> new NotFoundException(label + "不存在: " + unitId));
        if (!unit.institutionId().equals(institutionId)) {
            throw new NotFoundException(label + "不存在: " + unitId);
        }
        if (unit.billingMode() != BillingMode.MONTHLY) {
            throw new InvalidTeachingUnitRequestException(label + "必须是托管班: " + unit.name());
        }
        return unit;
    }

    // Kahn 算法：inDegree[S] = 有多少个步骤 P 的 source 恰好是 S 的 target（S 依赖 P 先执行，
    // 因为 P 执行后才会把 S.target 这个班级原来的学生清空搬走）。入度为 0 的先执行。
    private List<ValidatedStep> topologicalSort(List<ValidatedStep> steps) {
        Map<Long, ValidatedStep> stepBySourceId = steps.stream()
                .collect(Collectors.toMap(s -> s.sourceUnit().id(), s -> s, (a, b) -> a, LinkedHashMap::new));

        Map<ValidatedStep, Integer> inDegree = new LinkedHashMap<>();
        Map<Long, List<ValidatedStep>> dependentsByUnitId = new LinkedHashMap<>();
        for (ValidatedStep step : steps) {
            boolean hasPredecessor = step.outcome() == AnnualPromotionOutcome.MOVE_TO_UNIT
                    && stepBySourceId.containsKey(step.targetUnit().id());
            inDegree.put(step, hasPredecessor ? 1 : 0);
            if (step.outcome() == AnnualPromotionOutcome.MOVE_TO_UNIT) {
                dependentsByUnitId.computeIfAbsent(step.targetUnit().id(), k -> new ArrayList<>()).add(step);
            }
        }

        Deque<ValidatedStep> queue = new ArrayDeque<>();
        inDegree.forEach((step, degree) -> {
            if (degree == 0) queue.add(step);
        });

        List<ValidatedStep> order = new ArrayList<>();
        while (!queue.isEmpty()) {
            ValidatedStep step = queue.poll();
            order.add(step);
            for (ValidatedStep dependent : dependentsByUnitId.getOrDefault(step.sourceUnit().id(), List.of())) {
                int updated = inDegree.merge(dependent, -1, Integer::sum);
                if (updated == 0) {
                    queue.add(dependent);
                }
            }
        }

        if (order.size() != steps.size()) {
            Set<ValidatedStep> remaining = new LinkedHashSet<>(steps);
            remaining.removeAll(order);
            String names = remaining.stream().map(s -> s.sourceUnit().name()).collect(Collectors.joining("、"));
            throw new InvalidTeachingUnitRequestException("检测到循环的晋级链，无法确定执行顺序：" + names);
        }
        return order;
    }

    private Student applyOutcome(Student student, ValidatedStep step) {
        return switch (step.outcome()) {
            case MOVE_TO_UNIT -> new Student(student.id(), student.institutionId(), step.targetUnit().id(),
                    student.name(), student.schoolClassName(), student.enrolled(), student.avatarObjectKey(),
                    student.createdAt());
            case OFF_CAMPUS -> new Student(student.id(), student.institutionId(), null, student.name(),
                    student.schoolClassName(), student.enrolled(), student.avatarObjectKey(), student.createdAt());
            case DEACTIVATE -> new Student(student.id(), student.institutionId(), null, student.name(),
                    student.schoolClassName(), false, student.avatarObjectKey(), student.createdAt());
        };
    }

    private record ValidatedStep(TeachingUnit sourceUnit, AnnualPromotionOutcome outcome, TeachingUnit targetUnit) {
    }
}
