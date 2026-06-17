package com.qiuniu.util;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Properties;
import java.util.stream.Collectors;

/**
 * 统一 AI 大模型调用客户端
 * 支持 OpenAI 兼容接口（Groq / 硅基流动 / OpenAI / Claude API 等）
 * 配置从 db.properties 读取，未来换模型只需改配置
 */
public class AIModelClient {

    private String apiUrl;
    private String apiKey;
    private String model;
    private int timeout;
    private int maxTokens;
    private String proxyHost;
    private int proxyPort;
    private boolean enabled;

    // 预定义的 API 端点模板（支持 OpenAI Chat Completions 格式）
    public AIModelClient() {
        loadConfig();
    }

    public void loadConfig() {
        Properties props = new Properties();
        boolean loaded = false;

        // 1. 优先从 classpath 读取（WAR 部署后 db.properties 在 WEB-INF/classes/）
        try (InputStream is = AIModelClient.class
                .getClassLoader().getResourceAsStream("db.properties")) {
            if (is != null) {
                props.load(is);
                loaded = true;
                System.out.println("[AIModelClient] 已从 classpath 加载配置");
            }
        } catch (Exception e) {
            // ignore
        }

        // 2. 降级：从系统属性 qiuniu.config 指定的路径读取
        if (!loaded) {
            String configPath = System.getProperty("qiuniu.config");
            if (configPath != null && !configPath.isEmpty()) {
                try (InputStream is = new FileInputStream(configPath)) {
                    props.load(is);
                    loaded = true;
                    System.out.println("[AIModelClient] 已从 " + configPath + " 加载配置");
                } catch (Exception e) {
                    System.err.println("[AIModelClient] 无法从 " + configPath + " 加载配置: " + e.getMessage());
                }
            }
        }

        // 3. 最后降级：硬编码开发路径（仅本地开发用，内网部署不应走到这里）
        if (!loaded) {
            String devPath = "D:\\OpenclawCode\\QiuNiu\\src\\main\\resources\\db.properties";
            try (InputStream is = new FileInputStream(devPath)) {
                props.load(is);
                loaded = true;
                System.out.println("[AIModelClient] 警告：使用硬编码开发路径: " + devPath);
            } catch (Exception e) {
                System.err.println("[AIModelClient] 无法从硬编码路径加载配置: " + e.getMessage());
            }
        }

        if (!loaded) {
            System.err.println("[AIModelClient] 错误：无法从任何来源加载 db.properties！");
            return;
        }

        this.apiUrl = props.getProperty("ai.api.url", "").trim();
        this.apiKey = props.getProperty("ai.api.key", "").trim();
        this.model = props.getProperty("ai.model", "gpt-4o").trim();
        this.timeout = parseInt(props.getProperty("ai.timeout", "60"));
        this.maxTokens = parseInt(props.getProperty("ai.maxTokens", "4096"));
        this.enabled = "true".equalsIgnoreCase(props.getProperty("ai.enabled", "false").trim());

        String proxy = props.getProperty("ai.proxy", "").trim();
        if (proxy != null && proxy.contains(":")) {
            String[] parts = proxy.split(":");
            this.proxyHost = parts[0];
            this.proxyPort = Integer.parseInt(parts[1]);
        } else {
            this.proxyHost = null;
            this.proxyPort = 0;
        }

        System.out.println("[AIModelClient] Loaded: url=" + apiUrl + " model=" + model + " enabled=" + enabled);
    }

    /**
     * 调用 AI 分析一段代码差异
     * @param systemPrompt 系统提示词
     * @param userPrompt  用户提示词（包含占位符已替换好的）
     * @return AI 返回的分析结果，失败返回 null
     */
    public String analyze(String systemPrompt, String userPrompt) {
        if (!enabled || apiKey.isEmpty() || apiKey.equals("YOUR_API_KEY_HERE")) {
            System.out.println("[AIModelClient] AI analysis disabled or no API key configured");
            return null;
        }

        if (apiUrl.isEmpty()) {
            System.err.println("[AIModelClient] API URL is empty");
            return null;
        }

        try {
            return doRequest(systemPrompt, userPrompt);
        } catch (Exception e) {
            System.err.println("[AIModelClient] API call failed: " + e.getMessage());
            e.printStackTrace();
            return "[AI 分析失败] " + e.getMessage();
        }
    }

    private String doRequest(String systemPrompt, String userPrompt) throws Exception {
        URL url = new URL(apiUrl + "/chat/completions");
        HttpURLConnection conn = (HttpURLConnection) url.openConnection(
            proxyHost != null ? new Proxy(Proxy.Type.HTTP, new InetSocketAddress(proxyHost, proxyPort)) : Proxy.NO_PROXY
        );

        conn.setRequestMethod("POST");
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setRequestProperty("Authorization", "Bearer " + apiKey);
        conn.setDoOutput(true);
        conn.setConnectTimeout(timeout * 1000);
        conn.setReadTimeout(timeout * 1000);

        // Build OpenAI-compatible request body
        String body = buildRequestBody(systemPrompt, userPrompt);

        try (OutputStream os = conn.getOutputStream()) {
            os.write(body.getBytes(StandardCharsets.UTF_8));
        }

        int code = conn.getResponseCode();
        if (code != 200) {
            // Read error body
            try (BufferedReader r = new BufferedReader(
                    new InputStreamReader(conn.getErrorStream(), StandardCharsets.UTF_8))) {
                String errBody = r.lines().collect(Collectors.joining("\n"));
                throw new RuntimeException("API returned HTTP " + code + ": " + errBody);
            }
        }

        // Parse response
        try (BufferedReader r = new BufferedReader(
                new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
            String response = r.lines().collect(Collectors.joining("\n"));
            return extractContent(response);
        }
    }

    private String buildRequestBody(String systemPrompt, String userPrompt) {
        StringBuilder sb = new StringBuilder();
        sb.append("{\n");
        sb.append("  \"model\": \"").append(escapeJson(model)).append("\",\n");
        sb.append("  \"messages\": [\n");
        sb.append("    {\"role\": \"system\", \"content\": ").append(toJson(systemPrompt)).append("},\n");
        sb.append("    {\"role\": \"user\", \"content\": ").append(toJson(userPrompt)).append("}\n");
        sb.append("  ],\n");
        sb.append("  \"temperature\": 0.3,\n");
        sb.append("  \"max_tokens\": ").append(maxTokens).append("\n");
        sb.append("}");
        return sb.toString();
    }

    /**
     * 从 OpenAI 格式响应中提取 content
     * 兼容不同 API 响应格式
     */
    private String extractContent(String response) {
        // OpenAI format: {"choices":[{"message":{"content":"..."}}]}
        // Try multiple patterns for robustness
        String[] patterns = {
            "\"content\":\"",
            "\"content\": \"",
            "\"content\":'"
        };

        for (String pattern : patterns) {
            int idx = response.indexOf(pattern);
            if (idx >= 0) {
                int start = idx + pattern.length();
                int end = findEndQuote(response, start);
                if (end > start) {
                    String content = response.substring(start, end);
                    // Unescape JSON unicode
                    content = content.replace("\\\"", "\"")
                                   .replace("\\n", "\n")
                                   .replace("\\\\", "\\");
                    return content.trim();
                }
            }
        }

        // Fallback: try to find any text field after content key
        int idx = response.indexOf("\"content\"");
        if (idx >= 0) {
            int brace = response.indexOf("{", idx);
            int closeBrace = findMatchingClose(response, brace);
            if (closeBrace > brace) {
                String msgObj = response.substring(brace, closeBrace + 1);
                return extractContent(msgObj);
            }
        }

        return response;
    }

    private int findEndQuote(String s, int start) {
        for (int i = start; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '\\') {
                i++; // skip escaped char
                continue;
            }
            if (c == '"') return i;
        }
        return -1;
    }

    private int findMatchingClose(String s, int openBrace) {
        int depth = 0;
        boolean inString = false;
        for (int i = openBrace; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '"' && (i == 0 || s.charAt(i - 1) != '\\')) {
                inString = !inString;
            }
            if (!inString) {
                if (c == '{') depth++;
                else if (c == '}') {
                    depth--;
                    if (depth == 0) return i;
                }
            }
        }
        return s.length();
    }

    private String toJson(String s) {
        return "\"" + escapeJson(s) + "\"";
    }

    private String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    private int parseInt(String s) {
        try { return Integer.parseInt(s.trim()); }
        catch (Exception e) { return 60; }
    }

    public boolean isEnabled() { return enabled; }
    public String getModel() { return model; }
    public String getApiUrl() { return apiUrl; }
}
