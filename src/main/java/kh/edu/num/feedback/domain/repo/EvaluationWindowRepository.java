package kh.edu.num.feedback.domain.repo;

import kh.edu.num.feedback.domain.entity.EvaluationKind;
import kh.edu.num.feedback.domain.entity.EvaluationWindow;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface EvaluationWindowRepository extends JpaRepository<EvaluationWindow, Long> {

  Optional<EvaluationWindow> findBySemester_IdAndKind(Long semesterId, EvaluationKind kind);

  // ✅ correct: many rows per semester
  List<EvaluationWindow> findBySemester_IdOrderByKindAsc(Long semesterId);

  @Query("""
        select w from EvaluationWindow w
        where w.semester.id = :semesterId
          and w.kind = :kind
          and w.openAt <= :now
          and w.closeAt >= :now
      """)
  Optional<EvaluationWindow> findOpenWindow(@Param("semesterId") Long semesterId,
                                            @Param("kind") EvaluationKind kind,
                                            @Param("now") LocalDateTime now);

  // optional compatibility
  Optional<EvaluationWindow> findFirstBySemester_IdAndKind(Long semesterId, EvaluationKind kind);

  Optional<EvaluationWindow> findFirstBySemester_IdAndKindAndOpenAtLessThanEqualAndCloseAtGreaterThanEqual(
      Long semesterId, EvaluationKind kind, LocalDateTime now1, LocalDateTime now2);
}


// package kh.edu.num.feedback.domain.repo;

// import kh.edu.num.feedback.domain.entity.EvaluationKind;
// import kh.edu.num.feedback.domain.entity.EvaluationWindow;
// import org.springframework.data.jpa.repository.JpaRepository;
// import org.springframework.data.jpa.repository.Query;
// import org.springframework.data.repository.query.Param;

// import java.time.LocalDateTime;
// import java.util.Optional;

// public interface EvaluationWindowRepository extends JpaRepository<EvaluationWindow, Long> {

//   // Unique row per (semester_id, kind) — safe because of your unique constraint
//   Optional<EvaluationWindow> findBySemester_IdAndKind(Long semesterId, EvaluationKind kind);

//   Optional<EvaluationWindow> findBySemester_IdOrderByKindAsc(Long semesterId);
  
//   // Reliable "open now" query

//   @Query("""
//         select w from EvaluationWindow w
//         where w.semester.id = :semesterId
//           and w.kind = :kind
//           and w.openAt <= :now
//           and w.closeAt >= :now
//       """)
//   Optional<EvaluationWindow> findOpenWindow(@Param("semesterId") Long semesterId,
//       @Param("kind") EvaluationKind kind,
//       @Param("now") LocalDateTime now);
//   // (Optional) Compatibility if some code still calls findFirstBy...
//   Optional<EvaluationWindow> findFirstBySemester_IdAndKind(Long semesterId, EvaluationKind kind);
    
//   Optional<EvaluationWindow> findFirstBySemester_IdAndKindAndOpenAtLessThanEqualAndCloseAtGreaterThanEqual(
//       Long semesterId, EvaluationKind kind, LocalDateTime now1, LocalDateTime now2);
// }
