package kh.edu.num.feedback.web.student;

import kh.edu.num.feedback.domain.entity.UserAccount;
import kh.edu.num.feedback.domain.repo.StudentRegistryRepository;
import kh.edu.num.feedback.domain.repo.UserAccountRepository;
import kh.edu.num.feedback.security.UserPrincipal;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/student/profile")
public class StudentProfileController {

    private final UserAccountRepository userRepo;
    private final StudentRegistryRepository registryRepo;
    private final PasswordEncoder passwordEncoder;

    public StudentProfileController(UserAccountRepository userRepo,
                                    StudentRegistryRepository registryRepo,
                                    PasswordEncoder passwordEncoder) {
        this.userRepo = userRepo;
        this.registryRepo = registryRepo;
        this.passwordEncoder = passwordEncoder;
    }

    @GetMapping
    public String showProfile(Authentication auth, Model model) {
        UserAccount user = resolve(auth);
        if (user == null) return "redirect:/login";
        model.addAttribute("user", user);
        registryRepo.findByUser_Id(user.getId())
                    .ifPresent(r -> model.addAttribute("registry", r));
        return "student/profile";
    }

    @PostMapping("/info")
    public String updateInfo(Authentication auth,
                             @RequestParam(required = false) String fullName,
                             @RequestParam(required = false) String email,
                             @RequestParam(required = false) String phone,
                             @RequestParam(required = false) String gender,
                             RedirectAttributes ra) {
        UserAccount user = resolve(auth);
        if (user == null) return "redirect:/login";

        user.setFullName(fullName != null ? fullName.trim() : null);
        user.setEmail(email != null ? email.trim() : null);
        user.setPhone(phone != null ? phone.trim() : null);
        user.setGender(gender != null && !gender.isBlank() ? gender.trim() : null);

        userRepo.save(user);
        ra.addFlashAttribute("successInfo", "Profile updated successfully.");
        return "redirect:/student/profile";
    }

    @PostMapping("/password")
    public String changePassword(Authentication auth,
                                 @RequestParam String currentPassword,
                                 @RequestParam String newPassword,
                                 @RequestParam String confirmPassword,
                                 RedirectAttributes ra) {
        UserAccount user = resolve(auth);
        if (user == null) return "redirect:/login";

        if (!passwordEncoder.matches(currentPassword, user.getPasswordHash())) {
            ra.addFlashAttribute("errorPassword", "Current password is incorrect.");
            return "redirect:/student/profile";
        }
        if (newPassword.length() < 6) {
            ra.addFlashAttribute("errorPassword", "New password must be at least 6 characters.");
            return "redirect:/student/profile";
        }
        if (!newPassword.equals(confirmPassword)) {
            ra.addFlashAttribute("errorPassword", "New passwords do not match.");
            return "redirect:/student/profile";
        }

        user.setPasswordHash(passwordEncoder.encode(newPassword));
        userRepo.save(user);
        ra.addFlashAttribute("successPassword", "Password changed successfully.");
        return "redirect:/student/profile";
    }

    private UserAccount resolve(Authentication auth) {
        if (auth == null || !(auth.getPrincipal() instanceof UserPrincipal p)) return null;
        return p.getUser();
    }
}
