package com.qiuniu.servlet;

import com.qiuniu.dao.PromptAnalysisDAO;
import com.qiuniu.model.PromptAnalysis;
import com.qiuniu.model.User;
import com.qiuniu.util.PermissionHelper;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.*;

/**
 * 提示词管理 Servlet
 * 对接 prompt_analysis 表，按文件类型维护 AI 分析提示词
 */
@WebServlet("/prompt")
public class PromptServlet extends HttpServlet {

    private static final long serialVersionUID = 1L;
    private static final Gson gson = new GsonBuilder().setDateFormat("yyyy-MM-dd HH:mm:ss").create();
    private final PromptAnalysisDAO promptDAO = new PromptAnalysisDAO();

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        request.setCharacterEncoding("UTF-8");
        response.setContentType("application/json;charset=UTF-8");

        HttpSession session = request.getSession(false);
        if (session == null || session.getAttribute("user") == null) {
            sendError(response, 401, "请先登录");
            return;
        }

        String action = request.getParameter("action");

        try {
            switch (action != null ? action : "list") {
                case "list":
                    listPrompts(response);
                    break;
                case "get":
                    getPrompt(request, response);
                    break;
                case "search":
                    searchPrompts(request, response);
                    break;
                case "categories":
                    listCategories(response);
                    break;
                default:
                    listPrompts(response);
            }
        } catch (Exception e) {
            e.printStackTrace();
            sendError(response, 500, "系统错误：" + e.getMessage());
        }
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        request.setCharacterEncoding("UTF-8");
        response.setContentType("application/json;charset=UTF-8");

        HttpSession session = request.getSession(false);
        if (session == null || session.getAttribute("user") == null) {
            sendError(response, 401, "请先登录");
            return;
        }

        // 写操作需要管理员权限
        if (!PermissionHelper.requireAdmin(request, response)) {
            return;
        }

        String action = request.getParameter("action");

        try {
            switch (action != null ? action : "") {
                case "create":
                    createPrompt(request, response);
                    break;
                case "update":
                    updatePrompt(request, response);
                    break;
                case "delete":
                    deletePrompt(request, response);
                    break;
                default:
                    sendError(response, 400, "未知操作：" + action);
            }
        } catch (Exception e) {
            e.printStackTrace();
            sendError(response, 500, "系统错误：" + e.getMessage());
        }
    }

    // ==================== 查询类操作 ====================

    private void listPrompts(HttpServletResponse response) throws Exception {
        List<PromptAnalysis> prompts = promptDAO.getAll();
        if (prompts == null) prompts = new ArrayList<>();
        sendSuccess(response, prompts);
    }

    private void getPrompt(HttpServletRequest request, HttpServletResponse response) throws Exception {
        String idStr = request.getParameter("id");
        if (idStr == null || idStr.trim().isEmpty()) {
            sendError(response, 400, "缺少参数：id");
            return;
        }
        PromptAnalysis prompt = promptDAO.getById(Long.parseLong(idStr.trim()));
        if (prompt != null) {
            sendSuccess(response, prompt);
        } else {
            sendError(response, 404, "提示词不存在");
        }
    }

    private void searchPrompts(HttpServletRequest request, HttpServletResponse response) throws Exception {
        String keyword = request.getParameter("keyword");
        if (keyword == null || keyword.trim().isEmpty()) {
            listPrompts(response);
            return;
        }
        List<PromptAnalysis> prompts = promptDAO.search(keyword.trim());
        if (prompts == null) prompts = new ArrayList<>();
        sendSuccess(response, prompts);
    }

    private void listCategories(HttpServletResponse response) throws Exception {
        // 从 prompt_analysis 表的所有 file_type 构建分类列表
        List<PromptAnalysis> all = promptDAO.getAll();
        Set<String> cats = new LinkedHashSet<>();
        if (all != null) {
            for (PromptAnalysis p : all) {
                if (p.getFileType() != null) {
                    cats.add(p.getFileType());
                }
            }
        }
        sendSuccess(response, new ArrayList<>(cats));
    }

    // ==================== 写操作 ====================

    private void createPrompt(HttpServletRequest request, HttpServletResponse response) throws Exception {
        // 从 JSON body 读取参数
        StringBuilder body = new StringBuilder();
        try (BufferedReader r = request.getReader()) {
            String line;
            while ((line = r.readLine()) != null) body.append(line);
        }
        Map<String, Object> data = gson.fromJson(body.toString(), Map.class);

        String fileType = (String) data.get("fileType");
        String displayName = (String) data.get("displayName");
        String systemPrompt = (String) data.get("systemPrompt");
        String userPromptTemplate = (String) data.get("userPromptTemplate");
        Object maxDiffLinesObj = data.get("maxDiffLines");
        Object priorityObj = data.get("priority");

        if (fileType == null || fileType.trim().isEmpty()) {
            sendError(response, 400, "文件类型不能为空");
            return;
        }
        if (displayName == null || displayName.trim().isEmpty()) {
            sendError(response, 400, "显示名称不能为空");
            return;
        }
        if (systemPrompt == null || systemPrompt.trim().isEmpty()) {
            sendError(response, 400, "系统提示词不能为空");
            return;
        }
        if (userPromptTemplate == null || userPromptTemplate.trim().isEmpty()) {
            sendError(response, 400, "用户提示词模板不能为空");
            return;
        }

        PromptAnalysis prompt = new PromptAnalysis();
        prompt.setFileType(fileType.trim().toLowerCase());
        prompt.setDisplayName(displayName.trim());
        prompt.setSystemPrompt(systemPrompt.trim());
        prompt.setUserPromptTemplate(userPromptTemplate.trim());
        prompt.setMaxDiffLines(maxDiffLinesObj != null ? ((Double) maxDiffLinesObj).intValue() : 200);
        prompt.setPriority(priorityObj != null ? ((Double) priorityObj).intValue() : 100);

        boolean ok = promptDAO.insert(prompt);
        if (ok) {
            sendSuccessMsg(response, "创建成功");
        } else {
            sendError(response, 500, "创建失败，可能已存在相同文件类型的提示词");
        }
    }

    private void updatePrompt(HttpServletRequest request, HttpServletResponse response) throws Exception {
        StringBuilder body = new StringBuilder();
        try (BufferedReader r = request.getReader()) {
            String line;
            while ((line = r.readLine()) != null) body.append(line);
        }
        Map<String, Object> data = gson.fromJson(body.toString(), Map.class);

        String idStr = (String) data.get("id");
        if (idStr == null || idStr.trim().isEmpty()) {
            sendError(response, 400, "缺少参数：id");
            return;
        }

        PromptAnalysis existing = promptDAO.getById(Long.parseLong(idStr.trim()));
        if (existing == null) {
            sendError(response, 404, "提示词不存在");
            return;
        }

        // fileType 不允许修改（UNIQUE KEY）
        String displayName = (String) data.get("displayName");
        String systemPrompt = (String) data.get("systemPrompt");
        String userPromptTemplate = (String) data.get("userPromptTemplate");
        Object maxDiffLinesObj = data.get("maxDiffLines");
        Object priorityObj = data.get("priority");

        if (displayName != null) existing.setDisplayName(displayName.trim());
        if (systemPrompt != null) existing.setSystemPrompt(systemPrompt.trim());
        if (userPromptTemplate != null) existing.setUserPromptTemplate(userPromptTemplate.trim());
        if (maxDiffLinesObj != null) existing.setMaxDiffLines(((Double) maxDiffLinesObj).intValue());
        if (priorityObj != null) existing.setPriority(((Double) priorityObj).intValue());

        boolean ok = promptDAO.update(existing);
        if (ok) {
            sendSuccessMsg(response, "更新成功");
        } else {
            sendError(response, 500, "更新失败");
        }
    }

    private void deletePrompt(HttpServletRequest request, HttpServletResponse response) throws Exception {
        String idStr = request.getParameter("id");
        if (idStr == null || idStr.trim().isEmpty()) {
            sendError(response, 400, "缺少参数：id");
            return;
        }

        // 不允许删除 default 类型
        PromptAnalysis existing = promptDAO.getById(Long.parseLong(idStr.trim()));
        if (existing == null) {
            sendError(response, 404, "提示词不存在");
            return;
        }
        if ("default".equals(existing.getFileType())) {
            sendError(response, 400, "默认提示词不允许删除");
            return;
        }

        boolean ok = promptDAO.delete(Long.parseLong(idStr.trim()));
        if (ok) {
            sendSuccessMsg(response, "删除成功");
        } else {
            sendError(response, 500, "删除失败");
        }
    }

    // ==================== 响应工具 ====================

    private void sendSuccess(HttpServletResponse response, Object data) throws IOException {
        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("data", data);
        PrintWriter out = response.getWriter();
        out.write(gson.toJson(result));
        out.flush();
    }

    private void sendSuccessMsg(HttpServletResponse response, String message) throws IOException {
        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("message", message);
        PrintWriter out = response.getWriter();
        out.write(gson.toJson(result));
        out.flush();
    }

    private void sendError(HttpServletResponse response, int status, String message) throws IOException {
        response.setStatus(status);
        Map<String, Object> result = new HashMap<>();
        result.put("success", false);
        result.put("message", message);
        PrintWriter out = response.getWriter();
        out.write(gson.toJson(result));
        out.flush();
    }
}
