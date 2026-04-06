package kh.edu.num.feedback.security;


import java.util.Collection;
import java.util.List;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import kh.edu.num.feedback.domain.entity.UserAccount;

public class UserPrincipal implements UserDetails {

  private final UserAccount user;

  public UserPrincipal(UserAccount user) {
    this.user = user;
  }

  public UserAccount getUser() { return user; }

  @Override
  public Collection<? extends GrantedAuthority> getAuthorities() {
    // Spring expects ROLE_ prefix
    return List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole().name()));
  }

  @Override public String getPassword() { return user.getPasswordHash(); }
  @Override public String getUsername() { return user.getUsername(); }
  @Override public boolean isAccountNonExpired() { return true; }
  @Override public boolean isAccountNonLocked() { return true; }
  @Override public boolean isCredentialsNonExpired() { return true; }
  @Override public boolean isEnabled() { return user.isEnabled(); }
}
