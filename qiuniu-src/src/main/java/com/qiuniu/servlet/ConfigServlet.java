package com.qiuniu.servlet;

import com.qiuniu.util.FlaskConfig;

import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.PrintWriter;

/**
 * 前端配置接口 — 返回 Flask 地址等运行时配置
 * JSP 页面通过此接口获取后端服务地址，避免硬编码
 */
@WebServlet("/api/config")
public class ConfigServlet extends HttpServlet {

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        resp.setContentType("application/json;charset=UTF-8");
        resp.setHeader("Cache-Control", "no-cache, no-store");
        String json = String.format(
            "{\"flaskBaseUrl\":\"%s\"}",
            FlaskConfig.getBaseUrl()
        );
        PrintWriter out = resp.getWriter();
        out.print(json);
        out.flush();
    }
}
