package kh.edu.num.feedback.domain.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "class_sections", uniqueConstraints = {
        @UniqueConstraint(columnNames = {
                "semester_id",
                "cohort_id",
                "group_no",
                "shift_time",
                "building",
                "room"
        })
})
public class ClassSection {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "semester_id", nullable = false)
    private Semester semester;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cohort_id")
    private Cohort cohort;

    // ✅ course/teacher are OPTIONAL now (do NOT force not-null)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "course_id")
    private Course course;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "teacher_user_id")
    private UserAccount teacher;

    @Column(name = "group_no")
    private Integer groupNo;

    @Enumerated(EnumType.STRING)
    @Column(name = "shift_time", nullable = false, length = 30)
    private ShiftTime shiftTime;

    @Column(nullable = true, length = 10)
    private String building;

    @Column(nullable = true, length = 20)
    private String room;

    @Column(name = "section_name", nullable = true, length = 50)
    private String sectionName;

    // ================= GETTERS & SETTERS =================

    /**
     * Returns a clean section label for display, e.g. "C34 / G79".
     * Strips the shift-time suffix that is sometimes embedded in sectionName
     * (e.g. "C34-G79-EARLY_AFTERNOON" → "C34 / G79").
     */
    @Transient
    public String getCleanSectionLabel() {
        if (sectionName == null || sectionName.isBlank()) {
            String coh = (cohort != null) ? "C" + cohort.getCohortNo() : "";
            String grp = (groupNo != null) ? "/G" + groupNo : "";
            return coh.isBlank() && grp.isBlank() ? "—" : coh + grp;
        }
        String clean = sectionName;
        for (ShiftTime st : ShiftTime.values()) {
            clean = clean.replace("-" + st.name(), "").replace(st.name() + "-", "");
        }
        return clean.replace("-", " / ").trim();
    }

    @Transient
    public String getDisplayLabel() {
        String sem = (semester != null) ? semester.getName() : "-";
        String coh = (cohort != null) ? (cohort.getLabel() != null ? cohort.getLabel() : ("Cohort#" + cohort.getId()))
                : "-";
        String grp = (groupNo != null) ? ("Group#" + groupNo) : "-";
        String sh = (shiftTime != null) ? shiftTime.name() : "-";
        String bld = (building != null && !building.isBlank()) ? building.trim() : "";
        String rm = (room != null && !room.isBlank()) ? room.trim() : "";
        String loc = (bld.isBlank() && rm.isBlank()) ? "-" : (bld + (rm.isBlank() ? "" : ("-" + rm)));

        String secName = (sectionName != null && !sectionName.isBlank()) ? (" | " + sectionName.trim()) : "";

        // Example: 2025-2026 S2 | Cohort15 | G2 | MORNING | B1-A203 | SectionA
        return sem + " | " + coh + " | " + grp + " | " + sh + " | " + loc + secName;
    }

    public Long getId() {
        return id;
    }

    public Semester getSemester() {
        return semester;
    }

    public void setSemester(Semester semester) {
        this.semester = semester;
    }

    public Cohort getCohort() {
        return cohort;
    }

    public void setCohort(Cohort cohort) {
        this.cohort = cohort;
    }

    public Course getCourse() {
        return course;
    }

    public void setCourse(Course course) {
        this.course = course;
    }

    public UserAccount getTeacher() {
        return teacher;
    }

    public void setTeacher(UserAccount teacher) {
        this.teacher = teacher;
    }

    public Integer getGroupNo() {
        return groupNo;
    }

    public void setGroupNo(Integer groupNo) {
        this.groupNo = groupNo;
    }

    public ShiftTime getShiftTime() {
        return shiftTime;
    }

    public void setShiftTime(ShiftTime shiftTime) {
        this.shiftTime = shiftTime;
    }

    public String getBuilding() {
        return building;
    }

    public void setBuilding(String building) {
        this.building = building;
    }

    public String getRoom() {
        return room;
    }

    public void setRoom(String room) {
        this.room = room;
    }

    public String getSectionName() {
        return sectionName;
    }

    public void setSectionName(String sectionName) {
        this.sectionName = sectionName;
    }
}