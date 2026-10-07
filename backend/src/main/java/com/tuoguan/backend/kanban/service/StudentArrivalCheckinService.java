package com.tuoguan.backend.kanban.service;

import com.tuoguan.backend.billing.dao.StudentLeaveRecordDao;
import com.tuoguan.backend.kanban.dao.StudentArrivalCheckinDao;
import com.tuoguan.backend.kanban.dao.StudentMealRecordDao;
import com.tuoguan.backend.kanban.domain.StudentArrivalCheckin;
import com.tuoguan.backend.roster.dao.StudentDao;
import com.tuoguan.backend.roster.domain.Student;
import com.tuoguan.backend.roster.service.ClassRoomService;
import com.tuoguan.backend.roster.web.NotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

@Service
public class StudentArrivalCheckinService {

    private final StudentArrivalCheckinDao studentArrivalCheckinDao;
    private final StudentMealRecordDao studentMealRecordDao;
    private final StudentLeaveRecordDao studentLeaveRecordDao;
    private final StudentDao studentDao;
    private final ClassRoomService classRoomService;

    public StudentArrivalCheckinService(StudentArrivalCheckinDao studentArrivalCheckinDao,
                                         StudentMealRecordDao studentMealRecordDao,
                                         StudentLeaveRecordDao studentLeaveRecordDao, StudentDao studentDao,
                                         ClassRoomService classRoomService) {
        this.studentArrivalCheckinDao = studentArrivalCheckinDao;
        this.studentMealRecordDao = studentMealRecordDao;
        this.studentLeaveRecordDao = studentLeaveRecordDao;
        this.studentDao = studentDao;
        this.classRoomService = classRoomService;
    }

    public List<StudentArrivalCheckin> listForClass(Long teacherId, Long teachingUnitId, LocalDate date) {
        classRoomService.getOwnedByTeacher(teacherId, teachingUnitId);
        return studentArrivalCheckinDao.findAllByTeachingUnitIdAndDate(teachingUnitId, date);
    }

    @Transactional
    public void setArrival(Long teacherId, Long studentId, LocalDate date, String arrivedAt) {
        Student student = findStudentOwnedByTeacher(teacherId, studentId);
        studentArrivalCheckinDao.upsert(student.institutionId(), student.teachingUnitId(), studentId, date, arrivedAt);
        // 到了与请假互斥：打卡到了意味着孩子其实来了，自动清掉当天的请假记录（前端已在打卡前征得老师确认）。
        studentLeaveRecordDao.deleteByStudentIdAndDate(studentId, date);
    }

    @Transactional
    public void clearArrival(Long teacherId, Long studentId, LocalDate date) {
        findStudentOwnedByTeacher(teacherId, studentId);
        studentArrivalCheckinDao.clear(studentId, date);
        // 没有到了记录就不可能用餐，清除签到时联动清掉当天的用餐记录。
        studentMealRecordDao.clear(studentId, date);
    }

    private Student findStudentOwnedByTeacher(Long teacherId, Long studentId) {
        Student student = studentDao.findById(studentId)
                .orElseThrow(() -> new NotFoundException("Student not found: " + studentId));
        classRoomService.getOwnedByTeacher(teacherId, student.teachingUnitId());
        return student;
    }
}
