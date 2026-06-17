package com.qiuniu.dao;

import com.qiuniu.model.User;
import com.qiuniu.model.Role;
import org.mindrot.jbcrypt.BCrypt;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/**
 * 用户数据访问对象
 */
public class UserDAO {
    
    /**
     * 创建用户表（含 role 字段）
     */
    public static void createTable() throws Exception {
        String sql = "CREATE TABLE IF NOT EXISTS users (" +
                "id BIGINT PRIMARY KEY AUTO_INCREMENT," +
                "username VARCHAR(50) UNIQUE NOT NULL," +
                "password VARCHAR(255) NOT NULL," +
                "email VARCHAR(100)," +
                "nickname VARCHAR(50)," +
                "role VARCHAR(20) DEFAULT 'VIEWER'," +
                "create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP," +
                "last_login_time TIMESTAMP NULL" +
                ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4";
        
        try (Connection conn = DBUtil.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute(sql);
            System.out.println("[UserDAO] 用户表检查完成");
            
            // 尝试添加 role 列（如果表已存在但没有该列）
            try {
                stmt.execute("ALTER TABLE users ADD COLUMN role VARCHAR(20) DEFAULT 'VIEWER'");
                System.out.println("[UserDAO] 添加 role 列成功");
            } catch (SQLException e) {
                // 列已存在，忽略
            }
        }
    }
    
    /**
     * 根据用户名查找用户
     */
    public static User findByUsername(String username) throws Exception {
        String sql = "SELECT * FROM users WHERE username = ?";
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, username);
            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) {
                return mapResultSet(rs);
            }
        }
        return null;
    }
    
    /**
     * 根据 ID 查找用户
     */
    public static User findById(Long id) throws Exception {
        String sql = "SELECT * FROM users WHERE id = ?";
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
     * 创建新用户（默认角色为 VIEWER）
     */
    public static boolean create(User user) throws Exception {
        // 如果未指定角色，默认为查看人员
        if (user.getRole() == null) {
            user.setRole(Role.VIEWER);
        }
        
        String sql = "INSERT INTO users (username, password, email, nickname, role) VALUES (?, ?, ?, ?, ?)";
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, user.getUsername());
            // 密码加密
            pstmt.setString(2, BCrypt.hashpw(user.getPassword(), BCrypt.gensalt()));
            pstmt.setString(3, user.getEmail());
            pstmt.setString(4, user.getNickname());
            pstmt.setString(5, user.getRole().getCode());
            return pstmt.executeUpdate() > 0;
        }
    }
    
    /**
     * 创建指定角色的用户（仅供初始化超级管理员使用）
     */
    public static boolean createWithRole(User user, Role role) throws Exception {
        user.setRole(role);
        String sql = "INSERT INTO users (username, password, email, nickname, role) VALUES (?, ?, ?, ?, ?)";
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, user.getUsername());
            pstmt.setString(2, BCrypt.hashpw(user.getPassword(), BCrypt.gensalt()));
            pstmt.setString(3, user.getEmail());
            pstmt.setString(4, user.getNickname());
            pstmt.setString(5, role.getCode());
            return pstmt.executeUpdate() > 0;
        }
    }
    
    /**
     * 更新用户角色
     */
    public static boolean updateRole(Long userId, Role role) throws Exception {
        String sql = "UPDATE users SET role = ? WHERE id = ?";
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, role.getCode());
            pstmt.setLong(2, userId);
            return pstmt.executeUpdate() > 0;
        }
    }
    
    /**
     * 验证用户登录
     */
    public static User validateLogin(String username, String password) throws Exception {
        User user = findByUsername(username);
        if (user != null && BCrypt.checkpw(password, user.getPassword())) {
            // 更新最后登录时间
            updateLastLogin(user.getId());
            return user;
        }
        return null;
    }
    
    /**
     * 更新最后登录时间
     */
    private static void updateLastLogin(Long userId) throws Exception {
        String sql = "UPDATE users SET last_login_time = NOW() WHERE id = ?";
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setLong(1, userId);
            pstmt.executeUpdate();
        }
    }
    
    /**
     * 检查用户名是否存在
     */
    public static boolean existsByUsername(String username) throws Exception {
        return findByUsername(username) != null;
    }
    
    /**
     * 初始化超级管理员用户
     */
    public static void initSuperAdmin() throws Exception {
        String adminUsername = "RyanLoong";
        String adminPassword = "RyanLoong";
        
        User existingAdmin = findByUsername(adminUsername);
        if (existingAdmin == null) {
            User admin = new User();
            admin.setUsername(adminUsername);
            admin.setPassword(adminPassword);
            admin.setNickname("超级管理员");
            
            if (createWithRole(admin, Role.ADMIN)) {
                System.out.println("[UserDAO] 超级管理员初始化成功: " + adminUsername);
            }
        } else {
            // 确保现有用户是 ADMIN 角色
            if (existingAdmin.getRole() != Role.ADMIN) {
                updateRole(existingAdmin.getId(), Role.ADMIN);
                System.out.println("[UserDAO] 已将 " + adminUsername + " 更新为超级管理员");
            }
        }
    }
    
    /**
     * 将 ResultSet 映射为 User 对象
     */
    private static User mapResultSet(ResultSet rs) throws SQLException {
        User user = new User();
        user.setId(rs.getLong("id"));
        user.setUsername(rs.getString("username"));
        user.setPassword(rs.getString("password"));
        user.setEmail(rs.getString("email"));
        user.setNickname(rs.getString("nickname"));
        user.setRole(Role.fromCode(rs.getString("role")));
        user.setCreateTime(rs.getTimestamp("create_time"));
        user.setLastLoginTime(rs.getTimestamp("last_login_time"));
        return user;
    }
}
