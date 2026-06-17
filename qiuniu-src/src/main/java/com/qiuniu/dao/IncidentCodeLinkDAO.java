package com.qiuniu.dao;

import com.qiuniu.model.IncidentCodeLink;
import com.qiuniu.dao.DBUtil;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/**
 * 事故代码关联数据访问层
 * 对应表：incident_code_links
 */
public class IncidentCodeLinkDAO {

    /**
     * 插入一条代码关联记录
     * @return 生成的主键 ID，-1 表示失败
     */
    public int insert(IncidentCodeLink link) {
        String sql = "INSERT INTO incident_code_links (incident_id, repo_name, repo_url, " +
                "file_path, line_start, line_end, code_snippet, fix_snippet) VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, link.getIncidentId());
            ps.setString(2, link.getRepoName());
            ps.setString(3, link.getRepoUrl());
            ps.setString(4, link.getFilePath());
            if (link.getLineStart() != null) {
                ps.setInt(5, link.getLineStart());
            } else {
                ps.setNull(5, Types.INTEGER);
            }
            if (link.getLineEnd() != null) {
                ps.setInt(6, link.getLineEnd());
            } else {
                ps.setNull(6, Types.INTEGER);
            }
            ps.setString(7, link.getCodeSnippet());
            ps.setString(8, link.getFixSnippet());
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
     * 更新代码关联记录
     */
    public boolean update(IncidentCodeLink link) {
        String sql = "UPDATE incident_code_links SET incident_id=?, repo_name=?, repo_url=?, " +
                "file_path=?, line_start=?, line_end=?, code_snippet=?, fix_snippet=? WHERE id=?";
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, link.getIncidentId());
            ps.setString(2, link.getRepoName());
            ps.setString(3, link.getRepoUrl());
            ps.setString(4, link.getFilePath());
            if (link.getLineStart() != null) {
                ps.setInt(5, link.getLineStart());
            } else {
                ps.setNull(5, Types.INTEGER);
            }
            if (link.getLineEnd() != null) {
                ps.setInt(6, link.getLineEnd());
            } else {
                ps.setNull(6, Types.INTEGER);
            }
            ps.setString(7, link.getCodeSnippet());
            ps.setString(8, link.getFixSnippet());
            ps.setInt(9, link.getId());
            return ps.executeUpdate() > 0;
        } catch (Exception e) {
            e.printStackTrace();
        }
        return false;
    }

    /**
     * 删除代码关联记录
     */
    public boolean delete(int id) {
        String sql = "DELETE FROM incident_code_links WHERE id=?";
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, id);
            return ps.executeUpdate() > 0;
        } catch (Exception e) {
            e.printStackTrace();
        }
        return false;
    }

    /**
     * 按 ID 查询
     */
    public IncidentCodeLink getById(int id) {
        String sql = "SELECT * FROM incident_code_links WHERE id=?";
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, id);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                return mapRow(rs);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    /**
     * 按事故 ID 查询所有关联记录
     */
    public List<IncidentCodeLink> getByIncidentId(int incidentId) {
        List<IncidentCodeLink> list = new ArrayList<>();
        String sql = "SELECT * FROM incident_code_links WHERE incident_id=? ORDER BY id ASC";
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, incidentId);
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
     * 删除某个事故的所有代码关联（用于重新关联时先清后写）
     */
    public boolean deleteByIncidentId(int incidentId) {
        String sql = "DELETE FROM incident_code_links WHERE incident_id=?";
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, incidentId);
            ps.executeUpdate();
            return true;
        } catch (Exception e) {
            e.printStackTrace();
        }
        return false;
    }

    /**
     * 搜索某个事故的代码关联（按 repo_name / file_path / code_snippet 模糊匹配）
     */
    public List<IncidentCodeLink> search(int incidentId, String keyword) {
        List<IncidentCodeLink> list = new ArrayList<>();
        if (keyword == null || keyword.isEmpty()) {
            return getByIncidentId(incidentId);
        }
        String sql = "SELECT * FROM incident_code_links WHERE incident_id=? AND " +
                "(repo_name LIKE ? OR file_path LIKE ? OR code_snippet LIKE ? OR fix_snippet LIKE ?) " +
                "ORDER BY id ASC";
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, incidentId);
            String kw = "%" + keyword + "%";
            ps.setString(2, kw);
            ps.setString(3, kw);
            ps.setString(4, kw);
            ps.setString(5, kw);
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
     * 全局搜索：按文件路径匹配（用于 AI 分析时查找历史事故）
     */
    public List<IncidentCodeLink> searchByFilePath(String filePath) {
        List<IncidentCodeLink> list = new ArrayList<>();
        if (filePath == null || filePath.isEmpty()) return list;
        String sql = "SELECT * FROM incident_code_links WHERE file_path LIKE ? ORDER BY id ASC";
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, "%" + filePath + "%");
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
     * 全局搜索：按代码片段模糊匹配
     */
    public List<IncidentCodeLink> searchByCodeSnippet(String codeSnippet) {
        List<IncidentCodeLink> list = new ArrayList<>();
        if (codeSnippet == null || codeSnippet.isEmpty()) return list;
        String sql = "SELECT * FROM incident_code_links WHERE code_snippet LIKE ? OR fix_snippet LIKE ? ORDER BY id ASC";
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            String kw = "%" + codeSnippet.trim() + "%";
            ps.setString(1, kw);
            ps.setString(2, kw);
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
     * 将 ResultSet 当前行映射为 IncidentCodeLink 对象
     */
    private IncidentCodeLink mapRow(ResultSet rs) throws SQLException {
        IncidentCodeLink link = new IncidentCodeLink();
        link.setId(rs.getInt("id"));
        link.setIncidentId(rs.getInt("incident_id"));
        link.setRepoName(rs.getString("repo_name"));
        link.setRepoUrl(rs.getString("repo_url"));
        link.setFilePath(rs.getString("file_path"));
        int lineStart = rs.getInt("line_start");
        if (rs.wasNull()) {
            link.setLineStart(null);
        } else {
            link.setLineStart(lineStart);
        }
        int lineEnd = rs.getInt("line_end");
        if (rs.wasNull()) {
            link.setLineEnd(null);
        } else {
            link.setLineEnd(lineEnd);
        }
        link.setCodeSnippet(rs.getString("code_snippet"));
        link.setFixSnippet(rs.getString("fix_snippet"));
        link.setCreatedAt(rs.getTimestamp("created_at"));
        return link;
    }
}
