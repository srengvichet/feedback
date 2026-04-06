package kh.edu.num.feedback.web.admin.dto;

import kh.edu.num.feedback.domain.entity.ShiftTime;

import java.time.LocalDateTime;

public record AdminSectionReportRow(
    Long sectionId,
    String semesterName,
    String courseCode,
    String courseName,
    String teacherUsername,
    ShiftTime shiftTime,
    String building,
    String room,
    String sectionName,

    Double avgCatA,   // Q1–Q10
    Double avgCatB,   // Q11–Q13
    Double avgCatC,   // Q14–Q18
    Double avgCatD,   // Q19–Q21
    Double avgCatE,   // Q22–Q26

    Double overallAvg, // Q1–Q26 (or all rating questions)
    Long responses,
    LocalDateTime submittedAt
) {}
