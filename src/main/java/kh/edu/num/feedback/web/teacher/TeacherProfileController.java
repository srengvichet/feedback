package kh.edu.num.feedback.web.teacher;

import kh.edu.num.feedback.domain.repo.UserAccountRepository;
import kh.edu.num.feedback.security.UserPrincipal;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/teacher/profile")
public class TeacherProfileController {

  private final UserAccountRepository userRepo;
  private final PasswordEncoder passwordEncoder;

  public TeacherProfileController(UserAccountRepository userRepo, PasswordEncoder passwordEncoder) {
    this.userRepo = userRepo;
    this.passwordEncoder = passwordEncoder;
  }

  @GetMapping
  public String showProfile(Authentication auth, Model model) {
    var principal = (UserPrincipal) auth.getPrincipal();
    var user = userRepo.findById(principal.getUser().getId()).orElseThrow();
    model.addAttribute("user", user);
    return "teacher/profile";
  }

  @PostMapping("/info")
  public String updateInfo(Authentication auth,
      @RequestParam(required = false) String fullName,
      @RequestParam(required = false) String email,
      @RequestParam(required = false) String phone,
      @RequestParam(required = false) String department,
      @RequestParam(required = false) String position,
      @RequestParam(required = false) String gender,
      RedirectAttributes ra) {

    var principal = (UserPrincipal) auth.getPrincipal();
    var user = userRepo.findById(principal.getUser().getId()).orElseThrow();

    user.setFullName(fullName != null ? fullName.trim() : null);
    user.setEmail(email != null ? email.trim() : null);
    user.setPhone(phone != null ? phone.trim() : null);
    user.setDepartment(department != null ? department.trim() : null);
    user.setPosition(position != null ? position.trim() : null);
    user.setGender(gender != null && !gender.isBlank() ? gender.trim() : null);
    userRepo.save(user);

    ra.addFlashAttribute("successInfo", "Profile updated successfully.");
    return "redirect:/teacher/profile";
  }

  @PostMapping("/password")
  public String changePassword(Authentication auth,
      @RequestParam String currentPassword,
      @RequestParam String newPassword,
      @RequestParam String confirmPassword,
      RedirectAttributes ra) {

    var principal = (UserPrincipal) auth.getPrincipal();
    var user = userRepo.findById(principal.getUser().getId()).orElseThrow();

    if (!passwordEncoder.matches(currentPassword, user.getPasswordHash())) {
      ra.addFlashAttribute("errorPassword", "Current password is incorrect.");
      return "redirect:/teacher/profile";
    }
    if (newPassword.length() < 6) {
      ra.addFlashAttribute("errorPassword", "New password must be at least 6 characters.");
      return "redirect:/teacher/profile";
    }
    if (!newPassword.equals(confirmPassword)) {
      ra.addFlashAttribute("errorPassword", "New passwords do not match.");
      return "redirect:/teacher/profile";
    }

    user.setPasswordHash(passwordEncoder.encode(newPassword));
    userRepo.save(user);

    ra.addFlashAttribute("successPassword", "Password changed successfully.");
    return "redirect:/teacher/profile";
  }
}
