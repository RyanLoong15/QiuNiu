package com.qiuniu.servlet;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * 通用JSP转发Servlet。通过init-param配置path->jsp映射，
 * 在ROOT web.xml中注册一次，全权处理所有JSP页面的URL路由。
 * 避免每个JSP单独注册导致的@WebServlet冲突问题。
 */
@WebServlet(name="JspDispatcherServlet", urlPatterns={"/dashboard","/git-projects","/incidents",
    "/incident-codes","/knowledge-base","/version-compare","/version-detail",
    "/team-dashboard","/home","/mcp-service","/user-avatar"})
public class JspDispatcherServlet extends HttpServlet {

    private static final long serialVersionUID = 1L;

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {
        String pathInfo = req.getPathInfo();
        if (pathInfo == null || pathInfo.equals("/")) {
            pathInfo = req.getServletPath();
        }
        String jspFile = "/WEB-INF/jsp" + pathInfo + ".jsp";
        String dispatchPath = jspFile;
        if (getServletContext().getResource(dispatchPath) == null) {
            dispatchPath = pathInfo + ".jsp";
        }
        req.getRequestDispatcher(dispatchPath).forward(req, resp);
    }
}