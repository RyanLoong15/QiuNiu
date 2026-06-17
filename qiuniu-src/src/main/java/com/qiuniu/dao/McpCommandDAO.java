package com.qiuniu.dao;

import com.qiuniu.model.McpCommand;
import java.sql.*;
import java.util.*;

public class McpCommandDAO {

    public static List<McpCommand> getAll() throws Exception {
        List<McpCommand> list = new ArrayList<>();
        String sql = "SELECT command_id, command, description FROM mcp_command ORDER BY command_id";
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql);
             ResultSet rs = pstmt.executeQuery()) {
            while (rs.next()) list.add(mapRow(rs));
        }
        return list;
    }

    public static McpCommand getById(String commandId) throws Exception {
        String sql = "SELECT command_id, command, description FROM mcp_command WHERE command_id=?";
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, commandId);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) return mapRow(rs);
            }
        }
        return null;
    }

    public static void add(McpCommand cmd) throws Exception {
        String sql = "INSERT INTO mcp_command (command_id, command, description) VALUES (?, ?, ?)";
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, cmd.getCommandId());
            pstmt.setString(2, cmd.getCommand());
            pstmt.setString(3, cmd.getDescription());
            pstmt.executeUpdate();
        }
    }

    public static void update(McpCommand cmd) throws Exception {
        String sql = "UPDATE mcp_command SET command=?, description=? WHERE command_id=?";
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, cmd.getCommand());
            pstmt.setString(2, cmd.getDescription());
            pstmt.setString(3, cmd.getCommandId());
            pstmt.executeUpdate();
        }
    }

    public static void delete(String commandId) throws Exception {
        String sql = "DELETE FROM mcp_command WHERE command_id=?";
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, commandId);
            pstmt.executeUpdate();
        }
    }

    public static List<McpCommand> search(String keyword) throws Exception {
        List<McpCommand> list = new ArrayList<>();
        String sql = "SELECT command_id, command, description FROM mcp_command "
                   + "WHERE command_id LIKE ? OR command LIKE ? OR description LIKE ? "
                   + "ORDER BY command_id";
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

    private static McpCommand mapRow(ResultSet rs) throws SQLException {
        McpCommand cmd = new McpCommand();
        cmd.setCommandId(rs.getString("command_id"));
        cmd.setCommand(rs.getString("command"));
        cmd.setDescription(rs.getString("description"));
        return cmd;
    }
}
