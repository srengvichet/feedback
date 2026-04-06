package kh.edu.num.feedback.web.auth;

public class RegisterForm {
  private String studentLogin;
  private String password;
  private String confirmPassword;

  public String getStudentLogin() { return studentLogin; }
  public void setStudentLogin(String studentLogin) { this.studentLogin = studentLogin; }

  public String getPassword() { return password; }
  public void setPassword(String password) { this.password = password; }

  public String getConfirmPassword() { return confirmPassword; }
  public void setConfirmPassword(String confirmPassword) { this.confirmPassword = confirmPassword; }
}
