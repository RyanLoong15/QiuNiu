/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.google.gson.Gson
 *  com.google.gson.JsonArray
 *  com.google.gson.JsonElement
 *  com.google.gson.JsonObject
 *  com.google.gson.JsonParser
 *  javax.servlet.ServletException
 *  javax.servlet.annotation.WebServlet
 *  javax.servlet.http.HttpServlet
 *  javax.servlet.http.HttpServletRequest
 *  javax.servlet.http.HttpServletResponse
 */
package com.qiuniu.servlet;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.qiuniu.dao.DiffCacheDAO;
import com.qiuniu.dao.GitProjectDAO;
import com.qiuniu.dao.GitTeamDAO;
import com.qiuniu.dao.PromptAnalysisDAO;
import com.qiuniu.dao.VersionComparisonDAO;
import com.qiuniu.model.GitProject;
import com.qiuniu.model.PromptAnalysis;
import com.qiuniu.model.User;
import com.qiuniu.model.VersionComparison;
import com.qiuniu.util.PermissionHelper;
import com.qiuniu.util.AIModelClient;
import com.qiuniu.model.Role;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.FileAttribute;
import com.qiuniu.dao.DBUtil;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

@WebServlet(urlPatterns={"/version"}, loadOnStartup=1)
public class VersionServlet
extends HttpServlet {
    private GitProjectDAO projectDAO = new GitProjectDAO();
    private GitTeamDAO teamDAO = new GitTeamDAO();
    private VersionComparisonDAO vcDAO = new VersionComparisonDAO();
    private PromptAnalysisDAO promptDAO = new PromptAnalysisDAO();
    private DiffCacheDAO diffCacheDAO = new DiffCacheDAO();
    private AIModelClient aiClient = new AIModelClient();
    private Gson gson = new Gson();

    public void init() throws ServletException {
        this.vcDAO.createTable();
        this.projectDAO.createTable();
        this.teamDAO.createTable();
        this.promptDAO.createTable();
        this.promptDAO.seedDefaultPrompts();
        this.diffCacheDAO.createTable();
    }

    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        if (!this.isLoggedIn(request, response)) {
            return;
        }
        User user = (User)request.getSession(false).getAttribute("user");
        String action = request.getParameter("action");
        try {
            switch (action) {
                case "list": {
                    this.sendSuccess(response, this.vcDAO.getByUserId(user.getId()));
                    break;
                }
                case "result": {
                    JsonObject r = this.getComparisonDetail(Long.parseLong(request.getParameter("id")));
                    if (r == null) {
                        this.sendError(response, 404, "\u8bb0\u5f55\u4e0d\u5b58\u5728");
                        break;
                    }
                    this.sendSuccess(response, r);
                    break;
                }
                case "status": {
                    VersionComparison vc = this.vcDAO.getById(Long.parseLong(request.getParameter("id")));
                    if (vc == null) {
                        this.sendError(response, 404, "\u8bb0\u5f55\u4e0d\u5b58\u5728");
                        break;
                    }
                    this.sendSuccess(response, vc);
                    break;
                }
                case "prompts": {
                    this.sendSuccess(response, this.promptDAO.getAll());
                    break;
                }
                case "detail": {
                    JsonObject r = this.getResultDetail(Long.parseLong(request.getParameter("id")));
                    if (r == null) {
                        this.sendError(response, 404, "\\u8bb0\\u5f55\\u4e0d\\u5b58\\u5728");
                        break;
                    }
                    this.sendSuccess(response, r);
                    break;
                }
                default: {
                    this.sendError(response, 400, "\u672a\u77e5\u64cd\u4f5c");
                    break;
                }
            }
        }
        catch (Exception e) {
            e.printStackTrace();
            this.sendError(response, 500, "\u670d\u52a1\u5668\u9519\u8bef: " + e.getMessage());
        }
    }

    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        request.setCharacterEncoding("UTF-8");
        if (!this.isLoggedIn(request, response)) {
            return;
        }
        User user = (User)request.getSession(false).getAttribute("user");
        String action = request.getParameter("action");
        try {
            switch (action) {
                case "start": {
                    // 发起比对：需要写权限
                    if (!PermissionHelper.requireWrite(request, response)) {
                        return;
                    }
                    this.doStartComparison(user, request, response);
                    break;
                }
                case "updatePrompt": {
                    // 更新提示词：需要写权限
                    if (!PermissionHelper.requireWrite(request, response)) {
                        return;
                    }
                    this.doUpdatePrompt(request, response);
                    break;
                }
                case "analyzeDiff": {
                    // AI 分析：需要写权限
                    if (!PermissionHelper.requireWrite(request, response)) {
                        return;
                    }
                    this.doAnalyzeDiff(request, response);
                    break;
                }
                case "compareAll": {
                    // 批量比对所有仓库：需要写权限
                    if (!PermissionHelper.requireWrite(request, response)) {
                        return;
                    }
                    this.doCompareAll(user, request, response);
                    break;
                }
                default: {
                    this.sendError(response, 400, "未知操作");
                    break;
                }
            }
        }
        catch (Exception e) {
            e.printStackTrace();
            this.sendError(response, 500, "\u670d\u52a1\u5668\u9519\u8bef: " + e.getMessage());
        }
    }

    private boolean isLoggedIn(HttpServletRequest request, HttpServletResponse response) throws IOException {
        if (request.getSession(false) == null || request.getSession(false).getAttribute("user") == null) {
            this.sendError(response, 401, "\u672a\u767b\u5f55\u6216\u4f1a\u8bdd\u5df2\u8fc7\u671f");
            return false;
        }
        return true;
    }

    private void doStartComparison(User user, HttpServletRequest request, HttpServletResponse response) throws IOException {
        String versionName = request.getParameter("versionName");
        String baseBranch = request.getParameter("baseBranch");
        String compareBranch = request.getParameter("compareBranch");
        if (versionName == null || versionName.trim().isEmpty()) {
            this.sendError(response, 400, "\u7248\u672c\u540d\u79f0\u4e0d\u80fd\u4e3a\u7a7a");
            return;
        }
        String base = this.str(this.defaultStr(baseBranch, "master"));
        String compare = this.str(this.defaultStr(compareBranch, versionName.trim()));
        List<GitProject> projects = this.projectDAO.getAll();
        if (projects.isEmpty()) {
            this.sendError(response, 400, "\u5f53\u524d\u6ca1\u6709\u53ef\u5bf9\u6bd4\u7684\u4ee3\u7801\u4ed3\uff0c\u8bf7\u5148\u6dfb\u52a0\u9879\u76ee");
            return;
        }
        VersionComparison vc = new VersionComparison();
        vc.setUserId(user.getId());
        vc.setVersionName(versionName.trim());
        vc.setBaseBranch(base);
        vc.setCompareBranch(compare);
        vc.setTotalRepos(projects.size());
        vc.setStatus("PENDING");
        if (!this.vcDAO.insert(vc)) {
            this.sendError(response, 500, "\u521b\u5efa\u5bf9\u6bd4\u4efb\u52a1\u5931\u8d25");
            return;
        }
        Long taskId = vc.getId();
        String baseF = base;
        String compareF = compare;
        String versionF = versionName.trim();
        List<GitProject> projectList = projects;
        new Thread(() -> this.runComparison(taskId, user.getId(), projectList, baseF, compareF, versionF), "VC-" + taskId).start();
        this.sendSuccess(response, vc);
    }

    private void doUpdatePrompt(HttpServletRequest request, HttpServletResponse response) throws IOException {
        String pr;
        PromptAnalysis p = new PromptAnalysis();
        p.setId(Long.parseLong(request.getParameter("id")));
        p.setDisplayName(request.getParameter("displayName"));
        p.setSystemPrompt(request.getParameter("systemPrompt"));
        p.setUserPromptTemplate(request.getParameter("userPromptTemplate"));
        String ml = request.getParameter("maxDiffLines");
        if (ml != null && !ml.isEmpty()) {
            p.setMaxDiffLines(Integer.parseInt(ml));
        }
        if ((pr = request.getParameter("priority")) != null && !pr.isEmpty()) {
            p.setPriority(Integer.parseInt(pr));
        }
        this.promptDAO.update(p);
        this.sendSuccessMsg(response, "\u4fdd\u5b58\u6210\u529f");
    }

    private void doAnalyzeDiff(HttpServletRequest request, HttpServletResponse response) throws IOException {
        int maxLines;
        JsonObject req;
        StringBuilder body = new StringBuilder();
        try (BufferedReader r = request.getReader();){
            String line;
            while ((line = r.readLine()) != null) {
                body.append(line);
            }
        }
        try {
            req = JsonParser.parseString((String)body.toString()).getAsJsonObject();
        }
        catch (Exception e) {
            this.sendError(response, 400, "\u65e0\u6548\u7684\u8bf7\u6c42\u683c\u5f0f: " + e.getMessage());
            return;
        }
        String diff = this.getStr(req, "diff");
        String projectName = this.getStr(req, "projectName");
        String versionName = this.getStr(req, "versionName");
        String baseBranch = this.getStr(req, "baseBranch");
        String compareBranch = this.getStr(req, "compareBranch");
        if (diff == null || diff.trim().isEmpty()) {
            this.sendError(response, 400, "diff \u5185\u5bb9\u4e0d\u80fd\u4e3a\u7a7a");
            return;
        }
        if (!this.aiClient.isEnabled()) {
            this.sendError(response, 503, "AI \u5206\u6790\u672a\u542f\u7528\uff0c\u8bf7\u5728 db.properties \u4e2d\u914d\u7f6e ai.api.key \u5e76\u8bbe\u7f6e ai.enabled=true");
            return;
        }
        String fileType = this.guessFileType(diff);
        PromptAnalysis tmpl = this.promptDAO.getByFileType(fileType);
        if (tmpl == null) {
            tmpl = this.promptDAO.getByFileType("default");
        }
        if (tmpl == null) {
            this.sendError(response, 500, "\u672a\u627e\u5230\u5206\u6790\u63d0\u793a\u8bcd\u6a21\u677f");
            return;
        }
        JsonObject info = new JsonObject();
        info.addProperty("projectName", projectName != null ? projectName : "");
        String userPrompt = this.buildUserPrompt(tmpl.getUserPromptTemplate(), this.nullStr(versionName, "unknown"), this.nullStr(baseBranch, "master"), this.nullStr(compareBranch, "unknown"), diff, info);
        int n = maxLines = tmpl.getMaxDiffLines() != null ? tmpl.getMaxDiffLines() : 300;
        if (diff.split("\n").length > maxLines) {
            String[] lines = diff.split("\n");
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < maxLines; ++i) {
                sb.append(lines[i]).append("\n");
            }
            sb.append("\n[... diff truncated, first ").append(maxLines).append(" lines ...]");
            userPrompt = this.buildUserPrompt(tmpl.getUserPromptTemplate(), this.nullStr(versionName, "unknown"), this.nullStr(baseBranch, "master"), this.nullStr(compareBranch, "unknown"), sb.toString(), info);
        }
        System.out.println("[AiAnalysis] Analyzing: " + this.nullStr(projectName, "?") + " type=" + fileType);
        String analysis = this.aiClient.analyze(tmpl.getSystemPrompt(), userPrompt);
        JsonObject result = new JsonObject();
        result.addProperty("analysis", analysis != null ? analysis : "[AI \u672a\u8fd4\u56de\u6709\u6548\u5206\u6790\u7ed3\u679c]");
        result.addProperty("fileType", fileType);
        result.addProperty("promptUsed", tmpl.getDisplayName());
        result.addProperty("projectName", this.nullStr(projectName, ""));
        result.addProperty("model", this.aiClient.getModel());
        this.sendSuccess(response, result);
    }

    /**
     * 批量比对所有仓库（投产变更范围代码仓清单）
     * 参数：deployDate (格式：yyyyMMdd)
     * 比对每个项目的 master 分支与 release-YYYYMMDD 分支
     */
    private void doCompareAll(User user, HttpServletRequest request, HttpServletResponse response) throws IOException {
        String deployDate = request.getParameter("deployDate");
        if (deployDate == null || deployDate.trim().isEmpty()) {
            this.sendError(response, 400, "请提供投产日期(deployDate)");
            return;
        }
        
        String compareBranch = "release-" + deployDate.trim();
        
        // 获取所有项目
        List<GitProject> allProjects = projectDAO.getAll();
        if (allProjects == null || allProjects.isEmpty()) {
            this.sendError(response, 400, "没有找到任何项目");
            return;
        }
        
        // 创建比对任务
        VersionComparison task = new VersionComparison();
        task.setUserId(user.getId());
        task.setVersionName("投产比对 " + deployDate);
        task.setBaseBranch("master");
        task.setCompareBranch(compareBranch);
        task.setStatus("PENDING");
        
        boolean saved = vcDAO.insert(task);
        if (!saved) {
            this.sendError(response, 500, "创建比对任务失败");
            return;
        }
        
        // 异步执行比对
        final Long taskId = task.getId();
        final List<GitProject> projects = allProjects;
        final String baseBranch = "master";
        final String versionName = "投产比对 " + deployDate;
        
        new Thread(() -> {
            try {
                runComparison(taskId, user.getId(), projects, baseBranch, compareBranch, versionName);
            } catch (Exception e) {
                System.err.println("[CompareAll] Error: " + e.getMessage());
                e.printStackTrace();
            }
        }).start();
        
        // 返回任务ID
        JsonObject result = new JsonObject();
        result.addProperty("taskId", taskId);
        result.addProperty("message", "已开始比对 " + allProjects.size() + " 个仓库");
        this.sendSuccess(response, result);
    }

    private void runComparison(Long taskId, Long userId, List<GitProject> projects, String baseBranch, String compareBranch, String versionName) {
        HashMap<Long, List> teamProjects = new HashMap<Long, List>();
        for (GitProject p : projects) {
            Long tid = p.getTeamId() != null ? p.getTeamId() : 0L;
            teamProjects.computeIfAbsent(tid, k -> new ArrayList()).add(p);
        }
        ArrayList<JsonObject> ok = new ArrayList<JsonObject>();
        ArrayList<JsonObject> fail = new ArrayList<JsonObject>();
        ArrayList<Long> teamsNeedGit = new ArrayList<Long>();
        for (Map.Entry entry : teamProjects.entrySet()) {
            Long teamId = (Long)entry.getKey();
            List teamProjList = (List)entry.getValue();
            String cachedData = this.diffCacheDAO.getCache(teamId, baseBranch, compareBranch);
            if (cachedData != null) {
                System.out.println("[VersionCompare] Team " + teamId + " using cached diff");
                try {
                    JsonArray cachedResults = JsonParser.parseString((String)cachedData).getAsJsonArray();
                    for (int i = 0; i < cachedResults.size(); ++i) {
                        JsonObject item = cachedResults.get(i).getAsJsonObject().deepCopy();
                        Iterator iterator = teamProjList.iterator();
                        while (iterator.hasNext()) {
                            GitProject p = (GitProject)iterator.next();
                            if (!p.getName().equals(this.getStr(item, "projectName"))) continue;
                            item.addProperty("projectId", (Number)p.getId());
                            break;
                        }
                        if ("SUCCESS".equals(this.getStr(item, "status"))) {
                            ok.add(item);
                            continue;
                        }
                        fail.add(item);
                    }
                    continue;
                }
                catch (Exception e) {
                    System.err.println("[VersionCompare] Cache parse error for team " + teamId + ": " + e.getMessage());
                    teamsNeedGit.add(teamId);
                    continue;
                }
            }
            teamsNeedGit.add(teamId);
        }
        if (!teamsNeedGit.isEmpty()) {
            System.out.println("[VersionCompare] Teams needing Git: " + teamsNeedGit);
            ExecutorService exec = Executors.newFixedThreadPool(5);
            HashMap<Long, List<JsonObject>> teamResults = new HashMap<>();
            Object lock = new Object();
            for (Long teamId : teamsNeedGit) {
                List teamProjList = (List)teamProjects.get(teamId);
                Iterator i = teamProjList.iterator();
                while (i.hasNext()) {
                    GitProject proj;
                    GitProject p = proj = (GitProject)i.next();
                    Long tid = teamId;
                    exec.submit(() -> {
                        try {
                            DiffResult dr = this.safeCompare(p, baseBranch, compareBranch);
                            JsonObject item = this.buildItem(p, dr, baseBranch, compareBranch);
                            Object object = lock;
                            synchronized (object) {
                                if (dr.error == null) {
                                    ok.add(item);
                                } else {
                                    fail.add(item);
                                }
                                teamResults.computeIfAbsent(tid, k -> new ArrayList<>()).add(item);
                            }
                        }
                        catch (Exception e) {
                            JsonObject item = this.buildErrorItem(p, e.getMessage());
                            Object object = lock;
                            synchronized (object) {
                                fail.add(item);
                                teamResults.computeIfAbsent(tid, k -> new ArrayList<>()).add(item);
                            }
                        }
                    });
                }
            }
            exec.shutdown();
            try {
                exec.awaitTermination(30L, TimeUnit.MINUTES);
            }
            catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            for (Long teamId : teamsNeedGit) {
                List<JsonObject> results = teamResults.get(teamId);
                if (results == null || results.isEmpty()) continue;
                JsonArray arr = new JsonArray();
                for (JsonObject r : results) {
                    arr.add(r);
                }
                this.diffCacheDAO.saveCache(teamId, baseBranch, compareBranch, arr.toString());
                System.out.println("[VersionCompare] Cached diff for team " + teamId);
            }
        }
        String status = fail.isEmpty() ? "DONE" : (ok.isEmpty() ? "FAILED" : "DONE");
        this.vcDAO.updateStatus(taskId, status, ok.size(), fail.size());
        this.saveResults(taskId, ok, fail);
        if (this.aiClient.isEnabled()) {
            new Thread(() -> this.runAiAnalysis(taskId, ok, fail, versionName, baseBranch, compareBranch), "AI-" + taskId).start();
        }
        System.out.println("[VersionCompare] Task " + taskId + " done: " + ok.size() + " ok, " + fail.size() + " fail");
    }

    private JsonObject buildItem(GitProject p, DiffResult dr, String baseBranch, String compareBranch) {
        JsonObject item = new JsonObject();
        item.addProperty("projectId", (Number)p.getId());
        item.addProperty("projectName", p.getName());
        item.addProperty("teamId", (Number)(p.getTeamId() != null ? p.getTeamId() : 0L));
        item.addProperty("teamName", p.getTeamName() != null ? p.getTeamName() : "\u672a\u5206\u914d");
        item.addProperty("repoUrl", p.getRepoUrl());
        item.addProperty("baseBranch", baseBranch);
        item.addProperty("compareBranch", compareBranch);
        item.addProperty("hasChanges", Boolean.valueOf(dr.hasChanges));
        item.addProperty("changedFiles", dr.changedFiles);
        item.addProperty("insertions", dr.insertions);
        item.addProperty("deletions", dr.deletions);
        item.addProperty("diffSummary", dr.summary);
        item.addProperty("diff", dr.detail);
        item.addProperty("error", dr.error);
        item.addProperty("status", dr.error == null ? "SUCCESS" : "FAILED");
        item.add("addedFiles", (JsonElement)this.toFileListJson(dr.addedFiles));
        item.add("modifiedFiles", (JsonElement)this.toFileListJson(dr.modifiedFiles));
        item.add("deletedFiles", (JsonElement)this.toFileListJson(dr.deletedFiles));
        return item;
    }

    private JsonArray toFileListJson(List<FileDiffInfo> files) {
        JsonArray arr = new JsonArray();
        for (FileDiffInfo f : files) {
            JsonObject o = new JsonObject();
            o.addProperty("path", f.getPath());
            o.addProperty("status", f.getStatus());
            o.addProperty("statusLabel", f.getStatusLabel());
            arr.add((JsonElement)o);
        }
        return arr;
    }

    private JsonObject buildErrorItem(GitProject p, String error) {
        JsonObject item = new JsonObject();
        item.addProperty("projectId", (Number)p.getId());
        item.addProperty("projectName", p.getName());
        item.addProperty("teamId", (Number)(p.getTeamId() != null ? p.getTeamId() : 0L));
        item.addProperty("teamName", p.getTeamName() != null ? p.getTeamName() : "\u672a\u5206\u914d");
        item.addProperty("repoUrl", p.getRepoUrl());
        item.addProperty("status", "FAILED");
        item.addProperty("error", error);
        return item;
    }

    private void runAiAnalysis(Long taskId, List<JsonObject> results, List<JsonObject> failed, String versionName, String baseBranch, String compareBranch) {
        System.out.println("[AiAnalysis] Starting task " + taskId);
        int cnt = 0;
        for (JsonObject item : results) {
            String diff = this.getStr(item, "diff");
            if (diff == null || diff.length() < 20) continue;
            String type = this.guessFileType(diff);
            PromptAnalysis tmpl = this.promptDAO.getByFileType(type);
            if (tmpl == null) {
                tmpl = this.promptDAO.getByFileType("default");
            }
            if (tmpl == null) continue;
            JsonObject info = new JsonObject();
            info.addProperty("projectName", this.getStr(item, "projectName"));
            String userPrompt = this.buildUserPrompt(tmpl.getUserPromptTemplate(), versionName, baseBranch, compareBranch, diff, info);
            String analysis = this.aiClient.analyze(tmpl.getSystemPrompt(), userPrompt);
            this.saveAiAnalysis(taskId, item.get("projectId").getAsLong(), analysis, type);
            ++cnt;
            this.sleep(500L);
        }
        System.out.println("[AiAnalysis] Task " + taskId + " analyzed " + cnt);
    }

    private String guessFileType(String diff) {
        HashMap<String, Integer> cnt = new HashMap<String, Integer>();
        Pattern pt = Pattern.compile("\\+[+]{2} [ab]?/.+\\.([a-zA-Z0-9]+)");
        Matcher m = pt.matcher(diff);
        while (m.find()) {
            String ext = m.group(1).toLowerCase();
            String type = this.extToType(ext);
            cnt.put(type, cnt.getOrDefault(type, 0) + 1);
        }
        String dom = "default";
        int mx = 0;
        for (Map.Entry e : cnt.entrySet()) {
            if ((Integer)e.getValue() <= mx) continue;
            mx = (Integer)e.getValue();
            dom = (String)e.getKey();
        }
        return dom;
    }

    private String extToType(String ext) {
        switch (ext) {
            case "java": {
                return "java";
            }
            case "sql": {
                return "sql";
            }
            case "yml": 
            case "yaml": {
                return "yaml";
            }
            case "js": 
            case "jsx": {
                return "js";
            }
            case "ts": 
            case "tsx": {
                return "ts";
            }
            case "py": {
                return "py";
            }
            case "go": {
                return "go";
            }
            case "sh": 
            case "bash": {
                return "sh";
            }
            case "xml": {
                return "xml";
            }
            case "json": {
                return "json";
            }
            case "properties": {
                return "java";
            }
            case "html": 
            case "htm": {
                return "html";
            }
            case "css": {
                return "css";
            }
        }
        return "default";
    }

    private String buildUserPrompt(String tmpl, String versionName, String baseBranch, String compareBranch, String diff, JsonObject info) {
        return tmpl.replace("{versionName}", versionName != null ? versionName : "unknown").replace("{baseBranch}", baseBranch != null ? baseBranch : "master").replace("{compareBranch}", compareBranch != null ? compareBranch : "unknown").replace("{projectName}", this.getStr(info, "projectName")).replace("{diff}", diff);
    }

    private void saveAiAnalysis(Long taskId, Long projectId, String analysis, String fileType) {
        try {
            String sql = "UPDATE git_version_results SET ai_analysis=? WHERE task_id=? AND project_id=?";
            try (Connection conn = this.getConn();
                 PreparedStatement ps = conn.prepareStatement(sql);){
                ps.setString(1, analysis);
                ps.setLong(2, taskId);
                ps.setLong(3, projectId);
                ps.executeUpdate();
            }
        }
        catch (Exception e) {
            System.err.println("[AiAnalysis] save failed: " + e.getMessage());
        }
    }

    private DiffResult safeCompare(GitProject proj, String baseBranch, String compareBranch) {
        try {
            return this.compareBranches(proj, baseBranch, compareBranch);
        }
        catch (Exception e) {
            DiffResult r = new DiffResult();
            r.error = e.getMessage();
            return r;
        }
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    private DiffResult compareBranches(GitProject proj, String baseBranch, String compareBranch) throws Exception {
        Path tmpPath;
        File dir;
        DiffResult result = new DiffResult();
        String tmpBase = System.getProperty("java.io.tmpdir");
        if (tmpBase == null || tmpBase.isEmpty()) {
            tmpBase = "C:\\Temp";
        }
        if ((dir = (tmpPath = Paths.get(tmpBase, "qn-cmp-" + proj.getId() + "-" + System.currentTimeMillis())).toFile()).exists()) {
            this.deleteDir(dir);
        }
        Files.createDirectories(tmpPath, new FileAttribute[0]);
        try {
            String statOut;
            int exit = this.runGit(dir, 120, "git", "clone", "--branch", baseBranch, "--depth=50", proj.getRepoUrl(), ".");
            if (exit != 0) {
                result.error = "[CLONE_FAILED:" + exit + "]\n" + this.captureGit(dir, 120, "git", "clone", "--branch", baseBranch, "--depth=50", proj.getRepoUrl(), ".");
                DiffResult diffResult = result;
                return diffResult;
            }
            if (!new File(dir, ".git").exists()) {
                result.error = ".git not found after clone";
                DiffResult diffResult = result;
                return diffResult;
            }
            this.runGit(dir, 60, "git", "fetch", "origin", compareBranch + ":" + compareBranch, "--depth=50");
            String nameStatus = this.captureGit(dir, 30, "git", "diff", "--name-status", baseBranch + ".." + compareBranch);
            ArrayList<FileDiffInfo> addedFiles = new ArrayList<FileDiffInfo>();
            ArrayList<FileDiffInfo> modifiedFiles = new ArrayList<FileDiffInfo>();
            ArrayList<FileDiffInfo> deletedFiles = new ArrayList<FileDiffInfo>();
            ArrayList<FileDiffInfo> otherFiles = new ArrayList<FileDiffInfo>();
            int tf = 0;
            int ti = 0;
            int td = 0;
            int lines = 0;
            block15: for (String line : nameStatus.split("\n")) {
                if (line == null || line.trim().isEmpty()) continue;
                if (++lines > 500) break;
                String[] parts = line.split("\t");
                if (parts.length < 2) continue;
                String status = parts[0].toUpperCase();
                String filePath = parts[1];
                FileDiffInfo fileInfo = new FileDiffInfo(filePath, status);
                switch (status) {
                    case "A": {
                        addedFiles.add(fileInfo);
                        ++tf;
                        ++ti;
                        continue block15;
                    }
                    case "M": {
                        modifiedFiles.add(fileInfo);
                        ++tf;
                        ++ti;
                        continue block15;
                    }
                    case "D": {
                        deletedFiles.add(fileInfo);
                        ++tf;
                        ++td;
                        continue block15;
                    }
                    default: {
                        otherFiles.add(fileInfo);
                        ++tf;
                    }
                }
            }
            result.addedFiles = addedFiles;
            result.modifiedFiles = modifiedFiles;
            result.deletedFiles = deletedFiles;
            result.otherFiles = otherFiles;
            result.changedFiles = String.valueOf(tf);
            result.insertions = String.valueOf(ti);
            result.deletions = String.valueOf(td);
            result.summary = statOut = this.captureGit(dir, 30, "git", "diff", baseBranch + ".." + compareBranch, "--stat");
            result.hasChanges = tf > 0;
            result.detail = this.captureGitLines(dir, 500, 60, "git", "diff", baseBranch + ".." + compareBranch);
        }
        finally {
            this.deleteDir(dir);
        }
        return result;
    }

    private int runGit(File dir, int timeoutSec, String ... cmd) {
        try {
            ProcessBuilder pb = new ProcessBuilder(cmd);
            pb.directory(dir);
            pb.redirectErrorStream(true);
            Process p = pb.start();
            if (!p.waitFor(timeoutSec, TimeUnit.SECONDS)) {
                p.destroyForcibly();
                return -1;
            }
            return p.exitValue();
        }
        catch (Exception e) {
            return -1;
        }
    }

    private String captureGit(File dir, int timeoutSec, String ... cmd) {
        try {
            ProcessBuilder pb = new ProcessBuilder(cmd);
            pb.directory(dir);
            pb.redirectErrorStream(true);
            Process p = pb.start();
            StringBuilder sb = new StringBuilder();
            try (BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8));){
                String line;
                while ((line = r.readLine()) != null) {
                    sb.append(line).append("\n");
                }
            }
            p.waitFor(timeoutSec, TimeUnit.SECONDS);
            return sb.toString();
        }
        catch (Exception e) {
            return "[ERROR] " + e.getMessage();
        }
    }

    private String captureGitLines(File dir, int maxLines, int timeoutSec, String ... cmd) {
        try {
            ProcessBuilder pb = new ProcessBuilder(cmd);
            pb.directory(dir);
            pb.redirectErrorStream(true);
            Process p = pb.start();
            StringBuilder sb = new StringBuilder();
            int cnt = 0;
            try (BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8));){
                String line;
                while ((line = r.readLine()) != null) {
                    sb.append(line).append("\n");
                    if (++cnt < maxLines) continue;
                    sb.append("[TRUNCATED]\n");
                    break;
                }
            }
            p.waitFor(timeoutSec, TimeUnit.SECONDS);
            return sb.toString();
        }
        catch (Exception e) {
            return "[ERROR] " + e.getMessage();
        }
    }

    private void deleteDir(File dir) {
        File[] files;
        if (dir == null || !dir.exists()) {
            return;
        }
        if (dir.isDirectory() && (files = dir.listFiles()) != null) {
            for (File f : files) {
                this.deleteDir(f);
            }
        }
        dir.delete();
    }

    private void saveResults(Long taskId, List<JsonObject> ok, List<JsonObject> fail) {
        try (Connection conn = this.getConn();){
            conn.setAutoCommit(false);
            String sql = "INSERT INTO git_version_results (task_id, project_id, project_name, team_id, team_name, repo_url, has_changes, changed_files, insertions, deletions, diff_summary, diff_detail, error, status) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
            try (PreparedStatement ps = conn.prepareStatement(sql);){
                for (JsonObject r : ok) {
                    this.bindRow(ps, taskId, r, "SUCCESS");
                    ps.addBatch();
                }
                for (JsonObject r : fail) {
                    this.bindRow(ps, taskId, r, "FAILED");
                    ps.addBatch();
                }
                ps.executeBatch();
            }
            conn.commit();
        }
        catch (Exception e) {
            System.err.println("[VC] saveResults failed: " + e.getMessage());
        }
    }

    private void bindRow(PreparedStatement ps, Long taskId, JsonObject r, String status) throws Exception {
        ps.setLong(1, taskId);
        ps.setLong(2, r.get("projectId").getAsLong());
        ps.setString(3, this.getStr(r, "projectName"));
        ps.setLong(4, r.has("teamId") && !r.get("teamId").isJsonNull() ? r.get("teamId").getAsLong() : 0L);
        ps.setString(5, this.getStr(r, "teamName"));
        ps.setString(6, this.getStr(r, "repoUrl"));
        ps.setBoolean(7, r.has("hasChanges") && !r.get("hasChanges").isJsonNull() && r.get("hasChanges").getAsBoolean());
        ps.setString(8, this.getStr(r, "changedFiles"));
        ps.setString(9, this.getStr(r, "insertions"));
        ps.setString(10, this.getStr(r, "deletions"));
        ps.setString(11, this.getStr(r, "diffSummary"));
        ps.setString(12, this.getStr(r, "diff"));
        ps.setString(13, this.getStr(r, "error"));
        ps.setString(14, status);
    }

    private JsonObject getComparisonDetail(Long taskId) {
        System.err.println("[VC] getComparisonDetail called with taskId=" + taskId);
        VersionComparison vc = this.vcDAO.getById(taskId);
        if (vc == null) {
            System.err.println("[VC] VersionComparison not found");
            return null;
        }
        System.err.println("[VC] Found VC: status=" + vc.getStatus());
        JsonObject result = new JsonObject();
        result.addProperty("taskId", (Number)vc.getId());
        result.addProperty("versionName", vc.getVersionName());
        result.addProperty("baseBranch", vc.getBaseBranch());
        result.addProperty("compareBranch", vc.getCompareBranch());
        result.addProperty("status", vc.getStatus());
        result.addProperty("totalRepos", (Number)vc.getTotalRepos());
        result.addProperty("successCount", (Number)vc.getSuccessCount());
        result.addProperty("failCount", (Number)vc.getFailCount());
        result.addProperty("createdAt", vc.getCreatedAt());
        if (vc.getFinishedAt() != null) {
            result.addProperty("finishedAt", vc.getFinishedAt().toString());
        }
        result.addProperty("aiEnabled", Boolean.valueOf(this.aiClient.isEnabled()));
        if ("DONE".equals(vc.getStatus()) || "FAILED".equals(vc.getStatus())) {
            JsonArray all = this.loadResultsFromDb(taskId);
            result.add("allResults", (JsonElement)all);
            result.add("teamSummary", (JsonElement)this.aggregateByTeam(all));
        }
        return result;
    }

    private JsonArray loadResultsFromDb(Long taskId) {
        JsonArray arr = new JsonArray();
        System.err.println("[VC] loadResultsFromDb called with taskId=" + taskId);
        try (Connection conn = this.getConn();
             PreparedStatement ps = conn.prepareStatement("SELECT * FROM git_version_results WHERE task_id=? ORDER BY team_id, project_name");){
            ps.setLong(1, taskId);
            ResultSet rs = ps.executeQuery();
            System.err.println("[VC] Query executed, checking results...");
            while (rs.next()) {
                System.err.println("[VC] Found row: id=" + rs.getLong("id") + " project=" + rs.getString("project_name"));
                JsonObject r = new JsonObject();
                r.addProperty("id", (Number)rs.getLong("id"));
                r.addProperty("projectId", (Number)rs.getLong("project_id"));
                r.addProperty("projectName", rs.getString("project_name"));
                r.addProperty("teamId", (Number)rs.getLong("team_id"));
                r.addProperty("teamName", rs.getString("team_name"));
                r.addProperty("repoUrl", rs.getString("repo_url"));
                r.addProperty("hasChanges", Boolean.valueOf(rs.getBoolean("has_changes")));
                r.addProperty("changedFiles", rs.getString("changed_files"));
                r.addProperty("insertions", rs.getString("insertions"));
                r.addProperty("deletions", rs.getString("deletions"));
                r.addProperty("diffSummary", rs.getString("diff_summary"));
                r.addProperty("diff", rs.getString("diff_detail"));
                r.addProperty("error", rs.getString("error"));
                r.addProperty("status", rs.getString("status"));
                r.addProperty("aiAnalysis", rs.getString("ai_analysis"));
                arr.add((JsonElement)r);
            }
        }
        catch (Exception e) {
            System.err.println("[VC] loadResults: " + e.getMessage());
        }
        return arr;
    }

    private JsonObject getResultDetail(Long resultId) {
        String sql = "SELECT r.*, c.version_name, c.base_branch, c.compare_branch FROM git_version_results r " +
                     "JOIN git_version_comparisons c ON r.task_id = c.id WHERE r.id = ?";
        try (Connection conn = this.getConn();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, resultId);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                JsonObject r = new JsonObject();
                r.addProperty("id", (Number)rs.getLong("id"));
                r.addProperty("taskId", (Number)rs.getLong("task_id"));
                r.addProperty("projectId", (Number)rs.getLong("project_id"));
                r.addProperty("projectName", rs.getString("project_name"));
                r.addProperty("teamId", (Number)rs.getLong("team_id"));
                r.addProperty("teamName", rs.getString("team_name"));
                r.addProperty("repoUrl", rs.getString("repo_url"));
                r.addProperty("hasChanges", Boolean.valueOf(rs.getBoolean("has_changes")));
                r.addProperty("changedFiles", rs.getString("changed_files"));
                r.addProperty("insertions", rs.getString("insertions"));
                r.addProperty("deletions", rs.getString("deletions"));
                r.addProperty("diffSummary", rs.getString("diff_summary"));
                r.addProperty("diffDetail", rs.getString("diff_detail"));
                r.addProperty("error", rs.getString("error"));
                r.addProperty("status", rs.getString("status"));
                r.addProperty("aiAnalysis", rs.getString("ai_analysis"));
                r.addProperty("versionName", rs.getString("version_name"));
                r.addProperty("baseBranch", rs.getString("base_branch"));
                r.addProperty("compareBranch", rs.getString("compare_branch"));
                return r;
            }
        }
catch (Exception e) {
            System.err.println("[VC] getResultDetail: " + e.getMessage());
        }
        return null;
    }

    private JsonObject aggregateByTeam(JsonArray results) {
        JsonObject teams = new JsonObject();
        for (int i = 0; i < results.size(); ++i) {
            JsonObject t;
            long tId;
            JsonObject r = results.get(i).getAsJsonObject();
            String tName = this.getStr(r, "teamName");
            long l = tId = r.has("teamId") && !r.get("teamId").isJsonNull() ? r.get("teamId").getAsLong() : 0L;
            if (!teams.has(tName)) {
                t = new JsonObject();
                t.addProperty("teamId", (Number)tId);
                t.addProperty("teamName", tName);
                t.addProperty("totalRepos", (Number)0);
                t.addProperty("changedRepos", (Number)0);
                t.addProperty("totalFiles", (Number)0);
                t.addProperty("totalInsertions", (Number)0);
                t.addProperty("totalDeletions", (Number)0);
                t.addProperty("successCount", (Number)0);
                t.addProperty("failCount", (Number)0);
                t.add("repos", (JsonElement)new JsonArray());
                teams.add(tName, (JsonElement)t);
            }
            t = teams.get(tName).getAsJsonObject();
            t.addProperty("totalRepos", (Number)(t.get("totalRepos").getAsInt() + 1));
            t.addProperty("totalFiles", (Number)(t.get("totalFiles").getAsInt() + this.parseInt(this.getStr(r, "changedFiles"))));
            t.addProperty("totalInsertions", (Number)(t.get("totalInsertions").getAsInt() + this.parseInt(this.getStr(r, "insertions"))));
            t.addProperty("totalDeletions", (Number)(t.get("totalDeletions").getAsInt() + this.parseInt(this.getStr(r, "deletions"))));
            if ("SUCCESS".equals(this.getStr(r, "status"))) {
                t.addProperty("successCount", (Number)(t.get("successCount").getAsInt() + 1));
                if (r.has("hasChanges") && r.get("hasChanges").getAsBoolean()) {
                    t.addProperty("changedRepos", (Number)(t.get("changedRepos").getAsInt() + 1));
                }
            } else {
                t.addProperty("failCount", (Number)(t.get("failCount").getAsInt() + 1));
            }
            t.get("repos").getAsJsonArray().add((JsonElement)r);
        }
        return teams;
    }

    private Connection getConn() throws Exception {
        return DBUtil.getConnection();
    }

    private int parseInt(String s) {
        if (s == null || s.isEmpty()) {
            return 0;
        }
        try {
            return Integer.parseInt(s.replaceAll("[^0-9]", ""));
        }
        catch (Exception e) {
            return 0;
        }
    }

    private String getStr(JsonObject obj, String key) {
        if (obj == null || !obj.has(key) || obj.get(key).isJsonNull()) {
            return "";
        }
        return obj.get(key).getAsString();
    }

    private String nullStr(String s, String def) {
        return s != null && !s.isEmpty() ? s : def;
    }

    private String str(String s) {
        return s != null ? s : "";
    }

    private String defaultStr(String s, String def) {
        return s != null && !s.trim().isEmpty() ? s.trim() : def;
    }

    private void sleep(long ms) {
        try {
            Thread.sleep(ms);
        }
        catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private void sendSuccess(HttpServletResponse response, Object data) throws IOException {
        response.setContentType("application/json;charset=UTF-8");
        response.setStatus(200);
        JsonObject json = new JsonObject();
        json.addProperty("success", Boolean.valueOf(true));
        json.add("data", this.gson.toJsonTree(data));
        response.getWriter().write(json.toString());
    }

    private void sendSuccessMsg(HttpServletResponse response, String message) throws IOException {
        response.setContentType("application/json;charset=UTF-8");
        response.setStatus(200);
        JsonObject json = new JsonObject();
        json.addProperty("success", Boolean.valueOf(true));
        json.addProperty("message", message);
        response.getWriter().write(json.toString());
    }

    private void sendError(HttpServletResponse response, int status, String message) throws IOException {
        response.setContentType("application/json;charset=UTF-8");
        response.setStatus(status);
        JsonObject json = new JsonObject();
        json.addProperty("success", Boolean.valueOf(false));
        json.addProperty("message", message);
        response.getWriter().write(json.toString());
    }

    private static class DiffResult {
        boolean hasChanges;
        String changedFiles = "0";
        String insertions = "0";
        String deletions = "0";
        String summary = "";
        String detail = "";
        String error = null;
        List<FileDiffInfo> addedFiles = new ArrayList<FileDiffInfo>();
        List<FileDiffInfo> modifiedFiles = new ArrayList<FileDiffInfo>();
        List<FileDiffInfo> deletedFiles = new ArrayList<FileDiffInfo>();
        List<FileDiffInfo> otherFiles = new ArrayList<FileDiffInfo>();

        private DiffResult() {
        }
    }

    private static class FileDiffInfo {
        private String path;
        private String status;
        private String statusLabel;
        private String diffContent;

        public FileDiffInfo(String path, String status) {
            this.path = path;
            this.status = status;
            this.statusLabel = this.getStatusLabel(status);
        }

        private String getStatusLabel(String s) {
            switch (s) {
                case "A": {
                    return "\u65b0\u589e";
                }
                case "M": {
                    return "\u4fee\u6539";
                }
                case "D": {
                    return "\u5220\u9664";
                }
                case "R": {
                    return "\u91cd\u547d\u540d";
                }
            }
            return "\u5176\u4ed6";
        }

        public String getPath() {
            return this.path;
        }

        public String getStatus() {
            return this.status;
        }

        public String getStatusLabel() {
            return this.statusLabel;
        }

        public String getDiffContent() {
            return this.diffContent;
        }

        public void setDiffContent(String diff) {
            this.diffContent = diff;
        }
    }
}
