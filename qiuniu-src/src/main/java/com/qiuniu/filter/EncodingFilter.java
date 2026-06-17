package com.qiuniu.filter;

import javax.servlet.*;
import java.io.IOException;

/**
 * UTF-8 编码过滤器（兼容 Jetty 和 Tomcat）
 */
public class EncodingFilter implements Filter {
    
    @Override
    public void init(FilterConfig filterConfig) throws ServletException {
        // no-op
    }
    
    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        request.setCharacterEncoding("UTF-8");
        response.setCharacterEncoding("UTF-8");
        chain.doFilter(request, response);
    }
    
    @Override
    public void destroy() {
        // no-op
    }
}
