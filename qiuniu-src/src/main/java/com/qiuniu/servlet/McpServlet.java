package com.qiuniu.servlet;

import com.qiuniu.dao.McpCommandDAO;
import com.qiuniu.dao.McpEnvInfoDAO;
import com.qiuniu.dao.McpPromptDAO;
import com.qiuniu.model.McpCommand;
import com.qiuniu.model.McpEnvInfo;
import com.qiuniu.model.McpPrompt;
import com.qiuniu.model.User;
import com.qiuniu.util.PermissionHelper;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.*;
import java.io.IOException;
import java.util.List;

/**
 * MCP服务统一入口Servlet
 *
 * 路由设计：
 *   /api/mcp/env/*       → 环境信息CRUD
 *   /api/mcp/command/*  → MCP命令配置CRUD
 *   /api/mcp/prompt/*    → MCP提示词配置CRUD
 *   /api/mcp/execute/*   → MCP执行接口（待扩展）
 */
@WebServlet("/api/mcp/*")
public class McpServlet extends HttpServlet {

    private void out(HttpServletResponse resp, String json) throws IOException {
        resp.setContentType("application/json;charset=UTF-8");
        resp.getWriter().write(json);
    }

    private void outOk(HttpServletResponse resp) throws IOException {
        out(resp, "{\"ok\":true}");
    }

    private void outError(HttpServletResponse resp, int status, String msg) throws IOException {
        resp.setStatus(status);
        out(resp, "{\"ok\":false,\"error\":\"" + escapeJson(msg) + "\"}");
    }

    // ==================== GET ====================
    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {
        if (!PermissionHelper.requireLogin(req, resp)) return;

        String pathInfo = req.getPathInfo();
        try {
            if ("/env".equals(pathInfo)) {
                doListEnv(req, resp);
            } else if (pathInfo != null && pathInfo.startsWith("/command/")) {
                doGetCommand(req, resp, pathInfo.substring("/command/".length()));
            } else if ("/command".equals(pathInfo)) {
                doListCommand(req, resp);
            } else if (pathInfo != null && pathInfo.startsWith("/prompt/")) {
                doGetPrompt(req, resp, pathInfo.substring("/prompt/".length()));
            } else if ("/prompt".equals(pathInfo)) {
                doListPrompt(req, resp);
            } else if (pathInfo == null || "/".equals(pathInfo)) {
                out(resp, "{\"module\":\"mcp\",\"version\":\"1.1\",\"features\":[\"env\",\"command\",\"prompt\"]}");
            } else {
                outError(resp, 404, "unknown action: " + pathInfo);
            }
        } catch (Exception e) {
            e.printStackTrace();
            outError(resp, 500, e.getMessage());
        }
    }

    // ==================== POST ====================
    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {
        if (!PermissionHelper.requireLogin(req, resp)) return;

        String pathInfo = req.getPathInfo();
        try {
            if ("/env/add".equals(pathInfo)) {
                if (!PermissionHelper.requireWrite(req, resp)) return;
                doAddEnv(req, resp);
            } else if ("/env/update".equals(pathInfo)) {
                if (!PermissionHelper.requireWrite(req, resp)) return;
                doUpdateEnv(req, resp);
            } else if ("/env/delete".equals(pathInfo)) {
                if (!PermissionHelper.requireWrite(req, resp)) return;
                doDeleteEnv(req, resp);
            } else if ("/command/add".equals(pathInfo)) {
                if (!PermissionHelper.requireWrite(req, resp)) return;
                doAddCommand(req, resp);
            } else if ("/command/update".equals(pathInfo)) {
                if (!PermissionHelper.requireWrite(req, resp)) return;
                doUpdateCommand(req, resp);
            } else if ("/command/delete".equals(pathInfo)) {
                if (!PermissionHelper.requireWrite(req, resp)) return;
                doDeleteCommand(req, resp);
            } else if ("/prompt/add".equals(pathInfo)) {
                if (!PermissionHelper.requireWrite(req, resp)) return;
                doAddPrompt(req, resp);
            } else if ("/prompt/update".equals(pathInfo)) {
                if (!PermissionHelper.requireWrite(req, resp)) return;
                doUpdatePrompt(req, resp);
            } else if ("/prompt/delete".equals(pathInfo)) {
                if (!PermissionHelper.requireWrite(req, resp)) return;
                doDeletePrompt(req, resp);
            } else {
                outError(resp, 404, "unknown action: " + pathInfo);
            }
        } catch (Exception e) {
            e.printStackTrace();
            outError(resp, 500, e.getMessage());
        }
    }

    // ==================== 环境信息 CRUD ====================

    private void doListEnv(HttpServletRequest req, HttpServletResponse resp) throws Exception {
        String id = req.getParameter("id");
        String search = req.getParameter("search");
        if (id != null && !id.trim().isEmpty()) {
            McpEnvInfo info = McpEnvInfoDAO.getById(id.trim());
            out(resp, "{\"ok\":true,\"data\":" + (info != null ? envToJson(info) : "null") + "}");
        } else if (search != null && !search.trim().isEmpty()) {
            List<McpEnvInfo> list = McpEnvInfoDAO.search(search.trim());
            out(resp, "{\"ok\":true,\"data\":" + envListToJson(list) + "}");
        } else {
            List<McpEnvInfo> list = McpEnvInfoDAO.getAll();
            out(resp, "{\"ok\":true,\"data\":" + envListToJson(list) + "}");
        }
    }

    private void doAddEnv(HttpServletRequest req, HttpServletResponse resp) throws Exception {
        McpEnvInfo info = bindEnv(req);
        if (info.getEnvId() == null || info.getEnvId().trim().isEmpty()) {
            outError(resp, 400, "MCP环境信息编号不能为空"); return;
        }
        if (McpEnvInfoDAO.getById(info.getEnvId().trim()) != null) {
            outError(resp, 409, "编号 " + info.getEnvId() + " 已存在"); return;
        }
        McpEnvInfoDAO.add(info);
        outOk(resp);
    }

    private void doUpdateEnv(HttpServletRequest req, HttpServletResponse resp) throws Exception {
        McpEnvInfo info = bindEnv(req);
        if (info.getEnvId() == null || info.getEnvId().trim().isEmpty()) {
            outError(resp, 400, "envId不能为空"); return;
        }
        McpEnvInfoDAO.update(info);
        outOk(resp);
    }

    private void doDeleteEnv(HttpServletRequest req, HttpServletResponse resp) throws Exception {
        String envId = req.getParameter("envId");
        if (envId == null || envId.trim().isEmpty()) {
            outError(resp, 400, "envId参数不能为空"); return;
        }
        McpEnvInfoDAO.delete(envId.trim());
        outOk(resp);
    }

    // ==================== 命令配置 CRUD ====================

    private void doGetCommand(HttpServletRequest req, HttpServletResponse resp, String commandId) throws Exception {
        if (commandId == null || commandId.trim().isEmpty()) {
            doListCommand(req, resp); return;
        }
        McpCommand cmd = McpCommandDAO.getById(commandId.trim());
        if (cmd == null) { outError(resp, 404, "命令 " + commandId + " 不存在"); return; }
        out(resp, "{\"ok\":true,\"data\":" + cmdToJson(cmd) + "}");
    }

    private void doGetPrompt(HttpServletRequest req, HttpServletResponse resp, String promptId) throws Exception {
        if (promptId == null || promptId.trim().isEmpty()) {
            doListPrompt(req, resp); return;
        }
        McpPrompt p = McpPromptDAO.getById(promptId.trim());
        if (p == null) { outError(resp, 404, "提示词 " + promptId + " 不存在"); return; }
        out(resp, "{\"ok\":true,\"data\":" + promptToJson(p) + "}");
    }

    private void doListCommand(HttpServletRequest req, HttpServletResponse resp) throws Exception {
        String keyword = req.getParameter("keyword");
        if (keyword != null && !keyword.trim().isEmpty()) {
            List<McpCommand> list = McpCommandDAO.search(keyword.trim());
            out(resp, "{\"ok\":true,\"data\":" + cmdListToJson(list) + "}");
        } else {
            List<McpCommand> list = McpCommandDAO.getAll();
            out(resp, "{\"ok\":true,\"data\":" + cmdListToJson(list) + "}");
        }
    }

    private void doAddCommand(HttpServletRequest req, HttpServletResponse resp) throws Exception {
        McpCommand cmd = bindCommand(req);
        if (cmd.getCommandId() == null || cmd.getCommandId().trim().isEmpty()) {
            outError(resp, 400, "命令编号不能为空"); return;
        }
        if (McpCommandDAO.getById(cmd.getCommandId().trim()) != null) {
            outError(resp, 409, "命令编号 " + cmd.getCommandId() + " 已存在"); return;
        }
        McpCommandDAO.add(cmd);
        outOk(resp);
    }

    private void doUpdateCommand(HttpServletRequest req, HttpServletResponse resp) throws Exception {
        McpCommand cmd = bindCommand(req);
        if (cmd.getCommandId() == null || cmd.getCommandId().trim().isEmpty()) {
            outError(resp, 400, "commandId不能为空"); return;
        }
        McpCommandDAO.update(cmd);
        outOk(resp);
    }

    private void doDeleteCommand(HttpServletRequest req, HttpServletResponse resp) throws Exception {
        String cmdId = req.getParameter("commandId");
        if (cmdId == null || cmdId.trim().isEmpty()) {
            outError(resp, 400, "commandId参数不能为空"); return;
        }
        McpCommandDAO.delete(cmdId.trim());
        outOk(resp);
    }

    // ==================== 提示词配置 CRUD ====================

    private void doListPrompt(HttpServletRequest req, HttpServletResponse resp) throws Exception {
        String keyword = req.getParameter("keyword");
        if (keyword != null && !keyword.trim().isEmpty()) {
            List<McpPrompt> list = McpPromptDAO.search(keyword.trim());
            out(resp, "{\"ok\":true,\"data\":" + promptListToJson(list) + "}");
        } else {
            List<McpPrompt> list = McpPromptDAO.getAll();
            out(resp, "{\"ok\":true,\"data\":" + promptListToJson(list) + "}");
        }
    }

    private void doAddPrompt(HttpServletRequest req, HttpServletResponse resp) throws Exception {
        McpPrompt p = bindPrompt(req);
        if (p.getPromptId() == null || p.getPromptId().trim().isEmpty()) {
            outError(resp, 400, "提示词编号不能为空"); return;
        }
        if (McpPromptDAO.getById(p.getPromptId().trim()) != null) {
            outError(resp, 409, "提示词编号 " + p.getPromptId() + " 已存在"); return;
        }
        McpPromptDAO.add(p);
        outOk(resp);
    }

    private void doUpdatePrompt(HttpServletRequest req, HttpServletResponse resp) throws Exception {
        McpPrompt p = bindPrompt(req);
        if (p.getPromptId() == null || p.getPromptId().trim().isEmpty()) {
            outError(resp, 400, "promptId不能为空"); return;
        }
        McpPromptDAO.update(p);
        outOk(resp);
    }

    private void doDeletePrompt(HttpServletRequest req, HttpServletResponse resp) throws Exception {
        String pId = req.getParameter("promptId");
        if (pId == null || pId.trim().isEmpty()) {
            outError(resp, 400, "promptId参数不能为空"); return;
        }
        McpPromptDAO.delete(pId.trim());
        outOk(resp);
    }

    // ==================== Bind / toJson ====================

    private McpEnvInfo bindEnv(HttpServletRequest req) {
        McpEnvInfo info = new McpEnvInfo();
        info.setEnvId(req.getParameter("envId"));
        info.setSystemName(req.getParameter("systemName"));
        info.setEnvironment(req.getParameter("environment"));
        info.setClusterCode(req.getParameter("clusterCode"));
        info.setMachineName(req.getParameter("machineName"));
        info.setIpAddress(req.getParameter("ipAddress"));
        info.setUsername(req.getParameter("username"));
        info.setPassword(req.getParameter("password"));
        info.setCommandId(req.getParameter("commandId"));
        info.setPromptId(req.getParameter("promptId"));
        return info;
    }

    private McpCommand bindCommand(HttpServletRequest req) {
        McpCommand cmd = new McpCommand();
        cmd.setCommandId(req.getParameter("commandId"));
        cmd.setCommand(req.getParameter("command"));
        cmd.setDescription(req.getParameter("description"));
        return cmd;
    }

    private McpPrompt bindPrompt(HttpServletRequest req) {
        McpPrompt p = new McpPrompt();
        p.setPromptId(req.getParameter("promptId"));
        p.setContent(req.getParameter("content"));
        return p;
    }

    // --- Env ---
    private String envToJson(McpEnvInfo info) {
        StringBuilder sb = new StringBuilder("{");
        sb.append("\"envId\":\"").append(escapeJson(info.getEnvId())).append("\",");
        sb.append("\"systemName\":\"").append(escapeJson(info.getSystemName())).append("\",");
        sb.append("\"environment\":\"").append(escapeJson(info.getEnvironment())).append("\",");
        sb.append("\"clusterCode\":\"").append(escapeJson(info.getClusterCode())).append("\",");
        sb.append("\"machineName\":\"").append(escapeJson(info.getMachineName())).append("\",");
        sb.append("\"ipAddress\":\"").append(escapeJson(info.getIpAddress())).append("\",");
        sb.append("\"username\":\"").append(escapeJson(info.getUsername())).append("\",");
        sb.append("\"password\":\"").append(escapeJson(info.getPassword())).append("\",");
        sb.append("\"commandId\":\"").append(escapeJson(info.getCommandId())).append("\",");
        sb.append("\"promptId\":\"").append(escapeJson(info.getPromptId())).append("\"}");
        return sb.toString();
    }

    private String envListToJson(List<McpEnvInfo> list) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < list.size(); i++) {
            if (i > 0) sb.append(",");
            sb.append(envToJson(list.get(i)));
        }
        sb.append("]");
        return sb.toString();
    }

    // --- Command ---
    private String cmdToJson(McpCommand cmd) {
        return new StringBuilder("{")
            .append("\"commandId\":\"").append(escapeJson(cmd.getCommandId())).append("\",")
            .append("\"command\":\"").append(escapeJson(cmd.getCommand())).append("\",")
            .append("\"description\":\"").append(escapeJson(cmd.getDescription())).append("\"}")
            .toString();
    }

    private String cmdListToJson(List<McpCommand> list) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < list.size(); i++) {
            if (i > 0) sb.append(",");
            sb.append(cmdToJson(list.get(i)));
        }
        sb.append("]");
        return sb.toString();
    }

    // --- Prompt ---
    private String promptToJson(McpPrompt p) {
        return new StringBuilder("{")
            .append("\"promptId\":\"").append(escapeJson(p.getPromptId())).append("\",")
            .append("\"content\":\"").append(escapeJson(p.getContent())).append("\"}")
            .toString();
    }

    private String promptListToJson(List<McpPrompt> list) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < list.size(); i++) {
            if (i > 0) sb.append(",");
            sb.append(promptToJson(list.get(i)));
        }
        sb.append("]");
        return sb.toString();
    }

    private String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }
}
