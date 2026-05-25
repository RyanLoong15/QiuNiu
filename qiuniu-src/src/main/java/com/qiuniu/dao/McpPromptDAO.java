package com.qiuniu.dao;

import com.qiuniu.model.McpPrompt;
import java.sql.*;
import java.util.*;

public class McpPromptDAO {

    public static List<McpPrompt> getAll() throws Exception {
        List<McpPrompt> list = new ArrayList<>();
        String sql = "SELECT prompt_id, content FROM mcp_prompt ORDER BY prompt_id";
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql);
             ResultSet rs = pstmt.executeQuery()) {
            while (rs.next()) list.add(mapRow(rs));
        }
        return list;
    }

    public static McpPrompt getById(String promptId) throws Exception {
        String sql = "SELECT prompt_id, content FROM mcp_prompt WHERE prompt_id=?";
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, promptId);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) return mapRow(rs);
            }
        }
        return null;
    }

    public static void add(McpPrompt prompt) throws Exception {
        String sql = "INSERT INTO mcp_prompt (prompt_id, content) VALUES (?, ?)";
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, prompt.getPromptId());
            pstmt.setString(2, prompt.getContent());
            pstmt.executeUpdate();
        }
    }

    public static void update(McpPrompt prompt) throws Exception {
        String sql = "UPDATE mcp_prompt SET content=? WHERE prompt_id=?";
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, prompt.getContent());
            pstmt.setString(2, prompt.getPromptId());
            pstmt.executeUpdate();
        }
    }

    public static void delete(String promptId) throws Exception {
        String sql = "DELETE FROM mcp_prompt WHERE prompt_id=?";
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, promptId);
            pstmt.executeUpdate();
        }
    }

    public static List<McpPrompt> search(String keyword) throws Exception {
        List<McpPrompt> list = new ArrayList<>();
        String sql = "SELECT prompt_id, content FROM mcp_prompt "
                   + "WHERE prompt_id LIKE ? OR content LIKE ? ORDER BY prompt_id";
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            String kw = "%" + keyword + "%";
            pstmt.setString(1, kw);
            pstmt.setString(2, kw);
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) list.add(mapRow(rs));
            }
        }
        return list;
    }

    private static McpPrompt mapRow(ResultSet rs) throws SQLException {
        McpPrompt prompt = new McpPrompt();
        prompt.setPromptId(rs.getString("prompt_id"));
        prompt.setContent(rs.getString("content"));
        return prompt;
    }
}
