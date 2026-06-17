package com.qiuniu.model;

import java.math.BigDecimal;
import java.sql.Timestamp;

/**
 * 检核历史记录实体类
 */
public class IncidentCheckHistory {
    private int id;
    private String checkType;       // code_analysis / version_compare
    private String targetId;
    private int incidentId;
    private BigDecimal similarity;
    private Timestamp matchedAt;

    public IncidentCheckHistory() {}

    // Getters and Setters
    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public String getCheckType() { return checkType; }
    public void setCheckType(String checkType) { this.checkType = checkType; }

    public String getTargetId() { return targetId; }
    public void setTargetId(String targetId) { this.targetId = targetId; }

    public int getIncidentId() { return incidentId; }
    public void setIncidentId(int incidentId) { this.incidentId = incidentId; }

    public BigDecimal getSimilarity() { return similarity; }
    public void setSimilarity(BigDecimal similarity) { this.similarity = similarity; }

    public Timestamp getMatchedAt() { return matchedAt; }
    public void setMatchedAt(Timestamp matchedAt) { this.matchedAt = matchedAt; }
}
