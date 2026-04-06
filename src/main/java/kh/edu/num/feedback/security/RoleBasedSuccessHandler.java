
package kh.edu.num.feedback.security;

import kh.edu.num.feedback.domain.entity.Role;
import kh.edu.num.feedback.domain.entity.UserAccount;
import kh.edu.num.feedback.domain.repo.UserAccountRepository;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
public class RoleBasedSuccessHandler extends SimpleUrlAuthenticationSuccessHandler {

  private final UserAccountRepository userRepo;

  public RoleBasedSuccessHandler(UserAccountRepository userRepo) {
    this.userRepo = userRepo;
  }

  @Override
  public void onAuthenticationSuccess(HttpServletRequest request,
      HttpServletResponse response,
      Authentication authentication)
      throws IOException, ServletException {

    String username = authentication.getName();
    UserAccount u = userRepo.findByUsername(username).orElse(null);

    // absolute fallback
    if (u == null || u.getRole() == null) {
      getRedirectStrategy().sendRedirect(request, response, "/");
      return;
    }

    if (u.getRole() == Role.ADMIN) {
      getRedirectStrategy().sendRedirect(request, response, "/admin");
      return;
    }

    if (u.getRole() == Role.TEACHER) {
      getRedirectStrategy().sendRedirect(request, response, "/teacher");
      return;
    }

    // STUDENT default
    getRedirectStrategy().sendRedirect(request, response, "/student");
  }
}