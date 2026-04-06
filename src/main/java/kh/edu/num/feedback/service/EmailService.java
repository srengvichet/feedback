package kh.edu.num.feedback.service;

import jakarta.mail.*;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeBodyPart;
import jakarta.mail.internet.MimeMessage;
import jakarta.mail.internet.MimeMultipart;
import kh.edu.num.feedback.domain.entity.EvaluationWindow;
import kh.edu.num.feedback.domain.entity.UserAccount;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Properties;

@Service
public class EmailService {

    private static final Logger log = LoggerFactory.getLogger(EmailService.class);
    private static final DateTimeFormatter DISPLAY_FMT =
            DateTimeFormatter.ofPattern("dd MMM yyyy, HH:mm");

    @Value("${app.mail.enabled:false}")
    private boolean enabled;

    @Value("${app.mail.from:}")
    private String fromAddress;

    @Value("${spring.mail.host:smtp.gmail.com}")
    private String smtpHost;

    @Value("${spring.mail.port:587}")
    private int smtpPort;

    @Value("${spring.mail.username:}")
    private String smtpUsername;

    @Value("${spring.mail.password:}")
    private String smtpPassword;

    @Value("${app.base-url:http://localhost:8080}")
    private String baseUrl;

    /**
     * Send a self-assessment reminder to every teacher whose email is set.
     * Runs asynchronously so the admin's browser is not blocked.
     */
    @Async
    public void sendPasswordReset(UserAccount user, String resetUrl) {
        if (!enabled) {
            log.info("Mail disabled — skipping password reset email for {}", user.getUsername());
            return;
        }
        if (smtpUsername == null || smtpUsername.isBlank()) {
            log.warn("MAIL_USERNAME not configured — cannot send password reset email");
            return;
        }
        String email = user.getEmail();
        if (email == null || email.isBlank()) return;

        String name = user.getFullName() != null ? user.getFullName() : user.getUsername();
        String from = (fromAddress != null && !fromAddress.isBlank()) ? fromAddress : smtpUsername;

        try {
            Session session = buildSession();
            MimeMessage message = new MimeMessage(session);
            message.setFrom(new InternetAddress(from));
            message.setRecipients(Message.RecipientType.TO, InternetAddress.parse(email));
            message.setSubject("NUM Feedback — Password Reset Request", "UTF-8");

            MimeBodyPart htmlPart = new MimeBodyPart();
            htmlPart.setContent(buildResetHtml(name, resetUrl), "text/html; charset=UTF-8");

            MimeMultipart multipart = new MimeMultipart("alternative");
            multipart.addBodyPart(htmlPart);
            message.setContent(multipart);

            Transport.send(message);
            log.info("Password reset email sent to {}", email);
        } catch (Exception ex) {
            log.error("Failed to send password reset email to {}: {}", email, ex.getMessage());
        }
    }

    private String buildResetHtml(String name, String resetUrl) {
        return """
                <!DOCTYPE html>
                <html lang="en">
                <head><meta charset="UTF-8"><meta name="viewport" content="width=device-width,initial-scale=1"/></head>
                <body style="margin:0;padding:0;background:#f4f6fa;font-family:'Segoe UI',system-ui,sans-serif;">
                <table width="100%%" cellpadding="0" cellspacing="0" style="background:#f4f6fa;padding:40px 0;">
                  <tr><td align="center">
                    <table width="600" cellpadding="0" cellspacing="0"
                           style="background:#ffffff;border-radius:16px;overflow:hidden;box-shadow:0 4px 24px rgba(0,0,0,.08);">
                      <tr>
                        <td style="background:linear-gradient(135deg,#1b2a6b,#2e4fa3);padding:32px 40px;text-align:center;">
                          <p style="margin:0 0 10px;font-size:28px;">🔑</p>
                          <h1 style="margin:0;color:#ffffff;font-size:22px;font-weight:700;">Password Reset</h1>
                          <p style="margin:6px 0 0;color:rgba(255,255,255,.75);font-size:14px;">NUM Feedback System</p>
                        </td>
                      </tr>
                      <tr>
                        <td style="padding:36px 40px;">
                          <p style="margin:0 0 18px;font-size:15px;color:#374151;">Dear <strong>%s</strong>,</p>
                          <p style="margin:0 0 24px;font-size:15px;color:#374151;line-height:1.6;">
                            We received a request to reset your password. Click the button below to set a new password.
                            This link will expire in <strong>30 minutes</strong>.
                          </p>
                          <table width="100%%" cellpadding="0" cellspacing="0" style="margin-bottom:28px;">
                            <tr>
                              <td align="center">
                                <a href="%s"
                                   style="display:inline-block;padding:14px 36px;background:#2563eb;
                                          color:#ffffff;text-decoration:none;font-weight:700;font-size:15px;
                                          border-radius:12px;">
                                  Reset My Password
                                </a>
                              </td>
                            </tr>
                          </table>
                          <p style="margin:0 0 18px;font-size:13px;color:#6b7280;line-height:1.7;word-break:break-all;">
                            If the button does not open, copy and paste this link into your browser:<br>
                            <a href="%s" style="color:#2563eb;text-decoration:none;">%s</a>
                          </p>
                          <p style="margin:0;font-size:13px;color:#9ca3af;line-height:1.6;">
                            If you did not request a password reset, please ignore this email.<br>
                            Do not share this link with anyone.
                          </p>
                        </td>
                      </tr>
                      <tr>
                        <td style="background:#f9fafb;border-top:1px solid #e5e7eb;padding:20px 40px;text-align:center;">
                          <p style="margin:0;font-size:12px;color:#9ca3af;">
                            © NUM Feedback System — National University of Management
                          </p>
                        </td>
                      </tr>
                    </table>
                  </td></tr>
                </table>
                </body>
                </html>
                """.formatted(name, resetUrl, resetUrl, resetUrl);
    }

    @Async
    public void sendSelfAssessmentOpen(List<UserAccount> teachers, EvaluationWindow window) {
        if (!enabled) {
            log.info("Mail disabled — skipping self-assessment notification to {} teachers", teachers.size());
            return;
        }
        if (smtpUsername == null || smtpUsername.isBlank()) {
            log.warn("MAIL_USERNAME not configured — skipping email notifications");
            return;
        }

        Session session = buildSession();

        String semesterName = window.getSemester() != null ? window.getSemester().getName() : "";
        String openAt  = window.getOpenAt()  != null ? window.getOpenAt().format(DISPLAY_FMT)  : "-";
        String closeAt = window.getCloseAt() != null ? window.getCloseAt().format(DISPLAY_FMT) : "-";
        String selfUrl = baseUrl + "/teacher/self";
        String from    = (fromAddress != null && !fromAddress.isBlank()) ? fromAddress : smtpUsername;

        int sent = 0;
        for (UserAccount teacher : teachers) {
            String email = teacher.getEmail();
            if (email == null || email.isBlank()) continue;

            String name = teacher.getFullName() != null ? teacher.getFullName() : teacher.getUsername();
            try {
                MimeMessage message = new MimeMessage(session);
                message.setFrom(new InternetAddress(from));
                message.setRecipients(Message.RecipientType.TO, InternetAddress.parse(email));
                message.setSubject("Action Required: Self-Assessment Open — " + semesterName, "UTF-8");

                MimeBodyPart htmlPart = new MimeBodyPart();
                htmlPart.setContent(buildHtml(name, semesterName, openAt, closeAt, selfUrl), "text/html; charset=UTF-8");

                MimeMultipart multipart = new MimeMultipart("alternative");
                multipart.addBodyPart(htmlPart);
                message.setContent(multipart);

                Transport.send(message);
                sent++;
                log.debug("Self-assessment email sent to {}", email);
            } catch (Exception ex) {
                log.error("Failed to send self-assessment email to {}: {}", email, ex.getMessage());
            }
        }
        log.info("Self-assessment notification sent to {}/{} teachers", sent, teachers.size());
    }

    /**
     * Send a feedback reminder to every student enrolled in the semester.
     * Runs asynchronously so the admin's browser is not blocked.
     */
    @Async
    public void sendFeedbackOpen(List<UserAccount> students, EvaluationWindow window) {
        if (!enabled) {
            log.info("Mail disabled — skipping feedback notification to {} students", students.size());
            return;
        }
        if (smtpUsername == null || smtpUsername.isBlank()) {
            log.warn("MAIL_USERNAME not configured — skipping student email notifications");
            return;
        }

        Session session = buildSession();

        String semesterName = window.getSemester() != null ? window.getSemester().getName() : "";
        String openAt  = window.getOpenAt()  != null ? window.getOpenAt().format(DISPLAY_FMT)  : "-";
        String closeAt = window.getCloseAt() != null ? window.getCloseAt().format(DISPLAY_FMT) : "-";
        String feedbackUrl = baseUrl + "/student/sections";
        String from = (fromAddress != null && !fromAddress.isBlank()) ? fromAddress : smtpUsername;

        int sent = 0;
        for (UserAccount student : students) {
            String email = student.getEmail();
            if (email == null || email.isBlank()) continue;

            String name = student.getFullName() != null ? student.getFullName() : student.getUsername();
            try {
                MimeMessage message = new MimeMessage(session);
                message.setFrom(new InternetAddress(from));
                message.setRecipients(Message.RecipientType.TO, InternetAddress.parse(email));
                message.setSubject("Action Required: Feedback Now Open — " + semesterName, "UTF-8");

                MimeBodyPart htmlPart = new MimeBodyPart();
                htmlPart.setContent(buildStudentHtml(name, semesterName, openAt, closeAt, feedbackUrl),
                        "text/html; charset=UTF-8");

                MimeMultipart multipart = new MimeMultipart("alternative");
                multipart.addBodyPart(htmlPart);
                message.setContent(multipart);

                Transport.send(message);
                sent++;
                log.debug("Feedback email sent to {}", email);
            } catch (Exception ex) {
                log.error("Failed to send feedback email to {}: {}", email, ex.getMessage());
            }
        }
        log.info("Feedback notification sent to {}/{} students", sent, students.size());
    }

    private Session buildSession() {
        Properties props = new Properties();
        props.put("mail.smtp.host", smtpHost);
        props.put("mail.smtp.port", String.valueOf(smtpPort));
        props.put("mail.smtp.auth", "true");
        props.put("mail.smtp.starttls.enable", "true");
        props.put("mail.smtp.starttls.required", "true");

        return Session.getInstance(props, new Authenticator() {
            @Override
            protected PasswordAuthentication getPasswordAuthentication() {
                return new PasswordAuthentication(smtpUsername, smtpPassword);
            }
        });
    }

    private String buildStudentHtml(String name, String semester,
                                     String openAt, String closeAt, String feedbackUrl) {
        return """
                <!DOCTYPE html>
                <html lang="en">
                <head><meta charset="UTF-8"><meta name="viewport" content="width=device-width,initial-scale=1"/></head>
                <body style="margin:0;padding:0;background:#f4f6fa;font-family:'Segoe UI',system-ui,sans-serif;">
                <table width="100%%" cellpadding="0" cellspacing="0" style="background:#f4f6fa;padding:40px 0;">
                  <tr><td align="center">
                    <table width="600" cellpadding="0" cellspacing="0"
                           style="background:#ffffff;border-radius:16px;overflow:hidden;box-shadow:0 4px 24px rgba(0,0,0,.08);">

                      <!-- Header -->
                      <tr>
                        <td style="background:linear-gradient(135deg,#1b2a6b,#2e4fa3);padding:32px 40px;text-align:center;">
                          <p style="margin:0 0 10px;font-size:28px;">⭐</p>
                          <h1 style="margin:0;color:#ffffff;font-size:22px;font-weight:700;letter-spacing:.3px;">
                            Feedback Window Now Open
                          </h1>
                          <p style="margin:6px 0 0;color:rgba(255,255,255,.75);font-size:14px;">%s</p>
                        </td>
                      </tr>

                      <!-- Body -->
                      <tr>
                        <td style="padding:36px 40px;">
                          <p style="margin:0 0 18px;font-size:15px;color:#374151;">
                            Dear <strong>%s</strong>,
                          </p>
                          <p style="margin:0 0 24px;font-size:15px;color:#374151;line-height:1.6;">
                            The <strong>Student Feedback</strong> window is now open for your enrolled subjects.
                            Please log in and rate each of your teachers before the deadline.
                            Your feedback helps improve the quality of teaching at NUM.
                          </p>

                          <!-- Info box -->
                          <table width="100%%" cellpadding="0" cellspacing="0"
                                 style="background:#f0f4ff;border-left:4px solid #f0a500;border-radius:0 10px 10px 0;margin-bottom:28px;">
                            <tr>
                              <td style="padding:18px 20px;">
                                <p style="margin:0 0 8px;font-size:12px;color:#6b7280;font-weight:700;
                                          text-transform:uppercase;letter-spacing:.06em;">Feedback Period</p>
                                <p style="margin:0;font-size:14px;color:#1f2937;">
                                  <strong>Opens:</strong> %s<br>
                                  <strong>Closes:</strong> %s
                                </p>
                              </td>
                            </tr>
                          </table>

                          <!-- CTA button -->
                          <table width="100%%" cellpadding="0" cellspacing="0" style="margin-bottom:28px;">
                            <tr>
                              <td align="center">
                                <a href="%s"
                                   style="display:inline-block;padding:14px 36px;background:#f0a500;
                                          color:#1b2a6b;text-decoration:none;font-weight:700;font-size:15px;
                                          border-radius:12px;letter-spacing:.2px;">
                                  Give Feedback Now
                                </a>
                              </td>
                            </tr>
                          </table>

                          <p style="margin:0;font-size:13px;color:#9ca3af;line-height:1.6;">
                            If you have already submitted feedback for all your subjects, you can ignore this email.<br>
                            Do not reply to this email — it was sent automatically by the NUM Feedback System.
                          </p>
                        </td>
                      </tr>

                      <!-- Footer -->
                      <tr>
                        <td style="background:#f9fafb;border-top:1px solid #e5e7eb;padding:20px 40px;text-align:center;">
                          <p style="margin:0;font-size:12px;color:#9ca3af;">
                            © NUM Feedback System — National University of Management
                          </p>
                        </td>
                      </tr>

                    </table>
                  </td></tr>
                </table>
                </body>
                </html>
                """.formatted(semester, name, openAt, closeAt, feedbackUrl);
    }

    private String buildHtml(String name, String semester,
                              String openAt, String closeAt, String selfUrl) {
        return """
                <!DOCTYPE html>
                <html lang="en">
                <head><meta charset="UTF-8"><meta name="viewport" content="width=device-width,initial-scale=1"/></head>
                <body style="margin:0;padding:0;background:#f4f6fa;font-family:'Segoe UI',system-ui,sans-serif;">
                <table width="100%%" cellpadding="0" cellspacing="0" style="background:#f4f6fa;padding:40px 0;">
                  <tr><td align="center">
                    <table width="600" cellpadding="0" cellspacing="0"
                           style="background:#ffffff;border-radius:16px;overflow:hidden;box-shadow:0 4px 24px rgba(0,0,0,.08);">

                      <!-- Header -->
                      <tr>
                        <td style="background:linear-gradient(135deg,#1b2a6b,#2e4fa3);padding:32px 40px;text-align:center;">
                          <p style="margin:0 0 10px;font-size:28px;">📋</p>
                          <h1 style="margin:0;color:#ffffff;font-size:22px;font-weight:700;letter-spacing:.3px;">
                            Self-Assessment Now Open
                          </h1>
                          <p style="margin:6px 0 0;color:rgba(255,255,255,.75);font-size:14px;">%s</p>
                        </td>
                      </tr>

                      <!-- Body -->
                      <tr>
                        <td style="padding:36px 40px;">
                          <p style="margin:0 0 18px;font-size:15px;color:#374151;">
                            Dear <strong>%s</strong>,
                          </p>
                          <p style="margin:0 0 24px;font-size:15px;color:#374151;line-height:1.6;">
                            The <strong>Week 8 Teacher Self-Assessment</strong> window is now open.
                            Please log in to the Teacher Dashboard and complete your evaluation before the deadline.
                          </p>

                          <!-- Info box -->
                          <table width="100%%" cellpadding="0" cellspacing="0"
                                 style="background:#f0f4ff;border-left:4px solid #2563eb;border-radius:0 10px 10px 0;margin-bottom:28px;">
                            <tr>
                              <td style="padding:18px 20px;">
                                <p style="margin:0 0 8px;font-size:12px;color:#6b7280;font-weight:700;
                                          text-transform:uppercase;letter-spacing:.06em;">Window Period</p>
                                <p style="margin:0;font-size:14px;color:#1f2937;">
                                  <strong>Opens:</strong> %s<br>
                                  <strong>Closes:</strong> %s
                                </p>
                              </td>
                            </tr>
                          </table>

                          <!-- CTA button -->
                          <table width="100%%" cellpadding="0" cellspacing="0" style="margin-bottom:28px;">
                            <tr>
                              <td align="center">
                                <a href="%s"
                                   style="display:inline-block;padding:14px 36px;background:#1b2a6b;
                                          color:#ffffff;text-decoration:none;font-weight:700;font-size:15px;
                                          border-radius:12px;letter-spacing:.2px;">
                                  Complete Self-Assessment
                                </a>
                              </td>
                            </tr>
                          </table>

                          <p style="margin:0;font-size:13px;color:#9ca3af;line-height:1.6;">
                            If you have already submitted your assessment, you can ignore this email.<br>
                            Do not reply to this email — it was sent automatically by the NUM Feedback System.
                          </p>
                        </td>
                      </tr>

                      <!-- Footer -->
                      <tr>
                        <td style="background:#f9fafb;border-top:1px solid #e5e7eb;padding:20px 40px;text-align:center;">
                          <p style="margin:0;font-size:12px;color:#9ca3af;">
                            © NUM Feedback System — National University of Management
                          </p>
                        </td>
                      </tr>

                    </table>
                  </td></tr>
                </table>
                </body>
                </html>
                """.formatted(semester, name, openAt, closeAt, selfUrl);
    }
}
