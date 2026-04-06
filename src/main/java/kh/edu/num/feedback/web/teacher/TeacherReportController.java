package kh.edu.num.feedback.web.teacher;

import kh.edu.num.feedback.domain.entity.EvaluationKind;
import kh.edu.num.feedback.domain.repo.*;
import kh.edu.num.feedback.security.UserPrincipal;
import kh.edu.num.feedback.web.teacher.dto.QuestionStat;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

@Controller
@RequestMapping("/teacher/reports")
public class TeacherReportController {

  private final ClassSectionRepository sectionRepo;
  private final SubmissionRepository submissionRepo;
  private final AnswerRepository answerRepo;
  private final EnrollmentRepository enrollmentRepo;

  public TeacherReportController(ClassSectionRepository sectionRepo,
                                 SubmissionRepository submissionRepo,
                                 AnswerRepository answerRepo,
                                 EnrollmentRepository enrollmentRepo) {
    this.sectionRepo = sectionRepo;
    this.submissionRepo = submissionRepo;
    this.answerRepo = answerRepo;
    this.enrollmentRepo = enrollmentRepo;
  }

  @GetMapping
  public String list(org.springframework.security.core.Authentication auth, Model model) {
    var principal = (UserPrincipal) auth.getPrincipal();
    Long teacherId = principal.getUser().getId();

    var sections = sectionRepo.findByTeacherId(teacherId);

    Map<Long, Long> responsesMap = new HashMap<>();
    Map<Long, Long> enrolledMap = new HashMap<>();
    Map<Long, Double> rateMap = new HashMap<>();
    Map<Long, Double> avgMap = new HashMap<>();

    long totalResponses = 0;

    for (var s : sections) {
      Long sectionId = s.getId();

      long responses = submissionRepo.countByKindAndSectionId(EvaluationKind.STUDENT_FEEDBACK, sectionId);
      long enrolled = enrollmentRepo.countBySection_Id(sectionId);
      Double overallAvg = answerRepo.overallAvgScoreBySection(sectionId);

      double rate = (enrolled > 0) ? (responses * 100.0 / enrolled) : 0.0;

      responsesMap.put(sectionId, responses);
      enrolledMap.put(sectionId, enrolled);
      rateMap.put(sectionId, rate);
      avgMap.put(sectionId, overallAvg);

      totalResponses += responses;
    }

    model.addAttribute("sections", sections);
    model.addAttribute("responsesMap", responsesMap);
    model.addAttribute("enrolledMap", enrolledMap);
    model.addAttribute("rateMap", rateMap);
    model.addAttribute("avgMap", avgMap);

    model.addAttribute("totalSections", sections.size());
    model.addAttribute("totalResponses", totalResponses);

    return "teacher/reports";
  }

  @GetMapping("/{sectionId}")
  public String detail(@PathVariable Long sectionId,
                       org.springframework.security.core.Authentication auth,
                       Model model) {

    var principal = (UserPrincipal) auth.getPrincipal();
    Long teacherId = principal.getUser().getId();

    var section = sectionRepo.findById(sectionId).orElseThrow();
    if (!section.getTeacher().getId().equals(teacherId)) {
      return "redirect:/teacher/reports";
    }

    long responses = submissionRepo.countByKindAndSectionId(EvaluationKind.STUDENT_FEEDBACK, sectionId);
    long enrolled = enrollmentRepo.countBySection_Id(sectionId);
    double rate = (enrolled > 0) ? (responses * 100.0 / enrolled) : 0.0;

    List<QuestionStat> stats = answerRepo.ratingStatsBySection(sectionId);
    Double overallAvg = answerRepo.overallAvgScoreBySection(sectionId);

    // Top / bottom (by avgScore)
    var scored = stats.stream()
        .filter(s -> s.getAvgScore() != null)
        .sorted(Comparator.comparing(QuestionStat::getAvgScore).reversed())
        .collect(Collectors.toList());

    var top3 = scored.stream().limit(3).toList();
    var bottom3 = scored.stream()
        .sorted(Comparator.comparing(QuestionStat::getAvgScore))
        .limit(3)
        .toList();

    var comments = answerRepo.commentsBySection(sectionId);

    model.addAttribute("section", section);

    model.addAttribute("responses", responses);
    model.addAttribute("enrolled", enrolled);
    model.addAttribute("rate", rate);
    model.addAttribute("overallAvg", overallAvg);

    model.addAttribute("stats", stats);
    model.addAttribute("top3", top3);
    model.addAttribute("bottom3", bottom3);

    model.addAttribute("comments", comments);
    model.addAttribute("commentCount", comments != null ? comments.size() : 0);

    return "teacher/report_detail";
  }
}
