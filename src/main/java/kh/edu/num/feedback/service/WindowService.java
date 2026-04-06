package kh.edu.num.feedback.service;

import kh.edu.num.feedback.domain.entity.EvaluationKind;
import kh.edu.num.feedback.domain.entity.EvaluationWindow;
import kh.edu.num.feedback.domain.entity.Semester;
import kh.edu.num.feedback.domain.repo.EvaluationWindowRepository;
import kh.edu.num.feedback.domain.repo.SemesterRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;

@Service
public class WindowService {

  private final EvaluationWindowRepository windowRepo;
  private final SemesterRepository semesterRepo;

  private static final ZoneId ZONE = ZoneId.of("Asia/Phnom_Penh");

  public WindowService(EvaluationWindowRepository windowRepo, SemesterRepository semesterRepo) {
    this.windowRepo = windowRepo;
    this.semesterRepo = semesterRepo;
  }

  public LocalDateTime now() {
    return LocalDateTime.now(ZONE);
  }

  @Transactional
  public void createDefaultWindows(Semester semester) {
    // Teacher self-assessment: Week 8 window open 7 days
    LocalDate week8Start = semester.getStartDate().plusWeeks(7);
    LocalDateTime selfOpen = week8Start.atStartOfDay();
    LocalDateTime selfClose = selfOpen.plusDays(7);

    // Student feedback: last 14 days of semester
    LocalDate feedbackStart = semester.getEndDate().minusDays(14);
    LocalDateTime fbOpen = feedbackStart.atStartOfDay();
    LocalDateTime fbClose = semester.getEndDate().plusDays(1).atStartOfDay(); //
    // inclusive endDate
    upsertWindow(semester, EvaluationKind.TEACHER_SELF, selfOpen, selfClose);
    upsertWindow(semester, EvaluationKind.STUDENT_FEEDBACK, fbOpen, fbClose);
    // upsert(semester, EvaluationKind.TEACHER_SELF, selfOpen, selfClose);
    // upsert(semester, EvaluationKind.STUDENT_FEEDBACK, fbOpen, fbClose);
  }

  /** Create default windows for a semester (optional helper) */
  /**
   * Create default windows for a semester (week 8 self-assessment + last 14 days
   * student feedback).
   */
  // @Transactional
  // public void createDefaultWindows(Long semesterId) {
  // Semester semester = semesterRepo.findById(semesterId)
  // .orElseThrow(() -> new IllegalArgumentException("Semester not found: " +
  // semesterId));

  // // Teacher self-assessment: Week 8 window open 7 days
  // LocalDate week8Start = semester.getStartDate().plusWeeks(7);
  // LocalDateTime selfOpen = week8Start.atStartOfDay();
  // LocalDateTime selfClose = selfOpen.plusDays(7);

  // // Student feedback: last 14 days of semester
  // LocalDate feedbackStart = semester.getEndDate().minusDays(14);
  // LocalDateTime fbOpen = feedbackStart.atStartOfDay();
  // LocalDateTime fbClose = semester.getEndDate().plusDays(1).atStartOfDay(); //
  // inclusive endDate

  // upsertWindow(semester, EvaluationKind.TEACHER_SELF, selfOpen, selfClose);
  // upsertWindow(semester, EvaluationKind.STUDENT_FEEDBACK, fbOpen, fbClose);
  // }

  @Transactional
  public EvaluationWindow openNowForDays(Long semesterId, EvaluationKind kind, int days) {
    if (days < 1)
      days = 1;

    Semester semester = semesterRepo.findById(semesterId)
        .orElseThrow(() -> new IllegalArgumentException("Semester not found: " + semesterId));

    EvaluationWindow w = windowRepo.findBySemester_IdAndKind(semesterId, kind).orElseGet(EvaluationWindow::new);

    LocalDateTime openAt = now();
    LocalDateTime closeAt = openAt.plusDays(days);

    w.setSemester(semester);
    w.setKind(kind);
    w.setOpenAt(openAt);
    w.setCloseAt(closeAt);

    return windowRepo.save(w);
  }

  // @Transactional
  // public EvaluationWindow closeNow(Long semesterId, EvaluationKind kind) {
  // Semester sem = semesterRepo.findById(semesterId)
  // .orElseThrow(() -> new IllegalArgumentException("Semester not found: " +
  // semesterId));

  // EvaluationWindow w = windowRepo.findBySemester_IdAndKind(semesterId, kind)
  // .orElseGet(() -> {
  // EvaluationWindow nw = new EvaluationWindow();
  // nw.setSemester(sem);
  // nw.setKind(kind);
  // // if never existed, create a “closed” record
  // return nw;
  // });

  // LocalDateTime n = now();
  // LocalDateTime openAt = (w.getOpenAt() == null) ? n.minusMinutes(1) :
  // w.getOpenAt();

  // // ensure openAt <= closeAt
  // if (openAt.isAfter(n)) openAt = n.minusMinutes(1);

  // w.setOpenAt(openAt);
  // w.setCloseAt(n.minusSeconds(1));

  // return windowRepo.save(w);
  // }

  public boolean isOpen(EvaluationWindow w, LocalDateTime at) {
    if (w == null || w.getOpenAt() == null || w.getCloseAt() == null)
      return false;
    return !at.isBefore(w.getOpenAt()) && !at.isAfter(w.getCloseAt()); // inclusive
  }

  @Transactional
  public void closeNow(Long semesterId, EvaluationKind kind) {
    var wOpt = windowRepo.findBySemester_IdAndKind(semesterId, kind);
    if (wOpt.isEmpty())
      return;

    EvaluationWindow w = wOpt.get();
    LocalDateTime n = now();

    // Make it definitely closed (inclusive compare)
    w.setCloseAt(n.minusSeconds(1));
    windowRepo.save(w);
  }

  /** True if a window is open now (Cambodia time). */
  @Transactional(readOnly = true)
  public boolean isOpenNow(Long semesterId, EvaluationKind kind) {
    return windowRepo.findOpenWindow(semesterId, kind, now()).isPresent();
  }

  private void upsertWindow(Semester semester, EvaluationKind kind, LocalDateTime openAt, LocalDateTime closeAt) {
    EvaluationWindow w = windowRepo.findBySemester_IdAndKind(semester.getId(), kind).orElseGet(EvaluationWindow::new);
    w.setSemester(semester);
    w.setKind(kind);
    w.setOpenAt(openAt);
    w.setCloseAt(closeAt);
    windowRepo.save(w);
  }
}
