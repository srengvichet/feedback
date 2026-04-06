package kh.edu.num.feedback.web.admin;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import kh.edu.num.feedback.domain.entity.EvaluationKind;
import kh.edu.num.feedback.domain.entity.QuestionType;

public class QuestionForm {

  @NotNull
  private EvaluationKind kind;

  @NotNull
  private QuestionType type;

  @NotBlank
  private String text;

  private Integer scaleMin; // required if type=RATING
  private Integer scaleMax; // required if type=RATING

  @NotNull
  private Integer orderNo;

  private boolean active = true;

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
