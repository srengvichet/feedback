package kh.edu.num.feedback.web.admin;


import jakarta.validation.Valid;
import kh.edu.num.feedback.domain.entity.Semester;
import kh.edu.num.feedback.domain.repo.SemesterRepository;
import kh.edu.num.feedback.service.WindowService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;

@Controller
@RequestMapping("/admin/semesters")
public class AdminSemesterController {

  private final SemesterRepository semesterRepo;
  private final WindowService windowService;

  public AdminSemesterController(SemesterRepository semesterRepo, WindowService windowService) {
    this.semesterRepo = semesterRepo;
    this.windowService = windowService;
  }

  @GetMapping
  public String list(Model model) {
    model.addAttribute("semesters", semesterRepo.findAll());
    return "admin/semesters";
  }

  @GetMapping("/new")
  public String createForm(Model model) {
    model.addAttribute("form", new SemesterForm());
    return "admin/semester_new";
  }

  @PostMapping
  public String create(@Valid @ModelAttribute("form") SemesterForm form,
                       BindingResult br) {
    if (br.hasErrors()) return "admin/semester_new";
    if (form.getEndDate().isBefore(form.getStartDate())) {
      br.rejectValue("endDate", "endBeforeStart", "End date must be after start date");
      return "admin/semester_new";
    }

    Semester s = new Semester();
    s.setName(form.getName());
    s.setStartDate(form.getStartDate());
    s.setEndDate(form.getEndDate());
    s = semesterRepo.save(s);

    windowService.createDefaultWindows(s);
    return "redirect:/admin/semesters";
  }
}
