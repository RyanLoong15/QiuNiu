package com.qiuniu.servlet;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.qiuniu.dao.GitProjectDAO;
import com.qiuniu.dao.GitTeamDAO;
import com.qiuniu.model.GitProject;
import com.qiuniu.model.GitTeam;
import com.qiuniu.model.User;
import com.qiuniu.model.Role;
import com.qiuniu.util.PermissionHelper;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

@WebServlet(urlPatterns = {"/git"}, loadOnStartup = 1)
public class GitServlet extends HttpServlet {
    private GitProjectDAO gitProjectDAO = new GitProjectDAO();
    private GitTeamDAO teamDAO = new GitTeamDAO();
    private Gson gson = new Gson();
    private ExecutorService diffExecutor;

    @Override
    public void init() throws ServletException {
        gitProjectDAO.createTable();
        diffExecutor = Executors.newFixedThreadPool(5);
        System.out.println("[GitServlet] initialized with diffExecutor");
    }

    @Override
    public void destroy() {
        if (diffExecutor != null) {
            diffExecutor.shutdownNow();
        }
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        // 权限检查：需要登录
        if (!PermissionHelper.requireLogin(request, response)) {
            return;
        }

        User user = PermissionHelper.getLoginUser(request);
        String action = request.getParameter("action");

        try {
            if ("list".equals(action)) {
                // 全局共享：返回所有项目（支持 teamId 过滤）
                List<GitProject> projects = listProjects(request.getParameter("teamId"));
                sendSuccess(response, projects);

            } else if ("get".equals(action)) {
                Long id = Long.parseLong(request.getParameter("id"));
                // 全局共享：允许查看所有项目
                GitProject project = gitProjectDAO.getById(id);
                if (project != null) {
                    sendSuccess(response, project);
                } else {
                    sendError(response, HttpServletResponse.SC_NOT_FOUND, "项目不存在");
                }

            } else if ("diff".equals(action)) {
                Long id = Long.parseLong(request.getParameter("id"));
                // 全局共享：允许查看所有项目的 diff
                GitProject project = gitProjectDAO.getById(id);
                if (project == null) {
                    sendError(response, HttpServletResponse.SC_NOT_FOUND, "项目不存在");
                    return;
                }
                String diff = safeGetGitDiff(project);
                gitProjectDAO.updateLastSync(id);
                JsonObject result = new JsonObject();
                result.addProperty("projectId", project.getId());
                result.addProperty("projectName", project.getName());
                result.addProperty("repoUrl", project.getRepoUrl());
                result.addProperty("baseBranch", project.getBaseBranch());
                result.addProperty("compareBranch", project.getCompareBranch());
                result.addProperty("diff", diff);
                sendSuccess(response, result);

            } else if ("batchDiff".equals(action)) {
                batchDiff(request, response);
            } else {
                sendError(response, HttpServletResponse.SC_BAD_REQUEST, "未知操作");
            }
        } catch (Exception e) {
            e.printStackTrace();
            sendError(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "服务器错误: " + e.getMessage());
        }
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        request.setCharacterEncoding("UTF-8");
        
        // 权限检查：需要登录
        if (!PermissionHelper.requireLogin(request, response)) {
            return;
        }

        User user = PermissionHelper.getLoginUser(request);
        String action = request.getParameter("action");

        try {
            if ("create".equals(action)) {
                // 创建项目：需要写权限
                if (!PermissionHelper.requireWrite(request, response)) {
                    return;
                }

                Long teamId = parseTeamId(request.getParameter("teamId"));
                
                // 如果指定了团队，检查是否有权限在该团队创建项目
                if (teamId != null && !user.isAdmin()) {
                    GitTeam team = teamDAO.getById(teamId);
                    if (team == null || !PermissionHelper.canModifyTeam(user, teamId, team.getUserId())) {
                        response.setStatus(403);
                        sendError(response, 403, "您没有权限在此团队创建项目");
                        return;
                    }
                }

                GitProject project = new GitProject();
                project.setUserId(user.getId());
                project.setTeamId(teamId);
                project.setName(request.getParameter("name"));
                project.setRepoUrl(request.getParameter("repoUrl"));
                project.setBaseBranch(nullToDefault(request.getParameter("baseBranch"), "main"));
                project.setCompareBranch(nullToDefault(request.getParameter("compareBranch"), "develop"));
                
                if (gitProjectDAO.add(project)) {
                    sendSuccessMsg(response, "创建成功");
                } else {
                    sendError(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "创建失败");
                }

            } else if ("update".equals(action)) {
                // 更新项目：需要写权限
                if (!PermissionHelper.requireWrite(request, response)) {
                    return;
                }

                Long id = Long.parseLong(request.getParameter("id"));
                GitProject existing = gitProjectDAO.getById(id);
                if (existing == null) {
                    sendError(response, HttpServletResponse.SC_NOT_FOUND, "项目不存在");
                    return;
                }

                // 检查是否有权限修改此项目
                if (!user.isAdmin()) {
                    // TEAM_ADMIN 只能修改自己团队的项目
                    Long teamId = existing.getTeamId();
                    if (teamId != null) {
                        GitTeam team = teamDAO.getById(teamId);
                        if (team == null || !team.getUserId().equals(user.getId())) {
                            response.setStatus(403);
                            sendError(response, 403, "您没有权限修改此项目");
                            return;
                        }
                    } else {
                        // 没有团队归属的项目，只有 ADMIN 可以修改
                        response.setStatus(403);
                        sendError(response, 403, "您没有权限修改此项目");
                        return;
                    }
                }

                Long newTeamId = parseTeamId(request.getParameter("teamId"));
                
                GitProject project = new GitProject();
                project.setId(id);
                project.setUserId(existing.getUserId());
                project.setTeamId(newTeamId);
                project.setName(request.getParameter("name"));
                project.setRepoUrl(request.getParameter("repoUrl"));
                project.setBaseBranch(nullToDefault(request.getParameter("baseBranch"), "main"));
                project.setCompareBranch(nullToDefault(request.getParameter("compareBranch"), "develop"));
                
                if (gitProjectDAO.update(project)) {
                    sendSuccessMsg(response, "更新成功");
                } else {
                    sendError(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "更新失败");
                }

            } else if ("delete".equals(action)) {
                // 删除项目：需要写权限
                if (!PermissionHelper.requireWrite(request, response)) {
                    return;
                }

                Long id = Long.parseLong(request.getParameter("id"));
                GitProject existing = gitProjectDAO.getById(id);
                if (existing == null) {
                    sendError(response, HttpServletResponse.SC_NOT_FOUND, "项目不存在");
                    return;
                }

                // 检查是否有权限删除此项目
                if (!user.isAdmin()) {
                    Long teamId = existing.getTeamId();
                    if (teamId != null) {
                        GitTeam team = teamDAO.getById(teamId);
                        if (team == null || !team.getUserId().equals(user.getId())) {
                            response.setStatus(403);
                            sendError(response, 403, "您没有权限删除此项目");
                            return;
                        }
                    } else {
                        response.setStatus(403);
                        sendError(response, 403, "您没有权限删除此项目");
                        return;
                    }
                }

                if (gitProjectDAO.delete(id)) {
                    sendSuccessMsg(response, "删除成功");
                } else {
                    sendError(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "删除失败");
                }

            } else {
                sendError(response, HttpServletResponse.SC_BAD_REQUEST, "未知操作");
            }
        } catch (Exception e) {
            e.printStackTrace();
            sendError(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "服务器错误: " + e.getMessage());
        }
    }

    // ── batch diff ──────────────────────────────────────────────

    private void batchDiff(HttpServletRequest request, HttpServletResponse response) throws IOException {
        if (diffExecutor == null || diffExecutor.isShutdown()) {
            diffExecutor = Executors.newFixedThreadPool(5);
        }

        String teamIdStr = request.getParameter("teamId");
        List<GitProject> projects = listProjects(teamIdStr);

        List<JsonObject> results = new ArrayList<>();
        List<JsonObject> failed = new ArrayList<>();

        List<Runnable> tasks = new ArrayList<>();
        Object lock = new Object();

        for (GitProject p : projects) {
            final GitProject proj = p;
            tasks.add(() -> {
                try {
                    String diff = safeGetGitDiff(proj);
                    gitProjectDAO.updateLastSync(proj.getId());
                    JsonObject item = new JsonObject();
                    item.addProperty("projectId", proj.getId());
                    item.addProperty("projectName", proj.getName());
                    item.addProperty("repoUrl", proj.getRepoUrl());
                    item.addProperty("baseBranch", proj.getBaseBranch());
                    item.addProperty("compareBranch", proj.getCompareBranch());
                    item.addProperty("diff", diff);
                    synchronized (lock) {
                        results.add(item);
                    }
                } catch (Exception e) {
                    JsonObject f = new JsonObject();
                    f.addProperty("projectId", proj.getId());
                    f.addProperty("projectName", proj.getName());
                    f.addProperty("repoUrl", proj.getRepoUrl());
                    f.addProperty("error", e.getMessage());
                    synchronized (lock) {
                        failed.add(f);
                    }
                }
            });
        }

        for (Runnable t : tasks) {
            diffExecutor.submit(t);
        }

        diffExecutor.shutdown();
        try {
            diffExecutor.awaitTermination(10, TimeUnit.MINUTES);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        // Re-create executor for next request
        diffExecutor = Executors.newFixedThreadPool(5);

        JsonObject result = new JsonObject();
        result.add("results", gson.toJsonTree(results));
        result.add("failed", gson.toJsonTree(failed));
        result.addProperty("successCount", results.size());
        result.addProperty("failedCount", failed.size());
        result.addProperty("totalCount", projects.size());
        sendSuccess(response, result);
    }

    // ── git operations ───────────────────────────────────────────

    private String safeGetGitDiff(GitProject project) {
        try {
            return getGitDiff(project);
        } catch (Exception e) {
            e.printStackTrace();
            return "[ERROR] " + e.getMessage();
        }
    }

    private String getGitDiff(GitProject project) throws Exception {
        String tmpBase = System.getProperty("java.io.tmpdir");
        if (tmpBase == null || tmpBase.isEmpty()) {
            tmpBase = System.getenv("TEMP");
        }
        if (tmpBase == null || tmpBase.isEmpty()) {
            tmpBase = "C:\\Temp";
        }
        Path tmpPath = Paths.get(tmpBase, "qiuniu-git-" + project.getId());
        File dir = tmpPath.toFile();

        if (dir.exists()) deleteDirectory(dir);
        Files.createDirectories(tmpPath);

        String baseBranch = defaultStr(project.getBaseBranch(), "main");
        String compareBranch = defaultStr(project.getCompareBranch(), "develop");
        String repoUrl = project.getRepoUrl();

        // Verify git is available
        ProcessBuilder gitCheckPb = new ProcessBuilder("git", "--version");
        gitCheckPb.directory(dir);
        gitCheckPb.redirectErrorStream(true);
        Process gitCheckP = gitCheckPb.start();
        StringBuilder gitCheckOut = new StringBuilder();
        try (BufferedReader r = new BufferedReader(new InputStreamReader(gitCheckP.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = r.readLine()) != null) gitCheckOut.append(line);
        }
        gitCheckP.waitFor(5, TimeUnit.SECONDS);

        // Clone
        int cloneExit = runGit(dir, 120, "git", "clone",
            "--branch", baseBranch, "--depth=50", repoUrl, ".");
        if (cloneExit != 0) {
            String cloneLog = captureGit(dir, 120, "git", "clone",
                "--branch", baseBranch, "--depth=50", repoUrl, ".");
            deleteDirectory(dir);
            return "[CLONE_FAILED] exit=" + cloneExit + "\n" + cloneLog;
        }

        if (!new File(dir, ".git").exists()) {
            deleteDirectory(dir);
            return "[CLONE_FAILED] .git directory not found after clone.\n"
                 + "This may indicate an empty repository, authentication failure, or the branch does not exist.\n"
                 + "Branch: " + baseBranch + "  URL: " + repoUrl;
        }

        // Fetch compare branch
        runGit(dir, 60, "git", "fetch", "origin",
            compareBranch + ":" + compareBranch, "--depth=50");

        // Diff stat
        String statOut = captureGit(dir, 30, "git", "diff",
            baseBranch + ".." + compareBranch, "--stat");

        // Detailed diff (max 500 lines)
        String diffOut = captureGitLines(dir, 500, 60,
            "git", "diff", baseBranch + ".." + compareBranch);

        StringBuilder sb = new StringBuilder();
        sb.append("Repo: ").append(repoUrl).append("\n");
        sb.append("Base: ").append(baseBranch).append("  |  Compare: ").append(compareBranch).append("\n");
        sb.append("--- Changes ---\n");
        sb.append(statOut);
        if (diffOut.endsWith("[TRUNCATED]\n")) {
            sb.append("\n[... diff truncated at 500 lines ...]\n");
            sb.append(diffOut.replace("[TRUNCATED]\n", ""));
        } else {
            sb.append("\n--- Details ---\n");
            sb.append(diffOut);
        }

        deleteDirectory(dir);
        return sb.toString();
    }

    private int runGit(File workingDir, int timeoutSec, String... cmd) {
        try {
            ProcessBuilder pb = new ProcessBuilder(cmd);
            pb.directory(workingDir);
            pb.redirectErrorStream(true);
            Process p = pb.start();
            boolean done = p.waitFor(timeoutSec, TimeUnit.SECONDS);
            if (!done) {
                p.destroyForcibly();
                return -1;
            }
            return p.exitValue();
        } catch (Exception e) {
            return -1;
        }
    }

    private String captureGit(File workingDir, int timeoutSec, String... cmd) {
        try {
            ProcessBuilder pb = new ProcessBuilder(cmd);
            pb.directory(workingDir);
            pb.redirectErrorStream(true);
            Process p = pb.start();
            StringBuilder sb = new StringBuilder();
            try (BufferedReader r = new BufferedReader(
                    new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = r.readLine()) != null) {
                    sb.append(line).append("\n");
                }
            }
            p.waitFor(timeoutSec, TimeUnit.SECONDS);
            return sb.toString();
        } catch (Exception e) {
            return "[ERROR] " + e.getMessage();
        }
    }

    private String captureGitLines(File workingDir, int maxLines, int timeoutSec, String... cmd) {
        try {
            ProcessBuilder pb = new ProcessBuilder(cmd);
            pb.directory(workingDir);
            pb.redirectErrorStream(true);
            Process p = pb.start();
            StringBuilder sb = new StringBuilder();
            int count = 0;
            try (BufferedReader r = new BufferedReader(
                    new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = r.readLine()) != null) {
                    sb.append(line).append("\n");
                    count++;
                    if (count >= maxLines) {
                        sb.append("[TRUNCATED]\n");
                        break;
                    }
                }
            }
            p.waitFor(timeoutSec, TimeUnit.SECONDS);
            return sb.toString();
        } catch (Exception e) {
            return "[ERROR] " + e.getMessage();
        }
    }

    private void deleteDirectory(File dir) {
        if (dir == null || !dir.exists()) return;
        if (dir.isDirectory()) {
            File[] files = dir.listFiles();
            if (files != null) {
                for (File f : files) deleteDirectory(f);
            }
        }
        dir.delete();
    }

    // ── helpers ────────────────────────────────────────────────

    /**
     * 获取项目列表（全局共享）
     */
    private List<GitProject> listProjects(String teamIdStr) {
        if (teamIdStr != null && !teamIdStr.trim().isEmpty()) {
            return gitProjectDAO.getByTeamId(Long.parseLong(teamIdStr.trim()));
        }
        return gitProjectDAO.getAll();
    }

    private Long parseTeamId(String s) {
        if (s == null || s.trim().isEmpty()) return null;
        try {
            return Long.parseLong(s.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private String nullToDefault(String s, String def) {
        return (s == null || s.trim().isEmpty()) ? def : s.trim();
    }

    private String defaultStr(String s, String def) {
        return (s == null || s.trim().isEmpty()) ? def : s;
    }

    // ── response helpers ───────────────────────────────────────

    private void sendSuccess(HttpServletResponse response, Object data) throws IOException {
        response.setContentType("application/json;charset=UTF-8");
        response.setStatus(HttpServletResponse.SC_OK);
        JsonObject json = new JsonObject();
        json.addProperty("success", true);
        json.add("data", gson.toJsonTree(data));
        response.getWriter().write(json.toString());
    }

    private void sendSuccessMsg(HttpServletResponse response, String message) throws IOException {
        response.setContentType("application/json;charset=UTF-8");
        response.setStatus(HttpServletResponse.SC_OK);
        JsonObject json = new JsonObject();
        json.addProperty("success", true);
        json.addProperty("message", message);
        response.getWriter().write(json.toString());
    }

    private void sendError(HttpServletResponse response, int status, String message) throws IOException {
        response.setContentType("application/json;charset=UTF-8");
        response.setStatus(status);
        JsonObject json = new JsonObject();
        json.addProperty("success", false);
        json.addProperty("message", message);
        response.getWriter().write(json.toString());
    }
}
