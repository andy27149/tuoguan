package com.tuoguan.backend.kanban.service;

import com.tuoguan.backend.kanban.dao.DailyTaskDao;
import com.tuoguan.backend.kanban.dao.StudentDailyNoteDao;
import com.tuoguan.backend.kanban.domain.DailyTask;
import com.tuoguan.backend.kanban.domain.StudentDailyNote;
import com.tuoguan.backend.roster.dao.StudentDao;
import com.tuoguan.backend.roster.domain.Student;
import com.tuoguan.backend.roster.service.ClassRoomService;
import com.tuoguan.backend.roster.web.NotFoundException;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.stream.Collectors;

@Service
public class MonthlyStatsService {

    private final DailyTaskDao dailyTaskDao;
    private final StudentDailyNoteDao studentDailyNoteDao;
    private final StudentDao studentDao;
    private final ClassRoomService classRoomService;

    public MonthlyStatsService(DailyTaskDao dailyTaskDao, StudentDailyNoteDao studentDailyNoteDao,
                                StudentDao studentDao, ClassRoomService classRoomService) {
        this.dailyTaskDao = dailyTaskDao;
        this.studentDailyNoteDao = studentDailyNoteDao;
        this.studentDao = studentDao;
        this.classRoomService = classRoomService;
    }

    public MonthlyStatsResult getMonthlyStats(Long teacherId, Long studentId, YearMonth month) {
        Student student = studentDao.findById(studentId)
                .orElseThrow(() -> new NotFoundException("Student not found: " + studentId));
        classRoomService.getOwnedByTeacher(teacherId, student.classRoomId());

        return buildMonthlyStats(studentId, month);
    }

    public MonthlyStatsResult getMonthlyStatsForStudent(Long studentId, YearMonth month) {
        return buildMonthlyStats(studentId, month);
    }

    private MonthlyStatsResult buildMonthlyStats(Long studentId, YearMonth month) {
        LocalDate start = month.atDay(1);
        LocalDate today = LocalDate.now();
        LocalDate end = month.atEndOfMonth().isAfter(today) ? today : month.atEndOfMonth();

        if (end.isBefore(start)) {
            return new MonthlyStatsResult(0, 0, List.of(), 0.0, List.of(), List.of());
        }

        DateTimeFormatter isoDate = DateTimeFormatter.ISO_LOCAL_DATE;

        List<DailyTask> tasks = dailyTaskDao.findAllByStudentIdAndDateRange(studentId, start, end);
        Map<LocalDate, List<DailyTask>> byDate = tasks.stream()
                .collect(Collectors.groupingBy(DailyTask::taskDate, TreeMap::new, Collectors.toList()));

        int completedDays = 0;
        int incompleteDays = 0;
        List<DailyRate> dailyRates = new ArrayList<>();
        for (Map.Entry<LocalDate, List<DailyTask>> entry : byDate.entrySet()) {
            List<DailyTask> dayTasks = entry.getValue();
            boolean allCompleted = dayTasks.stream().allMatch(DailyTask::completed);
            if (allCompleted) {
                completedDays++;
            } else {
                incompleteDays++;
            }
            long completedCount = dayTasks.stream().filter(DailyTask::completed).count();
            double rate = completedCount / (double) dayTasks.size();
            dailyRates.add(new DailyRate(entry.getKey().format(isoDate), rate));
        }

        List<StudentDailyNote> notes = studentDailyNoteDao.findAllByStudentIdAndDateRange(studentId, start, end);
        Map<LocalDate, StudentDailyNote> noteByDate = notes.stream()
                .collect(Collectors.toMap(StudentDailyNote::noteDate, n -> n, (a, b) -> a, TreeMap::new));

        List<StudentDailyNote> ratedNotes = notes.stream().filter(n -> n.rating() > 0).toList();
        double averageRating = ratedNotes.isEmpty()
                ? 0.0
                : ratedNotes.stream().mapToInt(StudentDailyNote::rating).average().orElse(0.0);

        List<DailyRating> dailyRatings = notes.stream()
                .sorted(Comparator.comparing(StudentDailyNote::noteDate))
                .map(n -> new DailyRating(n.noteDate().format(isoDate), n.rating()))
                .toList();

        Set<LocalDate> allDates = new TreeSet<>(byDate.keySet());
        allDates.addAll(noteByDate.keySet());
        List<DayDetail> days = new ArrayList<>();
        for (LocalDate date : allDates) {
            List<DayTask> dayTasks = byDate.getOrDefault(date, List.of()).stream()
                    .map(t -> new DayTask(t.id(), t.subject(), t.name(), t.completed()))
                    .toList();
            StudentDailyNote note = noteByDate.get(date);
            int rating = note != null ? note.rating() : 0;
            String comment = note != null ? note.comment() : "";
            days.add(new DayDetail(date.format(isoDate), dayTasks, rating, comment));
        }

        return new MonthlyStatsResult(completedDays, incompleteDays, dailyRates, averageRating, dailyRatings, days);
    }

    public record DailyRate(String date, double rate) {
    }

    public record DailyRating(String date, int rating) {
    }

    public record DayTask(Long id, String subject, String name, boolean completed) {
    }

    public record DayDetail(String date, List<DayTask> tasks, int rating, String comment) {
    }

    public record MonthlyStatsResult(int completedDays, int incompleteDays, List<DailyRate> dailyRates,
                                      double averageRating, List<DailyRating> dailyRatings, List<DayDetail> days) {
    }
}
