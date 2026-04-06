package kh.edu.num.feedback.web.auth;

import jakarta.servlet.http.HttpSession;
import kh.edu.num.feedback.domain.entity.UserAccount;
import kh.edu.num.feedback.domain.repo.UserAccountRepository;
import kh.edu.num.feedback.service.PasswordResetService;
import kh.edu.num.feedback.service.TelegramService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.Optional;

@Controller
public class PasswordResetController {

    @Value("${app.mail.enabled:false}")
    private boolean mailEnabled;

    private final PasswordResetService resetService;
    private final TelegramService telegramService;
    private final UserAccountRepository userRepo;

    public PasswordResetController(PasswordResetService resetService,
                                    TelegramService telegramService,
                                    UserAccountRepository userRepo) {
        this.resetService = resetService;
        this.telegramService = telegramService;
        this.userRepo = userRepo;
    }

    // ─── Forgot password page ────────────────────────────────────────────────

    @GetMapping("/forgot-password")
    public String forgotPasswordPage(Model model) {
        model.addAttribute("mailEnabled", mailEnabled);
        model.addAttribute("telegramEnabled", telegramService.isEnabled());
        return "forgot-password";
    }

    @PostMapping("/forgot-password")
    public String forgotPasswordSubmit(@RequestParam String input, RedirectAttributes ra) {
        String result = resetService.requestReset(input);
        switch (result) {
            case "sent"      -> ra.addFlashAttribute("msg", "sent");
            case "no_email"  -> ra.addFlashAttribute("msg", "no_email");
            default          -> ra.addFlashAttribute("msg", "sent"); // don't reveal not_found
        }
        return "redirect:/forgot-password";
    }

    // ─── Telegram OTP ────────────────────────────────────────────────────────

    @PostMapping("/forgot-telegram")
    public String forgotTelegram(@RequestParam String username,
                                  HttpSession session,
                                  RedirectAttributes ra) {
        if (!telegramService.isEnabled()) {
            ra.addFlashAttribute("telegramMsg", "disabled");
            return "redirect:/forgot-password";
        }
        Optional<UserAccount> opt = userRepo.findByUsername(username.trim());
        if (opt.isEmpty()) {
            ra.addFlashAttribute("telegramMsg", "not_found");
            return "redirect:/forgot-password";
        }
        UserAccount user = opt.get();
        boolean sent = telegramService.sendOtp(user);
        if (!sent) {
            ra.addFlashAttribute("telegramMsg", "not_linked");
            return "redirect:/forgot-password";
        }
        session.setAttribute("otpChatId", user.getTelegramChatId());
        session.setAttribute("otpUserId", user.getId());
        return "redirect:/verify-otp";
    }

    // ─── OTP verification ────────────────────────────────────────────────────

    @GetMapping("/verify-otp")
    public String verifyOtpPage(HttpSession session, Model model) {
        if (session.getAttribute("otpChatId") == null) return "redirect:/forgot-password";
        return "verify-otp";
    }

    @PostMapping("/verify-otp")
    public String verifyOtpSubmit(@RequestParam String otp,
                                   HttpSession session,
                                   RedirectAttributes ra) {
        Long chatId = (Long) session.getAttribute("otpChatId");
        Long userId = (Long) session.getAttribute("otpUserId");
        if (chatId == null || userId == null) return "redirect:/forgot-password";

        boolean valid = telegramService.validateOtp(chatId, otp);
        if (!valid) {
            ra.addFlashAttribute("otpError", "Invalid or expired code. Please try again.");
            return "redirect:/verify-otp";
        }

        String token = resetService.createTokenForUser(userId);
        session.removeAttribute("otpChatId");
        session.removeAttribute("otpUserId");
        return "redirect:/reset-password?token=" + token;
    }

    // ─── Reset password ──────────────────────────────────────────────────────

    @GetMapping("/reset-password")
    public String resetPasswordPage(@RequestParam(required = false) String token, Model model) {
        if (token == null || token.isBlank()) {
            model.addAttribute("invalid", true);
            return "reset-password";
        }
        boolean valid = resetService.validateToken(token).isPresent();
        model.addAttribute("token", token);
        model.addAttribute("invalid", !valid);
        return "reset-password";
    }

    @PostMapping("/reset-password")
    public String resetPasswordSubmit(@RequestParam String token,
                                       @RequestParam String newPassword,
                                       @RequestParam String confirmPassword,
                                       RedirectAttributes ra) {
        if (!newPassword.equals(confirmPassword)) {
            ra.addFlashAttribute("resetError", "Passwords do not match.");
            return "redirect:/reset-password?token=" + token;
        }
        if (newPassword.length() < 8) {
            ra.addFlashAttribute("resetError", "Password must be at least 8 characters.");
            return "redirect:/reset-password?token=" + token;
        }
        boolean ok = resetService.resetPassword(token, newPassword);
        if (!ok) {
            ra.addFlashAttribute("resetError", "This link has expired or already been used.");
            return "redirect:/reset-password?token=" + token;
        }
        return "redirect:/login?passwordReset";
    }
}
