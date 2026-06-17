package com.qiuniu.filter;

import javax.servlet.*;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.io.IOException;

/**
 * 认证过滤器
 * 检查用户是否已登录
 * - API 请求（Accept: application/json）→ 返回 JSON 401
 * - 页面请求 → 重定向到登录页
 *
 * 注意：通过 web.xml 注册，不要在此类上加 @WebFilter 注解（避免重复注册）
 */
public class AuthFilter implements Filter {

    @Override
    public void init(FilterConfig filterConfig) throws ServletException {
        System.out.println("[AuthFilter] 初始化完成");
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response,
                         FilterChain chain) throws IOException, ServletException {

        HttpServletRequest httpRequest = (HttpServletRequest) request;
        HttpServletResponse httpResponse = (HttpServletResponse) response;
        HttpSession session = httpRequest.getSession(false);

        String requestURI = httpRequest.getRequestURI();

        // 放行登录、注册页面和静态资源
        if (requestURI.contains("/login.jsp") ||
            requestURI.contains("/register.jsp") ||
            requestURI.contains("/css/") ||
            requestURI.contains("/js/") ||
            requestURI.contains("/images/") ||
            requestURI.endsWith(".jsp") ||
            requestURI.endsWith("/login") ||
            requestURI.endsWith("/register") ||
            requestURI.equals("/") ||
            requestURI.equals("/qiuniu/")) {
            chain.doFilter(request, response);
            return;
        }

        // 放行文档导入 API（使用 JWT 认证，不依赖 Tomcat session）
        if (requestURI.contains("/api/document/")) {
            chain.doFilter(request, response);
            return;
        }

        // 放行聊天 API（RAG 问答不需要登录）
        if (requestURI.contains("/api/chat")) {
            chain.doFilter(request, response);
            return;
        }

        // 放行知识库管理 API
        if (requestURI.contains("/api/knowledge-base")) {
            chain.doFilter(request, response);
            return;
        }

        // 放行虚拟人聊天 API（前端调用 /api/virtual-human/chat）
        if (requestURI.contains("/api/virtual-human/")) {
            chain.doFilter(request, response);
            return;
        }

        // API Servlet（始终返回 JSON，不重定向）
        if (requestURI.contains("/version") ||
            requestURI.contains("/prompt") ||
            requestURI.contains("/git") ||
            requestURI.contains("/team") ||
            requestURI.contains("/codeAnalysis") ||
            requestURI.contains("/repo/") ||
            (requestURI.contains("/api/") && !requestURI.contains("/api/document/"))) {
            // 已登录则直接放行；未登录返回 401 JSON
            if (session == null || session.getAttribute("user") == null) {
                httpResponse.setContentType("application/json;charset=UTF-8");
                httpResponse.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                httpResponse.getWriter().write(
                    "{\"success\":false,\"message\":\"未登录或会话已过期\"}"
                );
                return;
            }
            chain.doFilter(request, response);
            return;
        }

        // 检查是否已登录
        if (session == null || session.getAttribute("user") == null) {
            // 判断是否为 API 请求：检查 Accept 头或 URI 模式
            String accept = httpRequest.getHeader("Accept");
            boolean isApiRequest = (accept != null && accept.contains("application/json"))
                                 || requestURI.endsWith(".json")
                                 || (requestURI.contains("/api/") && !requestURI.contains("/api/document/"));

            if (isApiRequest) {
                httpResponse.setContentType("application/json;charset=UTF-8");
                httpResponse.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                httpResponse.getWriter().write(
                    "{\"success\":false,\"message\":\"未登录或会话已过期\"}"
                );
            } else {
                // 页面请求 → 重定向到登录页
                httpResponse.sendRedirect(httpRequest.getContextPath() + "/login.jsp");
            }
            return;
        }

        // 已登录，继续处理请求
        chain.doFilter(request, response);
    }

    @Override
    public void destroy() {
        System.out.println("[AuthFilter] 销毁");
    }
}
