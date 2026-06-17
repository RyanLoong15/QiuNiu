package com.qiuniu.servlet;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.*;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

import com.qiuniu.util.FlaskConfig;

/**
 * 代理 /api/employee/* 请求到 Flask API
 */
@WebServlet("/api/employee/*")
public class EmployeeProxyServlet extends HttpServlet {

    private static String getFlaskBase() { return FlaskConfig.getBaseUrl(); }

    @Override
    protected void service(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        String method = req.getMethod();
        String pathInfo = req.getPathInfo();
        if (pathInfo == null) pathInfo = "/";

        // Flask 路径：/api/employee<pathInfo>
        String flaskPath = "/api/employee" + pathInfo;

        System.out.println("[EmployeeProxy] " + method + " " + flaskPath);

        // 读取请求体（POST/PUT）
        String body = null;
        if ("POST".equals(method) || "PUT".equals(method) || "PATCH".equals(method)) {
            body = readBody(req);
        }

        HttpURLConnection conn = null;
        try {
            URL url = new URL(getFlaskBase() + flaskPath);

            // 处理查询参数
            String queryString = req.getQueryString();
            if (queryString != null && !queryString.isEmpty()) {
                url = new URL(getFlaskBase() + flaskPath + "?" + queryString);
            }

            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod(method);
            conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(30000);

            // 发送请求体
            if (body != null && !body.isEmpty()) {
                conn.setDoOutput(true);
                try (OutputStream os = conn.getOutputStream()) {
                    os.write(body.getBytes(StandardCharsets.UTF_8));
                }
            }

            int status = conn.getResponseCode();
            resp.setStatus(status);
            resp.setContentType("application/json;charset=UTF-8");

            InputStream is = status >= 400 ? conn.getErrorStream() : conn.getInputStream();
            if (is != null) {
                byte[] data = is.readAllBytes();
                resp.getOutputStream().write(data);
                is.close();
            }

        } catch (Exception e) {
            System.err.println("[EmployeeProxy] Failed: " + e.getMessage());
            resp.setStatus(502);
            resp.setContentType("application/json;charset=UTF-8");
            resp.getWriter().write("{\"code\":502,\"message\":\"上游服务不可用\",\"data\":null}");
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    private String readBody(HttpServletRequest req) throws IOException {
        int contentLen = req.getContentLength();
        if (contentLen > 0) {
            byte[] buf = new byte[contentLen];
            int offset = 0;
            while (offset < contentLen) {
                int n = req.getInputStream().read(buf, offset, contentLen - offset);
                if (n < 0) break;
                offset += n;
            }
            return new String(buf, 0, offset, StandardCharsets.UTF_8);
        }
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (InputStream is = req.getInputStream()) {
            byte[] tmp = new byte[4096];
            int len;
            while ((len = is.read(tmp)) >= 0) {
                baos.write(tmp, 0, len);
            }
        }
        return baos.toString(StandardCharsets.UTF_8);
    }
}
