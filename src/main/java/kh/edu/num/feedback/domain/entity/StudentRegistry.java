package kh.edu.num.feedback.domain.entity;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(
  name = "student_registry",
  uniqueConstraints = @UniqueConstraint(name = "uk_registry_student_login", columnNames = "student_login")
  // If you want DB indexes later, you can add:
  // , indexes = {
  //   @Index(name="idx_registry_active_claimed", columnList="active,claimed"),
  //   @Index(name="idx_registry_cohort_group", columnList="cohort_id,group_no")
  // }
)
public class StudentRegistry {

  @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "student_login", nullable = false, length = 50)
  private String studentLogin; // e.g. "NUMP330612659"

  @Column(name = "full_name", length = 150)
  private String fullName;

  @Column(name = "first_name", length = 100)
  private String firstName; // English first/family name

  @Column(name = "last_name", length = 100)
  private String lastName; // English last/given name

  @Column(name = "first_name_kh", length = 150)
  private String firstNameKh; // Khmer given name (នាមខ្លួន)

  @Column(name = "last_name_kh", length = 150)
  private String lastNameKh; // Khmer family name (នាមត្រកូល)

  @Column(name = "remark", length = 255)
  private String remark; // សំគាល់

  @Column(name = "gender", length = 10)
  private String gender; // MALE / FEMALE

  @Column(name = "date_of_birth")
  private LocalDate dateOfBirth;

  @Column(name = "phone", length = 30)
  private String phone;

  @Column(name = "email", length = 150)
  private String email;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "cohort_id")
  private Cohort cohort; // cohort 34/35 ...

  @Column(name = "group_no")
  private Integer groupNo; // 79, 32 ...

  @Column(name = "class_name", length = 50)
  private String className; // e.g. "BIT3A"

  @Enumerated(EnumType.STRING)
  @Column(name = "shift_time", length = 30)
  private ShiftTime shiftTime; // MORNING/EARLY_AFTERNOON/AFTERNOON/EVENING

  @Column(nullable = false)
  private boolean active = true;

  @Column(nullable = false)
  private boolean claimed = false; // already used to create an account

  @Column(name = "imported_at", nullable = false)
  private LocalDateTime importedAt = LocalDateTime.now();

  @Column(name = "claimed_at")
  private LocalDateTime claimedAt;

  @OneToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "user_id", unique = true)
  private UserAccount user;

  // getters/setters
  public Long getId() { return id; }

  public String getStudentLogin() { return studentLogin; }
  public void setStudentLogin(String studentLogin) { this.studentLogin = studentLogin; }

  public String getFullName() { return fullName; }
  public void setFullName(String fullName) { this.fullName = fullName; }

  public String getFirstName() { return firstName; }
  public void setFirstName(String firstName) { this.firstName = firstName; }

  public String getLastName() { return lastName; }
  public void setLastName(String lastName) { this.lastName = lastName; }

  public String getFirstNameKh() { return firstNameKh; }
  public void setFirstNameKh(String firstNameKh) { this.firstNameKh = firstNameKh; }

  public String getLastNameKh() { return lastNameKh; }
  public void setLastNameKh(String lastNameKh) { this.lastNameKh = lastNameKh; }

  public String getRemark() { return remark; }
  public void setRemark(String remark) { this.remark = remark; }

  public String getGender() { return gender; }
  public void setGender(String gender) { this.gender = gender; }

  public LocalDate getDateOfBirth() { return dateOfBirth; }
  public void setDateOfBirth(LocalDate dateOfBirth) { this.dateOfBirth = dateOfBirth; }

  public String getPhone() { return phone; }
  public void setPhone(String phone) { this.phone = phone; }

  public String getEmail() { return email; }
  public void setEmail(String email) { this.email = email; }

  public Cohort getCohort() { return cohort; }
  public void setCohort(Cohort cohort) { this.cohort = cohort; }

  public Integer getGroupNo() { return groupNo; }
  public void setGroupNo(Integer groupNo) { this.groupNo = groupNo; }

  public String getClassName() { return className; }
  public void setClassName(String className) { this.className = className; }

  public ShiftTime getShiftTime() { return shiftTime; }
  public void setShiftTime(ShiftTime shiftTime) { this.shiftTime = shiftTime; }

  public boolean isActive() { return active; }
  public void setActive(boolean active) { this.active = active; }

  public boolean isClaimed() { return claimed; }
  public void setClaimed(boolean claimed) { this.claimed = claimed; }

  public LocalDateTime getImportedAt() { return importedAt; }
  public void setImportedAt(LocalDateTime importedAt) { this.importedAt = importedAt; }

  public LocalDateTime getClaimedAt() { return claimedAt; }
  public void setClaimedAt(LocalDateTime claimedAt) { this.claimedAt = claimedAt; }

  public UserAccount getUser() { return user; }
  public void setUser(UserAccount user) { this.user = user; }
}
