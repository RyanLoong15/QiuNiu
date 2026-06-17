package com.qiuniu.dao;

import com.qiuniu.model.McpEnvInfo;
import java.sql.*;
import java.util.*;

/**
 * MCP测试环境信息DAO - 增删改查
 */
public class McpEnvInfoDAO {

    /**
     * 查询全部环境信息
     */
    public static List<McpEnvInfo> getAll() throws Exception {
        List<McpEnvInfo> list = new ArrayList<>();
        String sql = "SELECT env_id, system_name, environment, cluster_code, machine_name, "
                   + "ip_address, username, password, command_id, prompt_id "
                   + "FROM mcp_env_info ORDER BY env_id";
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql);
             ResultSet rs = pstmt.executeQuery()) {
            while (rs.next()) {
                list.add(mapRow(rs));
            }
        }
        return list;
    }

    /**
     * 根据编号查单条
     */
    public static McpEnvInfo getById(String envId) throws Exception {
        String sql = "SELECT env_id, system_name, environment, cluster_code, machine_name, "
                   + "ip_address, username, password, command_id, prompt_id "
                   + "FROM mcp_env_info WHERE env_id=?";
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, envId);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) return mapRow(rs);
            }
        }
        return null;
    }

    /**
     * 新增
     */
    public static void add(McpEnvInfo info) throws Exception {
        String sql = "INSERT INTO mcp_env_info (env_id, system_name, environment, cluster_code, "
                   + "machine_name, ip_address, username, password, command_id, prompt_id) "
                   + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            bindParams(pstmt, info);
            pstmt.executeUpdate();
        }
    }

    /**
     * 更新
     */
    public static void update(McpEnvInfo info) throws Exception {
        String sql = "UPDATE mcp_env_info SET system_name=?, environment=?, cluster_code=?, "
                   + "machine_name=?, ip_address=?, username=?, password=?, command_id=?, prompt_id=? "
                   + "WHERE env_id=?";
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, info.getSystemName());
            pstmt.setString(2, info.getEnvironment());
            pstmt.setString(3, info.getClusterCode());
            pstmt.setString(4, info.getMachineName());
            pstmt.setString(5, info.getIpAddress());
            pstmt.setString(6, info.getUsername());
            pstmt.setString(7, info.getPassword());
            pstmt.setString(8, info.getCommandId());
            pstmt.setString(9, info.getPromptId());
            pstmt.setString(10, info.getEnvId());
            pstmt.executeUpdate();
        }
    }

    /**
     * 删除
     */
    public static void delete(String envId) throws Exception {
        String sql = "DELETE FROM mcp_env_info WHERE env_id=?";
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, envId);
            pstmt.executeUpdate();
        }
    }

    /**
     * 搜索（按系统名、环境、机器名模糊匹配）
     */
    public static List<McpEnvInfo> search(String keyword) throws Exception {
        List<McpEnvInfo> list = new ArrayList<>();
        String sql = "SELECT env_id, system_name, environment, cluster_code, machine_name, "
                   + "ip_address, username, password, command_id, prompt_id "
                   + "FROM mcp_env_info WHERE system_name LIKE ? OR environment LIKE ? "
                   + "OR machine_name LIKE ? ORDER BY env_id";
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            String kw = "%" + keyword + "%";
            pstmt.setString(1, kw);
            pstmt.setString(2, kw);
            pstmt.setString(3, kw);
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) list.add(mapRow(rs));
            }
        }
        return list;
    }

    private static McpEnvInfo mapRow(ResultSet rs) throws SQLException {
        McpEnvInfo info = new McpEnvInfo();
        info.setEnvId(rs.getString("env_id"));
        info.setSystemName(rs.getString("system_name"));
        info.setEnvironment(rs.getString("environment"));
        info.setClusterCode(rs.getString("cluster_code"));
        info.setMachineName(rs.getString("machine_name"));
        info.setIpAddress(rs.getString("ip_address"));
        info.setUsername(rs.getString("username"));
        info.setPassword(rs.getString("password"));
        info.setCommandId(rs.getString("command_id"));
        info.setPromptId(rs.getString("prompt_id"));
        return info;
    }

    private static void bindParams(PreparedStatement pstmt, McpEnvInfo info) throws SQLException {
        pstmt.setString(1, info.getEnvId());
        pstmt.setString(2, info.getSystemName());
        pstmt.setString(3, info.getEnvironment());
        pstmt.setString(4, info.getClusterCode());
        pstmt.setString(5, info.getMachineName());
        pstmt.setString(6, info.getIpAddress());
        pstmt.setString(7, info.getUsername());
        pstmt.setString(8, info.getPassword());
        pstmt.setString(9, info.getCommandId());
        pstmt.setString(10, info.getPromptId());
    }
}
