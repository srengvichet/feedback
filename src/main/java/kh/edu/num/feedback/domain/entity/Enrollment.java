package kh.edu.num.feedback.domain.entity;

import jakarta.persistence.*;

@Entity
@Table(
  name = "enrollments",
  uniqueConstraints = @UniqueConstraint(
    name = "uk_enroll_section_student",
    columnNames = {"section_id", "student_user_id"}
  )
)
public class Enrollment {

  @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(optional = false, fetch = FetchType.LAZY)
  @JoinColumn(name = "section_id", nullable = false)
  private ClassSection section;

  // student is a UserAccount with role STUDENT
  @ManyToOne(optional = false, fetch = FetchType.LAZY)
  @JoinColumn(name = "student_user_id", nullable = false)
  private UserAccount student;

  public Long getId() { return id; }
  public ClassSection getSection() { return section; }
  public void setSection(ClassSection section) { this.section = section; }
  public UserAccount getStudent() { return student; }
  public void setStudent(UserAccount student) { this.student = student; }
}

