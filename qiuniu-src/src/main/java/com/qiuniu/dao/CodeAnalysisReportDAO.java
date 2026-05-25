package com.qiuniu.dao;

import com.qiuniu.model.CodeAnalysisReport;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/**
 * 代码分析报告 DAO
 */
public class CodeAnalysisReportDAO {

    public boolean createTable() {
        String sql = "CREATE TABLE IF NOT EXISTS code_analysis_reports (" +
                "id BIGINT AUTO_INCREMENT PRIMARY KEY, " +
                "project_id BIGINT NULL, " +
                "project_name VARCHAR(255), " +
                "team_id BIGINT, " +
                "user_id BIGINT NOT NULL, " +
                "repo_url VARCHAR(500), " +
                "branch VARCHAR(100) DEFAULT 'main', " +
                "status VARCHAR(20) DEFAULT 'PENDING', " +
                "total_files INT DEFAULT 0, " +
                "analyzed_files INT DEFAULT 0, " +
                "total_issues INT DEFAULT 0, " +
                "report TEXT, " +
                "rule_set VARCHAR(50) DEFAULT 'default', " +
                "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, " +
                "finished_at TIMESTAMP NULL, " +
                "cost_ms BIGINT DEFAULT 0, " +
                "INDEX idx_project (project_id), " +
                "INDEX idx_user (user_id), " +
                "INDEX idx_status (status) " +
                ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4";
        try (Connection conn = DBUtil.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.executeUpdate(sql);
            System.out.println("[CodeAnalysisReportDAO] 表检查完成");
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    public boolean insert(CodeAnalysisReport r) {
        String sql = "INSERT INTO code_analysis_reports " +
                "(project_id, project_name, team_id, user_id, repo_url, branch, status, rule_set) " +
                "VALUES (?, ?, ?, ?, ?, ?, 'PENDING', ?)";
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            if (r.getProjectId() != null) ps.setLong(1, r.getProjectId()); else ps.setNull(1, Types.BIGINT);
            ps.setString(2, r.getProjectName());
            if (r.getTeamId() != null) ps.setLong(3, r.getTeamId()); else ps.setNull(3, Types.BIGINT);
            ps.setLong(4, r.getUserId());
            ps.setString(5, r.getRepoUrl());
            ps.setString(6, r.getBranch() != null ? r.getBranch() : "main");
            ps.setString(7, r.getRuleSet() != null ? r.getRuleSet() : "default");
            if (ps.executeUpdate() > 0) {
                try (ResultSet rs = ps.getGeneratedKeys()) {
                    if (rs.next()) r.setId(rs.getLong(1));
                }
                return true;
            }
            return false;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    public boolean updateStatus(Long id, String status, int analyzedFiles, int totalIssues) {
        String sql = "UPDATE code_analysis_reports " +
                "SET status=?, analyzed_files=?, total_issues=? WHERE id=?";
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, status);
            ps.setInt(2, analyzedFiles);
            ps.setInt(3, totalIssues);
            ps.setLong(4, id);
            return ps.executeUpdate() > 0;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    public boolean finish(Long id, String report, int totalIssues, long costMs) {
        String sql = "UPDATE code_analysis_reports " +
                "SET status='DONE', report=?, total_issues=?, finished_at=NOW(), cost_ms=? WHERE id=?";
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, report);
            ps.setInt(2, totalIssues);
            ps.setLong(3, costMs);
            ps.setLong(4, id);
            return ps.executeUpdate() > 0;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    public boolean fail(Long id, String errorMsg) {
        String sql = "UPDATE code_analysis_reports " +
                "SET status='FAILED', report=?, finished_at=NOW() WHERE id=?";
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, errorMsg);
            ps.setLong(2, id);
            return ps.executeUpdate() > 0;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    public boolean delete(Long id) {
        String sql = "DELETE FROM code_analysis_reports WHERE id=?";
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, id);
            return ps.executeUpdate() > 0;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    public CodeAnalysisReport getById(Long id) {
        String sql = "SELECT * FROM code_analysis_reports WHERE id=?";
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, id);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return mapRow(rs);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    public List<CodeAnalysisReport> getByProjectId(Long projectId) {
        List<CodeAnalysisReport> list = new ArrayList<>();
        String sql = "SELECT * FROM code_analysis_reports WHERE project_id=? ORDER BY created_at DESC";
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, projectId);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) list.add(mapRow(rs));
        } catch (Exception e) {
            e.printStackTrace();
        }
        return list;
    }

    public List<CodeAnalysisReport> getByUserId(Long userId) {
        List<CodeAnalysisReport> list = new ArrayList<>();
        String sql = "SELECT * FROM code_analysis_reports WHERE user_id=? ORDER BY created_at DESC";
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, userId);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) list.add(mapRow(rs));
        } catch (Exception e) {
            e.printStackTrace();
        }
        return list;
    }

    /** 获取全部报告（管理员用，支持 limit/offset） */
    public List<CodeAnalysisReport> getAll(int limit, int offset) {
        List<CodeAnalysisReport> list = new ArrayList<>();
        String sql = "SELECT * FROM code_analysis_reports ORDER BY created_at DESC LIMIT ? OFFSET ?";
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, limit);
            ps.setInt(2, offset);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) list.add(mapRow(rs));
        } catch (Exception e) {
            e.printStackTrace();
        }
        return list;
    }

    /** 获取报告总数 */
    public int getTotalCount() {
        String sql = "SELECT COUNT(*) FROM code_analysis_reports";
        try (Connection conn = DBUtil.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            if (rs.next()) return rs.getInt(1);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return 0;
    }

    /** 管理员批量删除指定天数之前的报告 */
    public int deleteOlderThan(int days) {
        String sql = "DELETE FROM code_analysis_reports WHERE created_at < DATE_SUB(NOW(), INTERVAL ? DAY)";
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, days);
            return ps.executeUpdate();
        } catch (Exception e) {
            e.printStackTrace();
            return 0;
        }
    }

    /** 管理员删除指定 ID 的报告 */
    public boolean adminDelete(Long id) {
        String sql = "DELETE FROM code_analysis_reports WHERE id=?";
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, id);
            return ps.executeUpdate() > 0;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    private CodeAnalysisReport mapRow(ResultSet rs) throws SQLException {
        CodeAnalysisReport r = new CodeAnalysisReport();
        r.setId(rs.getLong("id"));
        r.setProjectId(rs.getLong("project_id"));
        r.setProjectName(rs.getString("project_name"));
        r.setTeamId(rs.getObject("team_id", Long.class));
        r.setUserId(rs.getLong("user_id"));
        r.setRepoUrl(rs.getString("repo_url"));
        r.setBranch(rs.getString("branch"));
        r.setStatus(rs.getString("status"));
        r.setTotalFiles(rs.getInt("total_files"));
        r.setAnalyzedFiles(rs.getInt("analyzed_files"));
        r.setTotalIssues(rs.getInt("total_issues"));
        r.setReport(rs.getString("report"));
        r.setRuleSet(rs.getString("rule_set"));
        r.setCreatedAt(rs.getTimestamp("created_at") != null ? rs.getTimestamp("created_at").toString() : null);
        r.setFinishedAt(rs.getTimestamp("finished_at") != null ? rs.getTimestamp("finished_at").toString() : null);
        r.setCostMs(rs.getLong("cost_ms"));
        return r;
    }
}
