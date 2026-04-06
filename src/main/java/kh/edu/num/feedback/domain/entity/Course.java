package kh.edu.num.feedback.domain.entity;


import jakarta.persistence.*;

@Entity
@Table(
  name = "courses",
  uniqueConstraints = @UniqueConstraint(name = "uk_course_code", columnNames = "code")
)
public class Course {

  @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false, length = 30)
  private String code;     // e.g., IT101

  @Column(nullable = false, length = 200)
  private String name;     // e.g., Networking Basics

  @Column(nullable = true)
  private Integer credit;  // optional

  /** Year of study: 1, 2, 3, or 4 */
  @Column(name = "study_year", nullable = true)
  private Integer studyYear;

  /** Semester within the year: 1 or 2 */
  @Column(name = "semester_no", nullable = true)
  private Integer semesterNo;

  public Long getId() { return id; }
  public String getCode() { return code; }
  public void setCode(String code) { this.code = code; }
  public String getName() { return name; }
  public void setName(String name) { this.name = name; }
  public Integer getCredit() { return credit; }
  public void setCredit(Integer credit) { this.credit = credit; }
  public Integer getStudyYear() { return studyYear; }
  public void setStudyYear(Integer studyYear) { this.studyYear = studyYear; }
  public Integer getSemesterNo() { return semesterNo; }
  public void setSemesterNo(Integer semesterNo) { this.semesterNo = semesterNo; }
}
