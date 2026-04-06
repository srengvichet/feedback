package kh.edu.num.feedback.service;

import kh.edu.num.feedback.domain.entity.*;
import kh.edu.num.feedback.domain.repo.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
public class EvaluationService {

  private static final Logger log = LoggerFactory.getLogger(EvaluationService.class);
  private static final ZoneId ZONE = ZoneId.of("Asia/Phnom_Penh");

  private final QuestionRepository questionRepo;
  private final SubmissionRepository submissionRepo;
  private final AnswerRepository answerRepo;
  private final EvaluationWindowRepository windowRepo;
  private final CohortWindowOverrideRepository overrideRepo;
  private final ClassSectionRepository sectionRepo;
  private final EnrollmentRepository enrollmentRepo;
  private final WindowService windowService;

  public EvaluationService(QuestionRepository questionRepo,
                           SubmissionRepository submissionRepo,
                           AnswerRepository answerRepo,
                           EvaluationWindowRepository windowRepo,
                           CohortWindowOverrideRepository overrideRepo,
                           ClassSectionRepository sectionRepo,
                           EnrollmentRepository enrollmentRepo,
                           WindowService windowService) {
    this.questionRepo = questionRepo;
    this.submissionRepo = submissionRepo;
    this.answerRepo = answerRepo;
    this.windowRepo = windowRepo;
    this.overrideRepo = overrideRepo;
    this.sectionRepo = sectionRepo;
    this.enrollmentRepo = enrollmentRepo;
    this.windowService = windowService;
  }

  // ============================================================
  // Windows
  // ============================================================

  /** ✅ Single source of truth. Uses JPQL open-window query + Cambodia time. */
  public boolean isWindowOpen(Long semesterId, EvaluationKind kind) {
    LocalDateTime now = windowService.now(); // Asia/Phnom_Penh
    boolean open = windowRepo.findOpenWindow(semesterId, kind, now).isPresent();
    log.debug("WIN_CHECK sem={} kind={} now={} => {}", semesterId, kind, now, open);
    return open;
  }

  /**
   * Cohort-aware window check.
   * Returns true if a cohort/group-specific override is open OR the global window is open.
   * Pass cohortId=null to fall back to global window only.
   */
  public boolean isWindowOpenForSection(Long semesterId, EvaluationKind kind,
                                         Long cohortId, Integer groupNo) {
    LocalDateTime now = windowService.now();
    if (cohortId != null) {
      boolean overrideOpen = !overrideRepo
          .findOpenOverridesForCohortGroup(semesterId, kind, cohortId, groupNo, now)
          .isEmpty();
      if (overrideOpen) {
        log.debug("WIN_CHECK_COHORT sem={} kind={} cohort={} group={} => OPEN (override)", semesterId, kind, cohortId, groupNo);
        return true;
      }
    }
    boolean open = windowRepo.findOpenWindow(semesterId, kind, now).isPresent();
    log.debug("WIN_CHECK_COHORT sem={} kind={} cohort={} group={} => {} (global)", semesterId, kind, cohortId, groupNo, open);
    return open;
  }

  public boolean isStudentFeedbackWindowOpen(UserAccount student, ClassSection section) {
    if (section == null || section.getSemester() == null || section.getSemester().getId() == null) {
      return false;
    }

    Long cohortId = section.getCohort() != null
        ? section.getCohort().getId()
        : student != null && student.getCohort() != null ? student.getCohort().getId() : null;

    Integer groupNo = section.getGroupNo() != null
        ? section.getGroupNo()
        : student != null ? student.getGroupNo() : null;

    return isWindowOpenForSection(
        section.getSemester().getId(),
        EvaluationKind.STUDENT_FEEDBACK,
        cohortId,
        groupNo
    );
  }

  public boolean isTeacherSelfWindowOpen(UserAccount teacher, Semester semester) {
    if (semester == null || semester.getId() == null) {
      return false;
    }

    if (isWindowOpen(semester.getId(), EvaluationKind.TEACHER_SELF)) {
      return true;
    }

    if (teacher == null || teacher.getId() == null) {
      return false;
    }

    LocalDateTime now = windowService.now();
    return sectionRepo.findByTeacherId(teacher.getId()).stream()
        .filter(section -> section.getSemester() != null
            && Objects.equals(section.getSemester().getId(), semester.getId()))
        .anyMatch(section -> {
          Long cohortId = section.getCohort() != null ? section.getCohort().getId() : null;
        if (cohortId == null) {
        return false;
        }

        if (section.getGroupNo() != null) {
        return !overrideRepo
          .findOpenOverridesForCohortGroup(
            semester.getId(),
            EvaluationKind.TEACHER_SELF,
            cohortId,
            section.getGroupNo(),
            now)
          .isEmpty();
        }

        // Some teacher sections are stored at cohort level only with group_no = null.
        // In that case, allow any open override for the same cohort to unlock TEACHER_SELF.
        return overrideRepo
          .findBySemester_IdAndKindOrderByCohort_CohortNoAscGroupNoAsc(
            semester.getId(),
            EvaluationKind.TEACHER_SELF)
          .stream()
          .anyMatch(override -> override.getCohort() != null
            && Objects.equals(override.getCohort().getId(), cohortId)
            && override.getOpenAt() != null
            && override.getCloseAt() != null
            && !now.isBefore(override.getOpenAt())
            && !now.isAfter(override.getCloseAt()));
        });
  }

  // ============================================================
  // Questions
  // ============================================================

  public List<Question> activeQuestions(EvaluationKind kind) {
    return questionRepo.findByKindAndActiveOrderByOrderNoAsc(kind, true);
  }

  // ============================================================
  // Student Feedback
  // ============================================================

  @Transactional
  public Submission saveStudentFeedback(UserAccount student,
                                        ClassSection section,
                                        Map<Long, String> inputs) {

    // must be enrolled
    if (!enrollmentRepo.existsByStudent_IdAndSection_Id(student.getId(), section.getId())) {
      throw new IllegalStateException("You are not enrolled in this class section.");
    }

    Long semesterId = section.getSemester().getId();
    if (!isStudentFeedbackWindowOpen(student, section)) {
      throw new IllegalStateException("Student feedback window is closed.");
    }

    Submission existingSubmission = submissionRepo
        .findByKindAndSemester_IdAndSection_IdAndSubmittedBy_Id(
            EvaluationKind.STUDENT_FEEDBACK,
            semesterId,
            section.getId(),
            student.getId()
        )
        .orElse(null);

    if (existingSubmission != null) {
      throw new IllegalStateException("You have already submitted feedback for this section.");
    }

    Submission sub = new Submission();

    sub.setKind(EvaluationKind.STUDENT_FEEDBACK);
    sub.setSemester(section.getSemester());
    sub.setSection(section);
    sub.setSubmittedBy(student);
    sub.setSubmittedAt(LocalDateTime.now(ZONE));
    sub = submissionRepo.save(sub);

    // save new answers (only active questions)
    List<Question> questions = activeQuestions(EvaluationKind.STUDENT_FEEDBACK);
    List<Answer> toSave = new ArrayList<>();
    for (Question q : questions) {
      String v = inputs.get(q.getId());
      if (v == null) continue;

      String vv = v.trim();
      if (vv.isBlank()) continue;

      Answer a = new Answer();
      a.setSubmission(sub);
      a.setQuestion(q);

      if (q.getType() == QuestionType.RATING) {
        try {
          a.setNumericValue(Integer.parseInt(vv));
        } catch (NumberFormatException ex) {
          continue; // skip invalid rating input
        }
      } else {
        a.setTextValue(vv);
      }

      toSave.add(a);
    }
    answerRepo.saveAll(toSave);

    return sub;
  }

  // ============================================================
  // Teacher Self-Assessment
  // ============================================================

  @Transactional
  public Submission saveTeacherSelf(UserAccount teacher,
                                   Semester semester,
                                   Map<Long, String> inputs) {

    if (!isTeacherSelfWindowOpen(teacher, semester)) {
      throw new IllegalStateException("Teacher self-assessment window is closed.");
    }

    Submission existingSubmission = submissionRepo
      .findByKindAndSemester_IdAndSectionIsNullAndSubmittedBy_Id(
            EvaluationKind.TEACHER_SELF,
            semester.getId(),
            teacher.getId()
        )
      .orElse(null);

    if (existingSubmission != null) {
      throw new IllegalStateException("You have already submitted your self-assessment.");
    }

    // teacher-self is per semester (no section)
    Submission sub = new Submission();

    sub.setKind(EvaluationKind.TEACHER_SELF);
    sub.setSemester(semester);
    sub.setSection(null);
    sub.setSubmittedBy(teacher);
    sub.setSubmittedAt(LocalDateTime.now(ZONE));
    sub = submissionRepo.save(sub);

    // save answers
    List<Question> questions = activeQuestions(EvaluationKind.TEACHER_SELF);
    List<Answer> toSave = new ArrayList<>();
    for (Question q : questions) {
      String v = inputs.get(q.getId());
      if (v == null) continue;

      String vv = v.trim();
      if (vv.isBlank()) continue;

      Answer a = new Answer();
      a.setSubmission(sub);
      a.setQuestion(q);

      if (q.getType() == QuestionType.RATING) {
        try {
          a.setNumericValue(Integer.parseInt(vv));
        } catch (NumberFormatException ex) {
          continue; // skip invalid rating input
        }
      } else {
        a.setTextValue(vv);
      }

      toSave.add(a);
    }
    answerRepo.saveAll(toSave);

    return sub;
  }
}