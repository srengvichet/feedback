package kh.edu.num.feedback.service;

import kh.edu.num.feedback.domain.entity.PasswordResetToken;
import kh.edu.num.feedback.domain.entity.UserAccount;
import kh.edu.num.feedback.domain.repo.PasswordResetTokenRepository;
import kh.edu.num.feedback.domain.repo.UserAccountRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class PasswordResetService {

    private static final Logger log = LoggerFactory.getLogger(PasswordResetService.class);

    private static final int TOKEN_EXPIRY_MINUTES = 30;

    private final PasswordResetTokenRepository tokenRepo;
    private final UserAccountRepository userRepo;
    private final EmailService emailService;
    private final PasswordEncoder passwordEncoder;

    @Value("${app.base-url:https://localhost:8443}")
    private String baseUrl;

    public PasswordResetService(PasswordResetTokenRepository tokenRepo,
                                 UserAccountRepository userRepo,
                                 EmailService emailService,
                                 PasswordEncoder passwordEncoder) {
        this.tokenRepo = tokenRepo;
        this.userRepo = userRepo;
        this.emailService = emailService;
        this.passwordEncoder = passwordEncoder;
    }

    /**
     * Request a password reset via email or phone.
     * @param input email address or phone number
     * @return "sent" | "no_email" | "not_found"
     */
    @Transactional
    public String requestReset(String input) {
        if (input == null || input.isBlank()) return "not_found";
        String trimmed = input.trim();
        String normalizedEmail = trimmed.toLowerCase();
        String normalizedPhone = normalizePhone(trimmed);

        Optional<UserAccount> opt = pickResetUser(userRepo.findAllByEmailIgnoreCase(normalizedEmail), "email", trimmed);
        if (opt.isEmpty() && !normalizedPhone.isEmpty()) {
            opt = pickResetUser(userRepo.findAllByNormalizedPhone(normalizedPhone), "phone", trimmed);
        }
        if (opt.isEmpty()) {
            opt = pickResetUser(userRepo.findAllByPhone(trimmed), "phone", trimmed);
        }
        if (opt.isEmpty()) {
            opt = userRepo.findByUsername(trimmed)
                    .filter(UserAccount::isEnabled)
                    .or(() -> userRepo.findByUsername(trimmed));
        }
        if (opt.isEmpty()) return "not_found";

        UserAccount user = opt.get();
        if (user.getEmail() == null || user.getEmail().isBlank()) return "no_email";

        String token = createTokenForUser(user.getId());
        String resetUrl = baseUrl + "/reset-password?token=" + token;
        emailService.sendPasswordReset(user, resetUrl);
        return "sent";
    }

    private Optional<UserAccount> pickResetUser(List<UserAccount> matches, String lookupType, String lookupValue) {
        if (matches == null || matches.isEmpty()) {
            return Optional.empty();
        }

        List<UserAccount> enabledMatches = matches.stream()
                .filter(UserAccount::isEnabled)
                .toList();
        List<UserAccount> candidates = enabledMatches.isEmpty() ? matches : enabledMatches;

        if (candidates.size() > 1) {
            log.warn("Multiple users matched password reset {} '{}'; using the first enabled match", lookupType, lookupValue);
        }

        return Optional.of(candidates.get(0));
    }

    private String normalizePhone(String value) {
        StringBuilder digits = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            char current = value.charAt(i);
            if (Character.isDigit(current)) {
                digits.append(current);
            }
        }
        return digits.toString();
    }

    /**
     * Validate a reset token.
     * @return the UserAccount if valid, empty otherwise
     */
    @Transactional(readOnly = true)
    public Optional<UserAccount> validateToken(String token) {
        return tokenRepo.findByToken(token)
                .filter(t -> !t.isUsed())
                .filter(t -> t.getExpiresAt().isAfter(LocalDateTime.now()))
                .map(PasswordResetToken::getUser);
    }

    /**
     * Reset the password using the given token.
     * @return true on success, false if token is invalid/expired
     */
    @Transactional
    public boolean resetPassword(String token, String newPassword) {
        Optional<PasswordResetToken> opt = tokenRepo.findByToken(token)
                .filter(t -> !t.isUsed())
                .filter(t -> t.getExpiresAt().isAfter(LocalDateTime.now()));
        if (opt.isEmpty()) return false;

        PasswordResetToken prt = opt.get();
        UserAccount user = prt.getUser();
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        user.setMustChangePassword(false);
        userRepo.save(user);

        prt.setUsed(true);
        tokenRepo.save(prt);
        tokenRepo.deleteExpired(LocalDateTime.now());
        return true;
    }

    /**
     * Create a reset token for a given userId (used after OTP verification).
     */
    @Transactional
    public String createTokenForUser(Long userId) {
        UserAccount user = userRepo.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + userId));

        String token = UUID.randomUUID().toString().replace("-", "");
        PasswordResetToken prt = new PasswordResetToken();
        prt.setUser(user);
        prt.setToken(token);
        prt.setExpiresAt(LocalDateTime.now().plusMinutes(TOKEN_EXPIRY_MINUTES));
        tokenRepo.save(prt);
        return token;
    }
}
