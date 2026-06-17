package com.qiuniu.dao;

import com.qiuniu.model.Prompt;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/**
 * 提示词数据访问对象
 */
public class PromptDAO {
    
    /**
     * 创建提示词表
     */
    public static void createTable() throws Exception {
        String sql = "CREATE TABLE IF NOT EXISTS prompts (" +
                "id BIGINT PRIMARY KEY AUTO_INCREMENT," +
                "name VARCHAR(100) NOT NULL," +
                "content TEXT NOT NULL," +
                "description VARCHAR(500)," +
                "category VARCHAR(50)," +
                "user_id BIGINT NOT NULL," +
                "create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP," +
                "update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP," +
                "INDEX idx_user_id (user_id)," +
                "INDEX idx_category (category)" +
                ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4";
        
        try (Connection conn = DBUtil.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute(sql);
            System.out.println("[PromptDAO] 提示词表检查完成");
        }
    }
    
    /**
     * 根据 ID 获取提示词
     */
    public static Prompt findById(Long id) throws Exception {
        String sql = "SELECT * FROM prompts WHERE id = ?";
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setLong(1, id);
            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) {
                return mapResultSet(rs);
            }
        }
        return null;
    }
    
    /**
     * 获取用户的所有提示词
     */
    public static List<Prompt> findByUserId(Long userId) throws Exception {
        String sql = "SELECT * FROM prompts WHERE user_id = ? ORDER BY create_time DESC";
        List<Prompt> list = new ArrayList<>();
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setLong(1, userId);
            ResultSet rs = pstmt.executeQuery();
            while (rs.next()) {
                list.add(mapResultSet(rs));
            }
        }
        return list;
    }
    
    /**
     * 根据分类获取提示词
     */
    public static List<Prompt> findByCategory(Long userId, String category) throws Exception {
        String sql = "SELECT * FROM prompts WHERE user_id = ? AND category = ? ORDER BY create_time DESC";
        List<Prompt> list = new ArrayList<>();
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setLong(1, userId);
            pstmt.setString(2, category);
            ResultSet rs = pstmt.executeQuery();
            while (rs.next()) {
                list.add(mapResultSet(rs));
            }
        }
        return list;
    }
    
    /**
     * 搜索提示词
     */
    public static List<Prompt> search(Long userId, String keyword) throws Exception {
        String sql = "SELECT * FROM prompts WHERE user_id = ? AND (name LIKE ? OR content LIKE ? OR description LIKE ?) ORDER BY create_time DESC";
        List<Prompt> list = new ArrayList<>();
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setLong(1, userId);
            String pattern = "%" + keyword + "%";
            pstmt.setString(2, pattern);
            pstmt.setString(3, pattern);
            pstmt.setString(4, pattern);
            ResultSet rs = pstmt.executeQuery();
            while (rs.next()) {
                list.add(mapResultSet(rs));
            }
        }
        return list;
    }
    
    /**
     * 创建提示词
     */
    public static boolean create(Prompt prompt) throws Exception {
        String sql = "INSERT INTO prompts (name, content, description, category, user_id) VALUES (?, ?, ?, ?, ?)";
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, prompt.getName());
            pstmt.setString(2, prompt.getContent());
            pstmt.setString(3, prompt.getDescription());
            pstmt.setString(4, prompt.getCategory());
            pstmt.setLong(5, prompt.getUserId());
            return pstmt.executeUpdate() > 0;
        }
    }
    
    /**
     * 更新提示词
     */
    public static boolean update(Prompt prompt) throws Exception {
        String sql = "UPDATE prompts SET name = ?, content = ?, description = ?, category = ? WHERE id = ?";
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, prompt.getName());
            pstmt.setString(2, prompt.getContent());
            pstmt.setString(3, prompt.getDescription());
            pstmt.setString(4, prompt.getCategory());
            pstmt.setLong(5, prompt.getId());
            return pstmt.executeUpdate() > 0;
        }
    }
    
    /**
     * 删除提示词
     */
    public static boolean delete(Long id) throws Exception {
        String sql = "DELETE FROM prompts WHERE id = ?";
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setLong(1, id);
            return pstmt.executeUpdate() > 0;
        }
    }
    
    /**
     * 获取所有分类
     */
    public static List<String> getCategories(Long userId) throws Exception {
        String sql = "SELECT DISTINCT category FROM prompts WHERE user_id = ? AND category IS NOT NULL";
        List<String> list = new ArrayList<>();
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setLong(1, userId);
            ResultSet rs = pstmt.executeQuery();
            while (rs.next()) {
                String cat = rs.getString("category");
                if (cat != null && !cat.isEmpty()) {
                    list.add(cat);
                }
            }
        }
        return list;
    }
    
    /**
     * 将 ResultSet 映射为 Prompt 对象
     */
    private static Prompt mapResultSet(ResultSet rs) throws SQLException {
        Prompt prompt = new Prompt();
        prompt.setId(rs.getLong("id"));
        prompt.setName(rs.getString("name"));
        prompt.setContent(rs.getString("content"));
        prompt.setDescription(rs.getString("description"));
        prompt.setCategory(rs.getString("category"));
        prompt.setUserId(rs.getLong("user_id"));
        prompt.setCreateTime(rs.getTimestamp("create_time"));
        prompt.setUpdateTime(rs.getTimestamp("update_time"));
        return prompt;
    }
}
