package kh.edu.num.feedback.domain.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "questions")
public class Question {

  @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 30)
  private EvaluationKind kind; // STUDENT_FEEDBACK or TEACHER_SELF

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private QuestionType type; // RATING or TEXT

  @Column(nullable = false, length = 500)
  private String text;

  @Column(nullable = true)
  private Integer scaleMin;

  @Column(nullable = true)
  private Integer scaleMax;

  @Column(nullable = false)
  private Integer orderNo = 0;

  @Column(nullable = false)
  private boolean active = true;

  public Long getId() { return id; }
  public EvaluationKind getKind() { return kind; }
  public void setKind(EvaluationKind kind) { this.kind = kind; }
  public QuestionType getType() { return type; }
  public void setType(QuestionType type) { this.type = type; }
  public String getText() { return text; }
  public void setText(String text) { this.text = text; }
  public Integer getScaleMin() { return scaleMin; }
  public void setScaleMin(Integer scaleMin) { this.scaleMin = scaleMin; }
  public Integer getScaleMax() { return scaleMax; }
  public void setScaleMax(Integer scaleMax) { this.scaleMax = scaleMax; }
  public Integer getOrderNo() { return orderNo; }
  public void setOrderNo(Integer orderNo) { this.orderNo = orderNo; }
  public boolean isActive() { return active; }
  public void setActive(boolean active) { this.active = active; }
}
