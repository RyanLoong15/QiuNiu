package com.qiuniu.model;

public class GitTeam {
    private Long id;
    private Long userId;          // 创建者（管理员）
    private String name;           // 团队名称，如"交易线团队"
    private String code;           // 团队代码，如"trade"
    private String description;    // 团队描述
    private String createdAt;

    public GitTeam() {}

    public GitTeam(Long userId, String name, String code, String description) {
        this.userId = userId;
        this.name = name;
        this.code = code;
        this.description = description;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getCreatedAt() { return createdAt; }
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }
}
