package com.qiuniu.model;

/**
 * 代码分析提示词模板
 * 存储于数据库，按文件扩展名匹配
 */
public class PromptAnalysis {
    private Long id;
    private String fileType;       // 扩展名，如 java / sql / js / py
    private String displayName;     // 显示名称，如 "Java 代码风险分析"
    private String systemPrompt;    // 系统提示词（AI 角色定义）
    private String userPromptTemplate; // 用户提示词模板，{diff} 会被替换为实际 diff 内容
    private Integer maxDiffLines;  // 最多传入多少行 diff（防止 token 超限）
    private Integer priority;      // 优先级，数字越小优先级越高
    private String createdAt;

    public PromptAnalysis() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getFileType() { return fileType; }
    public void setFileType(String fileType) { this.fileType = fileType; }

    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }

    public String getSystemPrompt() { return systemPrompt; }
    public void setSystemPrompt(String systemPrompt) { this.systemPrompt = systemPrompt; }

    public String getUserPromptTemplate() { return userPromptTemplate; }
    public void setUserPromptTemplate(String userPromptTemplate) { this.userPromptTemplate = userPromptTemplate; }

    public Integer getMaxDiffLines() { return maxDiffLines; }
    public void setMaxDiffLines(Integer maxDiffLines) { this.maxDiffLines = maxDiffLines; }

    public Integer getPriority() { return priority; }
    public void setPriority(Integer priority) { this.priority = priority; }

    public String getCreatedAt() { return createdAt; }
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }
}
