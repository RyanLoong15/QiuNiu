package com.qiuniu.model;

/**
 * 全量代码分析报告
 */
public class CodeAnalysisReport {
    private Long id;
    private Long projectId;
    private String projectName;
    private Long teamId;
    private Long userId;
    private String repoUrl;
    private String branch;
    private String status;          // PENDING / RUNNING / DONE / FAILED
    private int totalFiles;         // 代码仓总文件数
    private int analyzedFiles;      // 已分析文件数
    private int totalIssues;        // 发现的问题总数
    private String report;          // AI 生成的最终分析报告
    private String ruleSet;         // 使用的代码规范集，如 "alibaba-java" / "google-java" / "default"
    private String createdAt;
    private String finishedAt;
    private long costMs;            // 耗时毫秒

    public CodeAnalysisReport() {}

    public CodeAnalysisReport(Long projectId, Long userId, String ruleSet) {
        this.projectId = projectId;
        this.userId = userId;
        this.ruleSet = ruleSet != null ? ruleSet : "default";
        this.status = "PENDING";
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getProjectId() { return projectId; }
    public void setProjectId(Long projectId) { this.projectId = projectId; }

    public String getProjectName() { return projectName; }
    public void setProjectName(String projectName) { this.projectName = projectName; }

    public Long getTeamId() { return teamId; }
    public void setTeamId(Long teamId) { this.teamId = teamId; }

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }

    public String getRepoUrl() { return repoUrl; }
    public void setRepoUrl(String repoUrl) { this.repoUrl = repoUrl; }

    public String getBranch() { return branch; }
    public void setBranch(String branch) { this.branch = branch; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public int getTotalFiles() { return totalFiles; }
    public void setTotalFiles(int totalFiles) { this.totalFiles = totalFiles; }

    public int getAnalyzedFiles() { return analyzedFiles; }
    public void setAnalyzedFiles(int analyzedFiles) { this.analyzedFiles = analyzedFiles; }

    public int getTotalIssues() { return totalIssues; }
    public void setTotalIssues(int totalIssues) { this.totalIssues = totalIssues; }

    public String getReport() { return report; }
    public void setReport(String report) { this.report = report; }

    public String getRuleSet() { return ruleSet; }
    public void setRuleSet(String ruleSet) { this.ruleSet = ruleSet; }

    public String getCreatedAt() { return createdAt; }
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }

    public String getFinishedAt() { return finishedAt; }
    public void setFinishedAt(String finishedAt) { this.finishedAt = finishedAt; }

    public long getCostMs() { return costMs; }
    public void setCostMs(long costMs) { this.costMs = costMs; }
}
