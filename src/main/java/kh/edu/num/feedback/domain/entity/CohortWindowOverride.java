package kh.edu.num.feedback.domain.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "cohort_window_overrides")
public class CohortWindowOverride {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(optional = false, fetch = FetchType.LAZY)
  @JoinColumn(name = "semester_id", nullable = false)
  private Semester semester;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 30)
  private EvaluationKind kind;

  @ManyToOne(optional = false, fetch = FetchType.EAGER)
  @JoinColumn(name = "cohort_id", nullable = false)
  private Cohort cohort;

  /** null means "all groups in this cohort" */
  @Column(name = "group_no", nullable = true)
  private Integer groupNo;

  @Column(nullable = false)
  private LocalDateTime openAt;

  @Column(nullable = false)
  private LocalDateTime closeAt;

  public Long getId() { return id; }

  public Semester getSemester() { return semester; }
  public void setSemester(Semester semester) { this.semester = semester; }

  public EvaluationKind getKind() { return kind; }
  public void setKind(EvaluationKind kind) { this.kind = kind; }

  public Cohort getCohort() { return cohort; }
  public void setCohort(Cohort cohort) { this.cohort = cohort; }

  public Integer getGroupNo() { return groupNo; }
  public void setGroupNo(Integer groupNo) { this.groupNo = groupNo; }

  public LocalDateTime getOpenAt() { return openAt; }
  public void setOpenAt(LocalDateTime openAt) { this.openAt = openAt; }

  public LocalDateTime getCloseAt() { return closeAt; }
  public void setCloseAt(LocalDateTime closeAt) { this.closeAt = closeAt; }
}
