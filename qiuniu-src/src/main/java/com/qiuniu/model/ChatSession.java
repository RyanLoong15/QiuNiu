package com.qiuniu.model;

import java.util.Date;
import java.util.List;
import java.util.ArrayList;

/**
 * 聊天会话模型
 * 用于存储多轮对话的会话信息
 */
public class ChatSession {
    
    private Long id;
    private String sessionId;          // 会话唯一标识（UUID）
    private Long userId;               // 用户ID
    private String title;              // 会话标题（可选，自动生成或用户设置）
    private Date createdAt;            // 创建时间
    private Date lastActiveAt;         // 最后活跃时间
    private Integer messageCount;      // 消息数量
    private String metadata;           // 扩展元数据（JSON格式）
    
    // 非数据库字段，运行时使用
    private List<ChatMessage> messages;
    
    public ChatSession() {
        this.messages = new ArrayList<>();
        this.messageCount = 0;
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
    
    public Long getUserId() {
        return userId;
    }
    
    public void setUserId(Long userId) {
        this.userId = userId;
    }
    
    public String getTitle() {
        return title;
    }
    
    public void setTitle(String title) {
        this.title = title;
    }
    
    public Date getCreatedAt() {
        return createdAt;
    }
    
    public void setCreatedAt(Date createdAt) {
        this.createdAt = createdAt;
    }
    
    public Date getLastActiveAt() {
        return lastActiveAt;
    }
    
    public void setLastActiveAt(Date lastActiveAt) {
        this.lastActiveAt = lastActiveAt;
    }
    
    public Integer getMessageCount() {
        return messageCount;
    }
    
    public void setMessageCount(Integer messageCount) {
        this.messageCount = messageCount;
    }
    
    public String getMetadata() {
        return metadata;
    }
    
    public void setMetadata(String metadata) {
        this.metadata = metadata;
    }
    
    public List<ChatMessage> getMessages() {
        return messages;
    }
    
    public void setMessages(List<ChatMessage> messages) {
        this.messages = messages;
    }
}
