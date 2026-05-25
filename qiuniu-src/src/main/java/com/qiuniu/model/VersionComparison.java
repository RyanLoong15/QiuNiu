package com.qiuniu.model;

import java.sql.Timestamp;

public class VersionComparison {
    private Long id;
    private Long userId;
    private String versionName;       // e.g. "V260417"
    private String baseBranch;        // e.g. "master"
    private String compareBranch;     // e.g. "V260417"
    private Integer totalRepos;      // 总代码仓数
    private Integer successCount;
    private Integer failCount;
    private String status;            // PENDING / RUNNING / DONE / FAILED
    private String createdAt;
    private Timestamp finishedAt;

    public VersionComparison() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }

    public String getVersionName() { return versionName; }
    public void setVersionName(String versionName) { this.versionName = versionName; }

    public String getBaseBranch() { return baseBranch; }
    public void setBaseBranch(String baseBranch) { this.baseBranch = baseBranch; }

    public String getCompareBranch() { return compareBranch; }
    public void setCompareBranch(String compareBranch) { this.compareBranch = compareBranch; }

    public Integer getTotalRepos() { return totalRepos; }
    public void setTotalRepos(Integer totalRepos) { this.totalRepos = totalRepos; }

    public Integer getSuccessCount() { return successCount; }
    public void setSuccessCount(Integer successCount) { this.successCount = successCount; }

    public Integer getFailCount() { return failCount; }
    public void setFailCount(Integer failCount) { this.failCount = failCount; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getCreatedAt() { return createdAt; }
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }

    public Timestamp getFinishedAt() { return finishedAt; }
    public void setFinishedAt(Timestamp finishedAt) { this.finishedAt = finishedAt; }
}
