package kh.edu.num.feedback.domain.repo;

import kh.edu.num.feedback.domain.entity.Semester;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.Optional;

public interface SemesterRepository extends JpaRepository<Semester, Long> {

  Optional<Semester> findFirstByStartDateLessThanEqualAndEndDateGreaterThanEqualOrderByStartDateDesc(
      LocalDate start, LocalDate end
  );
}
