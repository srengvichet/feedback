package kh.edu.num.feedback.domain.entity;

public enum ShiftTime {
  MORNING("Morning (7–10AM)"),
  EARLY_AFTERNOON("Early Afternoon (10:30AM–1:30PM)"),
  AFTERNOON("Afternoon (2–5PM)"),
  EVENING("Evening (5:30–8:30PM)");

  private final String label;
  ShiftTime(String label) { this.label = label; }
  public String getLabel() { return label; }
}
