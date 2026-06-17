package com.qiuniu.servlet;

import com.qiuniu.model.ProductionIncident;
import com.qiuniu.model.User;
import com.qiuniu.model.IncidentCodeLink;
import com.qiuniu.dao.ProductionIncidentDAO;
import com.qiuniu.dao.IncidentCodeLinkDAO;
import com.qiuniu.util.JsonUtil;
import com.qiuniu.util.PermissionHelper;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.*;
import java.io.*;
import java.util.List;

/**
 * 生产事故管理 API Servlet
 * 
 * GET  /api/incidents?action=list&page=1&pageSize=20&status=active&severity=P1
 * GET  /api/incidents?action=get&id=1
 * GET  /api/incidents?action=search&keyword=SQL
 * GET  /api/incidents?action=getCodeLinks&incidentId=1
 * POST /api/incidents?action=create
 * POST /api/incidents?action=update
 * POST /api/incidents?action=delete&id=1
 * POST /api/incidents?action=import
 * POST /api/incidents?action=addCodeLink
 * POST /api/incidents?action=updateCodeLink
 * POST /api/incidents?action=deleteCodeLink&id=1
 */
@WebServlet("/api/incidents/*")
public class ProductionIncidentServlet extends HttpServlet {

    private ProductionIncidentDAO dao = new ProductionIncidentDAO();
    private IncidentCodeLinkDAO codeLinkDao = new IncidentCodeLinkDAO();

    @Override
    public void init() {
        // 表在迁移脚本中已创建
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        resp.setContentType("application/json;charset=UTF-8");
        resp.setCharacterEncoding("UTF-8");
        
        String action = req.getParameter("action");
        if (action == null) action = "list";
        
        switch (action) {
            case "list":
                list(req, resp);
                break;
            case "get":
                get(req, resp);
                break;
            case "search":
                search(req, resp);
                break;
            case "getCodeLinks":
                getCodeLinks(req, resp);
                break;
            default:
                list(req, resp);
        }
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        resp.setContentType("application/json;charset=UTF-8");
        resp.setCharacterEncoding("UTF-8");
        
        // 权限检查
        User user = checkLoginUser(req, resp);
        if (user == null) return;
        
        String action = req.getParameter("action");
        if (action == null) {
            sendError(resp, 400, "missing action");
            return;
        }
        
        switch (action) {
            case "create":
                create(req, resp, user);
                break;
            case "update":
                update(req, resp, user);
                break;
            case "delete":
                delete(req, resp, user);
                break;
            case "import":
                batchImport(req, resp, user);
                break;
            case "addCodeLink":
                addCodeLink(req, resp, user);
                break;
            case "updateCodeLink":
                updateCodeLink(req, resp, user);
                break;
            case "deleteCodeLink":
                deleteCodeLink(req, resp, user);
                break;
            default:
                sendError(resp, 400, "unknown action: " + action);
        }
    }

    // ========== 实现方法 ==========

    private void list(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        String status = req.getParameter("status");
        String severity = req.getParameter("severity");
        String keyword = req.getParameter("keyword");
        int page = parseInt(req.getParameter("page"), 1);
        int pageSize = parseInt(req.getParameter("pageSize"), 20);
        
        List<ProductionIncident> list = dao.list(status, severity, keyword, page, pageSize);
        int total = dao.count(status, severity, keyword);
        
        JsonUtil.write(resp, JsonUtil.obj()
            .put("list", list)
            .put("total", total)
            .put("page", page)
            .put("pageSize", pageSize));
    }

    private void get(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        int id = parseInt(req.getParameter("id"), 0);
        if (id <= 0) {
            sendError(resp, 400, "missing id");
            return;
        }
        
        ProductionIncident incident = dao.getById(id);
        if (incident == null) {
            sendError(resp, 404, "not found");
            return;
        }
        
        JsonUtil.write(resp, incident);
    }

    private void search(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        String keyword = req.getParameter("keyword");
        if (keyword == null || keyword.trim().isEmpty()) {
            sendError(resp, 400, "missing keyword");
            return;
        }
        
        String[] keywords = keyword.split("[,，]");
        List<ProductionIncident> list = dao.searchByKeywords(keywords);
        JsonUtil.write(resp, JsonUtil.obj().put("list", list).put("total", list.size()));
    }

    private void create(HttpServletRequest req, HttpServletResponse resp, User user) throws IOException {
        String incidentNo = req.getParameter("incidentNo");
        String title = req.getParameter("title");
        String severity = req.getParameter("severity");
        String incidentType = req.getParameter("incidentType");
        
        // 验证必填
        if (incidentNo == null || incidentNo.trim().isEmpty() || 
            title == null || title.trim().isEmpty() ||
            incidentType == null || incidentType.trim().isEmpty()) {
            sendError(resp, 400, "missing required fields: incidentNo, title, incidentType");
            return;
        }
        
        // 检查编号唯一
        if (dao.getByIncidentNo(incidentNo) != null) {
            sendError(resp, 400, "incident_no already exists");
            return;
        }
        
        ProductionIncident incident = new ProductionIncident();
        incident.setIncidentNo(incidentNo.trim());
        incident.setTitle(title.trim());
        incident.setSeverity(severity != null ? severity : "P3");
        incident.setIncidentType(incidentType.trim());
        incident.setAffectedSystems(req.getParameter("affectedSystems"));
        incident.setAffectedModules(req.getParameter("affectedModules"));
        incident.setRootCause(req.getParameter("rootCause"));
        incident.setSolution(req.getParameter("solution"));
        incident.setLessonsLearned(req.getParameter("lessonsLearned"));
        incident.setCodePatterns(req.getParameter("codePatterns"));
        incident.setFilePaths(req.getParameter("filePaths"));
        incident.setKeywords(req.getParameter("keywords"));
        incident.setStatus("active");
        incident.setCreatedBy(user.getUsername());
        
        int id = dao.insert(incident);
        if (id > 0) {
            JsonUtil.write(resp, JsonUtil.obj().put("id", id).put("ok", true));
        } else {
            sendError(resp, 500, "insert failed");
        }
    }

    private void update(HttpServletRequest req, HttpServletResponse resp, User user) throws IOException {
        int id = parseInt(req.getParameter("id"), 0);
        if (id <= 0) {
            sendError(resp, 400, "missing id");
            return;
        }
        
        ProductionIncident existing = dao.getById(id);
        if (existing == null) {
            sendError(resp, 404, "not found");
            return;
        }
        
        // 权限检查：仅创建者或 ADMIN 可修改
        if (!existing.getCreatedBy().equals(user.getUsername()) && !user.isAdmin()) {
            sendError(resp, 403, "permission denied");
            return;
        }
        
        ProductionIncident incident = new ProductionIncident();
        incident.setId(id);
        incident.setTitle(req.getParameter("title"));
        incident.setSeverity(req.getParameter("severity"));
        incident.setIncidentType(req.getParameter("incidentType"));
        incident.setOccurrenceTime(parseTimestamp(req.getParameter("occurrenceTime")));
        incident.setDiscoveryTime(parseTimestamp(req.getParameter("discoveryTime")));
        incident.setResolutionTime(parseTimestamp(req.getParameter("resolutionTime")));
        incident.setAffectedSystems(req.getParameter("affectedSystems"));
        incident.setAffectedModules(req.getParameter("affectedModules"));
        incident.setRootCause(req.getParameter("rootCause"));
        incident.setSolution(req.getParameter("solution"));
        incident.setLessonsLearned(req.getParameter("lessonsLearned"));
        incident.setCodePatterns(req.getParameter("codePatterns"));
        incident.setFilePaths(req.getParameter("filePaths"));
        incident.setKeywords(req.getParameter("keywords"));
        incident.setStatus(req.getParameter("status"));
        incident.setRestricted(parseBoolean(req.getParameter("isRestricted"), false));
        
        boolean success = dao.update(incident);
        JsonUtil.write(resp, JsonUtil.obj().put("ok", success));
    }

    private void delete(HttpServletRequest req, HttpServletResponse resp, User user) throws IOException {
        // 权限检查：仅 ADMIN 可删除
        if (!user.isAdmin()) {
            sendError(resp, 403, "permission denied: admin only");
            return;
        }
        
        int id = parseInt(req.getParameter("id"), 0);
        if (id <= 0) {
            sendError(resp, 400, "missing id");
            return;
        }
        
        // 先删代码关联，再删事故
        codeLinkDao.deleteByIncidentId(id);
        boolean success = dao.delete(id);
        JsonUtil.write(resp, JsonUtil.obj().put("ok", success));
    }

    private void batchImport(HttpServletRequest req, HttpServletResponse resp, User user) throws IOException {
        // 权限检查：仅 ADMIN 可批量导入
        if (!user.isAdmin()) {
            sendError(resp, 403, "permission denied: admin only");
            return;
        }
        
        // 读取请求体
        StringBuilder sb = new StringBuilder();
        try (BufferedReader reader = req.getReader()) {
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
        }
        
        String json = sb.toString();
        if (json.isEmpty()) {
            sendError(resp, 400, "empty request body");
            return;
        }
        
        // 解析 JSON 数组
        List<ProductionIncident> incidents = JsonUtil.parseIncidents(json);
        if (incidents == null || incidents.isEmpty()) {
            sendError(resp, 400, "invalid JSON format");
            return;
        }
        
        int count = dao.batchImport(incidents);
        JsonUtil.write(resp, JsonUtil.obj()
            .put("imported", count)
            .put("total", incidents.size()));
    }

    // ========== 代码关联管理方法 ==========

    private void getCodeLinks(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        int incidentId = parseInt(req.getParameter("incidentId"), 0);
        if (incidentId <= 0) {
            sendError(resp, 400, "missing incidentId");
            return;
        }
        
        List<IncidentCodeLink> links = codeLinkDao.getByIncidentId(incidentId);
        JsonUtil.write(resp, JsonUtil.obj().put("list", links).put("total", links.size()));
    }

    private void addCodeLink(HttpServletRequest req, HttpServletResponse resp, User user) throws IOException {
        int incidentId = parseInt(req.getParameter("incidentId"), 0);
        if (incidentId <= 0) {
            sendError(resp, 400, "missing incidentId");
            return;
        }
        
        ProductionIncident incident = dao.getById(incidentId);
        if (incident == null) {
            sendError(resp, 404, "incident not found");
            return;
        }
        
        IncidentCodeLink link = new IncidentCodeLink();
        link.setIncidentId(incidentId);
        link.setRepoName(req.getParameter("repoName"));
        link.setRepoUrl(req.getParameter("repoUrl"));
        link.setFilePath(req.getParameter("filePath"));
        
        String lineStartStr = req.getParameter("lineStart");
        if (lineStartStr != null && !lineStartStr.isEmpty()) {
            link.setLineStart(Integer.parseInt(lineStartStr));
        }
        
        String lineEndStr = req.getParameter("lineEnd");
        if (lineEndStr != null && !lineEndStr.isEmpty()) {
            link.setLineEnd(Integer.parseInt(lineEndStr));
        }
        
        link.setCodeSnippet(req.getParameter("codeSnippet"));
        link.setFixSnippet(req.getParameter("fixSnippet"));
        
        int id = codeLinkDao.insert(link);
        if (id > 0) {
            JsonUtil.write(resp, JsonUtil.obj().put("id", id).put("ok", true));
        } else {
            sendError(resp, 500, "insert failed");
        }
    }

    private void updateCodeLink(HttpServletRequest req, HttpServletResponse resp, User user) throws IOException {
        int id = parseInt(req.getParameter("id"), 0);
        if (id <= 0) {
            sendError(resp, 400, "missing id");
            return;
        }
        
        IncidentCodeLink existing = codeLinkDao.getById(id);
        if (existing == null) {
            sendError(resp, 404, "not found");
            return;
        }
        
        IncidentCodeLink link = new IncidentCodeLink();
        link.setId(id);
        link.setIncidentId(existing.getIncidentId());
        link.setRepoName(req.getParameter("repoName"));
        link.setRepoUrl(req.getParameter("repoUrl"));
        link.setFilePath(req.getParameter("filePath"));
        
        String lineStartStr = req.getParameter("lineStart");
        if (lineStartStr != null && !lineStartStr.isEmpty()) {
            link.setLineStart(Integer.parseInt(lineStartStr));
        }
        
        String lineEndStr = req.getParameter("lineEnd");
        if (lineEndStr != null && !lineEndStr.isEmpty()) {
            link.setLineEnd(Integer.parseInt(lineEndStr));
        }
        
        link.setCodeSnippet(req.getParameter("codeSnippet"));
        link.setFixSnippet(req.getParameter("fixSnippet"));
        
        boolean success = codeLinkDao.update(link);
        JsonUtil.write(resp, JsonUtil.obj().put("ok", success));
    }

    private void deleteCodeLink(HttpServletRequest req, HttpServletResponse resp, User user) throws IOException {
        int id = parseInt(req.getParameter("id"), 0);
        if (id <= 0) {
            sendError(resp, 400, "missing id");
            return;
        }
        
        boolean success = codeLinkDao.delete(id);
        JsonUtil.write(resp, JsonUtil.obj().put("ok", success));
    }

    // ========== 辅助方法 ==========

    private User checkLoginUser(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        User user = PermissionHelper.getLoginUser(req);
        if (user == null) {
            sendError(resp, 401, "not logged in");
            return null;
        }
        return user;
    }

    private void sendError(HttpServletResponse resp, int code, String message) throws IOException {
        resp.setStatus(code);
        JsonUtil.write(resp, JsonUtil.obj()
            .put("error", message)
            .put("code", code));
    }

    private int parseInt(String s, int defaultVal) {
        if (s == null) return defaultVal;
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException e) {
            return defaultVal;
        }
    }

    private boolean parseBoolean(String s, boolean defaultVal) {
        if (s == null) return defaultVal;
        return "true".equalsIgnoreCase(s) || "1".equals(s);
    }

    private java.sql.Timestamp parseTimestamp(String s) {
        if (s == null || s.isEmpty()) return null;
        try {
            return java.sql.Timestamp.valueOf(s);
        } catch (Exception e) {
            return null;
        }
    }
}