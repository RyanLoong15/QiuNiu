package com.qiuniu.listener;

import com.qiuniu.dao.GitProjectDAO;
import com.qiuniu.dao.GitTeamDAO;
import com.qiuniu.dao.PromptDAO;
import com.qiuniu.dao.UserDAO;
import com.qiuniu.dao.VersionComparisonDAO;

import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;
import javax.servlet.annotation.WebListener;

/**
 * 应用初始化监听器
 * 负责初始化数据库表和超级管理员
 */
@WebListener
public class InitListener implements ServletContextListener {
    
    @Override
    public void contextInitialized(ServletContextEvent sce) {
        System.out.println("===========================================");
        System.out.println("  囚牛 (QiuNiu) 系统启动中...");
        System.out.println("===========================================");
        
        try {
            // 初始化数据库表
            System.out.println("[Init] 正在初始化数据库表...");
            UserDAO.createTable();
            PromptDAO.createTable();
            GitTeamDAO teamDAO = new GitTeamDAO();
            teamDAO.createTable();
            GitProjectDAO gitProjectDAO = new GitProjectDAO();
            gitProjectDAO.createTable();
            VersionComparisonDAO vcDAO = new VersionComparisonDAO();
            vcDAO.createTable();
            System.out.println("[Init] 数据库表初始化完成");
            
            // 初始化超级管理员
            System.out.println("[Init] 正在初始化超级管理员...");
            UserDAO.initSuperAdmin();
            
        } catch (Exception e) {
            System.err.println("[Init] 初始化失败：" + e.getMessage());
            e.printStackTrace();
        }
        
        System.out.println("===========================================");
        System.out.println("  囚牛系统启动完成");
        System.out.println("===========================================");
    }
    
    @Override
    public void contextDestroyed(ServletContextEvent sce) {
        System.out.println("[Init] 应用关闭");
    }
}
