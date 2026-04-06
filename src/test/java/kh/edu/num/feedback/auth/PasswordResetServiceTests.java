package kh.edu.num.feedback.auth;

import kh.edu.num.feedback.domain.entity.Role;
import kh.edu.num.feedback.domain.entity.UserAccount;
import kh.edu.num.feedback.domain.repo.PasswordResetTokenRepository;
import kh.edu.num.feedback.domain.repo.UserAccountRepository;
import kh.edu.num.feedback.service.EmailService;
import kh.edu.num.feedback.service.PasswordResetService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PasswordResetServiceTests {

    @Mock
    private PasswordResetTokenRepository tokenRepo;

    @Mock
    private UserAccountRepository userRepo;

    @Mock
    private EmailService emailService;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private PasswordResetService passwordResetService;

    @Test
    void requestResetAcceptsStudentUsername() {
        UserAccount student = enabledStudent("NUMP330612659", "student@num.edu.kh");

        ReflectionTestUtils.setField(passwordResetService, "baseUrl", "https://localhost:8443");

        when(userRepo.findAllByEmailIgnoreCase("nump330612659")).thenReturn(List.of());
        when(userRepo.findAllByNormalizedPhone("330612659")).thenReturn(List.of());
        when(userRepo.findAllByPhone("NUMP330612659")).thenReturn(List.of());
        when(userRepo.findByUsername("NUMP330612659")).thenReturn(Optional.of(student));
        when(userRepo.findById(1L)).thenReturn(Optional.of(student));

        String result = passwordResetService.requestReset("NUMP330612659");

        assertEquals("sent", result);
        ArgumentCaptor<String> resetUrl = ArgumentCaptor.forClass(String.class);
        verify(emailService).sendPasswordReset(eq(student), resetUrl.capture());
        assertTrue(resetUrl.getValue().startsWith("https://localhost:8443/reset-password?token="));
        verify(tokenRepo).save(any());
    }

    @Test
    void requestResetReturnsNoEmailForUsernameWithoutEmail() {
        UserAccount student = enabledStudent("NUMP330612659", null);

        when(userRepo.findAllByEmailIgnoreCase("nump330612659")).thenReturn(List.of());
        when(userRepo.findAllByNormalizedPhone("330612659")).thenReturn(List.of());
        when(userRepo.findAllByPhone("NUMP330612659")).thenReturn(List.of());
        when(userRepo.findByUsername("NUMP330612659")).thenReturn(Optional.of(student));

        String result = passwordResetService.requestReset("NUMP330612659");

        assertEquals("no_email", result);
        verify(emailService, never()).sendPasswordReset(any(), any());
        verify(tokenRepo, never()).save(any());
    }

    private static UserAccount enabledStudent(String username, String email) {
        UserAccount user = new UserAccount();
        ReflectionTestUtils.setField(user, "id", 1L);
        user.setUsername(username);
        user.setRole(Role.STUDENT);
        user.setEnabled(true);
        user.setEmail(email);
        return user;
    }
}