package com.qiuniu.servlet;

import com.qiuniu.dao.UserDAO;
import com.qiuniu.model.User;
import com.google.gson.Gson;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * 注册 Servlet
 */
@WebServlet("/register")
public class RegisterServlet extends HttpServlet {
    
    private static final long serialVersionUID = 1L;
    private static final Pattern EMAIL_PATTERN = Pattern.compile(
        "^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$"
    );
    
    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) 
            throws ServletException, IOException {
        // 转发到注册页面
        request.getRequestDispatcher("/register.jsp").forward(request, response);
    }
    
    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) 
            throws ServletException, IOException {
        
        request.setCharacterEncoding("UTF-8");
        response.setContentType("application/json;charset=UTF-8");
        
        String username = request.getParameter("username");
        String password = request.getParameter("password");
        String confirmPassword = request.getParameter("confirmPassword");
        String email = request.getParameter("email");
        String nickname = request.getParameter("nickname");
        
        Map<String, Object> result = new HashMap<>();
        Gson gson = new Gson();
        
        // 参数验证
        if (username == null || username.trim().isEmpty()) {
            result.put("success", false);
            result.put("message", "用户名不能为空");
            response.getWriter().write(gson.toJson(result));
            return;
        }
        
        if (password == null || password.length() < 6) {
            result.put("success", false);
            result.put("message", "密码长度不能少于 6 位");
            response.getWriter().write(gson.toJson(result));
            return;
        }
        
        if (!password.equals(confirmPassword)) {
            result.put("success", false);
            result.put("message", "两次输入的密码不一致");
            response.getWriter().write(gson.toJson(result));
            return;
        }
        
        if (email != null && !email.trim().isEmpty() && !EMAIL_PATTERN.matcher(email).matches()) {
            result.put("success", false);
            result.put("message", "邮箱格式不正确");
            response.getWriter().write(gson.toJson(result));
            return;
        }
        
        try {
            // 检查用户名是否已存在
            if (UserDAO.existsByUsername(username.trim())) {
                result.put("success", false);
                result.put("message", "用户名已存在");
                response.getWriter().write(gson.toJson(result));
                return;
            }
            
            // 创建用户
            User user = new User(
                username.trim(),
                password,
                email != null ? email.trim() : null,
                nickname != null ? nickname.trim() : username.trim()
            );
            
            if (UserDAO.create(user)) {
                result.put("success", true);
                result.put("message", "注册成功，请登录");
                result.put("redirect", request.getContextPath() + "/login.jsp");
            } else {
                result.put("success", false);
                result.put("message", "注册失败，请稍后重试");
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
