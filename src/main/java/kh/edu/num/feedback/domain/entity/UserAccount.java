package kh.edu.num.feedback.domain.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "users", uniqueConstraints = @UniqueConstraint(name = "uk_users_username", columnNames = "username"))
public class UserAccount {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false, length = 100)
  private String username;

  @Column(nullable = false, length = 200)
  private String passwordHash;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private Role role;

  @Column(nullable = false)
  private boolean enabled = true;

  @Column(nullable = false)
  private boolean mustChangePassword = false;

  // -------------------------
  // NEW (for STUDENT timetable)
  // -------------------------
  @ManyToOne(fetch = FetchType.EAGER)
  @JoinColumn(name = "cohort_id")
  private Cohort cohort; // e.g. 34

  @Column(name = "group_no")
  private Integer groupNo; // e.g. 79

  @Column(length = 50)
  private String className; // e.g. 15A

  @Enumerated(EnumType.STRING)
  @Column(name = "shift_time", length = 30)
  private ShiftTime shiftTime; // MORNING / EARLY_AFTERNOON / AFTERNOON / EVENING
  // ===== Teacher Profile Fields =====
  private String fullName;
  private String email;
  private String phone;
  private String department;
  private String position;
  private String avatarUrl;

  @Column(length = 10)
  private String gender;

  @Column(name = "telegram_chat_id")
  private Long telegramChatId;

  // -------------------------
  // getters/setters
  // -------------------------
  public Long getId() {
    return id;
  }

  public String getUsername() {
    return username;
  }

  public void setUsername(String username) {
    this.username = username;
  }

  public String getPasswordHash() {
    return passwordHash;
  }

  public void setPasswordHash(String passwordHash) {
    this.passwordHash = passwordHash;
  }

  public Role getRole() {
    return role;
  }

  public void setRole(Role role) {
    this.role = role;
  }

  public boolean isEnabled() {
    return enabled;
  }

  public void setEnabled(boolean enabled) {
    this.enabled = enabled;
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

  public String getClassName() {
    return className;
  }

  public void setClassName(String className) {
    this.className = className;
  }

  public ShiftTime getShiftTime() {
    return shiftTime;
  }

  public void setShiftTime(ShiftTime shiftTime) {
    this.shiftTime = shiftTime;
  }
  public String getFullName() {
    return fullName;
  }

  public void setFullName(String fullName) {
    this.fullName = fullName;
  }

  public String getEmail() {
    return email;
  }

  public void setEmail(String email) {
    this.email = email;
  }

  public String getPhone() {
    return phone;
  }

  public void setPhone(String phone) {
    this.phone = phone;
  }

  public String getDepartment() {
    return department;
  }

  public void setDepartment(String department) {
    this.department = department;
  }

  public String getPosition() {
    return position;
  }

  public void setPosition(String position) {
    this.position = position;
  }

  public String getAvatarUrl() {
    return avatarUrl;
  }

  public void setAvatarUrl(String avatarUrl) {
    this.avatarUrl = avatarUrl;
  }

  public String getGender() {
    return gender;
  }

  public void setGender(String gender) {
    this.gender = gender;
  }

  public Long getTelegramChatId() {
    return telegramChatId;
  }

  public void setTelegramChatId(Long telegramChatId) {
    this.telegramChatId = telegramChatId;
  }

  public boolean isMustChangePassword() {
    return mustChangePassword;
  }

  public void setMustChangePassword(boolean mustChangePassword) {
    this.mustChangePassword = mustChangePassword;
  }
}
