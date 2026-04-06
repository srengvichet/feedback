package kh.edu.num.feedback.web.admin.dto;

public record AdminTeacherSummaryRow(
    String teacherUsername,
    Long responses,
    Long enrolled,
    Double responseRate, // percentage, e.g. 62.50
    Double avgCatA,
    Double avgCatB,
    Double avgCatC,
    Double avgCatD,
    Double avgCatE,
    Double overallAvg
) {}
