package kh.edu.num.feedback.domain.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(
  name = "evaluation_windows",
  uniqueConstraints = @UniqueConstraint(
    name = "uk_window_semester_kind",
    columnNames = {"semester_id", "kind"}
  )
)
public class EvaluationWindow {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(optional = false, fetch = FetchType.LAZY)
  @JoinColumn(name = "semester_id", nullable = false)
  private Semester semester;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 30)
  private EvaluationKind kind;

  @Column(nullable = false)
  private LocalDateTime openAt;

  @Column(nullable = false)
  private LocalDateTime closeAt;

  @PrePersist
  @PreUpdate
  private void validateDates() {
    if (openAt == null || closeAt == null) return;
    // Safety: never allow closeAt < openAt (could break open checks)
    if (closeAt.isBefore(openAt)) {
      closeAt = openAt.plusMinutes(1);
    }
  }

  public Long getId() { return id; }

  public Semester getSemester() { return semester; }
  public void setSemester(Semester semester) { this.semester = semester; }

  public EvaluationKind getKind() { return kind; }
  public void setKind(EvaluationKind kind) { this.kind = kind; }

  public LocalDateTime getOpenAt() { return openAt; }
  public void setOpenAt(LocalDateTime openAt) { this.openAt = openAt; }

  public LocalDateTime getCloseAt() { return closeAt; }
  public void setCloseAt(LocalDateTime closeAt) { this.closeAt = closeAt; }
}
