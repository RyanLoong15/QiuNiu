package com.qiuniu.model;

public class GitProject {
    private Long id;
    private Long userId;
    private Long teamId;           // 所属团队 ID
    private String teamName;       // 所属团队名称（非持久字段，仅用于展示）
    private String name;
    private String repoUrl;
    private String baseBranch;
    private String compareBranch;
    private String lastSync;
    private String createdAt;
    
    public GitProject() {}
    
    public GitProject(Long userId, Long teamId, String name, String repoUrl, String baseBranch, String compareBranch) {
        this.userId = userId;
        this.teamId = teamId;
        this.name = name;
        this.repoUrl = repoUrl;
        this.baseBranch = baseBranch;
        this.compareBranch = compareBranch;
    }
    
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }

    public Long getTeamId() { return teamId; }
    public void setTeamId(Long teamId) { this.teamId = teamId; }

    public String getTeamName() { return teamName; }
    public void setTeamName(String teamName) { this.teamName = teamName; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    
    public String getRepoUrl() { return repoUrl; }
    public void setRepoUrl(String repoUrl) { this.repoUrl = repoUrl; }
    
    public String getBaseBranch() { return baseBranch; }
    public void setBaseBranch(String baseBranch) { this.baseBranch = baseBranch; }
    
    public String getCompareBranch() { return compareBranch; }
    public void setCompareBranch(String compareBranch) { this.compareBranch = compareBranch; }
    
    public String getLastSync() { return lastSync; }
    public void setLastSync(String lastSync) { this.lastSync = lastSync; }

    public String getCreatedAt() { return createdAt; }
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }
}
