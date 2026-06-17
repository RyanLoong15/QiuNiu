package com.qiuniu.dao;

import java.sql.*;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.TimeZone;

/**
 * Git 差异缓存 DAO
 * 按团队+日期+分支缓存差异结果，避免重复调用 Git 接口
 */
public class DiffCacheDAO {

    /**
     * 创建缓存表
     */
    public boolean createTable() {
        String sql = "CREATE TABLE IF NOT EXISTS git_diff_cache (" +
                "id BIGINT AUTO_INCREMENT PRIMARY KEY, " +
                "team_id BIGINT NOT NULL, " +
                "base_branch VARCHAR(100) NOT NULL, " +
                "compare_branch VARCHAR(100) NOT NULL, " +
                "cache_date DATE NOT NULL, " +
                "cache_data TEXT NOT NULL, " +
                "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, " +
                "updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP, " +
                "UNIQUE KEY uk_team_branch_date (team_id, base_branch, compare_branch, cache_date), " +
                "INDEX idx_team (team_id), " +
                "INDEX idx_date (cache_date)" +
                ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4";
        try (Connection conn = DBUtil.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute(sql);
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    /**
     * 获取缓存键：team_id + base_branch + compare_branch + 当天日期
     */
    private String getCacheKey(Long teamId, String baseBranch, String compareBranch) {
        return teamId + "|" + baseBranch + "|" + compareBranch + "|" + getTodayDate();
    }

    /**
     * 获取今天的日期字符串
     */
    private String getTodayDate() {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
        sdf.setTimeZone(TimeZone.getTimeZone("Asia/Shanghai"));
        return sdf.format(new Date());
    }

    /**
     * 检查缓存是否存在（当天有效）
     */
    public boolean hasCache(Long teamId, String baseBranch, String compareBranch) {
        String sql = "SELECT 1 FROM git_diff_cache WHERE team_id=? AND base_branch=? AND compare_branch=? AND cache_date=CURDATE() LIMIT 1";
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, teamId);
            ps.setString(2, baseBranch);
            ps.setString(3, compareBranch);
            ResultSet rs = ps.executeQuery();
            return rs.next();
        } catch (Exception e) {
            System.err.println("[DiffCacheDAO] hasCache error: " + e.getMessage());
            return false;
        }
    }

    /**
     * 获取缓存的差异数据
     */
    public String getCache(Long teamId, String baseBranch, String compareBranch) {
        String sql = "SELECT cache_data FROM git_diff_cache WHERE team_id=? AND base_branch=? AND compare_branch=? AND cache_date=CURDATE() LIMIT 1";
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, teamId);
            ps.setString(2, baseBranch);
            ps.setString(3, compareBranch);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                return rs.getString("cache_data");
            }
        } catch (Exception e) {
            System.err.println("[DiffCacheDAO] getCache error: " + e.getMessage());
        }
        return null;
    }

    /**
     * 保存差异数据到缓存（如果已存在则更新）
     */
    public boolean saveCache(Long teamId, String baseBranch, String compareBranch, String cacheData) {
        String sql = "INSERT INTO git_diff_cache (team_id, base_branch, compare_branch, cache_date, cache_data) " +
                "VALUES (?, ?, ?, CURDATE(), ?) " +
                "ON DUPLICATE KEY UPDATE cache_data=?, updated_at=NOW()";
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, teamId);
            ps.setString(2, baseBranch);
            ps.setString(3, compareBranch);
            ps.setString(4, cacheData);
            ps.setString(5, cacheData);
            return ps.executeUpdate() > 0;
        } catch (Exception e) {
            System.err.println("[DiffCacheDAO] saveCache error: " + e.getMessage());
            return false;
        }
    }

    /**
     * 清除指定团队的缓存
     */
    public boolean clearCache(Long teamId) {
        String sql = "DELETE FROM git_diff_cache WHERE team_id=?";
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, teamId);
            return ps.executeUpdate() > 0;
        } catch (Exception e) {
            System.err.println("[DiffCacheDAO] clearCache error: " + e.getMessage());
            return false;
        }
    }

    /**
     * 清除所有过期缓存（保留最近N天）
     */
    public int clearExpiredCache(int daysToKeep) {
        String sql = "DELETE FROM git_diff_cache WHERE cache_date < DATE_SUB(CURDATE(), INTERVAL ? DAY)";
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, daysToKeep);
            return ps.executeUpdate();
        } catch (Exception e) {
            System.err.println("[DiffCacheDAO] clearExpiredCache error: " + e.getMessage());
            return 0;
        }
    }
}
