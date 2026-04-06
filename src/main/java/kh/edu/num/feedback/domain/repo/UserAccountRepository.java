package kh.edu.num.feedback.domain.repo;


import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import kh.edu.num.feedback.domain.entity.Role;
import kh.edu.num.feedback.domain.entity.UserAccount;

public interface UserAccountRepository extends JpaRepository<UserAccount, Long> {
  Optional<UserAccount> findByUsername(String username);
  boolean existsByUsername(String username);
  List<UserAccount> findByRole(Role role);
  List<UserAccount> findAllByEmail(String email);
  List<UserAccount> findAllByEmailIgnoreCase(String email);
  List<UserAccount> findAllByPhone(String phone);
  @Query("""
      select u from UserAccount u
      where replace(replace(replace(replace(replace(coalesce(u.phone, ''), ' ', ''), '-', ''), '(', ''), ')', ''), '+', '') = :normalizedPhone
      """)
  List<UserAccount> findAllByNormalizedPhone(@Param("normalizedPhone") String normalizedPhone);
  Optional<UserAccount> findByTelegramChatId(Long telegramChatId);
}
