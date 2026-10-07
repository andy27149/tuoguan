package com.tuoguan.backend.roster.service;

import com.tuoguan.backend.auth.dao.TeacherDao;
import com.tuoguan.backend.auth.domain.Teacher;
import com.tuoguan.backend.roster.domain.Student;
import com.tuoguan.backend.unit.dao.StudentUnitEnrollmentDao;
import com.tuoguan.backend.unit.domain.StudentUnitEnrollment;
import com.tuoguan.backend.unit.domain.TeachingUnit;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 「学生总览」和「账单管理」两个列表共用的排序口径：托管学生（挂了托管班）排在纯课外课
 * 学生前面；托管组内按托管教师姓名排序，纯课外组内按该生所报课程里名称最靠前的那门课
 * 排序；同组内最终按学生姓名兜底排序，保证结果稳定可预期。
 */
public final class StudentOverviewOrder {

    private StudentOverviewOrder() {
    }

    public static Map<Long, String> computeFirstCourseNames(List<Student> students,
                                                              Map<Long, String> courseNameByUnitId,
                                                              StudentUnitEnrollmentDao enrollmentDao) {
        Map<Long, String> result = new HashMap<>();
        for (Student s : students) {
            if (s.teachingUnitId() != null) {
                continue;
            }
            String first = enrollmentDao.findAllByStudentId(s.id()).stream()
                    .filter(StudentUnitEnrollment::active)
                    .map(e -> courseNameByUnitId.get(e.teachingUnitId()))
                    .filter(Objects::nonNull)
                    .sorted()
                    .findFirst()
                    .orElse("");
            result.put(s.id(), first);
        }
        return result;
    }

    public static Comparator<Student> comparator(Map<Long, TeachingUnit> unitsById,
                                                  Map<Long, String> firstCourseNameByStudentId,
                                                  TeacherDao teacherDao) {
        return (a, b) -> {
            boolean aCustody = a.teachingUnitId() != null;
            boolean bCustody = b.teachingUnitId() != null;
            if (aCustody != bCustody) {
                return aCustody ? -1 : 1;
            }
            String aSecondary = aCustody ? teacherNameForUnit(a.teachingUnitId(), unitsById, teacherDao)
                    : firstCourseNameByStudentId.getOrDefault(a.id(), "");
            String bSecondary = bCustody ? teacherNameForUnit(b.teachingUnitId(), unitsById, teacherDao)
                    : firstCourseNameByStudentId.getOrDefault(b.id(), "");
            int cmp = aSecondary.compareTo(bSecondary);
            if (cmp != 0) {
                return cmp;
            }
            return a.name().compareTo(b.name());
        };
    }

    private static String teacherNameForUnit(Long teachingUnitId, Map<Long, TeachingUnit> unitsById,
                                              TeacherDao teacherDao) {
        TeachingUnit unit = unitsById.get(teachingUnitId);
        if (unit == null) {
            return "";
        }
        return teacherDao.findById(unit.teacherId()).map(Teacher::name).orElse("");
    }
}
