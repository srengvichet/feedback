package kh.edu.num.feedback.web.teacher.dto;

public class QuestionStat {
  private final Long questionId;
  private final String questionText;
  private final Double avgScore;
  private final Integer minScore;
  private final Integer maxScore;
  private final Long count;

  // Backward compatible (old 4-arg)
  public QuestionStat(Long questionId, String questionText, Double avgScore, Long count) {
    this(questionId, questionText, avgScore, null, null, count);
  }

  // New 6-arg (used by the query)
  public QuestionStat(Long questionId, String questionText,
                      Double avgScore, Integer minScore, Integer maxScore, Long count) {
    this.questionId = questionId;
    this.questionText = questionText;
    this.avgScore = avgScore;
    this.minScore = minScore;
    this.maxScore = maxScore;
    this.count = count;
  }

  public Long getQuestionId() { return questionId; }
  public String getQuestionText() { return questionText; }
  public Double getAvgScore() { return avgScore; }
  public Integer getMinScore() { return minScore; }
  public Integer getMaxScore() { return maxScore; }
  public Long getCount() { return count; }
}
