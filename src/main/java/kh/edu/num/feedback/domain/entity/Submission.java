package kh.edu.num.feedback.domain.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(
  name = "submissions",
  uniqueConstraints = {
    // Student feedback uniqueness: one per student per section per semester
    @UniqueConstraint(
      name = "uk_sub_student_section",
      columnNames = {"kind","semester_id","section_id","submitted_by_user_id"}
    ),
    // Teacher self uniqueness: one per teacher per semester (section_id null)
    // @UniqueConstraint(
    //   name = "uk_sub_teacher_semester",
    //   columnNames = {"kind","semester_id","submitted_by_user_id"}
    // )
  }
)
public class Submission {

  @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 30)
  private EvaluationKind kind;

  @ManyToOne(optional = false, fetch = FetchType.LAZY)
  @JoinColumn(name = "semester_id", nullable = false)
  private Semester semester;

  // Student feedback: section != null
  // Teacher self: section == null
  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "section_id")
  private ClassSection section;

  @ManyToOne(optional = false, fetch = FetchType.LAZY)
  @JoinColumn(name = "submitted_by_user_id", nullable = false)
  private UserAccount submittedBy;

  @Column(nullable = false)
  private LocalDateTime submittedAt;

  public Long getId() { return id; }
  public EvaluationKind getKind() { return kind; }
  public void setKind(EvaluationKind kind) { this.kind = kind; }
  public Semester getSemester() { return semester; }
  public void setSemester(Semester semester) { this.semester = semester; }
  public ClassSection getSection() { return section; }
  public void setSection(ClassSection section) { this.section = section; }
  public UserAccount getSubmittedBy() { return submittedBy; }
  public void setSubmittedBy(UserAccount submittedBy) { this.submittedBy = submittedBy; }
  public LocalDateTime getSubmittedAt() { return submittedAt; }
  public void setSubmittedAt(LocalDateTime submittedAt) { this.submittedAt = submittedAt; }
}
