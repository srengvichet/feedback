package kh.edu.num.feedback.api;

import kh.edu.num.feedback.api.dto.ActivateRequest;
import kh.edu.num.feedback.api.dto.ApiResponse;
import kh.edu.num.feedback.api.dto.LoginRequest;
import kh.edu.num.feedback.api.dto.LoginResponse;
import kh.edu.num.feedback.api.dto.UserProfileDto;
import kh.edu.num.feedback.domain.entity.Role;
import kh.edu.num.feedback.domain.entity.StudentRegistry;
import kh.edu.num.feedback.domain.entity.UserAccount;
import kh.edu.num.feedback.domain.repo.StudentRegistryRepository;
import kh.edu.num.feedback.domain.repo.UserAccountRepository;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;

@RestController
@RequestMapping("/api/auth")
public class ApiAuthController {

    private final UserAccountRepository userRepo;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final StudentRegistryRepository registryRepo;

    public ApiAuthController(UserAccountRepository userRepo,
                             PasswordEncoder passwordEncoder,
                             JwtUtil jwtUtil,
                             StudentRegistryRepository registryRepo) {
        this.userRepo = userRepo;
        this.passwordEncoder = passwordEncoder;
        this.jwtUtil = jwtUtil;
        this.registryRepo = registryRepo;
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody LoginRequest req) {
        if (req.getUsername() == null || req.getPassword() == null) {
            return ResponseEntity.badRequest().body(ApiResponse.error("Username and password are required."));
        }

        var userOpt = userRepo.findByUsername(req.getUsername().trim());
        if (userOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(ApiResponse.error("Invalid username or password."));
        }

        UserAccount user = userOpt.get();
        if (!user.isEnabled()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(ApiResponse.error("Account is disabled."));
        }
        if (!passwordEncoder.matches(req.getPassword(), user.getPasswordHash())) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(ApiResponse.error("Invalid username or password."));
        }

        String token = jwtUtil.generateToken(user.getUsername(), user.getRole().name());
        long expiresInMs = 86_400_000L; // 24 hours

        UserProfileDto profile = toProfileDto(user);
        return ResponseEntity.ok(new LoginResponse(token, expiresInMs, profile));
    }

    @PostMapping("/activate")
    @Transactional
    public ResponseEntity<?> activate(@RequestBody ActivateRequest req) {
        String login = req.getStudentLogin() == null ? "" : req.getStudentLogin().trim();
        String pw = req.getPassword() == null ? "" : req.getPassword();
        String cpw = req.getConfirmPassword() == null ? "" : req.getConfirmPassword();

        if (login.isBlank())
            return ResponseEntity.badRequest().body(ApiResponse.error("Student Login is required."));

        StudentRegistry reg = registryRepo.findByStudentLoginIgnoreCase(login).orElse(null);
        if (reg == null)
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ApiResponse.error("Student Login not found. Please check with your administrator."));

        if (!reg.isActive())
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(ApiResponse.error("Your registry record is inactive. Please contact admin."));

        if (pw.length() < 6)
            return ResponseEntity.badRequest().body(ApiResponse.error("Password must be at least 6 characters."));

        if (!pw.equals(cpw))
            return ResponseEntity.badRequest().body(ApiResponse.error("Passwords do not match."));

        UserAccount user = userRepo.findByUsername(reg.getStudentLogin()).orElse(null);
        if (user != null && user.isEnabled() && reg.isClaimed() && !user.isMustChangePassword())
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(ApiResponse.error("This account is already activated. Please login."));

        if (user == null) {
            user = new UserAccount();
            user.setUsername(reg.getStudentLogin());
            user.setRole(Role.STUDENT);
        }

        user.setPasswordHash(passwordEncoder.encode(pw));
        user.setEnabled(true);
        user.setMustChangePassword(false);
        if (reg.getFullName() != null) user.setFullName(reg.getFullName());
        user.setCohort(reg.getCohort());
        user.setGroupNo(reg.getGroupNo());
        user.setClassName(reg.getClassName());
        user.setShiftTime(reg.getShiftTime());
        userRepo.save(user);

        reg.setUser(user);
        reg.setClaimed(true);
        reg.setClaimedAt(LocalDateTime.now());
        registryRepo.save(reg);

        String token = jwtUtil.generateToken(user.getUsername(), user.getRole().name());
        return ResponseEntity.ok(new LoginResponse(token, 86_400_000L, toProfileDto(user)));
    }

    static UserProfileDto toProfileDto(UserAccount user) {
        UserProfileDto dto = new UserProfileDto();
        dto.setId(user.getId());
        dto.setUsername(user.getUsername());
        dto.setFullName(user.getFullName());
        dto.setEmail(user.getEmail());
        dto.setRole(user.getRole().name());
        dto.setCohort(user.getCohort() != null ? user.getCohort().getLabel() : null);
        dto.setGroupNo(user.getGroupNo());
        dto.setClassName(user.getClassName());
        dto.setShiftTime(user.getShiftTime() != null ? user.getShiftTime().name() : null);
        dto.setPhone(user.getPhone());
        dto.setDepartment(user.getDepartment());
        dto.setPosition(user.getPosition());
        dto.setMustChangePassword(user.isMustChangePassword());
        return dto;
    }
}
