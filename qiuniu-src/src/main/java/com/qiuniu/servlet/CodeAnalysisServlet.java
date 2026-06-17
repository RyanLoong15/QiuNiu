package com.qiuniu.servlet;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.qiuniu.dao.CodeAnalysisReportDAO;
import com.qiuniu.dao.GitProjectDAO;
import com.qiuniu.model.CodeAnalysisReport;
import com.qiuniu.model.CodeRuleSet;
import com.qiuniu.model.GitProject;
import com.qiuniu.model.User;
import com.qiuniu.service.CodeAnalyzerService;
import com.qiuniu.util.AIModelClient;
import com.qiuniu.util.PermissionHelper;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.io.BufferedReader;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Code Analysis Servlet
 *
 * API:
 * GET  /codeAnalysis?action=list              - 列表（支持 projectId 过滤）
 * GET  /codeAnalysis?action=get&id=           - 详情（包含报告内容）
 * GET  /codeAnalysis?action=status&id=        - 进度
 * GET  /codeAnalysis?action=rulesets          - 规则集列表
 * GET  /codeAnalysis?action=history&page=    - 历史报告（分页，管理员）
 * POST /codeAnalysis?action=start             - 启动全量分析
 * POST /codeAnalysis?action=analyzeFiles     - 分析指定文件列表
 * POST /codeAnalysis?action=adminDelete&id=  - 管理员删除单条报告
 * POST /codeAnalysis?action=adminPurge&days= - 管理员清理N天前报告
 */
@WebServlet(urlPatterns = {"/codeAnalysis", "/repo/analyze", "/repo/analyzeDiff"}, loadOnStartup = 1)
public class CodeAnalysisServlet extends HttpServlet {

    private static final long serialVersionUID = 1L;
    private static final Gson gson = new Gson();

    private final CodeAnalysisReportDAO reportDAO = new CodeAnalysisReportDAO();
    private final GitProjectDAO projectDAO = new GitProjectDAO();
    private final AIModelClient aiClient = new AIModelClient();

    private static CodeAnalyzerService sharedService;
    private static final Object serviceLock = new Object();

    // running task -> cancel flag
    private static final Map<Long, AtomicBoolean> runningTasks = new ConcurrentHashMap<>();

    @Override
    public void init() throws ServletException {
        reportDAO.createTable();
        sharedService = new CodeAnalyzerService(aiClient);
        System.out.println("[CodeAnalysisServlet] initialized, AI enabled=" + aiClient.isEnabled());
    }

    // ─── HTTP Methods ──────────────────────────────────────────────

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        request.setCharacterEncoding("UTF-8");
        response.setContentType("application/json;charset=UTF-8");
        if (!checkLogin(request, response)) return;

        String action = request.getParameter("action");
        switch (action != null ? action : "list") {
            case "list":     listReports(request, response); break;
            case "get":      getReport(request, response);    break;
            case "status":   getStatus(request, response);   break;
            case "rulesets": getRuleSets(response);          break;
            case "history":  listHistory(request, response); break;
            default:         listReports(request, response); break;
        }
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        request.setCharacterEncoding("UTF-8");
        response.setContentType("application/json;charset=UTF-8");
        User user = checkLoginUser(request, response);
        if (user == null) return;

        String action = request.getParameter("action");
        switch (action != null ? action : "") {
            case "start":
            case "analyze":      startFullAnalysis(user, request, response); break;
            case "analyzeFiles": analyzeDiffFiles(user, request, response);  break;
            case "adminDelete":  adminDeleteReport(request, response);        break;
            case "adminPurge":   adminPurgeOld(request, response);            break;
            default:             sendError(response, 400, "unknown action: " + action); break;
        }
    }

    @Override
    protected void doDelete(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        request.setCharacterEncoding("UTF-8");
        response.setContentType("application/json;charset=UTF-8");
        if (!checkLogin(request, response)) return;
        String action = request.getParameter("action");
        if ("delete".equals(action)) {
            deleteReport(request, response);
        } else {
            sendError(response, 400, "unknown action");
        }
    }

    // ─── Analysis Entry Points ────────────────────────────────────

    private void startFullAnalysis(User user, HttpServletRequest request, HttpServletResponse response) throws IOException {
        String projectIdStr = request.getParameter("projectId");
        String branch = request.getParameter("branch");
        String ruleSet = request.getParameter("ruleSet");

        if (projectIdStr == null || projectIdStr.trim().isEmpty()) {
            sendError(response, 400, "missing projectId");
            return;
        }

        Long projectId;
        try { projectId = Long.parseLong(projectIdStr.trim()); }
        catch (NumberFormatException e) { sendError(response, 400, "invalid projectId"); return; }

        GitProject project = projectDAO.getById(projectId);
        if (project == null) { sendError(response, 404, "project not found"); return; }

        if (!aiClient.isEnabled()) {
            sendError(response, 503, "AI analysis disabled, check db.properties ai.api.key and ai.enabled");
            return;
        }

        String ruleSetId = (ruleSet != null && !ruleSet.trim().isEmpty()) ? ruleSet.trim() : "auto";

        CodeAnalysisReport report = new CodeAnalysisReport(projectId, user.getId(), ruleSetId);
        report.setProjectName(project.getName());
        report.setTeamId(project.getTeamId());
        report.setRepoUrl(project.getRepoUrl());
        report.setBranch(branch != null ? branch.trim() : "main");

        if (!reportDAO.insert(report)) { sendError(response, 500, "failed to create report record"); return; }

        final Long reportId = report.getId();
        final String finalBranch = branch;
        runningTasks.put(reportId, new AtomicBoolean(false));

        new Thread(() -> {
            try {
                getSharedService().analyzeRepo(
                        project.getRepoUrl(), finalBranch, project.getName(),
                        projectId, project.getTeamId(), ruleSetId,
                        (pi) -> {
                            if (pi.error != null) System.err.println("[CodeAnalyzer] " + pi.error);
                            else System.out.println("[CodeAnalyzer] progress " + pi.analyzed + "/" + pi.total + " " + (pi.currentFile != null ? pi.currentFile : ""));
                        }
                );
            } finally {
                runningTasks.remove(reportId);
            }
        }, "CodeAnalyze-" + reportId).start();

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("reportId", reportId);
        result.put("projectId", projectId);
        result.put("projectName", project.getName());
        result.put("ruleSet", ruleSetId);
        result.put("message", "analysis started, check progress at analysis page");
        sendSuccess(response, result);
    }

    /**
     * Analyze given file list (for diff analysis, loose-coupling entry)
     * Body: { projectName:"", ruleSet:"", files:[{path:"", content:""}] }
     */
    private void analyzeDiffFiles(User user, HttpServletRequest request, HttpServletResponse response) throws IOException {
        StringBuilder body = new StringBuilder();
        try (BufferedReader r = request.getReader()) {
            String line;
            while ((line = r.readLine()) != null) body.append(line);
        }

        JsonObject req;
        try { req = JsonParser.parseString(body.toString()).getAsJsonObject(); }
        catch (Exception e) { sendError(response, 400, "invalid json: " + e.getMessage()); return; }

        String projectName = getStr(req, "projectName", "");
        String ruleSetId = getStr(req, "ruleSet", "alibaba-java");
        JsonArray filesArr = req.has("files") ? req.getAsJsonArray("files") : new JsonArray();

        if (!aiClient.isEnabled()) { sendError(response, 503, "AI disabled"); return; }
        if (filesArr.size() == 0) { sendError(response, 400, "empty file list"); return; }

        List<CodeAnalyzerService.FileInfo> fileInfos = new ArrayList<>();
        for (int i = 0; i < filesArr.size(); i++) {
            JsonObject f = filesArr.get(i).getAsJsonObject();
            fileInfos.add(new CodeAnalyzerService.FileInfo(
                    getStr(f, "path", "unknown"),
                    getStr(f, "content", "")));
        }

        long start = System.currentTimeMillis();
        String report = getSharedService().analyzeFileList(fileInfos, ruleSetId, projectName);
        long costMs = System.currentTimeMillis() - start;

        String incidentCheckResult = getSharedService().getLastIncidentCheckResult();

        int high = countIn(report, "\uD83D\uDD34");
        int med  = countIn(report, "\uD83D\uDFE1");
        int totalIssues = high + med;

        CodeAnalysisReport dbReport = new CodeAnalysisReport(null, user.getId(), ruleSetId);
        dbReport.setProjectName(projectName);
        dbReport.setStatus("DONE");
        dbReport.setReport(report);
        dbReport.setTotalFiles(filesArr.size());
        dbReport.setAnalyzedFiles(filesArr.size());
        dbReport.setTotalIssues(totalIssues);
        dbReport.setCostMs(costMs);
        dbReport.setIncidentCheckResult(incidentCheckResult);
        reportDAO.insert(dbReport);

        // Save incidentCheckResult to DB
        if (incidentCheckResult != null) {
            reportDAO.updateIncidentCheckResult(dbReport.getId(), incidentCheckResult);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("reportId", dbReport.getId());
        result.put("report", report);
        result.put("totalFiles", filesArr.size());
        result.put("totalIssues", totalIssues);
        result.put("costMs", costMs);
        result.put("incidentCheckResult", incidentCheckResult);
        sendSuccess(response, result);
    }

    // ─── Query APIs ───────────────────────────────────────────────

    private void listReports(HttpServletRequest request, HttpServletResponse response) throws IOException {
        String projectIdStr = request.getParameter("projectId");
        List<CodeAnalysisReport> reports;
        if (projectIdStr != null && !projectIdStr.trim().isEmpty()) {
            reports = reportDAO.getByProjectId(Long.parseLong(projectIdStr.trim()));
        } else {
            reports = reportDAO.getByUserId(getUserId(request));
        }
        sendSuccess(response, reports);
    }

    private void getReport(HttpServletRequest request, HttpServletResponse response) throws IOException {
        String idStr = request.getParameter("id");
        if (idStr == null || idStr.trim().isEmpty()) { sendError(response, 400, "missing id"); return; }
        CodeAnalysisReport r = reportDAO.getById(Long.parseLong(idStr.trim()));
        if (r == null) { sendError(response, 404, "report not found"); return; }
        sendSuccess(response, r);
    }

    private void getStatus(HttpServletRequest request, HttpServletResponse response) throws IOException {
        String idStr = request.getParameter("id");
        if (idStr == null || idStr.trim().isEmpty()) { sendError(response, 400, "missing id"); return; }
        CodeAnalysisReport r = reportDAO.getById(Long.parseLong(idStr.trim()));
        if (r == null) { sendError(response, 404, "report not found"); return; }

        Map<String, Object> status = new LinkedHashMap<>();
        status.put("id", r.getId());
        status.put("status", r.getStatus());
        status.put("totalFiles", r.getTotalFiles());
        status.put("analyzedFiles", r.getAnalyzedFiles());
        status.put("totalIssues", r.getTotalIssues());
        status.put("report", r.getReport());
        status.put("createdAt", r.getCreatedAt());
        status.put("finishedAt", r.getFinishedAt());
        status.put("costMs", r.getCostMs());
        sendSuccess(response, status);
    }

    private void getRuleSets(HttpServletResponse response) throws IOException {
        Map<String, CodeRuleSet> all = CodeRuleSet.getAllRuleSets();
        List<Map<String, String>> list = new ArrayList<>();
        for (CodeRuleSet rs : all.values()) {
            Map<String, String> m = new LinkedHashMap<>();
            m.put("id", rs.getId());
            m.put("name", rs.getName());
            m.put("description", rs.getDescription());
            m.put("fileTypes", rs.getFileTypes());
            list.add(m);
        }
        sendSuccess(response, list);
    }

    // ─── History (Admin) APIs ────────────────────────────────────

    private void listHistory(HttpServletRequest request, HttpServletResponse response) throws IOException {
        int page = 1, pageSize = 20;
        try { String p = request.getParameter("page"); if (p != null) page = Math.max(1, Integer.parseInt(p)); } catch (Exception ignored) {}
        try { String ps = request.getParameter("pageSize"); if (ps != null) pageSize = Math.min(100, Math.max(1, Integer.parseInt(ps))); } catch (Exception ignored) {}
        int offset = (page - 1) * pageSize;

        List<CodeAnalysisReport> list = reportDAO.getAll(pageSize, offset);
        int total = reportDAO.getTotalCount();

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("page", page);
        result.put("pageSize", pageSize);
        result.put("total", total);
        result.put("totalPages", (int) Math.ceil((double) total / pageSize));
        result.put("list", list);
        sendSuccess(response, result);
    }

    private void adminDeleteReport(HttpServletRequest request, HttpServletResponse response) throws IOException {
        if (!PermissionHelper.requireAdmin(request, response)) return;
        String idStr = request.getParameter("id");
        if (idStr == null || idStr.trim().isEmpty()) { sendError(response, 400, "missing id"); return; }
        boolean ok = reportDAO.adminDelete(Long.parseLong(idStr.trim()));
        if (ok) sendSuccessMsg(response, "deleted"); else sendError(response, 500, "delete failed");
    }

    private void adminPurgeOld(HttpServletRequest request, HttpServletResponse response) throws IOException {
        if (!PermissionHelper.requireAdmin(request, response)) return;
        int days = 90;
        try { String d = request.getParameter("days"); if (d != null) days = Math.max(1, Integer.parseInt(d)); } catch (Exception ignored) {}
        int deleted = reportDAO.deleteOlderThan(days);
        sendSuccessMsg(response, "deleted " + deleted + " reports older than " + days + " days");
    }

    private void deleteReport(HttpServletRequest request, HttpServletResponse response) throws IOException {
        String idStr = request.getParameter("id");
        if (idStr == null || idStr.trim().isEmpty()) { sendError(response, 400, "missing id"); return; }
        boolean ok = reportDAO.delete(Long.parseLong(idStr.trim()));
        if (ok) sendSuccessMsg(response, "deleted"); else sendError(response, 500, "delete failed");
    }

    // ─── Utility ─────────────────────────────────────────────────

    private boolean checkLogin(HttpServletRequest request, HttpServletResponse response) throws IOException {
        HttpSession session = request.getSession(false);
        if (session == null || session.getAttribute("user") == null) {
            sendError(response, 401, "not logged in");
            return false;
        }
        return true;
    }

    private User checkLoginUser(HttpServletRequest request, HttpServletResponse response) throws IOException {
        HttpSession session = request.getSession(false);
        if (session == null || session.getAttribute("user") == null) {
            sendError(response, 401, "not logged in");
            return null;
        }
        return (User) session.getAttribute("user");
    }

    private Long getUserId(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session == null) return 0L;
        User u = (User) session.getAttribute("user");
        return u != null ? u.getId() : 0L;
    }

    private static CodeAnalyzerService getSharedService() {
        synchronized (serviceLock) {
            if (sharedService == null) sharedService = new CodeAnalyzerService(new AIModelClient());
            return sharedService;
        }
    }

    private String getStr(JsonObject obj, String key, String def) {
        if (obj == null || !obj.has(key) || obj.get(key).isJsonNull()) return def;
        return obj.get(key).getAsString();
    }

    private int countIn(String text, String token) {
        if (text == null) return 0;
        int count = 0, idx = 0;
        while ((idx = text.indexOf(token, idx)) >= 0) { count++; idx += token.length(); }
        return count;
    }

    private void sendSuccess(HttpServletResponse response, Object data) throws IOException {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("success", true);
        result.put("data", data);
        response.getWriter().write(gson.toJson(result));
    }

    private void sendSuccessMsg(HttpServletResponse response, String message) throws IOException {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("success", true);
        result.put("message", message);
        response.getWriter().write(gson.toJson(result));
    }

    private void sendError(HttpServletResponse response, int status, String message) throws IOException {
        response.setStatus(status);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("success", false);
        result.put("message", message);
        response.getWriter().write(gson.toJson(result));
    }
}
