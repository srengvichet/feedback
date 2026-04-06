package kh.edu.num.feedback.web.admin;


import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class AdminController {

  @GetMapping("/admin")
  public String adminHome() {
    // Next step: manage semesters, windows, questions, enrollments
    return "admin/home";
  }
}

