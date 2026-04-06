package kh.edu.num.feedback.domain.entity;

import jakarta.persistence.*;

@Entity
@Table(
  name = "cohort_groups",
  uniqueConstraints = @UniqueConstraint(
    name = "uk_cohort_group",
    columnNames = {"cohort_id","group_no"}
  )
)
public class CohortGroup {

  @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(optional = false, fetch = FetchType.LAZY)
  @JoinColumn(name = "cohort_id", nullable = false)
  private Cohort cohort;

  @Column(name = "group_no", nullable = false)
  private Integer groupNo; // 79, 80, ...

  public Long getId() { return id; }
  public Cohort getCohort() { return cohort; }
  public void setCohort(Cohort cohort) { this.cohort = cohort; }
  public Integer getGroupNo() { return groupNo; }
  public void setGroupNo(Integer groupNo) { this.groupNo = groupNo; }

  @Transient
  public String getDisplay() {
    return "C" + (cohort != null ? cohort.getCohortNo() : "?") + " - G" + groupNo;
  }
}
