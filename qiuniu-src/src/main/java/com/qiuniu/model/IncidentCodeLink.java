package com.qiuniu.model;

import java.sql.Timestamp;

/**
 * IncidentCodeLink Model - 事故代码关联实体类
 * 
 * 对应数据库表：incident_code_links
 * 用于记录事故相关的代码仓、文件路径、行号范围等信息
 * 
 * @author Loong
 * @version 1.0
 * @date 2026-06-08
 */
public class IncidentCodeLink {
    
    /** 主键 ID */
    private Integer id;
    
    /** 关联的事故 ID（外键） */
    private Integer incidentId;
    
    /** 代码仓名称（如 qiuniu-src） */
    private String repoName;
    
    /** 代码仓地址（可选） */
    private String repoUrl;
    
    /** 文件路径（如 src/main/java/com/qiuniu/dao/UserDAO.java） */
    private String filePath;
    
    /** 起始行号（可选） */
    private Integer lineStart;
    
    /** 结束行号（可选） */
    private Integer lineEnd;
    
    /** 问题代码片段 */
    private String codeSnippet;
    
    /** 修复代码片段 */
    private String fixSnippet;
    
    /** 创建时间 */
    private Timestamp createdAt;
    
    // ========== 构造函数 ==========
    
    /** 无参构造函数 */
    public IncidentCodeLink() {
    }
    
    /** 全参构造函数 */
    public IncidentCodeLink(Integer id, Integer incidentId, String repoName, 
                          String repoUrl, String filePath, Integer lineStart,
                          Integer lineEnd, String codeSnippet, String fixSnippet,
                          Timestamp createdAt) {
        this.id = id;
        this.incidentId = incidentId;
        this.repoName = repoName;
        this.repoUrl = repoUrl;
        this.filePath = filePath;
        this.lineStart = lineStart;
        this.lineEnd = lineEnd;
        this.codeSnippet = codeSnippet;
        this.fixSnippet = fixSnippet;
        this.createdAt = createdAt;
    }
    
    // ========== Getters & Setters ==========
    
    public Integer getId() {
        return id;
    }
    
    public void setId(Integer id) {
        this.id = id;
    }
    
    public Integer getIncidentId() {
        return incidentId;
    }
    
    public void setIncidentId(Integer incidentId) {
        this.incidentId = incidentId;
    }
    
    public String getRepoName() {
        return repoName;
    }
    
    public void setRepoName(String repoName) {
        this.repoName = repoName;
    }
    
    public String getRepoUrl() {
        return repoUrl;
    }
    
    public void setRepoUrl(String repoUrl) {
        this.repoUrl = repoUrl;
    }
    
    public String getFilePath() {
        return filePath;
    }
    
    public void setFilePath(String filePath) {
        this.filePath = filePath;
    }
    
    public Integer getLineStart() {
        return lineStart;
    }
    
    public void setLineStart(Integer lineStart) {
        this.lineStart = lineStart;
    }
    
    public Integer getLineEnd() {
        return lineEnd;
    }
    
    public void setLineEnd(Integer lineEnd) {
        this.lineEnd = lineEnd;
    }
    
    public String getCodeSnippet() {
        return codeSnippet;
    }
    
    public void setCodeSnippet(String codeSnippet) {
        this.codeSnippet = codeSnippet;
    }
    
    public String getFixSnippet() {
        return fixSnippet;
    }
    
    public void setFixSnippet(String fixSnippet) {
        this.fixSnippet = fixSnippet;
    }
    
    public Timestamp getCreatedAt() {
        return createdAt;
    }
    
    public void setCreatedAt(Timestamp createdAt) {
        this.createdAt = createdAt;
    }
    
    // ========== 辅助方法 ==========
    
    @Override
    public String toString() {
        return "IncidentCodeLink{" +
                "id=" + id +
                ", incidentId=" + incidentId +
                ", repoName='" + repoName + '\'' +
                ", repoUrl='" + repoUrl + '\'' +
                ", filePath='" + filePath + '\'' +
                ", lineStart=" + lineStart +
                ", lineEnd=" + lineEnd +
                ", createdAt=" + createdAt +
                '}';
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        IncidentCodeLink that = (IncidentCodeLink) o;
        return id != null ? id.equals(that.id) : that.id == null;
    }
    
    @Override
    public int hashCode() {
        return id != null ? id.hashCode() : 0;
    }
}
