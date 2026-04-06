package kh.edu.num.feedback.api;

import kh.edu.num.feedback.api.dto.*;
import kh.edu.num.feedback.domain.entity.*;
import kh.edu.num.feedback.domain.repo.ClassJoinCodeRepository;
import kh.edu.num.feedback.domain.repo.ClassSectionRepository;
import kh.edu.num.feedback.domain.repo.EnrollmentRepository;
import kh.edu.num.feedback.domain.repo.SubmissionRepository;
import kh.edu.num.feedback.domain.repo.TeachingScheduleRepository;
import kh.edu.num.feedback.domain.repo.UserAccountRepository;
import kh.edu.num.feedback.security.UserPrincipal;
import kh.edu.num.feedback.service.EvaluationService;
import kh.edu.num.feedback.service.StudentRegistrySyncService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;

@RestController
@RequestMapping("/api/student")
public class ApiStudentController {

    private static final ZoneId ZONE = ZoneId.of("Asia/Phnom_Penh");

    private final EnrollmentRepository enrollRepo;
    private final SubmissionRepository submissionRepo;
    private final ClassSectionRepository sectionRepo;
    private final ClassJoinCodeRepository joinCodeRepo;
    private final TeachingScheduleRepository teachingRepo;
    private final EvaluationService evaluationService;
    private final UserAccountRepository userRepo;
    private final PasswordEncoder passwordEncoder;
    private final StudentRegistrySyncService registrySync;

    public ApiStudentController(EnrollmentRepository enrollRepo,
                                SubmissionRepository submissionRepo,
                                ClassSectionRepository sectionRepo,
                                ClassJoinCodeRepository joinCodeRepo,
                                TeachingScheduleRepository teachingRepo,
                                EvaluationService evaluationService,
                                UserAccountRepository userRepo,
                                PasswordEncoder passwordEncoder,
                                StudentRegistrySyncService registrySync) {
        this.enrollRepo = enrollRepo;
        this.submissionRepo = submissionRepo;
        this.sectionRepo = sectionRepo;
        this.joinCodeRepo = joinCodeRepo;
        this.teachingRepo = teachingRepo;
        this.evaluationService = evaluationService;
        this.userRepo = userRepo;
        this.passwordEncoder = passwordEncoder;
        this.registrySync = registrySync;
    }

    // ── GET /api/student/profile ─────────────────────────────────────────────
    @GetMapping("/profile")
    public ResponseEntity<?> profile(Authentication auth) {
        UserAccount student = resolveUser(auth);
        if (student == null) return unauthorized();
        return ResponseEntity.ok(ApiResponse.ok(ApiAuthController.toProfileDto(student)));
    }

    // ── PUT /api/student/profile ─────────────────────────────────────────────
    @PutMapping("/profile")
    public ResponseEntity<?> updateProfile(@RequestBody UpdateProfileRequest req, Authentication auth) {
        UserAccount student = resolveUser(auth);
        if (student == null) return unauthorized();

        if (req.getFullName() != null && !req.getFullName().isBlank())
            student.setFullName(req.getFullName().trim());
        if (req.getEmail() != null)
            student.setEmail(req.getEmail().trim());
        if (req.getPhone() != null)
            student.setPhone(req.getPhone().trim());

        if (req.getNewPassword() != null && !req.getNewPassword().isBlank()) {
            if (!student.isMustChangePassword()) {
                // Normal password change — require current password
                if (req.getCurrentPassword() == null ||
                        !passwordEncoder.matches(req.getCurrentPassword(), student.getPasswordHash()))
                    return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                            .body(ApiResponse.error("Current password is incorrect."));
            }
            if (req.getNewPassword().length() < 6)
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(ApiResponse.error("New password must be at least 6 characters."));
            student.setPasswordHash(passwordEncoder.encode(req.getNewPassword()));
            student.setMustChangePassword(false);
        }

        userRepo.save(student);
        return ResponseEntity.ok(ApiResponse.ok("Profile updated successfully.", ApiAuthController.toProfileDto(student)));
    }

    // ── GET /api/student/sections ────────────────────────────────────────────
    @GetMapping("/sections")
    public ResponseEntity<?> sections(Authentication auth) {
        UserAccount student = resolveUser(auth);
        if (student == null) return unauthorized();

        var enrollments = enrollRepo.findForStudentHome(student.getId());
        List<SectionDto> result = new ArrayList<>();

        for (Enrollment e : enrollments) {
            ClassSection sec = e.getSection();
            if (sec == null) continue;

            boolean open = evaluationService.isStudentFeedbackWindowOpen(student, sec);

            LocalDateTime submittedAt = null;
            boolean alreadySubmitted = false;
            if (sec.getSemester() != null) {
                var sub = submissionRepo.findByKindAndSemester_IdAndSection_IdAndSubmittedBy_Id(
                        EvaluationKind.STUDENT_FEEDBACK,
                        sec.getSemester().getId(),
                        sec.getId(),
                        student.getId());
                if (sub.isPresent()) {
                    alreadySubmitted = true;
                    submittedAt = sub.get().getSubmittedAt();
                }
            }

            result.add(toSectionDto(sec, open, alreadySubmitted, submittedAt));
        }

        return ResponseEntity.ok(ApiResponse.ok(result));
    }

    // ── GET /api/student/feedback/{sectionId}/questions ──────────────────────
    @GetMapping("/feedback/{sectionId}/questions")
    public ResponseEntity<?> questions(@PathVariable Long sectionId, Authentication auth) {
        UserAccount student = resolveUser(auth);
        if (student == null) return unauthorized();

        if (!enrollRepo.existsByStudent_IdAndSection_Id(student.getId(), sectionId))
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiResponse.error("Not enrolled in this section."));

        var section = sectionRepo.findById(sectionId).orElse(null);
        if (section == null) return ResponseEntity.notFound().build();

        boolean open = evaluationService.isStudentFeedbackWindowOpen(student, section);
        if (!open) return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiResponse.error("Feedback window is closed."));

        boolean alreadySubmitted = submissionRepo.findByKindAndSemester_IdAndSection_IdAndSubmittedBy_Id(
                EvaluationKind.STUDENT_FEEDBACK,
                section.getSemester().getId(),
                sectionId,
                student.getId()).isPresent();

        if (alreadySubmitted)
            return ResponseEntity.status(HttpStatus.CONFLICT).body(ApiResponse.error("You have already submitted feedback for this section."));

        List<QuestionDto> questions = evaluationService.activeQuestions(EvaluationKind.STUDENT_FEEDBACK)
                .stream().map(this::toQuestionDto).toList();

        return ResponseEntity.ok(ApiResponse.ok(questions));
    }

    // ── POST /api/student/feedback/{sectionId} ───────────────────────────────
    @PostMapping("/feedback/{sectionId}")
    public ResponseEntity<?> submitFeedback(@PathVariable Long sectionId,
                                            @RequestBody FeedbackSubmitRequest req,
                                            Authentication auth) {
        UserAccount student = resolveUser(auth);
        if (student == null) return unauthorized();

        if (!enrollRepo.existsByStudent_IdAndSection_Id(student.getId(), sectionId))
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiResponse.error("Not enrolled in this section."));

        var section = sectionRepo.findById(sectionId).orElse(null);
        if (section == null) return ResponseEntity.notFound().build();

        try {
            evaluationService.saveStudentFeedback(student, section, req.getAnswers() != null ? req.getAnswers() : Map.of());
            return ResponseEntity.ok(ApiResponse.ok("Feedback submitted successfully.", null));
        } catch (IllegalStateException ex) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiResponse.error(ex.getMessage()));
        }
    }

    // ── POST /api/student/join/{code} ────────────────────────────────────────
    @PostMapping("/join/{code}")
    @Transactional
    public ResponseEntity<?> joinSection(@PathVariable String code, Authentication auth) {
        UserAccount student = resolveUser(auth);
        if (student == null) return unauthorized();

        String cleanCode = code.trim().replaceAll("\\s+", "").toUpperCase();
        var now = LocalDateTime.now(ZONE);
        var jc = joinCodeRepo.findByCodeAndActiveTrueAndExpiresAtAfter(cleanCode, now).orElse(null);
        if (jc == null)
            return ResponseEntity.badRequest().body(ApiResponse.error("Join code is invalid, closed, or expired."));

        // Sync registry info if not yet synced
        registrySync.syncAndClaimIfPresent(student);

        // Effective cohort/group/shift: join code overrides then fall back to student profile
        Cohort effCohort = jc.getCohort() != null ? jc.getCohort() : student.getCohort();
        Integer effGroup = jc.getGroupNo() != null ? jc.getGroupNo() : student.getGroupNo();
        ShiftTime effShift = jc.getShiftTime() != null ? jc.getShiftTime() : student.getShiftTime();

        if (effCohort == null || effGroup == null || effShift == null)
            return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                    .body(ApiResponse.error("Your profile is missing Cohort/Group/Shift. Please contact admin."));

        // Block mismatched students
        boolean cohortOk = jc.getCohort() == null || student.getCohort() == null ||
                Objects.equals(student.getCohort().getId(), jc.getCohort().getId());
        boolean groupOk = jc.getGroupNo() == null || student.getGroupNo() == null ||
                Objects.equals(student.getGroupNo(), jc.getGroupNo());
        boolean shiftOk = jc.getShiftTime() == null || student.getShiftTime() == null ||
                student.getShiftTime() == jc.getShiftTime();
        if (!cohortOk || !groupOk || !shiftOk)
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(ApiResponse.error("This join code is not for your Cohort/Group/Shift."));

        // Back-fill missing student fields from join code
        boolean changed = false;
        if (student.getCohort() == null && jc.getCohort() != null) { student.setCohort(jc.getCohort()); changed = true; }
        if (student.getGroupNo() == null && jc.getGroupNo() != null) { student.setGroupNo(jc.getGroupNo()); changed = true; }
        if (student.getShiftTime() == null && jc.getShiftTime() != null) { student.setShiftTime(jc.getShiftTime()); changed = true; }
        if (changed) userRepo.save(student);

        // Find matching teaching schedules
        List<TeachingSchedule> schedules = teachingRepo.findForStudent(
                jc.getSemester().getId(), effCohort.getId(), effGroup, effShift);

        if (jc.getScheduleId() != null)
            schedules = schedules.stream()
                    .filter(t -> Objects.equals(t.getId(), jc.getScheduleId()))
                    .toList();

        if (schedules.isEmpty())
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ApiResponse.error("No teaching schedule found for this join code."));

        // Build section name and create/find sections, then enroll
        String sectionName = "C" + effCohort.getCohortNo() + "-G" + effGroup + "-" + effShift.name();
        int enrolled = 0;
        for (TeachingSchedule ts : schedules) {
            ClassSection sec = sectionRepo
                    .findFirstBySemester_IdAndCourse_IdAndTeacher_IdAndShiftTimeAndBuildingAndRoomAndSectionName(
                            ts.getSemester().getId(),
                            ts.getCourse().getId(),
                            ts.getTeacher().getId(),
                            ts.getShiftTime(),
                            ts.getBuilding(),
                            ts.getRoom(),
                            sectionName)
                    .orElseGet(() -> {
                        ClassSection s = new ClassSection();
                        s.setSemester(ts.getSemester());
                        s.setCourse(ts.getCourse());
                        s.setTeacher(ts.getTeacher());
                        s.setShiftTime(ts.getShiftTime());
                        s.setBuilding(ts.getBuilding());
                        s.setRoom(ts.getRoom());
                        s.setSectionName(sectionName);
                        s.setCohort(effCohort);
                        return sectionRepo.save(s);
                    });

            if (!enrollRepo.existsByStudent_IdAndSection_Id(student.getId(), sec.getId())) {
                Enrollment e = new Enrollment();
                e.setStudent(student);
                e.setSection(sec);
                enrollRepo.save(e);
                enrolled++;
            }
        }

        String msg = enrolled > 0
                ? "Successfully joined " + enrolled + " subject(s)."
                : "You are already enrolled in all sections for this code.";
        return ResponseEntity.ok(ApiResponse.ok(msg, null));
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private UserAccount resolveUser(Authentication auth) {
        if (auth == null || !(auth.getPrincipal() instanceof UserPrincipal p)) return null;
        return p.getUser();
    }

    private ResponseEntity<?> unauthorized() {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(ApiResponse.error("Unauthorized."));
    }

    private SectionDto toSectionDto(ClassSection sec, boolean open, boolean submitted, LocalDateTime submittedAt) {
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
        dto.setAlreadySubmitted(submitted);
        dto.setSubmittedAt(submittedAt);
        return dto;
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
