package kh.edu.num.feedback.domain.repo;

import kh.edu.num.feedback.domain.entity.StudentRegistry;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface StudentRegistryRepository extends JpaRepository<StudentRegistry, Long> {

  Optional<StudentRegistry> findByStudentLoginIgnoreCase(String studentLogin);

  Optional<StudentRegistry> findByUser_Id(Long userId);

  boolean existsByStudentLoginIgnoreCase(String studentLogin);

  // keep if you still use it somewhere
  boolean existsByStudentLogin(String studentLogin);
  Optional<StudentRegistry> findByStudentLogin(String studentLogin);
}