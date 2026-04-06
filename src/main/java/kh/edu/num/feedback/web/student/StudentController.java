package kh.edu.num.feedback.web.student;

import kh.edu.num.feedback.domain.entity.EvaluationKind;
import kh.edu.num.feedback.domain.repo.EnrollmentRepository;
import kh.edu.num.feedback.domain.repo.SubmissionRepository;
import kh.edu.num.feedback.security.UserPrincipal;
import kh.edu.num.feedback.service.EvaluationService;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.Map;

@Controller
@RequestMapping("/student")
public class StudentController {

  private final EnrollmentRepository enrollRepo;
  private final SubmissionRepository submissionRepo;
  private final EvaluationService evaluationService;

  private static final ZoneId ZONE = ZoneId.of("Asia/Phnom_Penh");

  public StudentController(EnrollmentRepository enrollRepo,
      SubmissionRepository submissionRepo,
      EvaluationService evaluationService) {
    this.enrollRepo = enrollRepo;
    this.submissionRepo = submissionRepo;
    this.evaluationService = evaluationService;
  }

  @GetMapping
  public String studentHome(Authentication auth,
      Model model,
      @RequestParam(required = false) String msg) {

    if (auth == null || auth.getPrincipal() == null)
      return "redirect:/login";

    Object p = auth.getPrincipal();
    if (!(p instanceof UserPrincipal))
      return "redirect:/login?msg=bad_principal";

    UserPrincipal principal = (UserPrincipal) p;
    var student = principal.getUser();
    if (student == null || student.getId() == null)
      return "redirect:/login?msg=no_user";

    Long studentId = student.getId();

    // ✅ Fetch-join section+semester+course+teacher
    var enrollments = enrollRepo.findForStudentHome(studentId);

    // ✅ NEW: window open per SECTION (reliable for UI)
    Map<Long, Boolean> windowOpenBySectionId = new HashMap<>();

    Map<Long, LocalDateTime> submittedAtBySectionId = new HashMap<>();

    for (var e : enrollments) {
      var sec = e.getSection();
      if (sec == null || sec.getId() == null)
        continue;

      Long sectionId = sec.getId();

      Long semId = (sec.getSemester() != null) ? sec.getSemester().getId() : null;
      boolean open = evaluationService.isStudentFeedbackWindowOpen(student, sec);

      // ✅ store open by sectionId for the template
      windowOpenBySectionId.put(sectionId, open);

      // submission time
      if (semId != null) {
        var subOpt = submissionRepo.findByKindAndSemester_IdAndSection_IdAndSubmittedBy_Id(
            EvaluationKind.STUDENT_FEEDBACK, semId, sectionId, studentId);
        subOpt.ifPresent(sub -> submittedAtBySectionId.put(sectionId, sub.getSubmittedAt()));
      }
    }
    

    // Count sections that are open AND not yet submitted
    long pendingFeedbackCount = enrollments.stream()
        .filter(e -> {
            Long sId = e.getSection() != null ? e.getSection().getId() : null;
            if (sId == null) return false;
            boolean open = Boolean.TRUE.equals(windowOpenBySectionId.get(sId));
            boolean submitted = submittedAtBySectionId.containsKey(sId);
            return open && !submitted;
        }).count();

    boolean anyFeedbackOpen = windowOpenBySectionId.values().stream().anyMatch(Boolean.TRUE::equals);

    model.addAttribute("enrollments", enrollments);
    model.addAttribute("student", student);
    model.addAttribute("windowOpenBySectionId", windowOpenBySectionId);
    model.addAttribute("submittedAtBySectionId", submittedAtBySectionId);
    model.addAttribute("pendingFeedbackCount", pendingFeedbackCount);
    model.addAttribute("anyFeedbackOpen", anyFeedbackOpen);
    model.addAttribute("now", LocalDateTime.now(ZONE));
    model.addAttribute("msg", msg);

    return "student/home";
  }

  @PostMapping("/join-by-code")
  public String joinByCode(@RequestParam("code") String code) {
    String clean = normalizeCode(code);
    if (clean.isBlank())
      return "redirect:/student?msg=empty_code";
    return "redirect:/student/join/" + clean;
  }

  private static String normalizeCode(String code) {
    if (code == null)
      return "";
    return code.trim().replaceAll("\\s+", "").toUpperCase();
  }
}