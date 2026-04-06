package kh.edu.num.feedback.web.admin;

import kh.edu.num.feedback.domain.entity.*;
import kh.edu.num.feedback.domain.repo.*;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

@Controller
@RequestMapping("/admin/sections")
public class AdminSectionController {

  private final ClassSectionRepository sectionRepo;
  private final SemesterRepository semesterRepo;
  private final CourseRepository courseRepo;
  private final UserAccountRepository userRepo;
    // add repo in constructor
private final TeachingScheduleRepository teachingRepo;

  public AdminSectionController(ClassSectionRepository sectionRepo,
                                SemesterRepository semesterRepo,
                                CourseRepository courseRepo,
                                UserAccountRepository userRepo,
                                TeachingScheduleRepository teachingRepo) {
    this.sectionRepo = sectionRepo;
    this.semesterRepo = semesterRepo;
    this.courseRepo = courseRepo;
    this.userRepo = userRepo;
    this.teachingRepo = teachingRepo;
}

  @GetMapping
  public String list(Model model) {
    model.addAttribute("sections", sectionRepo.findAll());
    return "admin/sections";
  }

  @GetMapping("/new")
  public String form(Model model) {
    model.addAttribute("semesters", semesterRepo.findAll());
    model.addAttribute("courses", courseRepo.findAll());
    model.addAttribute("teachers", userRepo.findByRole(Role.TEACHER));
    model.addAttribute("shifts", ShiftTime.values());
    model.addAttribute("section", new ClassSection());
    // inside GET /admin/sections/new
    model.addAttribute("teachings", teachingRepo.findAll());
    return "admin/section_new";
  }

  @PostMapping
  public String create(@RequestParam Long semesterId,
                       @RequestParam Long courseId,
                       @RequestParam Long teacherId,
                       @RequestParam ShiftTime shiftTime,
                       @RequestParam(required = false) String building,
                       @RequestParam(required = false) String room,
                       @RequestParam(required = false) String sectionName) {

    var semester = semesterRepo.findById(semesterId).orElseThrow();
    var course = courseRepo.findById(courseId).orElseThrow();
    var teacher = userRepo.findById(teacherId).orElseThrow();

    ClassSection s = new ClassSection();
    s.setSemester(semester);
    s.setCourse(course);
    s.setTeacher(teacher);
    s.setShiftTime(shiftTime);
    s.setBuilding(building != null ? building.trim() : null);
    s.setRoom(room != null ? room.trim() : null);
    s.setSectionName(sectionName != null ? sectionName.trim() : null);

    sectionRepo.save(s);
    return "redirect:/admin/sections";
  }
}

