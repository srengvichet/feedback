package kh.edu.num.feedback.security;


import org.springframework.security.core.userdetails.*;
import org.springframework.stereotype.Service;

import jakarta.transaction.Transactional;
import kh.edu.num.feedback.domain.repo.UserAccountRepository;


@Service
public class UserAccountDetailsService implements UserDetailsService {

  private final UserAccountRepository repo;

  public UserAccountDetailsService(UserAccountRepository repo) {
    this.repo = repo;
  }

  @Override
  @Transactional(Transactional.TxType.SUPPORTS)
  public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
    var user = repo.findByUsername(username)
        .orElseThrow(() -> new UsernameNotFoundException("User not found: " + username));
    return new UserPrincipal(user);
  }
}
