package kh.edu.num.feedback.api;

import kh.edu.num.feedback.api.dto.ApiResponse;
import kh.edu.num.feedback.domain.entity.EvaluationKind;
import kh.edu.num.feedback.domain.repo.AnswerRepository;
import kh.edu.num.feedback.domain.repo.ClassSectionRepository;
import kh.edu.num.feedback.domain.repo.EnrollmentRepository;
import kh.edu.num.feedback.domain.repo.SemesterRepository;
import kh.edu.num.feedback.domain.repo.UserAccountRepository;
import kh.edu.num.feedback.web.admin.dto.AdminSectionReportRow;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/admin")
public class ApiAdminController {

    private final SemesterRepository semesterRepo;
    private final ClassSectionRepository sectionRepo;
    private final EnrollmentRepository enrollmentRepo;
    private final AnswerRepository answerRepo;
    private final UserAccountRepository userRepo;

    public ApiAdminController(SemesterRepository semesterRepo,
                              ClassSectionRepository sectionRepo,
                              EnrollmentRepository enrollmentRepo,
                              AnswerRepository answerRepo,
                              UserAccountRepository userRepo) {
        this.semesterRepo = semesterRepo;
        this.sectionRepo = sectionRepo;
        this.enrollmentRepo = enrollmentRepo;
        this.answerRepo = answerRepo;
        this.userRepo = userRepo;
    }

    // ── GET /api/admin/dashboard/stats ────────────────────────────────────────
    @GetMapping("/dashboard/stats")
    public ResponseEntity<?> dashboardStats(Authentication auth) {
        long totalStudents = userRepo.findByRole(
                kh.edu.num.feedback.domain.entity.Role.STUDENT).size();
        long totalTeachers = userRepo.findByRole(
                kh.edu.num.feedback.domain.entity.Role.TEACHER).size();
        long activeSections = sectionRepo.count();

        Map<String, Object> data = new HashMap<>();
        data.put("totalStudents", totalStudents);
        data.put("totalTeachers", totalTeachers);
        data.put("activeSections", activeSections);
        data.put("responsesToday", 0); // placeholder
        return ResponseEntity.ok(ApiResponse.ok(data));
    }

    // ── GET /api/admin/semesters ──────────────────────────────────────────────
    @GetMapping("/semesters")
    public ResponseEntity<?> semesters() {
        var semesters = semesterRepo.findAll();
        var result = semesters.stream().map(s -> {
            Map<String, Object> m = new HashMap<>();
            m.put("id", s.getId());
            m.put("name", s.getName());
            return m;
        }).toList();
        return ResponseEntity.ok(ApiResponse.ok(result));
    }

    // ── GET /api/admin/report/sections?semesterId=X ───────────────────────────
    @GetMapping("/report/sections")
    public ResponseEntity<?> sectionReport(@RequestParam Long semesterId) {
        List<AdminSectionReportRow> rows = answerRepo.adminSectionReport(
                semesterId, EvaluationKind.STUDENT_FEEDBACK);

        // Build enrolled map
        Map<Long, Long> enrolledMap = enrollmentRepo
                .countEnrollmentsBySemesterGroupBySection(semesterId)
                .stream()
                .collect(Collectors.toMap(
                        e -> e.getSectionId(),
                        e -> e.getEnrolled()));

        // Build teacher fullName map from usernames
        Set<String> usernames = rows.stream()
                .map(AdminSectionReportRow::teacherUsername)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<String, String> nameMap = new HashMap<>();
        for (String username : usernames) {
            userRepo.findByUsername(username).ifPresent(u ->
                nameMap.put(username, u.getFullName() != null ? u.getFullName() : username));
        }

        var result = rows.stream().map(row -> {
            long enrolled = enrolledMap.getOrDefault(row.sectionId(), 0L);
            long responses = row.responses() != null ? row.responses() : 0L;
            double rate = enrolled > 0 ? (responses * 100.0 / enrolled) : 0.0;

            Map<String, Object> m = new LinkedHashMap<>();
            m.put("sectionId", row.sectionId());
            m.put("semester", row.semesterName());
            m.put("courseCode", row.courseCode());
            m.put("courseName", row.courseName());
            m.put("teacherName", nameMap.getOrDefault(row.teacherUsername(), row.teacherUsername()));
            m.put("shiftTime", row.shiftTime() != null ? row.shiftTime().name() : null);
            m.put("room", (row.building() != null ? row.building() : "") + "-" + (row.room() != null ? row.room() : ""));
            m.put("sectionName", row.sectionName());
            m.put("responses", responses);
            m.put("enrolled", enrolled);
            m.put("rate", Math.round(rate * 100.0) / 100.0);
            m.put("catA", row.avgCatA() != null ? Math.round(row.avgCatA() * 100.0) / 100.0 : null);
            m.put("catB", row.avgCatB() != null ? Math.round(row.avgCatB() * 100.0) / 100.0 : null);
            m.put("catC", row.avgCatC() != null ? Math.round(row.avgCatC() * 100.0) / 100.0 : null);
            m.put("catD", row.avgCatD() != null ? Math.round(row.avgCatD() * 100.0) / 100.0 : null);
            m.put("catE", row.avgCatE() != null ? Math.round(row.avgCatE() * 100.0) / 100.0 : null);
            m.put("overall", row.overallAvg() != null ? Math.round(row.overallAvg() * 100.0) / 100.0 : null);
            return m;
        }).toList();

        return ResponseEntity.ok(ApiResponse.ok(result));
    }
}
