package com.qiuniu.servlet;

import com.qiuniu.dao.UserDAO;
import com.qiuniu.model.User;
import com.google.gson.Gson;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.HashMap;
import java.util.Map;

/**
 * 登录 Servlet
 */
@WebServlet("/login")
public class LoginServlet extends HttpServlet {
    
    private static final long serialVersionUID = 1L;
    
    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) 
            throws ServletException, IOException {
        // 转发到登录页面
        request.getRequestDispatcher("/login.jsp").forward(request, response);
    }
    
    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) 
            throws ServletException, IOException {
        
        request.setCharacterEncoding("UTF-8");
        response.setContentType("application/json;charset=UTF-8");
        
        String username = request.getParameter("username");
        String password = request.getParameter("password");
        String remember = request.getParameter("remember");
        
        Map<String, Object> result = new HashMap<>();
        Gson gson = new Gson();
        
        // 参数验证
        if (username == null || username.trim().isEmpty() || 
            password == null || password.trim().isEmpty()) {
            result.put("success", false);
            result.put("message", "用户名和密码不能为空");
            response.getWriter().write(gson.toJson(result));
            return;
        }
        
        try {
            // 验证登录
            User user = UserDAO.validateLogin(username.trim(), password);
            
            if (user != null) {
                // 登录成功，创建会话
                HttpSession session = request.getSession();
                session.setAttribute("user", user);
                session.setMaxInactiveInterval(30 * 60); // 30 分钟超时
                
                // 如果记住我，设置更长的会话时间
                if ("on".equals(remember)) {
                    session.setMaxInactiveInterval(7 * 24 * 60 * 60); // 7 天
                }
                
                result.put("success", true);
                result.put("message", "登录成功");
                result.put("redirect", request.getContextPath() + "/home.jsp");
                // 返回角色信息，前端用于权限控制
                result.put("role", user.getRole() != null ? user.getRole().getCode() : "VIEWER");
                result.put("nickname", user.getNickname());
            } else {
                result.put("success", false);
                result.put("message", "用户名或密码错误");
            }
            
        } catch (Exception e) {
            e.printStackTrace();
            result.put("success", false);
            result.put("message", "系统错误：" + e.getMessage());
        }
        
        PrintWriter out = response.getWriter();
        out.write(gson.toJson(result));
        out.flush();
    }
}
