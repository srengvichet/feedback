package kh.edu.num.feedback.domain.entity;

import jakarta.persistence.*;

@Entity
@Table(
  name = "cohorts",
  uniqueConstraints = @UniqueConstraint(name = "uk_cohort_no", columnNames = "cohort_no")
)
public class Cohort {

  @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "cohort_no", nullable = false)
  private Integer cohortNo; // 34, 35

  @Column(nullable = true, length = 100)
  private String label; // optional: "BIT Cohort 34"

  @Column(nullable = true, length = 100)
  private String faculty; // optional: "Faculty of IT"

  @Column(nullable = false)
  private boolean active = true;
 
  public Long getId() { return id; }
  public Integer getCohortNo() { return cohortNo; }
  public void setCohortNo(Integer cohortNo) { this.cohortNo = cohortNo; }
  public String getLabel() { return label; }
  public void setLabel(String label) { this.label = label; }
  public String getFaculty() { return faculty; }
  public void setFaculty(String faculty) { this.faculty = faculty; }
  public boolean isActive() { return active; }
  public void setActive(boolean active) { this.active = active; }
}
