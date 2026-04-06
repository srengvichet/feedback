package kh.edu.num.feedback.web.auth;

import kh.edu.num.feedback.domain.entity.Role;
import kh.edu.num.feedback.domain.entity.StudentRegistry;
import kh.edu.num.feedback.domain.entity.UserAccount;
import kh.edu.num.feedback.domain.repo.StudentRegistryRepository;
import kh.edu.num.feedback.domain.repo.UserAccountRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.time.ZoneId;

@Controller
public class RegisterController {

  private final StudentRegistryRepository registryRepo;
  private final UserAccountRepository userRepo;
  private final PasswordEncoder passwordEncoder;

  private static final ZoneId ZONE = ZoneId.of("Asia/Phnom_Penh");

  public RegisterController(StudentRegistryRepository registryRepo,
      UserAccountRepository userRepo,
      PasswordEncoder passwordEncoder) {
    this.registryRepo = registryRepo;
    this.userRepo = userRepo;
    this.passwordEncoder = passwordEncoder;
  }

  @GetMapping("/register")
  public String form(Model model) {
    model.addAttribute("form", new RegisterForm());
    System.out.println("now open /register with RegisterForm()");
    return "register";
  }

  @PostMapping("/register")
  @Transactional
  public String submit(@ModelAttribute("form") RegisterForm form, Model model) {

    String studentLogin = form.getStudentLogin() == null ? "" : form.getStudentLogin().trim();
    String pw = form.getPassword() == null ? "" : form.getPassword();
    String cpw = form.getConfirmPassword() == null ? "" : form.getConfirmPassword();

    if (studentLogin.isBlank()) {
      model.addAttribute("error", "Student Login is required.");
      return "register";
    }

    StudentRegistry reg = registryRepo.findByStudentLoginIgnoreCase(studentLogin).orElse(null);

    if (reg == null) {
      model.addAttribute("error", "Student Login not found in registry.");
      return "register";
    }

    model.addAttribute("registry", reg);

    if (!reg.isActive()) {
      model.addAttribute("error", "Your registry record is inactive. Please contact admin.");
      return "register";
    }

    if (pw.length() < 6) {
      model.addAttribute("error", "Password must be at least 6 characters.");
      return "register";
    }

    if (!pw.equals(cpw)) {
      model.addAttribute("error", "Password and Confirm Password do not match.");
      return "register";
    }

    // 🔹 canonical username from registry
    String username = reg.getStudentLogin();

    UserAccount user = userRepo.findByUsername(username).orElse(null);

    // Block only when the account is fully active and not awaiting a reset-driven reactivation.
    if (user != null
        && Boolean.TRUE.equals(user.isEnabled())
        && reg.isClaimed()
        && !user.isMustChangePassword()) {
      model.addAttribute("error", "This account is already activated. Please login.");
      return "register";
    }

    // 🔹 If user does not exist → create
    if (user == null) {
      user = new UserAccount();
      user.setUsername(username);
      user.setRole(Role.STUDENT);
    }

    // ✅ Activation: set password + enable
    user.setPasswordHash(passwordEncoder.encode(pw));
    user.setEnabled(true);
    user.setMustChangePassword(false);

    // Sync academic info from registry
    user.setCohort(reg.getCohort());
    user.setGroupNo(reg.getGroupNo());
    user.setClassName(reg.getClassName());
    user.setShiftTime(reg.getShiftTime());

    userRepo.save(user);

    // Mark registry claimed
    reg.setUser(user);
    reg.setClaimed(true);
    reg.setClaimedAt(LocalDateTime.now());
    registryRepo.save(reg);

    return "redirect:/login?registered";
  }
}
