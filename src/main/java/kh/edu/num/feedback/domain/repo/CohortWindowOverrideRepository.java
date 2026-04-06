package kh.edu.num.feedback.domain.repo;

import kh.edu.num.feedback.domain.entity.CohortWindowOverride;
import kh.edu.num.feedback.domain.entity.EvaluationKind;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface CohortWindowOverrideRepository extends JpaRepository<CohortWindowOverride, Long> {

  List<CohortWindowOverride> findBySemester_IdAndKindOrderByCohort_CohortNoAscGroupNoAsc(
      Long semesterId, EvaluationKind kind);

  /** Find overrides that are currently open for a specific cohort+group. */
  @Query("""
      select o from CohortWindowOverride o
      where o.semester.id = :semId
        and o.kind = :kind
        and o.cohort.id = :cohortId
        and (o.groupNo is null or o.groupNo = :groupNo)
        and o.openAt <= :now
        and o.closeAt >= :now
      """)
  List<CohortWindowOverride> findOpenOverridesForCohortGroup(
      @Param("semId") Long semId,
      @Param("kind") EvaluationKind kind,
      @Param("cohortId") Long cohortId,
      @Param("groupNo") Integer groupNo,
      @Param("now") LocalDateTime now);
}
