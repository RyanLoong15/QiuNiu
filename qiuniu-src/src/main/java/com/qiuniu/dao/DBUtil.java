package com.qiuniu.dao;

import java.io.InputStream;
import java.sql.Connection;
import java.sql.DriverManager;
import java.util.Properties;
import org.apache.commons.dbcp2.BasicDataSource;

/**
 * 数据库连接工具类
 * 使用 DBCP 连接池管理数据库连接
 */
public class DBUtil {
    
    private static BasicDataSource dataSource;
    private static final String CONFIG_FILE = "/db.properties";
    
    static {
        initDataSource();
    }
    
    /**
     * 初始化数据源
     */
    private static void initDataSource() {
        try {
            Properties props = new Properties();
            InputStream in = DBUtil.class.getResourceAsStream(CONFIG_FILE);
            if (in == null) {
                throw new RuntimeException("无法找到配置文件：" + CONFIG_FILE);
            }
            props.load(in);
            in.close();
            
            dataSource = new BasicDataSource();
            dataSource.setDriverClassName(props.getProperty("db.driver"));
            dataSource.setUrl(props.getProperty("db.url"));
            dataSource.setUsername(props.getProperty("db.username"));
            dataSource.setPassword(props.getProperty("db.password"));
            
            // 连接池配置
            dataSource.setInitialSize(Integer.parseInt(props.getProperty("db.pool.initialSize", "5")));
            dataSource.setMaxTotal(Integer.parseInt(props.getProperty("db.pool.maxActive", "20")));
            dataSource.setMaxIdle(Integer.parseInt(props.getProperty("db.pool.maxIdle", "10")));
            dataSource.setMinIdle(Integer.parseInt(props.getProperty("db.pool.minIdle", "5")));
            dataSource.setMaxWaitMillis(Long.parseLong(props.getProperty("db.pool.maxWait", "3000")));
            
            System.out.println("[DBUtil] 数据库连接池初始化成功");
            
        } catch (Exception e) {
            System.err.println("[DBUtil] 初始化失败：" + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * 获取数据库连接
     */
    public static Connection getConnection() throws Exception {
        if (dataSource == null) {
            initDataSource();
        }
        return dataSource.getConnection();
    }
    
    /**
     * 关闭连接
     */
    public static void closeConnection(Connection conn) {
        if (conn != null) {
            try {
                conn.close();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }
    
    /**
     * 关闭数据源
     */
    public static void closeDataSource() {
        if (dataSource != null) {
            try {
                dataSource.close();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }
}
