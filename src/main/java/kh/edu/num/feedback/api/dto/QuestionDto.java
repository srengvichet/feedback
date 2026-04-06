package kh.edu.num.feedback.api.dto;

public class QuestionDto {
    private Long id;
    private int orderNo;
    private String text;
    private String type;   // RATING or TEXT
    private Integer scaleMin;
    private Integer scaleMax;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public int getOrderNo() { return orderNo; }
    public void setOrderNo(int orderNo) { this.orderNo = orderNo; }
    public String getText() { return text; }
    public void setText(String text) { this.text = text; }
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    public Integer getScaleMin() { return scaleMin; }
    public void setScaleMin(Integer scaleMin) { this.scaleMin = scaleMin; }
    public Integer getScaleMax() { return scaleMax; }
    public void setScaleMax(Integer scaleMax) { this.scaleMax = scaleMax; }
}
