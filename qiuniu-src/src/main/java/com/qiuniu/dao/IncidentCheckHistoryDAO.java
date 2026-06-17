package com.qiuniu.dao;

import com.qiuniu.model.IncidentCheckHistory;
import com.qiuniu.dao.DBUtil;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/**
 * 检核历史记录数据访问层
 */
public class IncidentCheckHistoryDAO {

    /**
     * 插入检核记录
     */
    public int insert(IncidentCheckHistory history) {
        String sql = "INSERT INTO incident_check_history (check_type, target_id, incident_id, similarity) VALUES (?, ?, ?, ?)";
        
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            
            ps.setString(1, history.getCheckType());
            ps.setString(2, history.getTargetId());
            ps.setInt(3, history.getIncidentId());
            ps.setObject(4, history.getSimilarity());
            
            int affected = ps.executeUpdate();
            if (affected > 0) {
                ResultSet rs = ps.getGeneratedKeys();
                if (rs.next()) {
                    return rs.getInt(1);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return -1;
    }

    /**
     * 按目标ID查询检核历史
     */
    public List<IncidentCheckHistory> getByTargetId(String checkType, String targetId) {
        List<IncidentCheckHistory> list = new ArrayList<>();
        String sql = "SELECT * FROM incident_check_history WHERE check_type=? AND target_id=? ORDER BY similarity DESC";
        
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, checkType);
            ps.setString(2, targetId);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                list.add(mapRow(rs));
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return list;
    }

    /**
     * 查询最近的检核记录
     */
    public List<IncidentCheckHistory> getRecent(int limit) {
        List<IncidentCheckHistory> list = new ArrayList<>();
        String sql = "SELECT * FROM incident_check_history ORDER BY matched_at DESC LIMIT ?";
        
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, limit);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                list.add(mapRow(rs));
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return list;
    }

    private IncidentCheckHistory mapRow(ResultSet rs) throws SQLException {
        IncidentCheckHistory h = new IncidentCheckHistory();
        h.setId(rs.getInt("id"));
        h.setCheckType(rs.getString("check_type"));
        h.setTargetId(rs.getString("target_id"));
        h.setIncidentId(rs.getInt("incident_id"));
        h.setSimilarity(rs.getBigDecimal("similarity"));
        h.setMatchedAt(rs.getTimestamp("matched_at"));
        return h;
    }
}
