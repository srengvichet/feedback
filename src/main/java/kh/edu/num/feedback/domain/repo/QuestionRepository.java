package kh.edu.num.feedback.domain.repo;

import kh.edu.num.feedback.domain.entity.EvaluationKind;
import kh.edu.num.feedback.domain.entity.Question;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

public interface QuestionRepository extends JpaRepository<Question, Long> {

  List<Question> findAllByOrderByKindAscOrderNoAsc();

  List<Question> findByKindOrderByOrderNoAsc(EvaluationKind kind);

  List<Question> findByKindAndActiveOrderByOrderNoAsc(EvaluationKind kind, boolean active);

  long countByKind(EvaluationKind kind);

  @Query("select coalesce(max(q.orderNo), 0) from Question q where q.kind = :kind")
  int maxOrderNoByKind(@Param("kind") EvaluationKind kind);

  Optional<Question> findFirstByKindAndOrderNoLessThanOrderByOrderNoDesc(EvaluationKind kind, Integer orderNo);

  Optional<Question> findFirstByKindAndOrderNoGreaterThanOrderByOrderNoAsc(EvaluationKind kind, Integer orderNo);

  @Modifying
  @Transactional
  @Query("update Question q set q.active = :active where q.kind = :kind")
  int updateActiveByKind(@Param("kind") EvaluationKind kind, @Param("active") boolean active);
  @Modifying
@Transactional
@Query("DELETE FROM Question q WHERE q.kind = :kind")
void deleteAllByKind(@Param("kind") EvaluationKind kind);
}
