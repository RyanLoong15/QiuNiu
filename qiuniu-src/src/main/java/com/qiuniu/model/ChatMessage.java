package com.qiuniu.model;

import java.util.Date;
import java.util.List;
import java.util.ArrayList;

/**
 * 聊天消息模型
 * 用于存储多轮对话中的单条消息
 */
public class ChatMessage {
    
    private Long id;
    private String sessionId;          // 所属会话ID（UUID字符串）
    private String role;              // 角色：user / assistant
    private String content;           // 消息内容
    private Date timestamp;           // 消息时间戳
    
    // 仅assistant消息使用
    private String sources;           // 来源文档（JSON格式）
    private Integer latencyMs;        // 响应延迟（毫秒）
    
    public ChatMessage() {}
    
    public ChatMessage(String role, String content) {
        this.role = role;
        this.content = content;
        this.timestamp = new Date();
    }
    
    // Getters and Setters
    
    public Long getId() {
        return id;
    }
    
    public void setId(Long id) {
        this.id = id;
    }
    
    public String getSessionId() {
        return sessionId;
    }
    
    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }
    
    public String getRole() {
        return role;
    }
    
    public void setRole(String role) {
        this.role = role;
    }
    
    public String getContent() {
        return content;
    }
    
    public void setContent(String content) {
        this.content = content;
    }
    
    public Date getTimestamp() {
        return timestamp;
    }
    
    public void setTimestamp(Date timestamp) {
        this.timestamp = timestamp;
    }
    
    public String getSources() {
        return sources;
    }
    
    public void setSources(String sources) {
        this.sources = sources;
    }
    
    public Integer getLatencyMs() {
        return latencyMs;
    }
    
    public void setLatencyMs(Integer latencyMs) {
        this.latencyMs = latencyMs;
    }
}
