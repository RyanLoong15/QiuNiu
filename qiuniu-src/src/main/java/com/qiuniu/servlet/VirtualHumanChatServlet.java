package com.qiuniu.servlet;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Enumeration;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.qiuniu.util.FlaskConfig;

/**
 * 虚拟人聊天 Servlet - 代理前端请求到 Flask RAG 服务
 * 处理：/api/characters, /api/chat, /api/unanswered 等
 */
@WebServlet("/api/*")
public class VirtualHumanChatServlet extends HttpServlet {

    private static String getFlaskBase() { return FlaskConfig.getBaseUrl(); }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        String path = req.getPathInfo();
        if (path == null) path = "/";

        log("[VirtualHumanChatServlet] GET " + path);

        // 构建目标 URL: 移除 /qiuniu 前缀，添加到 Flask
        String requestURI = req.getRequestURI();
        String contextPath = req.getContextPath(); // "/qiuniu"
        String flaskPath = requestURI.substring(contextPath.length()); // "/api/characters"
        String targetUrl = getFlaskBase() + flaskPath;

        // 添加查询参数
        String queryString = req.getQueryString();
        if (queryString != null && !queryString.isEmpty()) {
            targetUrl += "?" + queryString;
        }

        log("[VirtualHumanChatServlet] Proxying to: " + targetUrl);

        HttpURLConnection conn = (HttpURLConnection) new URL(targetUrl).openConnection();
        conn.setRequestMethod("GET");
        conn.setConnectTimeout(5000);
        conn.setReadTimeout(30000);

        // 复制请求头
        copyRequestHeaders(req, conn);

        int status;
        try {
            status = conn.getResponseCode();
        } catch (IOException e) {
            log("[VirtualHumanChatServlet] Error connecting to Flask: " + e.getMessage());
            resp.sendError(HttpServletResponse.SC_BAD_GATEWAY, "无法连接到后端服务");
            return;
        }

        log("[VirtualHumanChatServlet] Flask response status: " + status);

        resp.setStatus(status);

        // 复制响应头
        copyResponseHeaders(conn, resp);

        // 复制响应体
        InputStream input = (status >= 400) ? conn.getErrorStream() : conn.getInputStream();
        if (input != null) {
            OutputStream output = resp.getOutputStream();
            byte[] buffer = new byte[4096];
            int bytesRead;
            while ((bytesRead = input.read(buffer)) != -1) {
                output.write(buffer, 0, bytesRead);
            }
            input.close();
        }
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        String path = req.getPathInfo();
        if (path == null) path = "/";

        log("[VirtualHumanChatServlet] POST " + path);

        String requestURI = req.getRequestURI();
        String contextPath = req.getContextPath();
        String flaskPath = requestURI.substring(contextPath.length());
        String targetUrl = getFlaskBase() + flaskPath;

        log("[VirtualHumanChatServlet] Proxying to: " + targetUrl);

        HttpURLConnection conn = (HttpURLConnection) new URL(targetUrl).openConnection();
        conn.setRequestMethod("POST");
        conn.setConnectTimeout(5000);
        conn.setReadTimeout(60000);
        conn.setDoOutput(true);

        // 复制请求头
        copyRequestHeaders(req, conn);

        // 复制请求体
        InputStream requestBody = req.getInputStream();
        OutputStream connOutput = conn.getOutputStream();
        byte[] buffer = new byte[4096];
        int bytesRead;
        while ((bytesRead = requestBody.read(buffer)) != -1) {
            connOutput.write(buffer, 0, bytesRead);
        }
        connOutput.flush();
        connOutput.close();

        int status;
        try {
            status = conn.getResponseCode();
        } catch (IOException e) {
            log("[VirtualHumanChatServlet] Error connecting to Flask: " + e.getMessage());
            resp.sendError(HttpServletResponse.SC_BAD_GATEWAY, "无法连接到后端服务");
            return;
        }

        log("[VirtualHumanChatServlet] Flask response status: " + status);

        resp.setStatus(status);

        // 复制响应头
        copyResponseHeaders(conn, resp);

        // 复制响应体
        InputStream input = (status >= 400) ? conn.getErrorStream() : conn.getInputStream();
        if (input != null) {
            OutputStream output = resp.getOutputStream();
            buffer = new byte[4096];
            while ((bytesRead = input.read(buffer)) != -1) {
                output.write(buffer, 0, bytesRead);
            }
            input.close();
        }
    }

    // 辅助方法：复制请求头
    private void copyRequestHeaders(HttpServletRequest req, HttpURLConnection conn) {
        Enumeration<String> headerNames = req.getHeaderNames();
        while (headerNames.hasMoreElements()) {
            String headerName = headerNames.nextElement();
            String headerValue = req.getHeader(headerName);
            // 跳过某些头
            if (!"host".equalsIgnoreCase(headerName) && !"content-length".equalsIgnoreCase(headerName)) {
                conn.setRequestProperty(headerName, headerValue);
            }
        }
    }

    // 辅助方法：复制响应头
    private void copyResponseHeaders(HttpURLConnection conn, HttpServletResponse resp) {
        for (String headerKey : conn.getHeaderFields().keySet()) {
            if (headerKey != null) {
                String headerValue = conn.getHeaderField(headerKey);
                resp.setHeader(headerKey, headerValue);
            }
        }
    }
}
