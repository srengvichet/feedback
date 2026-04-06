// src/main/java/kh/edu/num/feedback/domain/repo/ClassJoinCodeRepository.java
package kh.edu.num.feedback.domain.repo;

import kh.edu.num.feedback.domain.entity.ClassJoinCode;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface ClassJoinCodeRepository extends JpaRepository<ClassJoinCode, Long> {
  Optional<ClassJoinCode> findByCodeAndActiveTrue(String code);
  Optional<ClassJoinCode> findByCodeAndActiveTrueAndExpiresAtAfter(String code, LocalDateTime now);

  List<ClassJoinCode> findBySemester_IdOrderByIdDesc(Long semesterId);
}
