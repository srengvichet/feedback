package kh.edu.num.feedback.api;

import kh.edu.num.feedback.domain.entity.UserAccount;
import kh.edu.num.feedback.domain.repo.UserAccountRepository;
import kh.edu.num.feedback.service.PasswordResetService;
import kh.edu.num.feedback.service.TelegramService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/auth")
public class ApiPasswordResetController {

    private final PasswordResetService resetService;
    private final TelegramService telegramService;
    private final UserAccountRepository userRepo;

    public ApiPasswordResetController(PasswordResetService resetService,
                                      TelegramService telegramService,
                                      UserAccountRepository userRepo) {
        this.resetService = resetService;
        this.telegramService = telegramService;
        this.userRepo = userRepo;
    }

    @PostMapping("/forgot-password")
    public ResponseEntity<Map<String, Object>> forgotPassword(@RequestBody Map<String, String> body) {
        String input = body.get("input");
        if (input == null || input.isBlank())
            return ResponseEntity.badRequest().body(Map.of("message", "Please provide your email, phone, or username."));
        return switch (resetService.requestReset(input)) {
            case "sent"     -> ResponseEntity.ok(Map.of("message", "Reset instructions have been sent to your email."));
            case "no_email" -> ResponseEntity.status(422).body(Map.of("message", "No email linked to this account. Please use Telegram reset instead."));
            default         -> ResponseEntity.ok(Map.of("message", "If an account exists, reset instructions have been sent."));
        };
    }

    @PostMapping("/forgot-telegram")
    public ResponseEntity<Map<String, Object>> forgotTelegram(@RequestBody Map<String, String> body) {
        if (!telegramService.isEnabled())
            return ResponseEntity.status(503).body(Map.of("message", "Telegram reset is not available."));
        String username = body.get("username");
        if (username == null || username.isBlank())
            return ResponseEntity.badRequest().body(Map.of("message", "Please provide your username."));
        Optional<UserAccount> opt = userRepo.findByUsername(username.trim());
        if (opt.isEmpty())
            return ResponseEntity.status(404).body(Map.of("message", "No account found with that username."));
        UserAccount user = opt.get();
        if (!telegramService.sendOtp(user))
            return ResponseEntity.status(422).body(Map.of("message", "This account is not linked to Telegram. Please use email reset instead."));
        return ResponseEntity.ok(Map.of(
            "message", "OTP sent to your Telegram.",
            "chatId",  user.getTelegramChatId(),
            "userId",  user.getId()
        ));
    }

    @PostMapping("/verify-otp")
    public ResponseEntity<Map<String, Object>> verifyOtp(@RequestBody Map<String, Object> body) {
        long chatId = ((Number) body.get("chatId")).longValue();
        long userId = ((Number) body.get("userId")).longValue();
        String otp  = (String) body.get("otp");
        if (otp == null || otp.isBlank())
            return ResponseEntity.badRequest().body(Map.of("message", "Please enter the OTP code."));
        if (!telegramService.validateOtp(chatId, otp))
            return ResponseEntity.badRequest().body(Map.of("message", "Invalid or expired code. Please try again."));
        String token = resetService.createTokenForUser(userId);
        return ResponseEntity.ok(Map.of("token", token, "message", "OTP verified."));
    }

    @PostMapping("/reset-password")
    public ResponseEntity<Map<String, Object>> resetPassword(@RequestBody Map<String, String> body) {
        String token       = body.get("token");
        String newPassword = body.get("newPassword");
        if (token == null || token.isBlank() || newPassword == null || newPassword.isBlank())
            return ResponseEntity.badRequest().body(Map.of("message", "Invalid request."));
        if (newPassword.length() < 8)
            return ResponseEntity.badRequest().body(Map.of("message", "Password must be at least 8 characters."));
        if (!resetService.resetPassword(token, newPassword))
            return ResponseEntity.badRequest().body(Map.of("message", "This reset link has expired or already been used."));
        return ResponseEntity.ok(Map.of("message", "Password reset successfully. Please sign in."));
    }
}


// import kh.edu.num.feedback.domain.entity.UserAccount;
// import kh.edu.num.feedback.domain.repo.UserAccountRepository;
// import kh.edu.num.feedback.service.PasswordResetService;
// import kh.edu.num.feedback.service.TelegramService;
// import org.springframework.http.ResponseEntity;
// import org.springframework.web.bind.annotation.*;

// import java.util.Map;
// import java.util.Optional;

// @RestController
// @RequestMapping("/api/auth")
// public class ApiPasswordResetController {

//     private final PasswordResetService resetService;
//     private final TelegramService telegramService;
//     private final UserAccountRepository userRepo;

//     public ApiPasswordResetController(PasswordResetService resetService,
//                                       TelegramService telegramService,
//                                       UserAccountRepository userRepo) {
//         this.resetService = resetService;
//         this.telegramService = telegramService;
//         this.userRepo = userRepo;
//     }

//     @PostMapping("/forgot-password")
//     public ResponseEntity<Map<String, Object>> forgotPassword(@RequestBody Map<String, String> body) {
//         String input = body.get("input");
//         if (input == null || input.isBlank())
//             return ResponseEntity.badRequest().body(Map.of("message", "Please provide your email, phone, or username."));
//         return switch (resetService.requestReset(input)) {
//             case "sent"     -> ResponseEntity.ok(Map.of("message", "Reset instructions have been sent to your email."));
//             case "no_email" -> ResponseEntity.status(422).body(Map.of("message", "No email is linked to this account. Please contact your administrator."));
//             default         -> ResponseEntity.ok(Map.of("message", "If an account exists, reset instructions have been sent."));
//         };
//     }

//     @PostMapping("/forgot-telegram")
//     public ResponseEntity<Map<String, Object>> forgotTelegram(@RequestBody Map<String, String> body) {
//         if (!telegramService.isEnabled())
//             return ResponseEntity.status(503).body(Map.of("message", "Telegram reset is not available."));
//         String username = body.get("username");
//         if (username == null || username.isBlank())
//             return ResponseEntity.badRequest().body(Map.of("message", "Please provide your username."));
//         Optional<UserAccount> opt = userRepo.findByUsername(username.trim());
//         if (opt.isEmpty())
//             return ResponseEntity.status(404).body(Map.of("message", "No account found with that username."));
//         UserAccount user = opt.get();
//         if (!telegramService.sendOtp(user))
//             return ResponseEntity.status(422).body(Map.of("message", "This account is not linked to Telegram. Please use email reset instead."));
//         return ResponseEntity.ok(Map.of(
//             "message", "OTP sent to your Telegram.",
//             "chatId",  user.getTelegramChatId(),
//             "userId",  user.getId()
//         ));
//     }

//     @PostMapping("/verify-otp")
//     public ResponseEntity<Map<String, Object>> verifyOtp(@RequestBody Map<String, Object> body) {
//         long chatId = ((Number) body.get("chatId")).longValue();
//         long userId = ((Number) body.get("userId")).longValue();
//         String otp  = (String) body.get("otp");
//         if (otp == null || otp.isBlank())
//             return ResponseEntity.badRequest().body(Map.of("message", "Please enter the OTP code."));
//         if (!telegramService.validateOtp(chatId, otp))
//             return ResponseEntity.badRequest().body(Map.of("message", "Invalid or expired code. Please try again."));
//         String token = resetService.createTokenForUser(userId);
//         return ResponseEntity.ok(Map.of("token", token, "message", "OTP verified."));
//     }

//     @PostMapping("/reset-password")
//     public ResponseEntity<Map<String, Object>> resetPassword(@RequestBody Map<String, String> body) {
//         String token       = body.get("token");
//         String newPassword = body.get("newPassword");
//         if (token == null || token.isBlank() || newPassword == null || newPassword.isBlank())
//             return ResponseEntity.badRequest().body(Map.of("message", "Invalid request."));
//         if (newPassword.length() < 8)
//             return ResponseEntity.badRequest().body(Map.of("message", "Password must be at least 8 characters."));
//         if (!resetService.resetPassword(token, newPassword))
//             return ResponseEntity.badRequest().body(Map.of("message", "This reset link has expired or already been used."));
//         return ResponseEntity.ok(Map.of("message", "Password reset successfully. Please sign in."));
//     }
// }

