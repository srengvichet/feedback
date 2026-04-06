package kh.edu.num.feedback.domain.repo;

import kh.edu.num.feedback.domain.entity.*;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SubmissionRepository extends JpaRepository<Submission, Long> {

    Optional<Submission> findByKindAndSemesterIdAndSectionIdAndSubmittedById(
            EvaluationKind kind, Long semesterId, Long sectionId, Long submittedById);

    Optional<Submission> findByKindAndSemesterIdAndSectionIsNullAndSubmittedById(
            EvaluationKind kind, Long semesterId, Long submittedById);

    long countByKindAndSectionId(EvaluationKind kind, Long sectionId);

    long countByKindAndSemester_IdAndSectionIsNullAndSubmittedBy_Id(
            EvaluationKind kind, Long semesterId, Long userId);

    Optional<Submission> findByKindAndSemester_IdAndSection_IdAndSubmittedBy_Id(
            EvaluationKind kind, Long semesterId, Long sectionId, Long userId);

    Optional<Submission> findByKindAndSemester_IdAndSectionIsNullAndSubmittedBy_Id(
            EvaluationKind kind, Long semesterId, Long userId);

    List<Submission> findByKindAndSubmittedById(EvaluationKind kind, Long submittedById);
        List<Submission> findBySection_IdAndKind(Long sectionId, EvaluationKind kind);
}
