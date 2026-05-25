package com.qiuniu.dao;

import com.qiuniu.model.GitTeam;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class GitTeamDAO {

    public boolean createTable() {
        String sql = "CREATE TABLE IF NOT EXISTS git_teams (" +
                "id BIGINT AUTO_INCREMENT PRIMARY KEY, " +
                "user_id BIGINT NOT NULL, " +
                "name VARCHAR(100) NOT NULL, " +
                "code VARCHAR(50) NOT NULL, " +
                "description VARCHAR(255), " +
                "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, " +
                "UNIQUE KEY uk_code (code)" +
                ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4";
        try (Connection conn = DBUtil.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.executeUpdate(sql);
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    public boolean add(GitTeam team) {
        String sql = "INSERT INTO git_teams (user_id, name, code, description) VALUES (?, ?, ?, ?)";
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setLong(1, team.getUserId());
            ps.setString(2, team.getName());
            ps.setString(3, team.getCode());
            ps.setString(4, team.getDescription());
            if (ps.executeUpdate() > 0) {
                try (ResultSet rs = ps.getGeneratedKeys()) {
                    if (rs.next()) {
                        team.setId(rs.getLong(1));
                    }
                }
                return true;
            }
            return false;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    /**
     * 更新团队的 user_id（用于绑定团队管理员）
     */
    public boolean updateUserId(Long teamId, Long newUserId) {
        String sql = "UPDATE git_teams SET user_id=? WHERE id=?";
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, newUserId);
            ps.setLong(2, teamId);
            return ps.executeUpdate() > 0;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    public boolean update(GitTeam team) {
        String sql = "UPDATE git_teams SET name=?, code=?, description=? WHERE id=? AND user_id=?";
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, team.getName());
            ps.setString(2, team.getCode());
            ps.setString(3, team.getDescription());
            ps.setLong(4, team.getId());
            ps.setLong(5, team.getUserId());
            return ps.executeUpdate() > 0;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    public boolean delete(Long id, Long userId) {
        String sql = "DELETE FROM git_teams WHERE id=? AND user_id=?";
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

    public GitTeam getById(Long id) {
        String sql = "SELECT * FROM git_teams WHERE id=?";
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

    public List<GitTeam> getByUserId(Long userId) {
        List<GitTeam> list = new ArrayList<>();
        String sql = "SELECT * FROM git_teams WHERE user_id=? ORDER BY id ASC";
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
     * 获取所有团队（全局共享）
     */
    public List<GitTeam> getAll() {
        List<GitTeam> list = new ArrayList<>();
        String sql = "SELECT * FROM git_teams ORDER BY id ASC";
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

    public boolean existsByCode(String code) {
        String sql = "SELECT 1 FROM git_teams WHERE code=?";
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, code);
            ResultSet rs = ps.executeQuery();
            return rs.next();
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    private GitTeam mapRow(ResultSet rs) throws SQLException {
        GitTeam t = new GitTeam();
        t.setId(rs.getLong("id"));
        t.setUserId(rs.getLong("user_id"));
        t.setName(rs.getString("name"));
        t.setCode(rs.getString("code"));
        t.setDescription(rs.getString("description"));
        Timestamp ts = rs.getTimestamp("created_at");
        t.setCreatedAt(ts != null ? ts.toString() : null);
        return t;
    }
}
