package com.qiuniu.servlet;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.qiuniu.dao.GitProjectDAO;
import com.qiuniu.dao.GitTeamDAO;
import com.qiuniu.dao.UserDAO;
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
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@WebServlet("/team")
public class TeamServlet extends HttpServlet {
    private GitTeamDAO teamDAO = new GitTeamDAO();
    private GitProjectDAO projectDAO = new GitProjectDAO();
    private Gson gson = new Gson();

    @Override
    public void init() throws ServletException {
        teamDAO.createTable();
        projectDAO.createTable();
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        // 权限检查：需要登录
        if (!PermissionHelper.requireLogin(request, response)) {
            return;
        }

        User user = PermissionHelper.getLoginUser(request);
        String action = request.getParameter("action");

        if ("list".equals(action)) {
            // 团队全局共享，返回所有团队
            List<GitTeam> teams = teamDAO.getAll();
            sendSuccess(response, teams);

        } else if ("get".equals(action)) {
            Long id = Long.parseLong(request.getParameter("id"));
            GitTeam team = teamDAO.getById(id);
            if (team != null) {
                sendSuccess(response, team);
            } else {
                sendError(response, "团队不存在");
            }

        } else if ("dashboard".equals(action)) {
            // 团队全局共享，返回所有团队和所有项目
            List<GitTeam> teams = teamDAO.getAll();
            List<GitProject> allProjects = projectDAO.getAll();

            List<Map<String, Object>> teamDataList = new ArrayList<>();
            for (GitTeam team : teams) {
                List<GitProject> teamProjects = new ArrayList<>();
                for (GitProject p : allProjects) {
                    if (p.getTeamId() != null && p.getTeamId().equals(team.getId())) {
                        teamProjects.add(p);
                    }
                }
                Map<String, Object> teamData = new HashMap<>();
                teamData.put("team", team);
                teamData.put("projects", teamProjects);
                teamDataList.add(teamData);
            }

            List<GitProject> noTeamProjects = new ArrayList<>();
            for (GitProject p : allProjects) {
                if (p.getTeamId() == null) {
                    noTeamProjects.add(p);
                }
            }

            Map<String, Object> result = new HashMap<>();
            result.put("teams", teamDataList);
            result.put("noTeamProjects", noTeamProjects);
            sendSuccess(response, result);
        }
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        // 权限检查：需要登录
        if (!PermissionHelper.requireLogin(request, response)) {
            return;
        }

        User user = PermissionHelper.getLoginUser(request);
        String action = request.getParameter("action");

        if ("create".equals(action)) {
            // 创建团队：仅限超级管理员
            if (!PermissionHelper.requireAdmin(request, response)) {
                return;
            }

            String name = request.getParameter("name");
            String code = request.getParameter("code");
            String description = request.getParameter("description");

            if (name == null || name.trim().isEmpty() || code == null || code.trim().isEmpty()) {
                sendError(response, "团队名称和代码不能为空");
                return;
            }

            code = code.trim().toLowerCase();
            if (teamDAO.existsByCode(code)) {
                sendError(response, "团队代码已存在：" + code);
                return;
            }

            GitTeam team = new GitTeam(user.getId(), name.trim(), code, description);
            if (teamDAO.add(team)) {
                // 自动创建团队管理员用户
                createTeamAdminUser(team);
                sendSuccess(response, "团队创建成功，已自动创建团队管理员: " + code + "admin");
            } else {
                sendError(response, "创建失败");
            }

        } else if ("update".equals(action)) {
            // 更新团队：需要写权限
            if (!PermissionHelper.requireWrite(request, response)) {
                return;
            }

            Long teamId = Long.parseLong(request.getParameter("id"));
            GitTeam existingTeam = teamDAO.getById(teamId);
            if (existingTeam == null) {
                sendError(response, "团队不存在");
                return;
            }

            // 检查是否有权限修改此团队
            if (!PermissionHelper.canModifyTeam(user, teamId, existingTeam.getUserId())) {
                response.setStatus(403);
                sendError(response, "您没有权限修改此团队");
                return;
            }

            GitTeam team = new GitTeam();
            team.setId(teamId);
            team.setUserId(existingTeam.getUserId());
            team.setName(request.getParameter("name"));
            team.setCode(request.getParameter("code").trim().toLowerCase());
            team.setDescription(request.getParameter("description"));

            if (teamDAO.update(team)) {
                sendSuccess(response, "更新成功");
            } else {
                sendError(response, "更新失败");
            }

        } else if ("delete".equals(action)) {
            // 删除团队：需要写权限
            if (!PermissionHelper.requireWrite(request, response)) {
                return;
            }

            Long teamId = Long.parseLong(request.getParameter("id"));
            GitTeam existingTeam = teamDAO.getById(teamId);
            if (existingTeam == null) {
                sendError(response, "团队不存在");
                return;
            }

            // 检查是否有权限删除此团队
            if (!PermissionHelper.canModifyTeam(user, teamId, existingTeam.getUserId())) {
                response.setStatus(403);
                sendError(response, "您没有权限删除此团队");
                return;
            }

            if (teamDAO.delete(teamId, existingTeam.getUserId())) {
                sendSuccess(response, "删除成功");
            } else {
                sendError(response, "删除失败");
            }
        }
    }

    private void sendSuccess(HttpServletResponse response, Object data) throws IOException {
        response.setContentType("application/json;charset=UTF-8");
        JsonObject json = new JsonObject();
        json.addProperty("success", true);
        json.add("data", gson.toJsonTree(data));
        response.getWriter().write(json.toString());
    }

    private void sendError(HttpServletResponse response, String message) throws IOException {
        response.setContentType("application/json;charset=UTF-8");
        JsonObject json = new JsonObject();
        json.addProperty("success", false);
        json.addProperty("message", message);
        response.getWriter().write(json.toString());
    }

    /**
     * 为团队创建管理员用户
     * 用户名: teamCode + "admin"
     * 密码: "password"
     */
    private void createTeamAdminUser(GitTeam team) {
        try {
            String username = team.getCode() + "admin";
            String nickname = team.getName() + "管理员";
            
            // 检查用户是否已存在
            if (UserDAO.existsByUsername(username)) {
                System.out.println("[TeamServlet] 团队管理员用户已存在: " + username);
                return;
            }
            
            // 创建用户
            User adminUser = new User();
            adminUser.setUsername(username);
            adminUser.setPassword("admin123"); // 默认密码
            adminUser.setNickname(nickname);
            adminUser.setRole(com.qiuniu.model.Role.TEAM_ADMIN);
            
            if (UserDAO.create(adminUser)) {
                System.out.println("[TeamServlet] 创建团队管理员成功: " + username + " (团队: " + team.getName() + ")");
            } else {
                System.err.println("[TeamServlet] 创建团队管理员失败: " + username);
            }
        } catch (Exception e) {
            System.err.println("[TeamServlet] 创建团队管理员异常: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
