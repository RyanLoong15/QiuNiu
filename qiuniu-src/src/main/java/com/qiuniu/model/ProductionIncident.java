package com.qiuniu.model;

import java.sql.Timestamp;

/**
 * 生产事故实体类
 */
public class ProductionIncident {
    private int id;
    private String incidentNo;
    private String title;
    private String severity;        // P1/P2/P3/P4
    private String incidentType;
    private Timestamp occurrenceTime;
    private Timestamp discoveryTime;
    private Timestamp resolutionTime;
    private String affectedSystems;
    private String affectedModules;
    private String rootCause;
    private String solution;
    private String lessonsLearned;
    private String codePatterns;
    private String filePaths;
    private String keywords;
    private String status;          // active/resolved/archived
    private boolean isRestricted;
    private String createdBy;
    private Timestamp createdAt;
    private Timestamp updatedAt;

    public ProductionIncident() {}

    // Getters and Setters
    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public String getIncidentNo() { return incidentNo; }
    public void setIncidentNo(String incidentNo) { this.incidentNo = incidentNo; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getSeverity() { return severity; }
    public void setSeverity(String severity) { this.severity = severity; }

    public String getIncidentType() { return incidentType; }
    public void setIncidentType(String incidentType) { this.incidentType = incidentType; }

    public Timestamp getOccurrenceTime() { return occurrenceTime; }
    public void setOccurrenceTime(Timestamp occurrenceTime) { this.occurrenceTime = occurrenceTime; }

    public Timestamp getDiscoveryTime() { return discoveryTime; }
    public void setDiscoveryTime(Timestamp discoveryTime) { this.discoveryTime = discoveryTime; }

    public Timestamp getResolutionTime() { return resolutionTime; }
    public void setResolutionTime(Timestamp resolutionTime) { this.resolutionTime = resolutionTime; }

    public String getAffectedSystems() { return affectedSystems; }
    public void setAffectedSystems(String affectedSystems) { this.affectedSystems = affectedSystems; }

    public String getAffectedModules() { return affectedModules; }
    public void setAffectedModules(String affectedModules) { this.affectedModules = affectedModules; }

    public String getRootCause() { return rootCause; }
    public void setRootCause(String rootCause) { this.rootCause = rootCause; }

    public String getSolution() { return solution; }
    public void setSolution(String solution) { this.solution = solution; }

    public String getLessonsLearned() { return lessonsLearned; }
    public void setLessonsLearned(String lessonsLearned) { this.lessonsLearned = lessonsLearned; }

    public String getCodePatterns() { return codePatterns; }
    public void setCodePatterns(String codePatterns) { this.codePatterns = codePatterns; }

    public String getFilePaths() { return filePaths; }
    public void setFilePaths(String filePaths) { this.filePaths = filePaths; }

    public String getKeywords() { return keywords; }
    public void setKeywords(String keywords) { this.keywords = keywords; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public boolean isRestricted() { return isRestricted; }
    public void setRestricted(boolean restricted) { isRestricted = restricted; }

    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }

    public Timestamp getCreatedAt() { return createdAt; }
    public void setCreatedAt(Timestamp createdAt) { this.createdAt = createdAt; }

    public Timestamp getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Timestamp updatedAt) { this.updatedAt = updatedAt; }
}
