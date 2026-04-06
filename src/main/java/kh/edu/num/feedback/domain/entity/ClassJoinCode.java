package kh.edu.num.feedback.domain.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "class_join_codes", uniqueConstraints = @UniqueConstraint(name = "uk_join_code_code", columnNames = "code"))
public class ClassJoinCode {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(optional = false, fetch = FetchType.LAZY)
  @JoinColumn(name = "semester_id", nullable = false)
  private Semester semester;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "cohort_id")
  private Cohort cohort;

  @Column(name = "group_no")
  private Integer groupNo;

  @Column(name = "schedule_id")
  private Long scheduleId;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 30)
  private ShiftTime shiftTime;

  @Column(nullable = false, length = 20)
  private String code;

  @Column(nullable = false)
  private boolean active = true;

  @Column(nullable = false)
  private LocalDateTime expiresAt;

  public Long getId() {
    return id;
  }

  public Semester getSemester() {
    return semester;
  }

  public void setSemester(Semester semester) {
    this.semester = semester;
  }

  public Cohort getCohort() {
    return cohort;
  }

  public void setCohort(Cohort cohort) {
    this.cohort = cohort;
  }

  public Integer getGroupNo() {
    return groupNo;
  }

  public void setGroupNo(Integer groupNo) {
    this.groupNo = groupNo;
  }

  public ShiftTime getShiftTime() {
    return shiftTime;
  }

  public void setShiftTime(ShiftTime shiftTime) {
    this.shiftTime = shiftTime;
  }

  public String getCode() {
    return code;
  }

  public void setCode(String code) {
    this.code = code;
  }

  public boolean isActive() {
    return active;
  }

  public void setActive(boolean active) {
    this.active = active;
  }

  public LocalDateTime getExpiresAt() {
    return expiresAt;
  }

  public void setExpiresAt(LocalDateTime expiresAt) {
    this.expiresAt = expiresAt;
  }

  public Long getScheduleId() {
    return scheduleId;
  }

  public void setScheduleId(Long scheduleId) {
    this.scheduleId = scheduleId;
  }
}
