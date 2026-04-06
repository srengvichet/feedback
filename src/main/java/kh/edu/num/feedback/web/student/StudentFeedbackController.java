package kh.edu.num.feedback.web.student;

import kh.edu.num.feedback.domain.entity.EvaluationKind;
import kh.edu.num.feedback.domain.repo.AnswerRepository;
import kh.edu.num.feedback.domain.repo.ClassSectionRepository;
import kh.edu.num.feedback.domain.repo.EnrollmentRepository;
import kh.edu.num.feedback.domain.repo.SubmissionRepository;
import kh.edu.num.feedback.security.UserPrincipal;
import kh.edu.num.feedback.service.EvaluationService;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@Controller
@RequestMapping("/student/feedback")
public class StudentFeedbackController {

  private final ClassSectionRepository sectionRepo;
  private final SubmissionRepository submissionRepo;
  private final AnswerRepository answerRepo;
  private final EnrollmentRepository enrollRepo;
  private final EvaluationService evaluationService;

  public StudentFeedbackController(ClassSectionRepository sectionRepo,
                                   SubmissionRepository submissionRepo,
                                   AnswerRepository answerRepo,
                                   EnrollmentRepository enrollRepo,
                                   EvaluationService evaluationService) {
    this.sectionRepo = sectionRepo;
    this.submissionRepo = submissionRepo;
    this.answerRepo = answerRepo;
    this.enrollRepo = enrollRepo;
    this.evaluationService = evaluationService;
  }

  @GetMapping("/{sectionId}")
  public String form(@PathVariable Long sectionId,
                     Authentication auth,
                     Model model,
                     @RequestParam(required = false) String msg) {

    if (auth == null || auth.getPrincipal() == null) return "redirect:/login";
    if (!(auth.getPrincipal() instanceof UserPrincipal)) return "redirect:/login?msg=bad_principal";

    var student = ((UserPrincipal) auth.getPrincipal()).getUser();
    if (student == null || student.getId() == null) return "redirect:/login?msg=no_user";

    // Must be enrolled
    if (!enrollRepo.existsByStudent_IdAndSection_Id(student.getId(), sectionId)) {
      return "redirect:/student?msg=not_enrolled";
    }

    var section = sectionRepo.findById(sectionId).orElseThrow();

    boolean open = evaluationService.isStudentFeedbackWindowOpen(student, section);

    var questions = evaluationService.activeQuestions(EvaluationKind.STUDENT_FEEDBACK);

    Map<Long, String> answers = new HashMap<>();
    boolean alreadySubmitted = false;

    var subOpt = submissionRepo.findByKindAndSemester_IdAndSection_IdAndSubmittedBy_Id(
        EvaluationKind.STUDENT_FEEDBACK,
        section.getSemester().getId(),
        section.getId(),
        student.getId()
    );

    if (subOpt.isPresent()) {
      alreadySubmitted = true;
      var sub = subOpt.get();

      var list = answerRepo.findBySubmission_Id(sub.getId());
      for (var a : list) {
        if (a.getQuestion() == null) continue;
        Long qid = a.getQuestion().getId();

        if (a.getNumericValue() != null) answers.put(qid, String.valueOf(a.getNumericValue()));
        else if (a.getTextValue() != null) answers.put(qid, a.getTextValue());
      }
    }

    model.addAttribute("section", section);
    model.addAttribute("questions", questions);
    model.addAttribute("open", open);
    model.addAttribute("alreadySubmitted", alreadySubmitted);
    model.addAttribute("answers", answers);
    model.addAttribute("msg", msg);

    return "student/feedback_form";
  }

  @PostMapping("/{sectionId}")
  public String submit(@PathVariable Long sectionId,
                       Authentication auth,
                       @RequestParam Map<String, String> params) {

    if (auth == null || auth.getPrincipal() == null) return "redirect:/login";
    if (!(auth.getPrincipal() instanceof UserPrincipal)) return "redirect:/login?msg=bad_principal";

    var student = ((UserPrincipal) auth.getPrincipal()).getUser();
    if (student == null || student.getId() == null) return "redirect:/login?msg=no_user";

    // Must be enrolled
    if (!enrollRepo.existsByStudent_IdAndSection_Id(student.getId(), sectionId)) {
      return "redirect:/student?msg=not_enrolled";
    }

    var section = sectionRepo.findById(sectionId).orElseThrow();

    boolean open = evaluationService.isStudentFeedbackWindowOpen(student, section);
    if (!open) return "redirect:/student/feedback/" + sectionId + "?msg=closed";

    Map<Long, String> inputs = new HashMap<>();
    for (var entry : params.entrySet()) {
      if (entry.getKey().startsWith("q_")) {
        Long qid = Long.parseLong(entry.getKey().substring(2));
        inputs.put(qid, entry.getValue());
      }
    }

    try {
      evaluationService.saveStudentFeedback(student, section, inputs);
      return "redirect:/student?msg=feedback_saved";
    } catch (Exception ex) {
      return "redirect:/student/feedback/" + sectionId + "?msg=error";
    }
  }
}