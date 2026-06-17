package com.qiuniu.servlet;

import java.io.*;
import java.net.*;
import java.util.*;
import javax.servlet.*;
import javax.servlet.http.*;
import javax.servlet.annotation.*;

import com.qiuniu.util.FlaskConfig;

/**
 * DocumentImportServlet
 * 代理 /api/document/* → Flask API
 */
@WebServlet("/api/document/*")
public class DocumentImportServlet extends HttpServlet {

    private static String getFlaskBase() { return FlaskConfig.getBaseUrl(); }

    @Override
    protected void service(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {

        String method = req.getMethod();
        String pathInfo = req.getPathInfo() == null ? "" : req.getPathInfo();
        String targetUrl = getFlaskBase() + "/api/document" + pathInfo
                + (req.getQueryString() == null ? "" : "?" + req.getQueryString());

        HttpURLConnection conn = (HttpURLConnection) new URL(targetUrl).openConnection();
        conn.setRequestMethod(method.equals("PATCH") ? "POST" : method);
        conn.setConnectTimeout(5000);
        conn.setReadTimeout(60000);

        // Forward headers
        Enumeration<String> headerNames = req.getHeaderNames();
        while (headerNames.hasMoreElements()) {
            String name = headerNames.nextElement();
            if (!"host".equalsIgnoreCase(name) && !"content-length".equalsIgnoreCase(name)) {
                conn.setRequestProperty(name, req.getHeader(name));
            }
        }

        // Handle request body (POST/PUT)
        if ("POST".equals(method) || "PUT".equals(method) || "PATCH".equals(method)) {
            conn.setDoOutput(true);
            // Forward Content-Type
            String ct = req.getContentType();
            if (ct != null) conn.setRequestProperty("Content-Type", ct);

            try (InputStream in = req.getInputStream();
                 OutputStream out = conn.getOutputStream()) {
                byte[] buf = new byte[8192];
                int len;
                while ((len = in.read(buf)) != -1) {
                    out.write(buf, 0, len);
                }
            }
        }

        // Read response
        int status = conn.getResponseCode();
        resp.setStatus(status);

        // Forward response headers
        Map<String, List<String>> respHeaders = conn.getHeaderFields();
        for (Map.Entry<String, List<String>> entry : respHeaders.entrySet()) {
            String key = entry.getKey();
            if (key == null || key.equalsIgnoreCase("Transfer-Encoding")) continue;
            for (String val : entry.getValue()) {
                resp.addHeader(key, val);
            }
        }

        // Forward response body
        InputStream respIn = (status >= 400) ? conn.getErrorStream() : conn.getInputStream();
        if (respIn != null) {
            try (OutputStream out = resp.getOutputStream()) {
                byte[] buf = new byte[8192];
                int len;
                while ((len = respIn.read(buf)) != -1) {
                    out.write(buf, 0, len);
                }
            }
        }
    }
}
