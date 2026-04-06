package kh.edu.num.feedback.api;

import kh.edu.num.feedback.api.dto.ApiResponse;
import kh.edu.num.feedback.api.dto.FeedbackSubmitRequest;
import kh.edu.num.feedback.api.dto.QuestionDto;
import kh.edu.num.feedback.api.dto.SectionDto;
import kh.edu.num.feedback.api.dto.UpdateProfileRequest;
import kh.edu.num.feedback.domain.entity.ClassSection;
import kh.edu.num.feedback.domain.entity.EvaluationKind;
import kh.edu.num.feedback.domain.entity.EvaluationWindow;
import kh.edu.num.feedback.domain.entity.Question;
import kh.edu.num.feedback.domain.repo.AnswerRepository;
import kh.edu.num.feedback.domain.repo.ClassSectionRepository;
import kh.edu.num.feedback.domain.repo.EnrollmentRepository;
import kh.edu.num.feedback.domain.repo.EvaluationWindowRepository;
import kh.edu.num.feedback.domain.repo.SemesterRepository;
import kh.edu.num.feedback.domain.repo.SubmissionRepository;
import kh.edu.num.feedback.domain.repo.UserAccountRepository;
import kh.edu.num.feedback.security.UserPrincipal;
import kh.edu.num.feedback.service.EvaluationService;
import kh.edu.num.feedback.service.WindowService;
import kh.edu.num.feedback.web.teacher.dto.QuestionStat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/teacher")
public class ApiTeacherController {

    private final ClassSectionRepository sectionRepo;
    private final EnrollmentRepository enrollRepo;
    private final SubmissionRepository submissionRepo;
    private final AnswerRepository answerRepo;
    private final EvaluationService evaluationService;
    private final EvaluationWindowRepository windowRepo;
    private final SemesterRepository semesterRepo;
    private final UserAccountRepository userRepo;
    private final PasswordEncoder passwordEncoder;
    private final WindowService windowService;

    private static final DateTimeFormatter ISO_FMT = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    public ApiTeacherController(ClassSectionRepository sectionRepo,
                                EnrollmentRepository enrollRepo,
                                SubmissionRepository submissionRepo,
                                AnswerRepository answerRepo,
                                EvaluationService evaluationService,
                                EvaluationWindowRepository windowRepo,
                                SemesterRepository semesterRepo,
                                UserAccountRepository userRepo,
                                PasswordEncoder passwordEncoder,
                                WindowService windowService) {
        this.sectionRepo = sectionRepo;
        this.enrollRepo = enrollRepo;
        this.submissionRepo = submissionRepo;
        this.answerRepo = answerRepo;
        this.evaluationService = evaluationService;
        this.windowRepo = windowRepo;
        this.semesterRepo = semesterRepo;
        this.userRepo = userRepo;
        this.passwordEncoder = passwordEncoder;
        this.windowService = windowService;
    }

    // ── GET /api/teacher/profile ──────────────────────────────────────────────
    @GetMapping("/profile")
    public ResponseEntity<?> getProfile(Authentication auth) {
        var principal = resolveUser(auth);
        if (principal == null) return unauthorized();
        var user = userRepo.findById(principal.getUser().getId()).orElseThrow();
        return ResponseEntity.ok(ApiResponse.ok(ApiAuthController.toProfileDto(user)));
    }

    // ── PUT /api/teacher/profile ──────────────────────────────────────────────
    @PutMapping("/profile")
    public ResponseEntity<?> updateProfile(@RequestBody UpdateProfileRequest req, Authentication auth) {
        var principal = resolveUser(auth);
        if (principal == null) return unauthorized();
        var user = userRepo.findById(principal.getUser().getId()).orElseThrow();

        if (req.getFullName() != null) user.setFullName(req.getFullName().trim());
        if (req.getEmail() != null) user.setEmail(req.getEmail().trim());
        if (req.getPhone() != null) user.setPhone(req.getPhone().trim());
        if (req.getDepartment() != null) user.setDepartment(req.getDepartment().trim());
        if (req.getPosition() != null) user.setPosition(req.getPosition().trim());

        if (req.getNewPassword() != null && !req.getNewPassword().isBlank()) {
            if (req.getCurrentPassword() == null ||
                    !passwordEncoder.matches(req.getCurrentPassword(), user.getPasswordHash()))
                return ResponseEntity.badRequest().body(ApiResponse.error("Current password is incorrect."));
            if (req.getNewPassword().length() < 6)
                return ResponseEntity.badRequest().body(ApiResponse.error("New password must be at least 6 characters."));
            user.setPasswordHash(passwordEncoder.encode(req.getNewPassword()));
        }

        userRepo.save(user);
        return ResponseEntity.ok(ApiResponse.ok(ApiAuthController.toProfileDto(user)));
    }

    // ── GET /api/teacher/sections ─────────────────────────────────────────────
    @GetMapping("/sections")
    public ResponseEntity<?> sections(Authentication auth) {
        var principal = resolveUser(auth);
        if (principal == null) return unauthorized();

        Long teacherId = principal.getUser().getId();
        List<ClassSection> sections = sectionRepo.findByTeacherId(teacherId);

        List<SectionDto> result = sections.stream().map(sec -> {
            boolean open = sec.getSemester() != null &&
                    evaluationService.isWindowOpen(sec.getSemester().getId(), EvaluationKind.STUDENT_FEEDBACK);
            long enrolled = enrollRepo.countBySection_Id(sec.getId());
            long responses = submissionRepo.countByKindAndSectionId(EvaluationKind.STUDENT_FEEDBACK, sec.getId());

            SectionDto dto = new SectionDto();
            dto.setSectionId(sec.getId());
            dto.setSemester(sec.getSemester() != null ? sec.getSemester().getName() : null);
            dto.setCourseCode(sec.getCourse() != null ? sec.getCourse().getCode() : null);
            dto.setCourseName(sec.getCourse() != null ? sec.getCourse().getName() : null);
            dto.setTeacherName(sec.getTeacher() != null
                    ? (sec.getTeacher().getFullName() != null ? sec.getTeacher().getFullName() : sec.getTeacher().getUsername())
                    : null);
            dto.setShiftTime(sec.getShiftTime() != null ? sec.getShiftTime().name() : null);
            dto.setRoom((sec.getBuilding() != null ? sec.getBuilding() : "") + "-" + (sec.getRoom() != null ? sec.getRoom() : ""));
            dto.setSectionName(sec.getSectionName());
            dto.setWindowOpen(open);
            dto.setAlreadySubmitted(false);
            dto.setEnrolled(enrolled);
            dto.setResponseCount(responses);
            return dto;
        }).toList();

        return ResponseEntity.ok(ApiResponse.ok(result));
    }

    // ── GET /api/teacher/sections/{sectionId}/stats ───────────────────────────
    @GetMapping("/sections/{sectionId}/stats")
    public ResponseEntity<?> sectionStats(@PathVariable Long sectionId, Authentication auth) {
        var principal = resolveUser(auth);
        if (principal == null) return unauthorized();

        Long teacherId = principal.getUser().getId();
        var section = sectionRepo.findById(sectionId).orElse(null);
        if (section == null) return ResponseEntity.notFound().build();
        if (!section.getTeacher().getId().equals(teacherId))
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiResponse.error("Access denied."));

        long responses = submissionRepo.countByKindAndSectionId(EvaluationKind.STUDENT_FEEDBACK, sectionId);
        long enrolled = enrollRepo.countBySection_Id(sectionId);
        double rate = (enrolled > 0) ? (responses * 100.0 / enrolled) : 0.0;
        Double overallAvg = answerRepo.overallAvgScoreBySection(sectionId);
        List<QuestionStat> stats = answerRepo.ratingStatsBySection(sectionId);
        List<String> comments = answerRepo.commentsBySection(sectionId);

        List<Map<String, Object>> statList = stats.stream().map(s -> {
            Map<String, Object> m = new HashMap<>();
            m.put("questionId", s.getQuestionId());
            m.put("questionText", s.getQuestionText());
            m.put("avgScore", s.getAvgScore());
            m.put("minScore", s.getMinScore());
            m.put("maxScore", s.getMaxScore());
            m.put("count", s.getCount());
            return m;
        }).toList();

        Map<String, Object> data = new HashMap<>();
        data.put("responses", responses);
        data.put("enrolled", enrolled);
        data.put("rate", Math.round(rate * 10.0) / 10.0);
        data.put("overallAvg", overallAvg);
        data.put("questionStats", statList);
        data.put("comments", comments);

        return ResponseEntity.ok(ApiResponse.ok(data));
    }

    // ── GET /api/teacher/self-assessment/status ───────────────────────────────
    /**
     * Returns the current TEACHER_SELF evaluation window status so the mobile
     * app can show a reminder banner at week 8 of the semester.
     *
     * Response fields:
     *   windowOpen      – true if window is open right now
     *   alreadySubmitted – true if this teacher already submitted for this semester
     *   semesterId      – current semester id (or null if no active semester)
     *   semesterName    – e.g. "2025–2026 S2"
     *   openAt          – ISO datetime when window opens (or null)
     *   closeAt         – ISO datetime when window closes (or null)
     */
    @GetMapping("/self-assessment/status")
    public ResponseEntity<?> selfAssessmentStatus(Authentication auth) {
        var principal = resolveUser(auth);
        if (principal == null) return unauthorized();

        var now = windowService.now();
        var today = now.toLocalDate();

        // Find the active semester by today's date
        var semesterOpt = semesterRepo
                .findFirstByStartDateLessThanEqualAndEndDateGreaterThanEqualOrderByStartDateDesc(today, today);

        Map<String, Object> data = new HashMap<>();

        if (semesterOpt.isEmpty()) {
            data.put("windowOpen", false);
            data.put("alreadySubmitted", false);
            data.put("semesterId", null);
            data.put("semesterName", null);
            data.put("openAt", null);
            data.put("closeAt", null);
            return ResponseEntity.ok(ApiResponse.ok(data));
        }

        var semester = semesterOpt.get();
        boolean windowOpen = evaluationService.isTeacherSelfWindowOpen(principal.getUser(), semester);

        boolean alreadySubmitted = submissionRepo
                .findByKindAndSemester_IdAndSectionIsNullAndSubmittedBy_Id(
                        EvaluationKind.TEACHER_SELF, semester.getId(), principal.getUser().getId())
                .isPresent();

        Optional<EvaluationWindow> windowOpt = windowRepo
                .findBySemester_IdAndKind(semester.getId(), EvaluationKind.TEACHER_SELF);

        data.put("windowOpen", windowOpen);
        data.put("alreadySubmitted", alreadySubmitted);
        data.put("semesterId", semester.getId());
        data.put("semesterName", semester.getName());
        data.put("openAt", windowOpt.map(w -> w.getOpenAt().format(ISO_FMT)).orElse(null));
        data.put("closeAt", windowOpt.map(w -> w.getCloseAt().format(ISO_FMT)).orElse(null));

        return ResponseEntity.ok(ApiResponse.ok(data));
    }

    // ── GET /api/teacher/self-assessment/questions ──────────────────────────
    @GetMapping("/self-assessment/questions")
    public ResponseEntity<?> selfAssessmentQuestions(Authentication auth) {
    var principal = resolveUser(auth);
    if (principal == null) return unauthorized();

    var today = windowService.now().toLocalDate();
    var semesterOpt = semesterRepo
        .findFirstByStartDateLessThanEqualAndEndDateGreaterThanEqualOrderByStartDateDesc(today, today);

    if (semesterOpt.isEmpty()) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
            .body(ApiResponse.error("No active semester for self-assessment."));
    }

    var semester = semesterOpt.get();
    if (!evaluationService.isTeacherSelfWindowOpen(principal.getUser(), semester)) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
            .body(ApiResponse.error("Teacher self-assessment window is closed."));
    }

    boolean alreadySubmitted = submissionRepo
        .findByKindAndSemester_IdAndSectionIsNullAndSubmittedBy_Id(
            EvaluationKind.TEACHER_SELF,
            semester.getId(),
            principal.getUser().getId())
        .isPresent();

    if (alreadySubmitted) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
            .body(ApiResponse.error("You have already submitted your self-assessment."));
    }

    List<QuestionDto> questions = evaluationService.activeQuestions(EvaluationKind.TEACHER_SELF)
        .stream()
        .map(this::toQuestionDto)
        .toList();

    return ResponseEntity.ok(ApiResponse.ok(questions));
    }

    // ── POST /api/teacher/self-assessment ───────────────────────────────────
    @PostMapping("/self-assessment")
    public ResponseEntity<?> submitSelfAssessment(@RequestBody FeedbackSubmitRequest req,
                          Authentication auth) {
    var principal = resolveUser(auth);
    if (principal == null) return unauthorized();

    var today = windowService.now().toLocalDate();
    var semesterOpt = semesterRepo
        .findFirstByStartDateLessThanEqualAndEndDateGreaterThanEqualOrderByStartDateDesc(today, today);

    if (semesterOpt.isEmpty()) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
            .body(ApiResponse.error("No active semester for self-assessment."));
    }

    var semester = semesterOpt.get();
    boolean alreadySubmitted = submissionRepo
        .findByKindAndSemester_IdAndSectionIsNullAndSubmittedBy_Id(
            EvaluationKind.TEACHER_SELF,
            semester.getId(),
            principal.getUser().getId())
        .isPresent();

    if (alreadySubmitted) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
            .body(ApiResponse.error("You have already submitted your self-assessment."));
    }

    try {
        evaluationService.saveTeacherSelf(
            principal.getUser(),
            semester,
            req.getAnswers() != null ? req.getAnswers() : Map.of());
        return ResponseEntity.ok(ApiResponse.ok("Self-assessment submitted successfully.", null));
    } catch (IllegalStateException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
            .body(ApiResponse.error(ex.getMessage()));
    }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private UserPrincipal resolveUser(Authentication auth) {
        if (auth == null || !(auth.getPrincipal() instanceof UserPrincipal p)) return null;
        return p;
    }

    private ResponseEntity<?> unauthorized() {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(ApiResponse.error("Unauthorized."));
    }

    private QuestionDto toQuestionDto(Question q) {
        QuestionDto dto = new QuestionDto();
        dto.setId(q.getId());
        dto.setOrderNo(q.getOrderNo());
        dto.setText(q.getText());
        dto.setType(q.getType().name());
        dto.setScaleMin(q.getScaleMin());
        dto.setScaleMax(q.getScaleMax());
        return dto;
    }
}
