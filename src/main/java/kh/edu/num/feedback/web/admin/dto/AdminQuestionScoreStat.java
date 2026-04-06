package kh.edu.num.feedback.web.admin.dto;

public record AdminQuestionScoreStat(
    Integer orderNo,
    String questionText,
    Double avgScore,
    Integer minScore,
    Integer maxScore,
    Long n
) {}
