package com.qiuniu.servlet;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonArray;
import com.qiuniu.dao.ChatSessionDAO;
import com.qiuniu.model.ChatSession;
import com.qiuniu.model.ChatMessage;
import com.qiuniu.model.User;
import com.qiuniu.util.FlaskConfig;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 多轮对话 Servlet
 * 提供会话管理和多轮对话接口
 */
@WebServlet("/api/chat/*")
public class ChatServlet extends HttpServlet {

    private static final long serialVersionUID = 1L;
    private static final Gson gson = new GsonBuilder().setDateFormat("yyyy-MM-dd HH:mm:ss").create();
    private static String getFlaskRagUrl() { return FlaskConfig.getBaseUrl(); }
    private static final int MAX_HISTORY_TURNS = 5;  // 保留最近5轮对话

    @Override
    public void init() throws ServletException {
        try {
            ChatSessionDAO.createTables();
        } catch (Exception e) {
            System.err.println("[ChatServlet] 创建表失败: " + e.getMessage());
        }
    }

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

        User user = (User) session.getAttribute("user");
        String pathInfo = request.getPathInfo();

        try {
            if (pathInfo == null || "/".equals(pathInfo) || "/sessions".equals(pathInfo)) {
                // 获取用户的所有会话
                listSessions(request, response, user);
            } else if (pathInfo.startsWith("/session/")) {
                // 获取特定会话的历史消息
                String sessionId = pathInfo.substring("/session/".length());
                getSessionHistory(request, response, sessionId);
            } else {
                sendError(response, 404, "未知的接口路径");
            }
        } catch (Exception e) {
            log("[ChatServlet] doPost EXCEPTION: " + e.getClass().getName() + " - " + e.getMessage());
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

        User user = (User) session.getAttribute("user");
        String pathInfo = request.getPathInfo();

        try {
            if (pathInfo == null || "/".equals(pathInfo) || "/message".equals(pathInfo)) {
                // 发送消息（多轮对话）
                sendMessage(request, response, user);
            } else if ("/session".equals(pathInfo)) {
                // 创建新会话
                createNewSession(request, response, user);
            } else {
                sendError(response, 404, "未知的接口路径");
            }
        } catch (Exception e) {
            log("[ChatServlet] doPost EXCEPTION: " + e.getClass().getName() + " - " + e.getMessage());
            e.printStackTrace();
            sendError(response, 500, "系统错误：" + e.getMessage());
        }
    }

    @Override
    protected void doDelete(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        request.setCharacterEncoding("UTF-8");
        response.setContentType("application/json;charset=UTF-8");

        HttpSession session = request.getSession(false);
        if (session == null || session.getAttribute("user") == null) {
            sendError(response, 401, "请先登录");
            return;
        }

        String pathInfo = request.getPathInfo();
        if (pathInfo == null || !pathInfo.startsWith("/session/")) {
            sendError(response, 400, "需要提供会话ID");
            return;
        }

        String sessionId = pathInfo.substring("/session/".length());

        try {
            ChatSessionDAO.deleteSession(sessionId);
            sendSuccess(response, "会话已删除");
        } catch (Exception e) {
            e.printStackTrace();
            sendError(response, 500, "删除失败：" + e.getMessage());
        }
    }

    // ==================== 业务方法 ====================

    /**
     * 获取用户的所有会话
     */
    private void listSessions(HttpServletRequest request, HttpServletResponse response, User user)
            throws Exception {

        int limit = 20;
        String limitStr = request.getParameter("limit");
        if (limitStr != null && !limitStr.isEmpty()) {
            limit = Integer.parseInt(limitStr);
        }

        List<ChatSession> sessions = ChatSessionDAO.getByUserId(user.getId(), limit);

        Map<String, Object> result = new HashMap<>();
        result.put("sessions", sessions);
        sendSuccess(response, result);
    }

    /**
     * 创建新会话
     */
    private void createNewSession(HttpServletRequest request, HttpServletResponse response, User user)
            throws Exception {

        ChatSession session = ChatSessionDAO.createSession(user.getId());
        if (session != null) {
            sendSuccess(response, session);
        } else {
            sendError(response, 500, "创建会话失败");
        }
    }

    /**
     * 获取会话历史消息
     */
    private void getSessionHistory(HttpServletRequest request, HttpServletResponse response, String sessionId)
            throws Exception {

        ChatSession session = ChatSessionDAO.getBySessionId(sessionId);
        if (session == null) {
            sendError(response, 404, "会话不存在");
            return;
        }

        int limit = 100;
        String limitStr = request.getParameter("limit");
        if (limitStr != null && !limitStr.isEmpty()) {
            limit = Integer.parseInt(limitStr);
        }

        List<ChatMessage> messages = ChatSessionDAO.getMessages(sessionId, limit);

        Map<String, Object> result = new HashMap<>();
        result.put("session", session);
        result.put("messages", messages);
        sendSuccess(response, result);
    }

    /**
     * 发送消息（核心多轮对话逻辑）
     */
    private void sendMessage(HttpServletRequest request, HttpServletResponse response, User user)
            throws Exception {

        // 读取请求体
        StringBuilder body = new StringBuilder();
        try (BufferedReader r = request.getReader()) {
            String line;
            while ((line = r.readLine()) != null) body.append(line);
        }
        Map<String, Object> data = gson.fromJson(body.toString(), Map.class);
log("[ChatServlet] sendMessage body: " + body.toString().substring(0, Math.min(body.length(), 200)));
log("[ChatServlet] sendMessage data: query=" + data.get("query") + ", character_id=" + data.get("character_id"));

        String query = (String) data.get("query");
        String sessionId = (String) data.get("session_id");
        // 健壮解析 top_k（支持数字或字符串）
        int topK = 5;
        Object topKObj = data.get("top_k");
        if (topKObj != null) {
            if (topKObj instanceof Number) {
                topK = ((Number) topKObj).intValue();
            } else {
                try { topK = Integer.parseInt(topKObj.toString()); } catch (Exception e) {}
            }
        }
        // 健壮解析 character_id（支持数字或字符串）
        int characterId = 1;
        Object cidObj = data.get("character_id");
        if (cidObj != null) {
            if (cidObj instanceof Number) {
                characterId = ((Number) cidObj).intValue();
            } else {
                try { characterId = Integer.parseInt(cidObj.toString()); } catch (Exception e) {}
            }
        }

        if (query == null || query.trim().isEmpty()) {
            sendError(response, 400, "query参数不能为空");
            return;
        }

        // 如果没有session_id，创建新会话
        if (sessionId == null || sessionId.trim().isEmpty()) {
            ChatSession newSession = ChatSessionDAO.createSession(user.getId());
            sessionId = newSession.getSessionId();
        }

        // 验证会话是否存在
        ChatSession session = ChatSessionDAO.getBySessionId(sessionId);
        if (session == null) {
            sendError(response, 404, "会话不存在");
            return;
        }

        // 获取最近N轮对话历史
        List<ChatMessage> history = ChatSessionDAO.getRecentMessages(sessionId, MAX_HISTORY_TURNS);

        // 保存用户消息
        ChatMessage userMsg = new ChatMessage("user", query.trim());
        userMsg.setSessionId(sessionId);
        ChatSessionDAO.addMessage(userMsg);

        // 调用Flask RAG服务（带历史上下文）
        long startTime = System.currentTimeMillis();
        Map<String, Object> ragResult = callFlaskRAG(query.trim(), history, topK, characterId);
        long latency = System.currentTimeMillis() - startTime;

        String answer = (String) ragResult.get("answer");
        List<Map<String, Object>> sources = (List<Map<String, Object>>) ragResult.get("sources");

        // 保存助手消息
        ChatMessage assistantMsg = new ChatMessage("assistant", answer);
        assistantMsg.setSessionId(sessionId);
        assistantMsg.setSources(gson.toJson(sources));
        assistantMsg.setLatencyMs((int) latency);
        ChatSessionDAO.addMessage(assistantMsg);

        // 更新会话标题（第一条用户消息）
        if (session.getMessageCount() == 0 && session.getTitle().equals("新对话")) {
            String title = query.length() > 30 ? query.substring(0, 30) + "..." : query;
            ChatSessionDAO.updateTitle(sessionId, title);
        }

        // 返回结果
        Map<String, Object> result = new HashMap<>();
        result.put("answer", answer);
        result.put("sources", sources);
        result.put("session_id", sessionId);
        result.put("latency_ms", latency);
        result.put("model", ragResult.get("model"));

        sendSuccess(response, result);
    }

    /**
     * 调用Flask RAG服务（带历史上下文）
     */
    private Map<String, Object> callFlaskRAG(String query, List<ChatMessage> history, int topK, int characterId)
            throws Exception {

        URL url = new URL(getFlaskRagUrl() + "/api/chat");
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setDoOutput(true);
        conn.setConnectTimeout(30000);
        conn.setReadTimeout(60000);

        // 构建请求体（包含历史上下文和角色ID）
        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("query", query);
        requestBody.put("top_k", topK);
        requestBody.put("character_id", characterId);

        // 添加历史对话
        if (history != null && !history.isEmpty()) {
            List<Map<String, String>> historyList = new ArrayList<>();
            for (ChatMessage msg : history) {
                Map<String, String> h = new HashMap<>();
                h.put("role", msg.getRole());
                h.put("content", msg.getContent());
                historyList.add(h);
            }
            requestBody.put("history", historyList);
        }

        String jsonBody = gson.toJson(requestBody);
        conn.setRequestProperty("Content-Length", String.valueOf(jsonBody.getBytes(StandardCharsets.UTF_8).length));

        try (java.io.OutputStream os = conn.getOutputStream()) {
            os.write(jsonBody.getBytes(StandardCharsets.UTF_8));
        }

        int code = conn.getResponseCode();
        if (code != 200) {
            String errorBody;
            try (BufferedReader r = new BufferedReader(
                    new InputStreamReader(conn.getErrorStream(), StandardCharsets.UTF_8))) {
                errorBody = r.lines().collect(Collectors.joining("\n"));
            }
            throw new RuntimeException("Flask RAG服务返回错误: HTTP " + code + " - " + errorBody);
        }

        // 解析响应
        String response;
        try (BufferedReader r = new BufferedReader(
                new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
            response = r.lines().collect(Collectors.joining("\n"));
        }

        return gson.fromJson(response, Map.class);
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

    private void sendSuccess(HttpServletResponse response, String message) throws IOException {
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
