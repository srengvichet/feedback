package kh.edu.num.feedback.domain.repo;

import kh.edu.num.feedback.domain.entity.AiFeedbackSummary;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface AiFeedbackSummaryRepository extends JpaRepository<AiFeedbackSummary, Long> {

    Optional<AiFeedbackSummary> findBySectionId(Long sectionId);

    void deleteBySectionId(Long sectionId);
}