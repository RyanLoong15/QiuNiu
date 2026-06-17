package com.qiuniu.util;

import com.qiuniu.model.ProductionIncident;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.PrintWriter;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

/**
 * 简化版 JSON 工具类
 */
public class JsonUtil {

    public static void write(HttpServletResponse resp, Object obj) throws IOException {
        resp.setContentType("application/json;charset=UTF-8");
        PrintWriter w = resp.getWriter();
        w.write(toJson(obj));
        w.flush();
    }

    public static String toJson(Object obj) {
        if (obj == null) return "null";
        if (obj instanceof JsonBuilder) return ((JsonBuilder)obj).toString();
        if (obj instanceof String) return "\"" + escape((String) obj) + "\"";
        if (obj instanceof Number || obj instanceof Boolean) return obj.toString();
        if (obj instanceof java.util.Date) return "\"" + new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format((java.util.Date)obj) + "\"";
        if (obj instanceof List) {
            List<?> list = (List<?>) obj;
            StringBuilder sb = new StringBuilder("[");
            for (int i = 0; i < list.size(); i++) {
                if (i > 0) sb.append(",");
                sb.append(toJson(list.get(i)));
            }
            sb.append("]");
            return sb.toString();
        }
        // Bean 转 JSON
        StringBuilder sb = new StringBuilder("{");
        Field[] fields = obj.getClass().getDeclaredFields();
        boolean first = true;
        for (Field f : fields) {
            try {
                f.setAccessible(true);
                Object val = f.get(obj);
                if (val == null) continue;
                if (!first) sb.append(",");
                sb.append("\"").append(f.getName()).append("\":");
                sb.append(toJson(val));
                first = false;
            } catch (Exception ignored) {}
        }
        sb.append("}");
        return sb.toString();
    }

    public static JsonBuilder obj() {
        return new JsonBuilder();
    }

    public static class JsonBuilder {
        private final StringBuilder sb = new StringBuilder("{");
        private boolean first = true;

        public JsonBuilder put(String key, Object val) {
            if (!first) sb.append(",");
            sb.append("\"").append(key).append("\":");
            sb.append(toJson(val));
            first = false;
            return this;
        }

        @Override
        public String toString() {
            return sb.toString() + "}";
        }
    }

    public static List<ProductionIncident> parseIncidents(String json) {
        List<ProductionIncident> list = new ArrayList<>();
        // 简化解析：假设是 JSON 数组格式
        json = json.trim();
        if (!json.startsWith("[")) return list;

        int depth = 0;
        int start = 1;
        for (int i = 1; i < json.length(); i++) {
            char c = json.charAt(i);
            if (c == '{') depth++;
            else if (c == '}') {
                depth--;
                if (depth == 0) {
                    String item = json.substring(start, i + 1);
                    ProductionIncident inc = parseIncident(item);
                    if (inc != null) list.add(inc);
                    start = i + 2;
                }
            }
        }
        return list;
    }

    private static ProductionIncident parseIncident(String json) {
        ProductionIncident inc = new ProductionIncident();
        // 简化解析：提取常见字段
        inc.setIncidentNo(extract(json, "incidentNo"));
        inc.setTitle(extract(json, "title"));
        inc.setSeverity(extract(json, "severity"));
        inc.setIncidentType(extract(json, "incidentType"));
        inc.setAffectedSystems(extract(json, "affectedSystems"));
        inc.setAffectedModules(extract(json, "affectedModules"));
        inc.setRootCause(extract(json, "rootCause"));
        inc.setSolution(extract(json, "solution"));
        inc.setLessonsLearned(extract(json, "lessonsLearned"));
        inc.setCodePatterns(extract(json, "codePatterns"));
        inc.setFilePaths(extract(json, "filePaths"));
        inc.setKeywords(extract(json, "keywords"));
        return inc;
    }

    private static String extract(String json, String key) {
        String k = "\"" + key + "\"";
        int idx = json.indexOf(k);
        if (idx < 0) return null;
        int colon = json.indexOf(":", idx);
        if (colon < 0) return null;
        int start = colon + 1;
        while (start < json.length() && json.charAt(start) <= ' ') start++;
        if (start >= json.length()) return null;
        char quote = json.charAt(start);
        if (quote != '"' && quote != '\'') return null;
        char endQuote = quote;
        int end = start + 1;
        while (end < json.length()) {
            if (json.charAt(end) == endQuote && json.charAt(end - 1) != '\\') break;
            end++;
        }
        return json.substring(start + 1, end);
    }

    private static String escape(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t");
    }
}