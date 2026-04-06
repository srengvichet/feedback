package kh.edu.num.feedback.config;

import kh.edu.num.feedback.api.JwtFilter;
import kh.edu.num.feedback.api.JwtUtil;
import kh.edu.num.feedback.security.RoleBasedSuccessHandler;
import kh.edu.num.feedback.security.UserAccountDetailsService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
public class SecurityConfig {

  private final RoleBasedSuccessHandler roleBasedSuccessHandler;
  private final JwtUtil jwtUtil;
  private final UserAccountDetailsService userDetailsService;

  public SecurityConfig(RoleBasedSuccessHandler roleBasedSuccessHandler,
                        JwtUtil jwtUtil,
                        UserAccountDetailsService userDetailsService) {
    this.roleBasedSuccessHandler = roleBasedSuccessHandler;
    this.jwtUtil = jwtUtil;
    this.userDetailsService = userDetailsService;
  }

  /** API filter chain — stateless JWT, matches /api/** only */
  @Bean
  @Order(1)
  SecurityFilterChain apiChain(HttpSecurity http) throws Exception {
    http
      .securityMatcher("/api/**")
      .csrf(csrf -> csrf.disable())
      .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
      .authorizeHttpRequests(auth -> auth
        .requestMatchers("/api/auth/**", "/api/telegram/webhook").permitAll()
        .requestMatchers("/api/student/**").hasRole("STUDENT")
        .requestMatchers("/api/teacher/**").hasRole("TEACHER")
        .requestMatchers("/api/admin/**").hasAnyRole("ADMIN", "QA")
        .anyRequest().authenticated()
      )
      .addFilterBefore(new JwtFilter(jwtUtil, userDetailsService),
                       UsernamePasswordAuthenticationFilter.class);

    return http.build();
  }

  /** Web filter chain — session-based, all other routes */
  @Bean
  @Order(2)
  SecurityFilterChain webChain(HttpSecurity http) throws Exception {
    http
      .redirectToHttps(Customizer.withDefaults())
      .csrf(csrf -> csrf
          .ignoringRequestMatchers("/h2/**", "/api/**"))
      .authorizeHttpRequests(auth -> auth
        .requestMatchers("/css/**", "/js/**", "/images/**", "/login", "/h2/**", "/ai/**","/api/auth/**","/error/**","/api/student/**",
            "/forgot-password", "/forgot-telegram", "/verify-otp", "/reset-password").permitAll()
        .requestMatchers("/admin/teachers/**").hasRole("ADMIN")
        .requestMatchers("/admin/**").hasAnyRole("ADMIN", "QA")
        .requestMatchers("/teacher/**").hasRole("TEACHER")
        .requestMatchers("/student/**").hasRole("STUDENT")
        .requestMatchers("/register", "/register/**").permitAll()
        .anyRequest().authenticated()
      )
      .formLogin(form -> form
        .loginPage("/login")
        .successHandler(roleBasedSuccessHandler)
        .permitAll()
      )
      .logout(logout -> logout
        .logoutUrl("/logout")
        .logoutSuccessUrl("/login?logout").permitAll()
      )

      .httpBasic(Customizer.withDefaults());
      
    return http.build();
  }

  @Bean
  PasswordEncoder passwordEncoder() {
    return new BCryptPasswordEncoder();
  }
}
