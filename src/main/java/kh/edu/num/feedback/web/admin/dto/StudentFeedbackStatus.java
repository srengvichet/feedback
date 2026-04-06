package kh.edu.num.feedback.web.admin.dto;

// package kh.edu.num.feedback.domain.dto;

import java.time.LocalDateTime;

public interface StudentFeedbackStatus {

    Long getStudentId();

    String getStudentUsername();

    String getFullName();

    String getEmail();

    LocalDateTime getSubmittedAt();
}