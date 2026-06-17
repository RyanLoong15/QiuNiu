package com.qiuniu.model;

import java.time.LocalDateTime;

public class DiffAiResult {
    private Integer id;
    private String projectName;
    private String baseBranch;
    private String compareBranch;
    private String diffHash;       // MD5 of diff content
    private String diffContent;    // stored diff (optional, for reference)
    private String aiAnalysis;
    private String incidentCheckResult;
    private String fileType;
    private String modelUsed;
    private String promptUsed;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public DiffAiResult() {}

    public Integer getId() { return id; }
    public void setId(Integer id) { this.id = id; }
    public String getProjectName() { return projectName; }
    public void setProjectName(String projectName) { this.projectName = projectName; }
    public String getBaseBranch() { return baseBranch; }
    public void setBaseBranch(String baseBranch) { this.baseBranch = baseBranch; }
    public String getCompareBranch() { return compareBranch; }
    public void setCompareBranch(String compareBranch) { this.compareBranch = compareBranch; }
    public String getDiffHash() { return diffHash; }
    public void setDiffHash(String diffHash) { this.diffHash = diffHash; }
    public String getDiffContent() { return diffContent; }
    public void setDiffContent(String diffContent) { this.diffContent = diffContent; }
    public String getAiAnalysis() { return aiAnalysis; }
    public void setAiAnalysis(String aiAnalysis) { this.aiAnalysis = aiAnalysis; }
    public String getIncidentCheckResult() { return incidentCheckResult; }
    public void setIncidentCheckResult(String incidentCheckResult) { this.incidentCheckResult = incidentCheckResult; }
    public String getFileType() { return fileType; }
    public void setFileType(String fileType) { this.fileType = fileType; }
    public String getModelUsed() { return modelUsed; }
    public void setModelUsed(String modelUsed) { this.modelUsed = modelUsed; }
    public String getPromptUsed() { return promptUsed; }
    public void setPromptUsed(String promptUsed) { this.promptUsed = promptUsed; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
