package kh.edu.num.feedback.auth;

import kh.edu.num.feedback.api.ApiAuthController;
import kh.edu.num.feedback.api.JwtUtil;
import kh.edu.num.feedback.api.dto.ActivateRequest;
import kh.edu.num.feedback.domain.entity.Role;
import kh.edu.num.feedback.domain.entity.StudentRegistry;
import kh.edu.num.feedback.domain.entity.UserAccount;
import kh.edu.num.feedback.domain.repo.StudentRegistryRepository;
import kh.edu.num.feedback.domain.repo.UserAccountRepository;
import kh.edu.num.feedback.web.auth.RegisterController;
import kh.edu.num.feedback.web.auth.RegisterForm;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.ui.ExtendedModelMap;
import org.springframework.ui.Model;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ActivationResetRegressionTests {

    @Mock
    private StudentRegistryRepository registryRepo;

    @Mock
    private UserAccountRepository userRepo;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtUtil jwtUtil;

    @InjectMocks
    private RegisterController registerController;

    @InjectMocks
    private ApiAuthController apiAuthController;

    @Test
    void webActivationAllowsClaimedUserWhenPasswordMustBeChanged() {
        StudentRegistry registry = activeClaimedRegistry();
        UserAccount user = resetPendingUser();

        RegisterForm form = new RegisterForm();
        form.setStudentLogin("NUMP330612659");
        form.setPassword("new-pass");
        form.setConfirmPassword("new-pass");

        when(registryRepo.findByStudentLoginIgnoreCase("NUMP330612659")).thenReturn(Optional.of(registry));
        when(userRepo.findByUsername("NUMP330612659")).thenReturn(Optional.of(user));
        when(passwordEncoder.encode("new-pass")).thenReturn("encoded-pass");

        Model model = new ExtendedModelMap();
        String view = registerController.submit(form, model);

        assertEquals("redirect:/login?registered", view);
        assertEquals("encoded-pass", user.getPasswordHash());
        assertFalse(user.isMustChangePassword());
        assertEquals(user, registry.getUser());
        verify(userRepo).save(user);
        verify(registryRepo).save(registry);
        assertNull(model.getAttribute("error"));
    }

    @Test
    void apiActivationAllowsClaimedUserWhenPasswordMustBeChanged() {
        StudentRegistry registry = activeClaimedRegistry();
        UserAccount user = resetPendingUser();

        ActivateRequest request = new ActivateRequest();
        request.setStudentLogin("NUMP330612659");
        request.setPassword("new-pass");
        request.setConfirmPassword("new-pass");

        when(registryRepo.findByStudentLoginIgnoreCase("NUMP330612659")).thenReturn(Optional.of(registry));
        when(userRepo.findByUsername("NUMP330612659")).thenReturn(Optional.of(user));
        when(passwordEncoder.encode("new-pass")).thenReturn("encoded-pass");
        when(jwtUtil.generateToken("NUMP330612659", "STUDENT")).thenReturn("jwt-token");

        var response = apiAuthController.activate(request);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("encoded-pass", user.getPasswordHash());
        assertFalse(user.isMustChangePassword());
        assertEquals(user, registry.getUser());
        verify(userRepo).save(user);
        verify(registryRepo).save(registry);
    }

    private static StudentRegistry activeClaimedRegistry() {
        StudentRegistry registry = new StudentRegistry();
        registry.setStudentLogin("NUMP330612659");
        registry.setActive(true);
        registry.setClaimed(true);
        return registry;
    }

    private static UserAccount resetPendingUser() {
        UserAccount user = new UserAccount();
        user.setUsername("NUMP330612659");
        user.setRole(Role.STUDENT);
        user.setEnabled(true);
        user.setMustChangePassword(true);
        return user;
    }
}