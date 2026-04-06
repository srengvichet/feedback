package kh.edu.num.feedback.web.teacher;

import kh.edu.num.feedback.domain.entity.EvaluationKind;
import kh.edu.num.feedback.domain.repo.ClassSectionRepository;
import kh.edu.num.feedback.domain.repo.SemesterRepository;
import kh.edu.num.feedback.security.UserPrincipal;
import kh.edu.num.feedback.service.EvaluationService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

@Controller
@RequestMapping("/teacher/self")
public class TeacherSelfController {

  private final ClassSectionRepository sectionRepo;
  private final SemesterRepository semesterRepo;
  private final EvaluationService evaluationService;

  public TeacherSelfController(ClassSectionRepository sectionRepo,
                               SemesterRepository semesterRepo,
                               EvaluationService evaluationService) {
    this.sectionRepo = sectionRepo;
    this.semesterRepo = semesterRepo;
    this.evaluationService = evaluationService;
  }

  @GetMapping
  public String form(@RequestParam(required = false) Long semesterId,
                     org.springframework.security.core.Authentication auth,
                     Model model,
                     @RequestParam(required = false) String msg) {

    var principal = (UserPrincipal) auth.getPrincipal();
    Long teacherId = principal.getUser().getId();

    // semesters this teacher teaches
    var sections = sectionRepo.findByTeacherId(teacherId);
    var semesterIds = sections.stream().map(s -> s.getSemester().getId()).collect(Collectors.toSet());
    var semesters = semesterRepo.findAll().stream()
        .filter(s -> semesterIds.contains(s.getId()))
        .toList();

    if (semesterId == null && !semesters.isEmpty()) semesterId = semesters.get(0).getId();

    var semester = (semesterId != null) ? semesterRepo.findById(semesterId).orElse(null) : null;
    boolean open = (semester != null) && evaluationService.isTeacherSelfWindowOpen(principal.getUser(), semester);

    model.addAttribute("semesters", semesters);
    model.addAttribute("semester", semester);
    model.addAttribute("questions", evaluationService.activeQuestions(EvaluationKind.TEACHER_SELF));
    model.addAttribute("open", open);
    model.addAttribute("msg", msg);
    return "teacher/self_form";
  }

  @PostMapping
  public String submit(@RequestParam Long semesterId,
                       org.springframework.security.core.Authentication auth,
                       @RequestParam Map<String, String> params) {

    var principal = (UserPrincipal) auth.getPrincipal();
    var teacher = principal.getUser();

    var semester = semesterRepo.findById(semesterId).orElseThrow();

    Map<Long, String> inputs = new HashMap<>();
    for (var entry : params.entrySet()) {
      if (entry.getKey().startsWith("q_")) {
        Long qid = Long.parseLong(entry.getKey().substring(2));
        inputs.put(qid, entry.getValue());
      }
    }

    try {
      evaluationService.saveTeacherSelf(teacher, semester, inputs);
      return "redirect:/teacher/self?semesterId=" + semesterId + "&msg=saved";
    } catch (Exception ex) {
      return "redirect:/teacher/self?semesterId=" + semesterId + "&msg=error";
    }
  }
}
