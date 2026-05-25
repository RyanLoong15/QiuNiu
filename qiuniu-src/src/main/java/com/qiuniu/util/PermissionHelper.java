package com.qiuniu.util;

import com.qiuniu.model.User;
import com.qiuniu.model.Role;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.io.IOException;

/**
 * 权限检查工具类
 */
public class PermissionHelper {
    
    /**
     * 检查用户是否已登录
     */
    public static User getLoginUser(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session == null) {
            return null;
        }
        return (User) session.getAttribute("user");
    }
    
    /**
     * 要求用户登录，未登录返回 false 并发送 401
     */
    public static boolean requireLogin(HttpServletRequest request, HttpServletResponse response) 
            throws IOException {
        User user = getLoginUser(request);
        if (user == null) {
            response.setStatus(401);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"success\":false,\"message\":\"请先登录\",\"redirect\":\"/qiuniu/login.jsp\"}");
            return false;
        }
        return true;
    }
    
    /**
     * 要求写权限（ADMIN 或 TEAM_ADMIN），无权限返回 false 并发送 403
     */
    public static boolean requireWrite(HttpServletRequest request, HttpServletResponse response) 
            throws IOException {
        User user = getLoginUser(request);
        if (user == null) {
            response.setStatus(401);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"success\":false,\"message\":\"请先登录\"}");
            return false;
        }
        if (!user.canWrite()) {
            response.setStatus(403);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"success\":false,\"message\":\"您没有权限执行此操作，仅超级管理员和团队管理员可以修改数据\"}");
            return false;
        }
        return true;
    }
    
    /**
     * 要求超级管理员权限，无权限返回 false 并发送 403
     */
    public static boolean requireAdmin(HttpServletRequest request, HttpServletResponse response) 
            throws IOException {
        User user = getLoginUser(request);
        if (user == null) {
            response.setStatus(401);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"success\":false,\"message\":\"请先登录\"}");
            return false;
        }
        if (!user.isAdmin()) {
            response.setStatus(403);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"success\":false,\"message\":\"此操作仅限超级管理员\"}");
            return false;
        }
        return true;
    }
    
    /**
     * 检查用户是否可以修改指定团队的数据
     * ADMIN 可以修改所有团队，TEAM_ADMIN 只能修改自己管理的团队
     */
    public static boolean canModifyTeam(User user, Long teamId, Long teamUserId) {
        if (user.isAdmin()) {
            return true; // 超级管理员可以修改所有团队
        }
        if (user.getRole() == Role.TEAM_ADMIN) {
            // 团队管理员只能修改自己管理的团队
            return teamUserId != null && teamUserId.equals(user.getId());
        }
        return false; // VIEWER 不能修改
    }
    
    /**
     * 要求团队修改权限
     */
    public static boolean requireTeamModify(HttpServletRequest request, HttpServletResponse response, 
            Long teamId, Long teamUserId) throws IOException {
        User user = getLoginUser(request);
        if (user == null) {
            response.setStatus(401);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"success\":false,\"message\":\"请先登录\"}");
            return false;
        }
        if (!canModifyTeam(user, teamId, teamUserId)) {
            response.setStatus(403);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"success\":false,\"message\":\"您没有权限修改此团队的数据\"}");
            return false;
        }
        return true;
    }
}
