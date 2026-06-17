package com.qiuniu.util;

import java.io.InputStream;
import java.util.Properties;

/**
 * Flask 服务配置工具类
 * 从 db.properties 读取 flask.base.url，部署时只需修改配置文件
 */
public class FlaskConfig {

    private static final String CONFIG_FILE = "/db.properties";
    private static final String DEFAULT_URL = "http://localhost:5001";
    private static String baseUrl;

    static {
        loadConfig();
    }

    private static void loadConfig() {
        try {
            Properties props = new Properties();
            InputStream in = FlaskConfig.class.getResourceAsStream(CONFIG_FILE);
            if (in != null) {
                props.load(in);
                in.close();
            }
            baseUrl = props.getProperty("flask.base.url", DEFAULT_URL);
            // 移除末尾斜杠
            if (baseUrl.endsWith("/")) {
                baseUrl = baseUrl.substring(0, baseUrl.length() - 1);
            }
            System.out.println("[FlaskConfig] Flask base URL: " + baseUrl);
        } catch (Exception e) {
            System.err.println("[FlaskConfig] 加载配置失败，使用默认值: " + DEFAULT_URL);
            baseUrl = DEFAULT_URL;
        }
    }

    /**
     * 获取 Flask 服务基础地址，如 http://localhost:5001
     */
    public static String getBaseUrl() {
        return baseUrl;
    }
}
