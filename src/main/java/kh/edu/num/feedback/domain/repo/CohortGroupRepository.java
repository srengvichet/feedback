package kh.edu.num.feedback.domain.repo;

import kh.edu.num.feedback.domain.entity.CohortGroup;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CohortGroupRepository extends JpaRepository<CohortGroup, Long> {

  List<CohortGroup> findAllByOrderByIdAsc();

  List<CohortGroup> findByCohort_IdOrderByGroupNoAsc(Long cohortId);

  // ✅ needed for CSV import + validation (avoid duplicates)
  boolean existsByCohort_IdAndGroupNo(Long cohortId, Integer groupNo);

  // Optional<CohortGroup> findFirstByCohort_IdAndGroupNo(Long cohortId, Integer groupNo);
  Optional<CohortGroup> findByCohort_IdAndGroupNo(Long cohortId, Integer groupNo);
}
