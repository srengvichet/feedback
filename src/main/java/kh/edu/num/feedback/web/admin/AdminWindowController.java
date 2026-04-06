package kh.edu.num.feedback.web.admin;

import kh.edu.num.feedback.domain.entity.EvaluationKind;
import kh.edu.num.feedback.domain.entity.EvaluationWindow;
import kh.edu.num.feedback.domain.entity.UserAccount;
import kh.edu.num.feedback.domain.repo.ClassSectionRepository;
import kh.edu.num.feedback.domain.repo.CohortRepository;
import kh.edu.num.feedback.domain.repo.CohortWindowOverrideRepository;
import kh.edu.num.feedback.domain.repo.EnrollmentRepository;
import kh.edu.num.feedback.domain.repo.EvaluationWindowRepository;
import kh.edu.num.feedback.domain.repo.SemesterRepository;
import kh.edu.num.feedback.domain.entity.CohortWindowOverride;
import kh.edu.num.feedback.service.EmailService;
import kh.edu.num.feedback.service.WindowService;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

@Controller
@RequestMapping("/admin/windows")
public class AdminWindowController {

  private final SemesterRepository semesterRepo;
  private final EvaluationWindowRepository windowRepo;
  private final CohortWindowOverrideRepository overrideRepo;
  private final CohortRepository cohortRepo;
  private final WindowService windowService;
  private final EmailService emailService;
  private final ClassSectionRepository sectionRepo;
  private final EnrollmentRepository enrollmentRepo;

  public AdminWindowController(SemesterRepository semesterRepo,
                               EvaluationWindowRepository windowRepo,
                               CohortWindowOverrideRepository overrideRepo,
                               CohortRepository cohortRepo,
                               WindowService windowService,
                               EmailService emailService,
                               ClassSectionRepository sectionRepo,
                               EnrollmentRepository enrollmentRepo) {
    this.semesterRepo = semesterRepo;
    this.windowRepo = windowRepo;
    this.overrideRepo = overrideRepo;
    this.cohortRepo = cohortRepo;
    this.windowService = windowService;
    this.emailService = emailService;
    this.sectionRepo = sectionRepo;
    this.enrollmentRepo = enrollmentRepo;
  }

  @GetMapping
  public String page(@RequestParam(required = false) Long semesterId,
                     @RequestParam(required = false) String msg,
                     Model model) {

    var semesters = semesterRepo.findAll();
    if (semesterId == null && !semesters.isEmpty()) {
      semesterId = semesters.get(0).getId();
    }

    model.addAttribute("semesters", semesters);
    model.addAttribute("semesterId", semesterId);
    model.addAttribute("kinds", EvaluationKind.values());
    model.addAttribute("msg", msg);

    LocalDateTime now = windowService.now(); // Cambodia time
    model.addAttribute("now", now);

    model.addAttribute("cohorts", cohortRepo.findAllByOrderByCohortNoAsc());

    if (semesterId != null) {
      var windows = windowRepo.findBySemester_IdOrderByKindAsc(semesterId);
      model.addAttribute("windows", windows);

      Map<EvaluationKind, EvaluationWindow> windowMap = new LinkedHashMap<>();
      Map<EvaluationKind, String> statusMap = new LinkedHashMap<>();

      for (var w : windows) {
        windowMap.put(w.getKind(), w);
        statusMap.put(w.getKind(), computeStatus(w, now));
      }

      model.addAttribute("windowMap", windowMap);
      model.addAttribute("statusMap", statusMap);

      // Cohort overrides per kind
      Map<EvaluationKind, List<CohortWindowOverride>> overrideMap = new LinkedHashMap<>();
      for (EvaluationKind k : EvaluationKind.values()) {
        overrideMap.put(k, overrideRepo.findBySemester_IdAndKindOrderByCohort_CohortNoAscGroupNoAsc(semesterId, k));
      }
      model.addAttribute("overrideMap", overrideMap);

    } else {
      model.addAttribute("windows", java.util.List.of());
      model.addAttribute("windowMap", new LinkedHashMap<>());
      model.addAttribute("statusMap", new LinkedHashMap<>());
      model.addAttribute("overrideMap", new LinkedHashMap<>());
    }

    return "admin/windows";
  }

  // ✅ Accurate status for admin UI
  private String computeStatus(EvaluationWindow w, LocalDateTime now) {
    if (w == null) return "CLOSED";

    LocalDateTime openAt = w.getOpenAt();
    LocalDateTime closeAt = w.getCloseAt();

    // If times are missing, treat as CLOSED (safer than incorrectly showing OPEN)
    if (openAt == null || closeAt == null) return "CLOSED";

    if (now.isBefore(openAt)) return "NOT_YET";
    if (now.isAfter(closeAt)) return "CLOSED";

    // inclusive openAt..closeAt
    return windowService.isOpen(w, now) ? "OPEN" : "CLOSED";
  }

  @PostMapping("/defaults")
  public String createDefaults(@RequestParam Long semesterId) {
    var sem = semesterRepo.findById(semesterId).orElseThrow();
    windowService.createDefaultWindows(sem);
    return "redirect:/admin/windows?semesterId=" + semesterId + "&msg=defaults";
  }

  @PostMapping("/open-now")
  public String openNow(@RequestParam Long semesterId,
                        @RequestParam EvaluationKind kind,
                        @RequestParam(defaultValue = "7") int days) {
    windowService.openNowForDays(semesterId, kind, days);
    if (kind == EvaluationKind.TEACHER_SELF) {
      notifyTeachers(semesterId);
    } else if (kind == EvaluationKind.STUDENT_FEEDBACK) {
      notifyStudents(semesterId);
    }
    return "redirect:/admin/windows?semesterId=" + semesterId + "&msg=openNow";
  }

  @PostMapping("/close-now")
  public String closeNow(@RequestParam Long semesterId,
                         @RequestParam EvaluationKind kind) {
    windowService.closeNow(semesterId, kind);
    return "redirect:/admin/windows?semesterId=" + semesterId + "&msg=closed";
  }

  @PostMapping("/save")
  public String save(@RequestParam Long semesterId,
                     @RequestParam EvaluationKind kind,
                     @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime openAt,
                     @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime closeAt) {

    if (openAt == null || closeAt == null) {
      return "redirect:/admin/windows?semesterId=" + semesterId + "&msg=badRange";
    }
    if (openAt.isAfter(closeAt)) {
      return "redirect:/admin/windows?semesterId=" + semesterId + "&msg=badRange";
    }

    var sem = semesterRepo.findById(semesterId).orElseThrow();

    EvaluationWindow w = windowRepo.findBySemester_IdAndKind(semesterId, kind)
        .orElseGet(EvaluationWindow::new);

    w.setSemester(sem);
    w.setKind(kind);
    w.setOpenAt(openAt);
    w.setCloseAt(closeAt);

    windowRepo.save(w);

    // Notify when admin explicitly opens or schedules a window that is already live
    LocalDateTime now = windowService.now();
    boolean isNowOpen = !openAt.isAfter(now) && !closeAt.isBefore(now);
    if (isNowOpen) {
      if (kind == EvaluationKind.TEACHER_SELF) {
        notifyTeachers(semesterId);
      } else if (kind == EvaluationKind.STUDENT_FEEDBACK) {
        notifyStudents(semesterId);
      }
    }

    return "redirect:/admin/windows?semesterId=" + semesterId + "&msg=saved";
  }

  /** Load teachers who have sections in this semester and send async email notification. */
  private void notifyTeachers(Long semesterId) {
    var window = windowRepo.findBySemester_IdAndKind(semesterId, EvaluationKind.TEACHER_SELF)
        .orElse(null);
    if (window == null) return;

    // Only teachers who actually have at least one class section in this semester
    List<UserAccount> teachers = sectionRepo.findBySemesterId(semesterId).stream()
        .map(sec -> sec.getTeacher())
        .filter(Objects::nonNull)
        .filter(t -> t.getEmail() != null && !t.getEmail().isBlank())
        .collect(Collectors.collectingAndThen(
            Collectors.toMap(UserAccount::getId, t -> t, (a, b) -> a),
            map -> List.copyOf(map.values())
        ));

    emailService.sendSelfAssessmentOpen(teachers, window);
  }

  @PostMapping("/open-cohort")
  public String openForCohort(@RequestParam Long semesterId,
                              @RequestParam EvaluationKind kind,
                              @RequestParam Long cohortId,
                              @RequestParam(required = false) Integer groupNo,
                              @RequestParam(defaultValue = "7") int days) {
    var sem = semesterRepo.findById(semesterId).orElseThrow();
    var cohort = cohortRepo.findById(cohortId).orElseThrow();

    // Upsert: find existing override for same (semester, kind, cohort, groupNo) or create new
    var existing = overrideRepo
        .findBySemester_IdAndKindOrderByCohort_CohortNoAscGroupNoAsc(semesterId, kind)
        .stream()
        .filter(o -> o.getCohort().getId().equals(cohortId)
            && java.util.Objects.equals(o.getGroupNo(), groupNo))
        .findFirst();

    CohortWindowOverride ov = existing.orElseGet(CohortWindowOverride::new);
    ov.setSemester(sem);
    ov.setKind(kind);
    ov.setCohort(cohort);
    ov.setGroupNo(groupNo);
    ov.setOpenAt(windowService.now());
    ov.setCloseAt(windowService.now().plusDays(Math.max(1, days)));
    overrideRepo.save(ov);

    return "redirect:/admin/windows?semesterId=" + semesterId + "&msg=openCohort";
  }

  @PostMapping("/close-cohort")
  public String closeForCohort(@RequestParam Long overrideId,
                               @RequestParam Long semesterId) {
    overrideRepo.deleteById(overrideId);
    return "redirect:/admin/windows?semesterId=" + semesterId + "&msg=closedCohort";
  }

  /** Load students enrolled in this semester's sections and send async email notification. */
  private void notifyStudents(Long semesterId) {
    var window = windowRepo.findBySemester_IdAndKind(semesterId, EvaluationKind.STUDENT_FEEDBACK)
        .orElse(null);
    if (window == null) return;

    List<UserAccount> students = enrollmentRepo.findDistinctStudentsBySemesterId(semesterId);

    emailService.sendFeedbackOpen(students, window);
  }
}