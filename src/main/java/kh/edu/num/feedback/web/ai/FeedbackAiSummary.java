package kh.edu.num.feedback.web.ai;

import java.util.List;

public class FeedbackAiSummary {
    private String summary;
    private List<String> strengths;
    private List<String> improvements;
    private String recommendation;

    // Constructors
    public FeedbackAiSummary() {}

    public FeedbackAiSummary(String summary, List<String> strengths,
                              List<String> improvements, String recommendation) {
        this.summary = summary;
        this.strengths = strengths;
        this.improvements = improvements;
        this.recommendation = recommendation;
    }

    // Getters & Setters
    public String getSummary() { return summary; }
    public void setSummary(String summary) { this.summary = summary; }

    public List<String> getStrengths() { return strengths; }
    public void setStrengths(List<String> strengths) { this.strengths = strengths; }

    public List<String> getImprovements() { return improvements; }
    public void setImprovements(List<String> improvements) { this.improvements = improvements; }

    public String getRecommendation() { return recommendation; }
    public void setRecommendation(String recommendation) { this.recommendation = recommendation; }
}