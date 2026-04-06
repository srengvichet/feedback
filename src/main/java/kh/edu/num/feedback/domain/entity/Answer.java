package kh.edu.num.feedback.domain.entity;

import jakarta.persistence.*;

@Entity
@Table(
  name = "answers",
  uniqueConstraints = @UniqueConstraint(
    name = "uk_answer_submission_question",
    columnNames = {"submission_id","question_id"}
  )
)
public class Answer {

  @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(optional = false, fetch = FetchType.LAZY)
  @JoinColumn(name = "submission_id", nullable = false)
  private Submission submission;

  @ManyToOne(optional = false, fetch = FetchType.LAZY)
  @JoinColumn(name = "question_id", nullable = false)
  private Question question;

  @Column(nullable = true)
  private Integer numericValue;

  @Column(nullable = true, length = 2000)
  private String textValue;

  public Long getId() { return id; }
  public Submission getSubmission() { return submission; }
  public void setSubmission(Submission submission) { this.submission = submission; }
  public Question getQuestion() { return question; }
  public void setQuestion(Question question) { this.question = question; }
  public Integer getNumericValue() { return numericValue; }
  public void setNumericValue(Integer numericValue) { this.numericValue = numericValue; }
  public String getTextValue() { return textValue; }
  public void setTextValue(String textValue) { this.textValue = textValue; }
}
