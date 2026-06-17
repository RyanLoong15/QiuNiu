package com.qiuniu.listener;

import com.mysql.cj.jdbc.AbandonedConnectionCleanupThread;
import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;
import javax.servlet.annotation.WebListener;
import java.sql.DriverManager;
import java.sql.Driver;
import java.util.Enumeration;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 在应用关闭时清理 MySQL 驱动和 AbandonedConnectionCleanupThread，
 * 避免 Tomcat 热部署时产生 IllegalStateException 日志噪音。
 */
@WebListener
public class MySQLCleanupListener implements ServletContextListener {

    private static final AtomicBoolean SHUTDOWN = new AtomicBoolean(false);

    @Override
    public void contextInitialized(ServletContextEvent sce) {
        // NOP
    }

    @Override
    public void contextDestroyed(ServletContextEvent sce) {
        if (!SHUTDOWN.compareAndSet(false, true)) {
            return;
        }

        // 停止 MySQL 后台清理线程
        try {
            AbandonedConnectionCleanupThread.checkedShutdown();
        } catch (Exception e) {
            System.out.println("[MySQLCleanup] AbandonedConnectionCleanupThread shutdown: " + e.getMessage());
        }

        // 注销 JDBC 驱动
        Enumeration<Driver> drivers = DriverManager.getDrivers();
        while (drivers.hasMoreElements()) {
            try {
                DriverManager.deregisterDriver(drivers.nextElement());
            } catch (Exception e) {
                System.out.println("[MySQLCleanup] deregister driver: " + e.getMessage());
            }
        }

        System.out.println("[MySQLCleanup] MySQL resources cleaned up");
    }
}
