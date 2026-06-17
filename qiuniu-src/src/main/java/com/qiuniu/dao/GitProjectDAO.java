package com.qiuniu.dao;

import com.qiuniu.model.GitProject;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class GitProjectDAO {

    public boolean createTable() {
        String sql = "CREATE TABLE IF NOT EXISTS git_projects (" +
                "id INT AUTO_INCREMENT PRIMARY KEY, " +
                "user_id BIGINT NOT NULL, " +
                "team_id BIGINT, " +
                "name VARCHAR(255) NOT NULL, " +
                "repo_url VARCHAR(500) NOT NULL, " +
                "base_branch VARCHAR(100) DEFAULT 'main', " +
                "compare_branch VARCHAR(100) DEFAULT 'develop', " +
                "last_sync DATETIME, " +
                "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, " +
                "INDEX idx_team (team_id), " +
                "INDEX idx_user (user_id)" +
                ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4";
        try (Connection conn = DBUtil.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.executeUpdate(sql);

            // 尝试新增 team_id 列（如果表是老版本没有这列）
            try {
                stmt.executeUpdate("ALTER TABLE git_projects ADD COLUMN team_id BIGINT AFTER user_id");
            } catch (SQLException ignored) {}

            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    public boolean add(GitProject project) {
        String sql = "INSERT INTO git_projects (user_id, team_id, name, repo_url, base_branch, compare_branch) VALUES (?, ?, ?, ?, ?, ?)";
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, project.getUserId());
            if (project.getTeamId() != null) {
                ps.setLong(2, project.getTeamId());
            } else {
                ps.setNull(2, Types.BIGINT);
            }
            ps.setString(3, project.getName());
            ps.setString(4, project.getRepoUrl());
            ps.setString(5, project.getBaseBranch());
            ps.setString(6, project.getCompareBranch());
            return ps.executeUpdate() > 0;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    public boolean update(GitProject project) {
        String sql = "UPDATE git_projects SET name=?, repo_url=?, base_branch=?, compare_branch=?, team_id=? WHERE id=? AND user_id=?";
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, project.getName());
            ps.setString(2, project.getRepoUrl());
            ps.setString(3, project.getBaseBranch());
            ps.setString(4, project.getCompareBranch());
            if (project.getTeamId() != null) {
                ps.setLong(5, project.getTeamId());
            } else {
                ps.setNull(5, Types.BIGINT);
            }
            ps.setLong(6, project.getId());
            ps.setLong(7, project.getUserId());
            return ps.executeUpdate() > 0;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    public boolean delete(Long id, Long userId) {
        String sql = "DELETE FROM git_projects WHERE id=? AND user_id=?";
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, id);
            ps.setLong(2, userId);
            return ps.executeUpdate() > 0;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    /**
     * 删除项目（无用户限制，仅供管理员使用）
     */
    public boolean delete(Long id) {
        String sql = "DELETE FROM git_projects WHERE id=?";
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, id);
            return ps.executeUpdate() > 0;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    public GitProject getById(Long id, Long userId) {
        String sql = "SELECT p.*, t.name as team_name FROM git_projects p " +
                     "LEFT JOIN git_teams t ON p.team_id = t.id WHERE p.id=? AND p.user_id=?";
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, id);
            ps.setLong(2, userId);
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
     * 根据 ID 获取项目（全局共享，无用户限制）
     */
    public GitProject getById(Long id) {
        String sql = "SELECT p.*, t.name as team_name FROM git_projects p " +
                     "LEFT JOIN git_teams t ON p.team_id = t.id WHERE p.id=?";
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, id);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                return mapRow(rs);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    public List<GitProject> getByUserId(Long userId) {
        List<GitProject> list = new ArrayList<>();
        String sql = "SELECT p.*, t.name as team_name FROM git_projects p " +
                     "LEFT JOIN git_teams t ON p.team_id = t.id WHERE p.user_id=? ORDER BY p.created_at DESC";
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, userId);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                list.add(mapRow(rs));
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return list;
    }

    public List<GitProject> getByTeamId(Long teamId) {
        List<GitProject> list = new ArrayList<>();
        String sql = "SELECT p.*, t.name as team_name FROM git_projects p " +
                     "LEFT JOIN git_teams t ON p.team_id = t.id WHERE p.team_id=? ORDER BY p.created_at DESC";
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, teamId);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                list.add(mapRow(rs));
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return list;
    }

    public List<GitProject> getByUserIdAndTeamId(Long userId, Long teamId) {
        List<GitProject> list = new ArrayList<>();
        String sql = "SELECT p.*, t.name as team_name FROM git_projects p " +
                     "LEFT JOIN git_teams t ON p.team_id = t.id WHERE p.user_id=? AND p.team_id=? ORDER BY p.created_at DESC";
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, userId);
            ps.setLong(2, teamId);
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
     * 获取用户所有项目（跨团队，用于批量比对）
     */
    public List<GitProject> getAllByUserId(Long userId) {
        List<GitProject> list = new ArrayList<>();
        String sql = "SELECT p.*, t.name as team_name FROM git_projects p " +
                     "LEFT JOIN git_teams t ON p.team_id = t.id WHERE p.user_id=? ORDER BY t.name, p.name";
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, userId);
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
     * 获取所有项目（全局共享）
     */
    public List<GitProject> getAll() {
        List<GitProject> list = new ArrayList<>();
        String sql = "SELECT p.*, t.name as team_name FROM git_projects p " +
                     "LEFT JOIN git_teams t ON p.team_id = t.id ORDER BY t.name, p.name";
        try (Connection conn = DBUtil.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                list.add(mapRow(rs));
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return list;
    }

    public boolean updateLastSync(Long id) {
        String sql = "UPDATE git_projects SET last_sync=NOW() WHERE id=?";
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, id);
            return ps.executeUpdate() > 0;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    private GitProject mapRow(ResultSet rs) throws SQLException {
        GitProject p = new GitProject();
        p.setId(rs.getLong("id"));
        p.setUserId(rs.getLong("user_id"));

        long teamId = rs.getLong("team_id");
        if (rs.wasNull()) {
            p.setTeamId(null);
        } else {
            p.setTeamId(teamId);
        }

        p.setTeamName(rs.getString("team_name"));
        p.setName(rs.getString("name"));
        p.setRepoUrl(rs.getString("repo_url"));
        p.setBaseBranch(rs.getString("base_branch"));
        p.setCompareBranch(rs.getString("compare_branch"));
        Timestamp lastSync = rs.getTimestamp("last_sync");
        p.setLastSync(lastSync != null ? lastSync.toString() : null);
        Timestamp createdAt = rs.getTimestamp("created_at");
        p.setCreatedAt(createdAt != null ? createdAt.toString() : null);
        return p;
    }
}
