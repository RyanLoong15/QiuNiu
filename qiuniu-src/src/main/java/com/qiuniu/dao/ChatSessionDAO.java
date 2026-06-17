package com.qiuniu.dao;

import com.qiuniu.model.ChatSession;
import com.qiuniu.model.ChatMessage;
import com.qiuniu.dao.DBUtil;

import java.sql.*;
import java.util.*;
import java.util.Date;

/**
 * 聊天会话数据访问对象
 * 支持多轮对话的会话持久化
 */
public class ChatSessionDAO {

    // ==================== 会话管理 ====================

    /**
     * 创建会话表
     */
    public static void createTables() throws Exception {
        // 会话表
        String sessionSql = "CREATE TABLE IF NOT EXISTS chat_sessions (" +
                "id BIGINT PRIMARY KEY AUTO_INCREMENT," +
                "session_id VARCHAR(64) NOT NULL UNIQUE," +
                "user_id BIGINT NOT NULL," +
                "title VARCHAR(200)," +
                "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP," +
                "last_active_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP," +
                "message_count INT DEFAULT 0," +
                "metadata TEXT," +
                "INDEX idx_session_id (session_id)," +
                "INDEX idx_user_id (user_id)," +
                "INDEX idx_last_active (last_active_at)" +
                ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4";

        // 消息表
        String messageSql = "CREATE TABLE IF NOT EXISTS chat_messages (" +
                "id BIGINT PRIMARY KEY AUTO_INCREMENT," +
                "session_id VARCHAR(64) NOT NULL," +
                "role VARCHAR(20) NOT NULL," +
                "content TEXT NOT NULL," +
                "timestamp TIMESTAMP DEFAULT CURRENT_TIMESTAMP," +
                "sources TEXT," +
                "latency_ms INT," +
                "INDEX idx_session_id (session_id)," +
                "INDEX idx_timestamp (timestamp)" +
                ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4";

        try (Connection conn = DBUtil.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute(sessionSql);
            stmt.execute(messageSql);
            System.out.println("[ChatSessionDAO] 聊天会话表检查完成");
        }
    }

    /**
     * 创建新会话
     */
    public static ChatSession createSession(Long userId) throws Exception {
        String sessionId = UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        String sql = "INSERT INTO chat_sessions (session_id, user_id, title, created_at, last_active_at) VALUES (?, ?, ?, NOW(), NOW())";
        
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            pstmt.setString(1, sessionId);
            pstmt.setLong(2, userId);
            pstmt.setString(3, "新对话");
            pstmt.executeUpdate();
            
            ResultSet rs = pstmt.getGeneratedKeys();
            if (rs.next()) {
                ChatSession session = new ChatSession();
                session.setId(rs.getLong(1));
                session.setSessionId(sessionId);
                session.setUserId(userId);
                session.setTitle("新对话");
                session.setCreatedAt(new Date());
                session.setLastActiveAt(new Date());
                session.setMessageCount(0);
                return session;
            }
        }
        return null;
    }

    /**
     * 根据sessionId获取会话
     */
    public static ChatSession getBySessionId(String sessionId) throws Exception {
        String sql = "SELECT * FROM chat_sessions WHERE session_id = ?";
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, sessionId);
            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) {
                return mapSession(rs);
            }
        }
        return null;
    }

    /**
     * 获取用户的所有会话
     */
    public static List<ChatSession> getByUserId(Long userId, int limit) throws Exception {
        String sql = "SELECT * FROM chat_sessions WHERE user_id = ? ORDER BY last_active_at DESC LIMIT ?";
        List<ChatSession> list = new ArrayList<>();
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setLong(1, userId);
            pstmt.setInt(2, limit);
            ResultSet rs = pstmt.executeQuery();
            while (rs.next()) {
                list.add(mapSession(rs));
            }
        }
        return list;
    }

    /**
     * 更新会话活跃时间
     */
    public static void updateActiveTime(String sessionId) throws Exception {
        String sql = "UPDATE chat_sessions SET last_active_at = NOW() WHERE session_id = ?";
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, sessionId);
            pstmt.executeUpdate();
        }
    }

    /**
     * 更新会话标题
     */
    public static void updateTitle(String sessionId, String title) throws Exception {
        String sql = "UPDATE chat_sessions SET title = ? WHERE session_id = ?";
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, title);
            pstmt.setString(2, sessionId);
            pstmt.executeUpdate();
        }
    }

    /**
     * 删除会话（及其消息）
     */
    public static void deleteSession(String sessionId) throws Exception {
        try (Connection conn = DBUtil.getConnection()) {
            // 先删除消息
            try (PreparedStatement pstmt = conn.prepareStatement("DELETE FROM chat_messages WHERE session_id = ?")) {
                pstmt.setString(1, sessionId);
                pstmt.executeUpdate();
            }
            // 再删除会话
            try (PreparedStatement pstmt = conn.prepareStatement("DELETE FROM chat_sessions WHERE session_id = ?")) {
                pstmt.setString(1, sessionId);
                pstmt.executeUpdate();
            }
        }
    }

    /**
     * 清理过期会话（超过30天未活跃）
     */
    public static int cleanupExpiredSessions() throws Exception {
        String sql = "DELETE FROM chat_sessions WHERE last_active_at < DATE_SUB(NOW(), INTERVAL 30 DAY)";
        try (Connection conn = DBUtil.getConnection();
             Statement stmt = conn.createStatement()) {
            return stmt.executeUpdate(sql);
        }
    }

    // ==================== 消息管理 ====================

    /**
     * 添加消息到会话
     */
    public static void addMessage(ChatMessage message) throws Exception {
        String sql = "INSERT INTO chat_messages (session_id, role, content, timestamp, sources, latency_ms) VALUES (?, ?, ?, NOW(), ?, ?)";
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, message.getSessionId());
            pstmt.setString(2, message.getRole());
            pstmt.setString(3, message.getContent());
            pstmt.setString(4, message.getSources());
            pstmt.setInt(5, message.getLatencyMs() != null ? message.getLatencyMs() : 0);
            pstmt.executeUpdate();
            
            // 更新会话消息计数
            try (PreparedStatement updateStmt = conn.prepareStatement(
                    "UPDATE chat_sessions SET message_count = message_count + 1, last_active_at = NOW() WHERE session_id = ?")) {
                updateStmt.setString(1, message.getSessionId());
                updateStmt.executeUpdate();
            }
        }
    }

    /**
     * 获取会话的消息历史
     */
    public static List<ChatMessage> getMessages(String sessionId, int limit) throws Exception {
        String sql = "SELECT * FROM chat_messages WHERE session_id = ? ORDER BY timestamp ASC LIMIT ?";
        List<ChatMessage> list = new ArrayList<>();
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, sessionId);
            pstmt.setInt(2, limit);
            ResultSet rs = pstmt.executeQuery();
            while (rs.next()) {
                list.add(mapMessage(rs));
            }
        }
        return list;
    }

    /**
     * 获取会话最近N条消息（用于构建上下文）
     */
    public static List<ChatMessage> getRecentMessages(String sessionId, int maxTurns) throws Exception {
        // maxTurns是轮数，每轮包含user和assistant两条消息
        int limit = maxTurns * 2;
        String sql = "SELECT * FROM (" +
                "SELECT * FROM chat_messages WHERE session_id = ? ORDER BY timestamp DESC LIMIT ?" +
                ") AS recent ORDER BY timestamp ASC";
        List<ChatMessage> list = new ArrayList<>();
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, sessionId);
            pstmt.setInt(2, limit);
            ResultSet rs = pstmt.executeQuery();
            while (rs.next()) {
                list.add(mapMessage(rs));
            }
        }
        return list;
    }

    /**
     * 清除会话的消息历史
     */
    public static void clearMessages(String sessionId) throws Exception {
        String sql = "DELETE FROM chat_messages WHERE session_id = ?";
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, sessionId);
            pstmt.executeUpdate();
            
            // 重置消息计数
            try (PreparedStatement updateStmt = conn.prepareStatement(
                    "UPDATE chat_sessions SET message_count = 0 WHERE session_id = ?")) {
                updateStmt.setString(1, sessionId);
                updateStmt.executeUpdate();
            }
        }
    }

    // ==================== 映射方法 ====================

    private static ChatSession mapSession(ResultSet rs) throws SQLException {
        ChatSession session = new ChatSession();
        session.setId(rs.getLong("id"));
        session.setSessionId(rs.getString("session_id"));
        session.setUserId(rs.getLong("user_id"));
        session.setTitle(rs.getString("title"));
        session.setCreatedAt(rs.getTimestamp("created_at"));
        session.setLastActiveAt(rs.getTimestamp("last_active_at"));
        session.setMessageCount(rs.getInt("message_count"));
        session.setMetadata(rs.getString("metadata"));
        return session;
    }

    private static ChatMessage mapMessage(ResultSet rs) throws SQLException {
        ChatMessage msg = new ChatMessage();
        msg.setId(rs.getLong("id"));
        msg.setSessionId(rs.getString("session_id"));
        msg.setRole(rs.getString("role"));
        msg.setContent(rs.getString("content"));
        msg.setTimestamp(rs.getTimestamp("timestamp"));
        msg.setSources(rs.getString("sources"));
        msg.setLatencyMs(rs.getInt("latency_ms"));
        return msg;
    }
}
