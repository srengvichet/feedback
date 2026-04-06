package kh.edu.num.feedback.web.teacher;

import kh.edu.num.feedback.domain.entity.EvaluationKind;
import kh.edu.num.feedback.domain.entity.EvaluationWindow;
import kh.edu.num.feedback.domain.repo.ClassSectionRepository;
import kh.edu.num.feedback.domain.repo.EnrollmentRepository;
import kh.edu.num.feedback.domain.repo.EvaluationWindowRepository;
import kh.edu.num.feedback.domain.repo.SubmissionRepository;
import kh.edu.num.feedback.security.UserPrincipal;
import kh.edu.num.feedback.service.EvaluationService;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import kh.edu.num.feedback.domain.repo.SemesterRepository;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;

@Controller
public class TeacherController {

  private final ClassSectionRepository sectionRepo;
  private final EvaluationWindowRepository windowRepo;
  private final SubmissionRepository submissionRepo;
  private final EnrollmentRepository enrollmentRepo;
  private final EvaluationService evaluationService;
  private final SemesterRepository semesterRepo;

  public TeacherController(ClassSectionRepository sectionRepo,
      EvaluationWindowRepository windowRepo,
      SubmissionRepository submissionRepo,
      EnrollmentRepository enrollmentRepo,
      EvaluationService evaluationService,
      SemesterRepository semesterRepo) {
    this.sectionRepo = sectionRepo;
    this.windowRepo = windowRepo;
    this.submissionRepo = submissionRepo;
    this.enrollmentRepo = enrollmentRepo;
    this.evaluationService = evaluationService;
    this.semesterRepo = semesterRepo;
  }

  @GetMapping("/teacher")
  public String teacherHome(Authentication auth, Model model) {
    var principal = (UserPrincipal) auth.getPrincipal();
    Long teacherId = principal.getUser().getId();

    var sections = sectionRepo.findByTeacherId(teacherId);
    var now = LocalDateTime.now(ZoneId.of("Asia/Phnom_Penh"));


    // Unique semesters from assigned sections (keeps stable order)
    Map<Long, kh.edu.num.feedback.domain.entity.Semester> semesterById = new LinkedHashMap<>();
    for (var sec : sections) {
      
      if (sec.getSemester() != null) {
        semesterById.putIfAbsent(sec.getSemester().getId(), sec.getSemester());
      }
    }
    // Start with semesters from sections
    var semesters = new ArrayList<>(semesterById.values());

    // ✅ Option B: if teacher has no sections, still allow self-assessment based on
    // admin window
    if (semesters.isEmpty()) {
      semesters = new ArrayList<>(semesterRepo.findAll());
    }

    // Maps for UI
    Map<Long, EvaluationWindow> selfWindowMap = new HashMap<>();
    Map<Long, Boolean> selfOpenMap = new HashMap<>();
    Map<Long, LocalDateTime> selfSubmittedAtMap = new HashMap<>();

    for (var sem : semesters) {
      Long semId = sem.getId();

      // window info (optional)
      selfWindowMap.put(semId,
          windowRepo.findFirstBySemester_IdAndKind(semId, EvaluationKind.TEACHER_SELF).orElse(null));

      // open status (authoritative check)
      selfOpenMap.put(semId, evaluationService.isTeacherSelfWindowOpen(principal.getUser(), sem));

      // submission status (COMPLETED if exists)
      submissionRepo
          .findByKindAndSemester_IdAndSectionIsNullAndSubmittedBy_Id(
              EvaluationKind.TEACHER_SELF, semId, teacherId)
          .ifPresent(sub -> selfSubmittedAtMap.put(semId, sub.getSubmittedAt()));
    }

    // Per-section: enrolled count and student-feedback submission count
    Map<Long, Long> sectionEnrolledMap = new HashMap<>();
    Map<Long, Long> sectionFeedbackMap = new HashMap<>();
    for (var sec : sections) {
      long enrolled = enrollmentRepo.countBySection_Id(sec.getId());
      long feedback = submissionRepo.countByKindAndSectionId(EvaluationKind.STUDENT_FEEDBACK, sec.getId());
      sectionEnrolledMap.put(sec.getId(), enrolled);
      sectionFeedbackMap.put(sec.getId(), feedback);
    }
    long totalEnrolled = sectionEnrolledMap.values().stream().mapToLong(Long::longValue).sum();
    long totalFeedback = sectionFeedbackMap.values().stream().mapToLong(Long::longValue).sum();

    model.addAttribute("sections", sections);
    model.addAttribute("now", now);
    model.addAttribute("sectionEnrolledMap", sectionEnrolledMap);
    model.addAttribute("sectionFeedbackMap", sectionFeedbackMap);
    model.addAttribute("totalEnrolled", totalEnrolled);
    model.addAttribute("totalFeedback", totalFeedback);

    model.addAttribute("semesters", semesters);
    model.addAttribute("selfWindowMap", selfWindowMap);
    model.addAttribute("selfOpenMap", selfOpenMap);
    model.addAttribute("selfSubmittedAtMap", selfSubmittedAtMap);

    long pendingSelfCount = semesters.stream()
        .filter(sem -> Boolean.TRUE.equals(selfOpenMap.get(sem.getId()))
                    && !selfSubmittedAtMap.containsKey(sem.getId()))
        .count();

    boolean anySelfOpen = selfOpenMap.values().stream().anyMatch(Boolean.TRUE::equals);

    model.addAttribute("selfTotal", semesters.size());
    model.addAttribute("selfCompleted", selfSubmittedAtMap.size());
    model.addAttribute("pendingSelfCount", pendingSelfCount);
    model.addAttribute("anySelfOpen", anySelfOpen);

    return "teacher/home";
  }
}
