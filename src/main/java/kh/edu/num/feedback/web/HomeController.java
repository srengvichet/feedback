package kh.edu.num.feedback.web;

import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import kh.edu.num.feedback.security.UserPrincipal;


@Controller
public class HomeController {

  @GetMapping("/login")
  public String login() {
    return "login";
  }

  @GetMapping("/me")
  public String me(Authentication auth, Model model) {
    var principal = (UserPrincipal) auth.getPrincipal();
    var user = principal.getUser();
    model.addAttribute("username", user.getUsername());
    model.addAttribute("role", user.getRole().name());
    return "me";
  }

  @GetMapping("/")
  public String root() {
    return "redirect:/me";
  }
}
