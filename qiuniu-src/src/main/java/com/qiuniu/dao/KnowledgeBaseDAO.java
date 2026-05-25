package com.qiuniu.dao;

import com.qiuniu.model.KnowledgeEntry;
import com.qiuniu.dao.DBUtil;

import java.sql.*;
import java.util.*;

public class KnowledgeBaseDAO {

    public static List<KnowledgeEntry> getAllEnabled() throws Exception {
        List<KnowledgeEntry> list = new ArrayList<>();
        String sql = "SELECT id, question, answer, category FROM knowledge_base WHERE enabled=1 ORDER BY id";
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql);
             ResultSet rs = pstmt.executeQuery()) {
            while (rs.next()) {
                KnowledgeEntry e = new KnowledgeEntry();
                e.setId(rs.getInt("id"));
                e.setQuestion(rs.getString("question"));
                e.setAnswer(rs.getString("answer"));
                e.setCategory(rs.getString("category"));
                list.add(e);
            }
        }
        return list;
    }

    public static List<KnowledgeEntry> getAll() throws Exception {
        List<KnowledgeEntry> list = new ArrayList<>();
        String sql = "SELECT id, question, answer, category, enabled, created_at FROM knowledge_base ORDER BY id";
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql);
             ResultSet rs = pstmt.executeQuery()) {
            while (rs.next()) {
                KnowledgeEntry e = new KnowledgeEntry();
                e.setId(rs.getInt("id"));
                e.setQuestion(rs.getString("question"));
                e.setAnswer(rs.getString("answer"));
                e.setCategory(rs.getString("category"));
                e.setEnabled(rs.getBoolean("enabled"));
                e.setCreatedAt(rs.getTimestamp("created_at"));
                list.add(e);
            }
        }
        return list;
    }

    public static void add(KnowledgeEntry e) throws Exception {
        String sql = "INSERT INTO knowledge_base (question, answer, category, created_by) VALUES (?, ?, ?, ?)";
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, e.getQuestion());
            pstmt.setString(2, e.getAnswer());
            pstmt.setString(3, e.getCategory());
            pstmt.setString(4, e.getCreatedBy());
            pstmt.executeUpdate();
        }
    }

    public static void update(KnowledgeEntry e) throws Exception {
        String sql = "UPDATE knowledge_base SET question=?, answer=?, category=?, enabled=? WHERE id=?";
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, e.getQuestion());
            pstmt.setString(2, e.getAnswer());
            pstmt.setString(3, e.getCategory());
            pstmt.setBoolean(4, e.isEnabled());
            pstmt.setInt(5, e.getId());
            pstmt.executeUpdate();
        }
    }

    public static void delete(int id) throws Exception {
        String sql = "DELETE FROM knowledge_base WHERE id=?";
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, id);
            pstmt.executeUpdate();
        }
    }

    public static List<String> getCategories() throws Exception {
        List<String> cats = new ArrayList<>();
        String sql = "SELECT DISTINCT category FROM knowledge_base WHERE category IS NOT NULL AND category<>'' ORDER BY category";
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql);
             ResultSet rs = pstmt.executeQuery()) {
            while (rs.next()) cats.add(rs.getString(1));
        }
        return cats;
    }
}
