package kh.edu.num.feedback.api.dto;

import java.time.LocalDateTime;

public class SectionDto {
    private Long sectionId;
    private String semester;
    private String courseCode;
    private String courseName;
    private String teacherName;
    private String shiftTime;
    private String room;
    private String sectionName;
    private boolean windowOpen;
    private boolean alreadySubmitted;
    private LocalDateTime submittedAt;
    private Long enrolled;
    private Long responseCount;

    public Long getSectionId() { return sectionId; }
    public void setSectionId(Long sectionId) { this.sectionId = sectionId; }
    public String getSemester() { return semester; }
    public void setSemester(String semester) { this.semester = semester; }
    public String getCourseCode() { return courseCode; }
    public void setCourseCode(String courseCode) { this.courseCode = courseCode; }
    public String getCourseName() { return courseName; }
    public void setCourseName(String courseName) { this.courseName = courseName; }
    public String getTeacherName() { return teacherName; }
    public void setTeacherName(String teacherName) { this.teacherName = teacherName; }
    public String getShiftTime() { return shiftTime; }
    public void setShiftTime(String shiftTime) { this.shiftTime = shiftTime; }
    public String getRoom() { return room; }
    public void setRoom(String room) { this.room = room; }
    public String getSectionName() { return sectionName; }
    public void setSectionName(String sectionName) { this.sectionName = sectionName; }
    public boolean isWindowOpen() { return windowOpen; }
    public void setWindowOpen(boolean windowOpen) { this.windowOpen = windowOpen; }
    public boolean isAlreadySubmitted() { return alreadySubmitted; }
    public void setAlreadySubmitted(boolean alreadySubmitted) { this.alreadySubmitted = alreadySubmitted; }
    public LocalDateTime getSubmittedAt() { return submittedAt; }
    public void setSubmittedAt(LocalDateTime submittedAt) { this.submittedAt = submittedAt; }
    public Long getEnrolled() { return enrolled; }
    public void setEnrolled(Long enrolled) { this.enrolled = enrolled; }
    public Long getResponseCount() { return responseCount; }
    public void setResponseCount(Long responseCount) { this.responseCount = responseCount; }
}
