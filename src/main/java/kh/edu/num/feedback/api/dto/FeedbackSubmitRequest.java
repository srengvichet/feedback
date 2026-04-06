package kh.edu.num.feedback.api.dto;

import java.util.Map;

public class FeedbackSubmitRequest {
    /** Map of questionId -> answer value (numeric as string for RATING, text for TEXT) */
    private Map<Long, String> answers;

    public Map<Long, String> getAnswers() { return answers; }
    public void setAnswers(Map<Long, String> answers) { this.answers = answers; }
}
