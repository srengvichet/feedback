package kh.edu.num.feedback.domain.repo;

import kh.edu.num.feedback.domain.entity.Cohort;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CohortRepository extends JpaRepository<Cohort, Long> {

  List<Cohort> findAllByOrderByCohortNoAsc();

  // keep your existing method
  Optional<Cohort> findByCohortNo(Integer cohortNo);

  // optional convenience alias (helps if your controller uses "findFirstBy...")
  // Optional<Cohort> findFirstByCohortNo(Integer cohortNo);
}
