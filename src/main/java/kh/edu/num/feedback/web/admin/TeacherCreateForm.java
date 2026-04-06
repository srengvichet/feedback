package kh.edu.num.feedback.web.admin;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class TeacherCreateForm {

  @NotBlank
  @Size(max = 100)
  private String username;

  @NotBlank
  @Size(min = 4, max = 100)
  private String password;
  @NotBlank
  @Size(max = 150)
  private String fullName;

  @Email
  @Size(max = 150)
  private String email;

  @Size(max = 50)
  private String phone;

  @Size(max = 150)
  private String department;

  @Size(max = 150)
  private String position;

  private String avatarUrl;

  @Size(max = 10)
  private String gender;

  public String getUsername() { return username; }
  public void setUsername(String username) { this.username = username; }

  public String getPassword() { return password; }
  public void setPassword(String password) { this.password = password; }
  public String getFullName() { return fullName; }
  public void setFullName(String fullName) { this.fullName = fullName; }
  public String getEmail() { return email; }
  public void setEmail(String email) { this.email = email; }
  public String getPhone() { return phone; }
  public void setPhone(String phone) { this.phone = phone; }
  public String getDepartment() { return department; }
  public void setDepartment(String department) { this.department = department; }
  public String getPosition() { return position; }
  public void setPosition(String position) { this.position = position; }
  public String getAvatarUrl() { return avatarUrl; }
  public void setAvatarUrl(String avatarUrl) { this.avatarUrl = avatarUrl; }
  public String getGender() { return gender; }
  public void setGender(String gender) { this.gender = gender; }
}
