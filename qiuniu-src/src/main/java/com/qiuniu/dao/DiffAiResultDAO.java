package com.qiuniu.dao;

import com.qiuniu.model.DiffAiResult;
import com.qiuniu.dao.DBUtil;
import java.sql.*;
import java.time.LocalDateTime;

public class DiffAiResultDAO {

    public void createTable() {
        String sql = "CREATE TABLE IF NOT EXISTS diff_ai_results ("
            + "id INT AUTO_INCREMENT PRIMARY KEY,"
            + "project_name VARCHAR(255) NOT NULL,"
            + "base_branch VARCHAR(128) NOT NULL,"
            + "compare_branch VARCHAR(128) NOT NULL,"
            + "diff_hash VARCHAR(64) NOT NULL,"
            + "diff_content LONGTEXT,"
            + "ai_analysis LONGTEXT NOT NULL,"
            + "incident_check_result LONGTEXT,"
            + "file_type VARCHAR(64),"
            + "model_used VARCHAR(128),"
            + "prompt_used VARCHAR(255),"
            + "created_at DATETIME DEFAULT CURRENT_TIMESTAMP,"
            + "updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,"
            + "UNIQUE KEY uk_project_branches (project_name, base_branch, compare_branch)"
            + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci";
        try (Connection conn = DBUtil.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute(sql);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public DiffAiResult findByProjectAndBranches(String projectName, String baseBranch, String compareBranch) {
        String sql = "SELECT * FROM diff_ai_results WHERE project_name=? AND base_branch=? AND compare_branch=? LIMIT 1";
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, projectName);
            ps.setString(2, baseBranch);
            ps.setString(3, compareBranch);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return mapRow(rs);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    public int save(DiffAiResult r) {
        String sql = "INSERT INTO diff_ai_results (project_name,base_branch,compare_branch,diff_hash,diff_content,ai_analysis,incident_check_result,file_type,model_used,prompt_used,created_at,updated_at) VALUES(?,?,?,?,?,?,?,?,?,?,?,?)";
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, r.getProjectName());
            ps.setString(2, r.getBaseBranch());
            ps.setString(3, r.getCompareBranch());
            ps.setString(4, r.getDiffHash());
            ps.setString(5, r.getDiffContent());
            ps.setString(6, r.getAiAnalysis());
            ps.setString(7, r.getIncidentCheckResult());
            ps.setString(8, r.getFileType());
            ps.setString(9, r.getModelUsed());
            ps.setString(10, r.getPromptUsed());
            ps.setTimestamp(11, Timestamp.valueOf(LocalDateTime.now()));
            ps.setTimestamp(12, Timestamp.valueOf(LocalDateTime.now()));
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) {
                    return rs.getInt(1);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return -1;
    }

    public int update(DiffAiResult r) {
        String sql = "UPDATE diff_ai_results SET diff_hash=?,diff_content=?,ai_analysis=?,incident_check_result=?,file_type=?,model_used=?,prompt_used=?,updated_at=? WHERE id=?";
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, r.getDiffHash());
            ps.setString(2, r.getDiffContent());
            ps.setString(3, r.getAiAnalysis());
            ps.setString(4, r.getIncidentCheckResult());
            ps.setString(5, r.getFileType());
            ps.setString(6, r.getModelUsed());
            ps.setString(7, r.getPromptUsed());
            ps.setTimestamp(8, Timestamp.valueOf(LocalDateTime.now()));
            ps.setInt(9, r.getId());
            return ps.executeUpdate();
        } catch (Exception e) {
            e.printStackTrace();
        }
        return -1;
    }

    private DiffAiResult mapRow(ResultSet rs) throws SQLException {
        DiffAiResult r = new DiffAiResult();
        r.setId(rs.getInt("id"));
        r.setProjectName(rs.getString("project_name"));
        r.setBaseBranch(rs.getString("base_branch"));
        r.setCompareBranch(rs.getString("compare_branch"));
        r.setDiffHash(rs.getString("diff_hash"));
        r.setDiffContent(rs.getString("diff_content"));
        r.setAiAnalysis(rs.getString("ai_analysis"));
        r.setIncidentCheckResult(rs.getString("incident_check_result"));
        r.setFileType(rs.getString("file_type"));
        r.setModelUsed(rs.getString("model_used"));
        r.setPromptUsed(rs.getString("prompt_used"));
        r.setCreatedAt(toLocalDateTime(rs.getTimestamp("created_at")));
        r.setUpdatedAt(toLocalDateTime(rs.getTimestamp("updated_at")));
        return r;
    }

    private LocalDateTime toLocalDateTime(Timestamp ts) {
        return ts != null ? ts.toLocalDateTime() : null;
    }
}
