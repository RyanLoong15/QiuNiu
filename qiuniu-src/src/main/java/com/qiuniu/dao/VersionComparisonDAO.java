package com.qiuniu.dao;

import com.google.gson.JsonObject;
import com.qiuniu.model.VersionComparison;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class VersionComparisonDAO {

    public boolean createTable() {
        String sql = "CREATE TABLE IF NOT EXISTS git_version_comparisons (" +
                "id BIGINT AUTO_INCREMENT PRIMARY KEY, " +
                "user_id BIGINT NOT NULL, " +
                "version_name VARCHAR(50) NOT NULL, " +
                "base_branch VARCHAR(100) NOT NULL DEFAULT 'master', " +
                "compare_branch VARCHAR(100) NOT NULL, " +
                "total_repos INT DEFAULT 0, " +
                "success_count INT DEFAULT 0, " +
                "fail_count INT DEFAULT 0, " +
                "status VARCHAR(20) NOT NULL DEFAULT 'PENDING', " +
                "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, " +
                "finished_at TIMESTAMP NULL, " +
                "INDEX idx_user (user_id), " +
                "INDEX idx_status (status)" +
                ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4";
        String sql2 = "CREATE TABLE IF NOT EXISTS git_version_results (" +
                "id BIGINT AUTO_INCREMENT PRIMARY KEY, " +
                "task_id BIGINT NOT NULL, " +
                "project_id BIGINT NOT NULL, " +
                "project_name VARCHAR(255) NOT NULL, " +
                "team_id BIGINT NOT NULL DEFAULT 0, " +
                "team_name VARCHAR(100), " +
                "repo_url VARCHAR(500), " +
                "has_changes TINYINT(1) DEFAULT 0, " +
                "changed_files VARCHAR(50) DEFAULT '0', " +
                "insertions VARCHAR(50) DEFAULT '0', " +
                "deletions VARCHAR(50) DEFAULT '0', " +
                "diff_summary TEXT, " +
                "diff_detail TEXT, " +
                "error TEXT, " +
                "status VARCHAR(20) DEFAULT 'PENDING', " +
                "ai_analysis TEXT, " +
                "INDEX idx_task (task_id), " +
                "INDEX idx_team (team_id)" +
                ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4";
        try (Connection conn = DBUtil.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute(sql);
            stmt.execute(sql2);
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    public boolean insert(VersionComparison vc) {
        String sql = "INSERT INTO git_version_comparisons " +
                "(user_id, version_name, base_branch, compare_branch, total_repos, success_count, fail_count, status) " +
                "VALUES (?, ?, ?, ?, ?, 0, 0, ?)";
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setLong(1, vc.getUserId());
            ps.setString(2, vc.getVersionName());
            ps.setString(3, vc.getBaseBranch());
            ps.setString(4, vc.getCompareBranch());
            ps.setInt(5, vc.getTotalRepos() != null ? vc.getTotalRepos() : 0);
            ps.setString(6, vc.getStatus());
            int affected = ps.executeUpdate();
            if (affected > 0) {
                ResultSet rs = ps.getGeneratedKeys();
                if (rs.next()) vc.setId(rs.getLong(1));
                return true;
            }
            return false;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    public boolean updateStatus(Long id, String status, Integer successCount, Integer failCount) {
        String sql;
        if ("DONE".equals(status) || "FAILED".equals(status)) {
            sql = "UPDATE git_version_comparisons SET status=?, success_count=?, fail_count=?, finished_at=NOW() WHERE id=?";
        } else {
            sql = "UPDATE git_version_comparisons SET status=?, success_count=?, fail_count=? WHERE id=?";
        }
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, status);
            ps.setInt(2, successCount != null ? successCount : 0);
            ps.setInt(3, failCount != null ? failCount : 0);
            ps.setLong(4, id);
            return ps.executeUpdate() > 0;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    public List<VersionComparison> getByUserId(Long userId) {
        List<VersionComparison> list = new ArrayList<>();
        String sql = "SELECT * FROM git_version_comparisons WHERE user_id=? ORDER BY created_at DESC LIMIT 50";
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

    /**
     * 获取用户已完成对比的每日汇总（按日期分组）
     */
    public List<JsonObject> getDailyList(Long userId) {
        List<JsonObject> list = new ArrayList<>();
        String sql = "SELECT " +
                "  DATE(created_at) as stat_date, " +
                "  COUNT(*) as total_tasks, " +
                "  SUM(CASE WHEN status='DONE' THEN 1 ELSE 0 END) as done_tasks, " +
                "  SUM(CASE WHEN status='FAILED' THEN 1 ELSE 0 END) as failed_tasks, " +
                "  SUM(success_count) as total_success, " +
                "  SUM(fail_count) as total_fail " +
                "FROM git_version_comparisons " +
                "WHERE user_id=? AND status IN ('DONE','FAILED') " +
                "GROUP BY DATE(created_at) " +
                "ORDER BY stat_date DESC " +
                "LIMIT 30";
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, userId);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                JsonObject day = new JsonObject();
                day.addProperty("date", rs.getDate("stat_date").toString());
                day.addProperty("totalTasks", rs.getInt("total_tasks"));
                day.addProperty("doneTasks", rs.getInt("done_tasks"));
                day.addProperty("failedTasks", rs.getInt("failed_tasks"));
                day.addProperty("totalSuccess", rs.getInt("total_success"));
                day.addProperty("totalFail", rs.getInt("total_fail"));
                list.add(day);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return list;
    }

    /**
     * 获取某一天的所有对比记录
     */
    public List<VersionComparison> getByDate(Long userId, String date) {
        List<VersionComparison> list = new ArrayList<>();
        String sql = "SELECT * FROM git_version_comparisons " +
                "WHERE user_id=? AND DATE(created_at)=? AND status IN ('DONE','FAILED') " +
                "ORDER BY created_at DESC";
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, userId);
            ps.setString(2, date);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) list.add(mapRow(rs));
        } catch (Exception e) {
            e.printStackTrace();
        }
        return list;
    }

    public VersionComparison getById(Long id) {
        String sql = "SELECT * FROM git_version_comparisons WHERE id=?";
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

    private VersionComparison mapRow(ResultSet rs) throws SQLException {
        VersionComparison vc = new VersionComparison();
        vc.setId(rs.getLong("id"));
        vc.setUserId(rs.getLong("user_id"));
        vc.setVersionName(rs.getString("version_name"));
        vc.setBaseBranch(rs.getString("base_branch"));
        vc.setCompareBranch(rs.getString("compare_branch"));
        vc.setTotalRepos(rs.getInt("total_repos"));
        vc.setSuccessCount(rs.getInt("success_count"));
        vc.setFailCount(rs.getInt("fail_count"));
        vc.setStatus(rs.getString("status"));
        Timestamp createdAt = rs.getTimestamp("created_at");
        vc.setCreatedAt(createdAt != null ? createdAt.toString() : null);
        vc.setFinishedAt(rs.getTimestamp("finished_at"));
        return vc;
    }
}
