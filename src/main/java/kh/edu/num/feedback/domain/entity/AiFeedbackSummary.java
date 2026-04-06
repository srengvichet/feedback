package kh.edu.num.feedback.domain.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.List;

@Entity
@Table(name = "ai_feedback_summaries")
public class AiFeedbackSummary {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "section_id", nullable = false, unique = true)
    private Long sectionId;

    @Column(columnDefinition = "TEXT")
    private String summary;

    // Store as comma-separated string
    @Column(columnDefinition = "TEXT")
    private String strengths;

    @Column(columnDefinition = "TEXT")
    private String improvements;

    @Column(columnDefinition = "TEXT")
    private String recommendation;

    // Khmer translations
    @Column(name = "khmer_summary", columnDefinition = "TEXT")
    private String khmerSummary;

    @Column(name = "khmer_strengths", columnDefinition = "TEXT")
    private String khmerStrengths;

    @Column(name = "khmer_improvements", columnDefinition = "TEXT")
    private String khmerImprovements;

    @Column(name = "khmer_recommendation", columnDefinition = "TEXT")
    private String khmerRecommendation;

    @Column(name = "generated_at")
    private LocalDateTime generatedAt;

    @PrePersist
    protected void onCreate() {
        generatedAt = LocalDateTime.now();
    }

    // ── Helpers to convert List <-> String ──────────────────
    public List<String> getStrengthsList() {
        return strengths == null || strengths.isBlank()
                ? List.of()
                : List.of(strengths.split("\\|\\|"));
    }

    public void setStrengthsList(List<String> list) {
        this.strengths = list == null ? "" : String.join("||", list);
    }

    public List<String> getImprovementsList() {
        return improvements == null || improvements.isBlank()
                ? List.of()
                : List.of(improvements.split("\\|\\|"));
    }

    public void setImprovementsList(List<String> list) {
        this.improvements = list == null ? "" : String.join("||", list);
    }

    public List<String> getKhmerStrengthsList() {
        return khmerStrengths == null || khmerStrengths.isBlank()
                ? List.of()
                : List.of(khmerStrengths.split("\\|\\|"));
    }

    public void setKhmerStrengthsList(List<String> list) {
        this.khmerStrengths = list == null ? "" : String.join("||", list);
    }

    public List<String> getKhmerImprovementsList() {
        return khmerImprovements == null || khmerImprovements.isBlank()
                ? List.of()
                : List.of(khmerImprovements.split("\\|\\|"));
    }

    public void setKhmerImprovementsList(List<String> list) {
        this.khmerImprovements = list == null ? "" : String.join("||", list);
    }

    // ── Getters & Setters ────────────────────────────────────
    public Long getId() { return id; }
    public Long getSectionId() { return sectionId; }
    public void setSectionId(Long sectionId) { this.sectionId = sectionId; }
    public String getSummary() { return summary; }
    public void setSummary(String summary) { this.summary = summary; }
    public String getStrengths() { return strengths; }
    public void setStrengths(String strengths) { this.strengths = strengths; }
    public String getImprovements() { return improvements; }
    public void setImprovements(String improvements) { this.improvements = improvements; }
    public String getRecommendation() { return recommendation; }
    public void setRecommendation(String recommendation) { this.recommendation = recommendation; }
    public String getKhmerSummary() { return khmerSummary; }
    public void setKhmerSummary(String khmerSummary) { this.khmerSummary = khmerSummary; }
    public String getKhmerStrengths() { return khmerStrengths; }
    public void setKhmerStrengths(String khmerStrengths) { this.khmerStrengths = khmerStrengths; }
    public String getKhmerImprovements() { return khmerImprovements; }
    public void setKhmerImprovements(String khmerImprovements) { this.khmerImprovements = khmerImprovements; }
    public String getKhmerRecommendation() { return khmerRecommendation; }
    public void setKhmerRecommendation(String khmerRecommendation) { this.khmerRecommendation = khmerRecommendation; }
    public LocalDateTime getGeneratedAt() { return generatedAt; }
    public void setGeneratedAt(LocalDateTime generatedAt) { this.generatedAt = generatedAt; }
}
