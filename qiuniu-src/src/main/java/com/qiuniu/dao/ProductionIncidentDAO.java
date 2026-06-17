package com.qiuniu.dao;

import com.qiuniu.model.ProductionIncident;
import com.qiuniu.dao.DBUtil;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/**
 * 生产事故数据访问层
 */
public class ProductionIncidentDAO {

    /**
     * 插入事故记录
     */
    public int insert(ProductionIncident incident) {
        String sql = "INSERT INTO production_incidents (incident_no, title, severity, incident_type, " +
                "occurrence_time, discovery_time, resolution_time, affected_systems, affected_modules, " +
                "root_cause, solution, lessons_learned, code_patterns, file_paths, keywords, status, " +
                "is_restricted, created_by) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            
            ps.setString(1, incident.getIncidentNo());
            ps.setString(2, incident.getTitle());
            ps.setString(3, incident.getSeverity());
            ps.setString(4, incident.getIncidentType());
            ps.setTimestamp(5, incident.getOccurrenceTime());
            ps.setTimestamp(6, incident.getDiscoveryTime());
            ps.setTimestamp(7, incident.getResolutionTime());
            ps.setString(8, incident.getAffectedSystems());
            ps.setString(9, incident.getAffectedModules());
            ps.setString(10, incident.getRootCause());
            ps.setString(11, incident.getSolution());
            ps.setString(12, incident.getLessonsLearned());
            ps.setString(13, incident.getCodePatterns());
            ps.setString(14, incident.getFilePaths());
            ps.setString(15, incident.getKeywords());
            ps.setString(16, incident.getStatus() != null ? incident.getStatus() : "active");
            ps.setBoolean(17, incident.isRestricted());
            ps.setString(18, incident.getCreatedBy());
            
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
     * 更新事故记录（部分更新：只更新非NULL字段）
     */
    public boolean update(ProductionIncident incident) {
        List<String> setClauses = new ArrayList<>();
        List<Object> params = new ArrayList<>();
        
        if (incident.getTitle() != null) {
            setClauses.add("title=?");
            params.add(incident.getTitle());
        }
        if (incident.getSeverity() != null) {
            setClauses.add("severity=?");
            params.add(incident.getSeverity());
        }
        if (incident.getIncidentType() != null) {
            setClauses.add("incident_type=?");
            params.add(incident.getIncidentType());
        }
        if (incident.getOccurrenceTime() != null) {
            setClauses.add("occurrence_time=?");
            params.add(incident.getOccurrenceTime());
        }
        if (incident.getDiscoveryTime() != null) {
            setClauses.add("discovery_time=?");
            params.add(incident.getDiscoveryTime());
        }
        if (incident.getResolutionTime() != null) {
            setClauses.add("resolution_time=?");
            params.add(incident.getResolutionTime());
        }
        if (incident.getAffectedSystems() != null) {
            setClauses.add("affected_systems=?");
            params.add(incident.getAffectedSystems());
        }
        if (incident.getAffectedModules() != null) {
            setClauses.add("affected_modules=?");
            params.add(incident.getAffectedModules());
        }
        if (incident.getRootCause() != null) {
            setClauses.add("root_cause=?");
            params.add(incident.getRootCause());
        }
        if (incident.getSolution() != null) {
            setClauses.add("solution=?");
            params.add(incident.getSolution());
        }
        if (incident.getLessonsLearned() != null) {
            setClauses.add("lessons_learned=?");
            params.add(incident.getLessonsLearned());
        }
        if (incident.getCodePatterns() != null) {
            setClauses.add("code_patterns=?");
            params.add(incident.getCodePatterns());
        }
        if (incident.getFilePaths() != null) {
            setClauses.add("file_paths=?");
            params.add(incident.getFilePaths());
        }
        if (incident.getKeywords() != null) {
            setClauses.add("keywords=?");
            params.add(incident.getKeywords());
        }
        if (incident.getStatus() != null) {
            setClauses.add("status=?");
            params.add(incident.getStatus());
        }
        
        // is_restricted 无条件更新
        setClauses.add("is_restricted=?");
        params.add(incident.isRestricted());
        
        if (setClauses.isEmpty()) {
            return false;
        }
        
        String sql = "UPDATE production_incidents SET " + String.join(", ", setClauses) + " WHERE id=?";
        params.add(incident.getId());
        
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            for (int i = 0; i < params.size(); i++) {
                ps.setObject(i + 1, params.get(i));
            }
            return ps.executeUpdate() > 0;
        } catch (Exception e) {
            e.printStackTrace();
        }
        return false;
    }

    /**
     * 删除事故记录
     */
    public boolean delete(int id) {
        String sql = "DELETE FROM production_incidents WHERE id=?";
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
     * 按ID查询
     */
    public ProductionIncident getById(int id) {
        String sql = "SELECT * FROM production_incidents WHERE id=?";
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
     * 按编号查询
     */
    public ProductionIncident getByIncidentNo(String incidentNo) {
        String sql = "SELECT * FROM production_incidents WHERE incident_no=?";
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, incidentNo);
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
     * 列表查询（支持分页、筛选）
     */
    public List<ProductionIncident> list(String status, String severity, String keyword, int page, int pageSize) {
        List<ProductionIncident> list = new ArrayList<>();
        StringBuilder sql = new StringBuilder("SELECT * FROM production_incidents WHERE 1=1");
        List<Object> params = new ArrayList<>();
        
        if (status != null && !status.isEmpty()) {
            sql.append(" AND status=?");
            params.add(status);
        }
        if (severity != null && !severity.isEmpty()) {
            sql.append(" AND severity=?");
            params.add(severity);
        }
        if (keyword != null && !keyword.isEmpty()) {
            sql.append(" AND (title LIKE ? OR incident_no LIKE ? OR keywords LIKE ? OR root_cause LIKE ?)");
            String kw = "%" + keyword + "%";
            params.add(kw);
            params.add(kw);
            params.add(kw);
            params.add(kw);
        }
        
        sql.append(" ORDER BY updated_at DESC LIMIT ?, ?");
        params.add((page - 1) * pageSize);
        params.add(pageSize);
        
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql.toString())) {
            for (int i = 0; i < params.size(); i++) {
                ps.setObject(i + 1, params.get(i));
            }
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
     * 统计数量
     */
    public int count(String status, String severity, String keyword) {
        StringBuilder sql = new StringBuilder("SELECT COUNT(*) FROM production_incidents WHERE 1=1");
        List<Object> params = new ArrayList<>();
        
        if (status != null && !status.isEmpty()) {
            sql.append(" AND status=?");
            params.add(status);
        }
        if (severity != null && !severity.isEmpty()) {
            sql.append(" AND severity=?");
            params.add(severity);
        }
        if (keyword != null && !keyword.isEmpty()) {
            sql.append(" AND (title LIKE ? OR incident_no LIKE ? OR keywords LIKE ? OR root_cause LIKE ?)");
            String kw = "%" + keyword + "%";
            params.add(kw);
            params.add(kw);
            params.add(kw);
            params.add(kw);
        }
        
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql.toString())) {
            for (int i = 0; i < params.size(); i++) {
                ps.setObject(i + 1, params.get(i));
            }
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                return rs.getInt(1);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return 0;
    }

    /**
     * 关键词搜索（用于检核）
     */
    public List<ProductionIncident> searchByKeywords(String[] keywords) {
        if (keywords == null || keywords.length == 0) {
            return new ArrayList<>();
        }
        
        List<ProductionIncident> list = new ArrayList<>();
        StringBuilder sql = new StringBuilder("SELECT * FROM production_incidents WHERE status='active' AND (");
        
        for (int i = 0; i < keywords.length; i++) {
            if (i > 0) sql.append(" OR ");
            sql.append("keywords LIKE ?");
        }
        sql.append(")");
        
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql.toString())) {
            for (int i = 0; i < keywords.length; i++) {
                ps.setString(i + 1, "%" + keywords[i].trim() + "%");
            }
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
     * 文件路径搜索（用于检核）
     */
    public List<ProductionIncident> searchByFilePaths(String[] paths) {
        if (paths == null || paths.length == 0) {
            return new ArrayList<>();
        }
        
        List<ProductionIncident> list = new ArrayList<>();
        StringBuilder sql = new StringBuilder("SELECT * FROM production_incidents WHERE status='active' AND (");
        
        for (int i = 0; i < paths.length; i++) {
            if (i > 0) sql.append(" OR ");
            sql.append("file_paths LIKE ?");
        }
        sql.append(")");
        
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql.toString())) {
            for (int i = 0; i < paths.length; i++) {
                ps.setString(i + 1, "%" + paths[i].trim() + "%");
            }
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
     * 代码模式匹配（用于检核）
     */
    public List<ProductionIncident> searchByCodePatterns(String codeContent) {
        if (codeContent == null || codeContent.isEmpty()) {
            return new ArrayList<>();
        }
        
        List<ProductionIncident> list = new ArrayList<>();
        // 先获取所有 active 的记录，再在代码中匹配正则
        String sql = "SELECT * FROM production_incidents WHERE status='active' AND code_patterns IS NOT NULL AND code_patterns != ''";
        
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            
            while (rs.next()) {
                ProductionIncident incident = mapRow(rs);
                String patterns = incident.getCodePatterns();
                if (patterns != null && !patterns.isEmpty()) {
                    // 逐个正则匹配
                    String[] patternArr = patterns.split("\n");
                    for (String pattern : patternArr) {
                        pattern = pattern.trim();
                        if (!pattern.isEmpty()) {
                            try {
                                if (java.util.regex.Pattern.matches(pattern, codeContent)) {
                                    list.add(incident);
                                    break;
                                }
                            } catch (Exception e) {
                                // 正则表达式无效，跳过
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return list;
    }

    /**
     * 获取所有活跃的事故记录
     */
    public List<ProductionIncident> searchActive() {
        List<ProductionIncident> list = new ArrayList<>();
        String sql = "SELECT * FROM production_incidents WHERE status='active' ORDER BY severity, updated_at";
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                list.add(mapRow(rs));
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return list;
    }

    /**
     * 批量导入
     */
    public int batchImport(List<ProductionIncident> incidents) {
        int count = 0;
        for (ProductionIncident incident : incidents) {
            if (insert(incident) > 0) {
                count++;
            }
        }
        return count;
    }

    private ProductionIncident mapRow(ResultSet rs) throws SQLException {
        ProductionIncident i = new ProductionIncident();
        i.setId(rs.getInt("id"));
        i.setIncidentNo(rs.getString("incident_no"));
        i.setTitle(rs.getString("title"));
        i.setSeverity(rs.getString("severity"));
        i.setIncidentType(rs.getString("incident_type"));
        i.setOccurrenceTime(rs.getTimestamp("occurrence_time"));
        i.setDiscoveryTime(rs.getTimestamp("discovery_time"));
        i.setResolutionTime(rs.getTimestamp("resolution_time"));
        i.setAffectedSystems(rs.getString("affected_systems"));
        i.setAffectedModules(rs.getString("affected_modules"));
        i.setRootCause(rs.getString("root_cause"));
        i.setSolution(rs.getString("solution"));
        i.setLessonsLearned(rs.getString("lessons_learned"));
        i.setCodePatterns(rs.getString("code_patterns"));
        i.setFilePaths(rs.getString("file_paths"));
        i.setKeywords(rs.getString("keywords"));
        i.setStatus(rs.getString("status"));
        i.setRestricted(rs.getBoolean("is_restricted"));
        i.setCreatedBy(rs.getString("created_by"));
        i.setCreatedAt(rs.getTimestamp("created_at"));
        i.setUpdatedAt(rs.getTimestamp("updated_at"));
        return i;
    }
}
