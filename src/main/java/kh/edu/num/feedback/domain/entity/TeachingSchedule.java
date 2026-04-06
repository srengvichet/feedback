package kh.edu.num.feedback.domain.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "teaching_schedules")
public class TeachingSchedule {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(optional = false, fetch = FetchType.LAZY)
  @JoinColumn(name = "semester_id", nullable = false)
  private Semester semester;

  @ManyToOne(optional = false, fetch = FetchType.LAZY)
  @JoinColumn(name = "course_id", nullable = false)
  private Course course;

  @ManyToOne(optional = false, fetch = FetchType.LAZY)
  @JoinColumn(name = "teacher_user_id", nullable = false)
  private UserAccount teacher;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 30)
  private ShiftTime shiftTime;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 15)
  private Weekday weekday;

  @Column(length = 10)
  private String building;

  @Column(length = 20)
  private String room;

  // optional: subject order 1..5 for your “5 subjects per shift”
  private Integer subjectNo;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "cohort_id")
  private Cohort cohort;

  @Column(name = "group_no")
  private Integer groupNo;

  // ================= GETTERS & SETTERS =================
  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "class_section_id")
  private ClassSection classSection;

  public ClassSection getClassSection() {
    return classSection;
  }

  public void setClassSection(ClassSection classSection) {
    this.classSection = classSection;
  }

  public Long getId() {
    return id;
  }

  public Semester getSemester() {
    return semester;
  }

  public void setSemester(Semester semester) {
    this.semester = semester;
  }

  public Course getCourse() {
    return course;
  }

  public void setCourse(Course course) {
    this.course = course;
  }

  public UserAccount getTeacher() {
    return teacher;
  }

  public void setTeacher(UserAccount teacher) {
    this.teacher = teacher;
  }

  public ShiftTime getShiftTime() {
    return shiftTime;
  }

  public void setShiftTime(ShiftTime shiftTime) {
    this.shiftTime = shiftTime;
  }

  public Weekday getWeekday() {
    return weekday;
  }

  public void setWeekday(Weekday weekday) {
    this.weekday = weekday;
  }

  public String getBuilding() {
    return building;
  }

  public void setBuilding(String building) {
    this.building = building;
  }

  public String getRoom() {
    return room;
  }

  public void setRoom(String room) {
    this.room = room;
  }

  public Integer getSubjectNo() {
    return subjectNo;
  }

  public void setSubjectNo(Integer subjectNo) {
    this.subjectNo = subjectNo;
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
}
