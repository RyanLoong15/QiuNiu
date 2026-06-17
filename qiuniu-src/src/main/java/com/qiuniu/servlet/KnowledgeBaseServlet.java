package com.qiuniu.servlet;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.qiuniu.util.FlaskConfig;
import com.qiuniu.dao.KnowledgeBaseDAO;
import com.qiuniu.model.KnowledgeEntry;
import com.qiuniu.model.User;
import com.qiuniu.model.Role;
import com.qiuniu.util.PermissionHelper;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.annotation.MultipartConfig;
import javax.servlet.http.*;
import javax.servlet.http.Part;
import java.io.*;
import java.util.List;

@WebServlet("/api/knowledge-base/*")
@MultipartConfig(fileSizeThreshold=1048576, maxFileSize=10485760, maxRequestSize=20971520)
public class KnowledgeBaseServlet extends HttpServlet {

    private void out(HttpServletResponse resp, String json) throws IOException {
        resp.setContentType("application/json;charset=UTF-8");
        resp.getWriter().write(json);
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        if (PermissionHelper.getLoginUser(req) == null) {
            resp.setStatus(401);
            out(resp, "{\"error\":\"unauthorized\"}");
            return;
        }

        String pathInfo = req.getPathInfo();
        try {
            if (pathInfo == null || "/".equals(pathInfo) || "/add".equals(pathInfo)) {
                if (!PermissionHelper.requireWrite(req, resp)) return;
                doAdd(req, resp);
            } else if ("/update".equals(pathInfo)) {
                if (!PermissionHelper.requireWrite(req, resp)) return;
                doUpdate(req, resp);
            } else if ("/delete".equals(pathInfo)) {
                if (!PermissionHelper.requireWrite(req, resp)) return;
                deleteEntry(req, resp);
            } else if ("/import".equals(pathInfo)) {
                if (!PermissionHelper.requireWrite(req, resp)) return;
                doImport(req, resp);
            } else {
                resp.setStatus(404);
                out(resp, "{\"error\":\"unknown action\"}");
            }
        } catch (Exception e) {
            e.printStackTrace();
            resp.setStatus(500);
            out(resp, "{\"error\":\"" + escapeJson(e.getMessage()) + "\"}");
        }
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        // [DEBUG] Temporarily open knowledge base query (login not required)
        try {
            List<KnowledgeEntry> entries = KnowledgeBaseDAO.getAll();
            StringBuilder sb = new StringBuilder("{\"entries\":");
            sb.append(listToJson(entries));
            sb.append("}");
            out(resp, sb.toString());
        } catch (Exception e) {
            resp.setStatus(500);
            out(resp, "{\"error\":\"" + escapeJson(e.getMessage()) + "\"}");
        }
    }

    private void doAdd(HttpServletRequest req, HttpServletResponse resp) throws Exception {
        String question = req.getParameter("question");
        String answer = req.getParameter("answer");
        String category = req.getParameter("category");
        User user = (User) req.getSession().getAttribute("user");

        if (question == null || answer == null || question.trim().isEmpty() || answer.trim().isEmpty()) {
            out(resp, "{\"error\":\"question and answer are required\"}");
            return;
        }

        KnowledgeEntry e = new KnowledgeEntry();
        e.setQuestion(question.trim());
        e.setAnswer(answer.trim());
        e.setCategory(category != null ? category.trim() : "???");
        e.setCreatedBy(user.getUsername());
        KnowledgeBaseDAO.add(e);
        out(resp, "{\"ok\":true}");
    }

    private void doUpdate(HttpServletRequest req, HttpServletResponse resp) throws Exception {
        int id = Integer.parseInt(req.getParameter("id"));
        String question = req.getParameter("question");
        String answer = req.getParameter("answer");
        String category = req.getParameter("category");
        String enabled = req.getParameter("enabled");

        KnowledgeEntry e = new KnowledgeEntry();
        e.setId(id);
        e.setQuestion(question != null ? question.trim() : "");
        e.setAnswer(answer != null ? answer.trim() : "");
        e.setCategory(category != null ? category.trim() : "???");
        e.setEnabled(enabled == null || "1".equals(enabled) || "true".equalsIgnoreCase(enabled));
        KnowledgeBaseDAO.update(e);
        out(resp, "{\"ok\":true}");
    }

    private void deleteEntry(HttpServletRequest req, HttpServletResponse resp) throws Exception {
        int id = Integer.parseInt(req.getParameter("id"));
        KnowledgeBaseDAO.delete(id);
        out(resp, "{\"ok\":true}");
    }

    private String listToJson(List<?> list) {
        StringBuilder sb = new StringBuilder("[");
        boolean first = true;
        for (Object item : list) {
            if (!first) sb.append(",");
            first = false;
            if (item instanceof KnowledgeEntry) {
                KnowledgeEntry e = (KnowledgeEntry) item;
                sb.append("{\"id\":").append(e.getId()).append(",");
                sb.append("\"question\":\"").append(escapeJson(e.getQuestion())).append("\",");
                sb.append("\"answer\":\"").append(escapeJson(e.getAnswer())).append("\",");
                sb.append("\"category\":\"").append(escapeJson(e.getCategory() != null ? e.getCategory() : "")).append("\",");
                sb.append("\"enabled\":").append(e.isEnabled()).append(",");
                sb.append("\"createdAt\":\"").append(e.getCreatedAt() != null ? e.getCreatedAt() : "").append("\"}");
            } else if (item instanceof String) {
                sb.append("\"").append(escapeJson((String) item)).append("\"");
            } else {
                sb.append(item);
            }
        }
        sb.append("]");
        return sb.toString();
    }

    /**
     * POST /api/knowledge-base/import
     * Import TXT/MD documents into knowledge base via multipart upload.
     */
    private void doImport(HttpServletRequest req, HttpServletResponse resp) throws Exception {
        String contentType = req.getContentType() != null ? req.getContentType().toLowerCase() : "";
        int imported = 0;
        StringBuilder errors = new StringBuilder();

        if (contentType.contains("application/json")) {
            // JSON body: single entry {question,answer,category} or array [{question,answer,category},...]
            StringBuilder sb = new StringBuilder();
            try (BufferedReader br = req.getReader()) {
                String line;
                while ((line = br.readLine()) != null) sb.append(line);
            }
            String body = sb.toString().trim();
            User user = (User) req.getSession().getAttribute("user");
            String username = user != null ? user.getUsername() : "system";
            Gson gson = new Gson();
            try {
                body = body.trim();
                if (body.startsWith("[")) {
                    // Array: import multiple entries
                    JsonArray arr = gson.fromJson(body, JsonArray.class);
                    for (int i = 0; i < arr.size(); i++) {
                        JsonObject obj = arr.get(i).getAsJsonObject();
                        String q = getJsonStr(obj, "question");
                        String a = getJsonStr(obj, "answer");
                        String cat = getJsonStr(obj, "category");
                        if (q == null || q.trim().isEmpty() || a == null || a.trim().isEmpty()) {
                            errors.append("Entry ").append(i + 1).append(": question/answer required\n");
                            continue;
                        }
                        KnowledgeEntry e = new KnowledgeEntry();
                        e.setQuestion(q.trim());
                        e.setAnswer(a.trim());
                        e.setCategory(cat != null && !cat.trim().isEmpty() ? cat.trim() : "通用囨。");
                        e.setCreatedBy(username);
                        KnowledgeBaseDAO.add(e);
                        imported++;
                    }
                } else {
                    // Single entry
                    JsonObject obj = gson.fromJson(body, JsonObject.class);
                    String q = getJsonStr(obj, "question");
                    String a = getJsonStr(obj, "answer");
                    String cat = getJsonStr(obj, "category");
                    if (q == null || q.trim().isEmpty() || a == null || a.trim().isEmpty()) {
                        out(resp, "{\"error\":\"question and answer are required\"}");
                        return;
                    }
                    KnowledgeEntry e = new KnowledgeEntry();
                    e.setQuestion(q.trim());
                    e.setAnswer(a.trim());
                    e.setCategory(cat != null && !cat.trim().isEmpty() ? cat.trim() : "通用囨。");
                    e.setCreatedBy(username);
                    KnowledgeBaseDAO.add(e);
                    imported = 1;
                }
            } catch (Exception ex) {
                errors.append("JSON parse error: ").append(ex.getMessage()).append("\n");
            }
        } else {
            // Multipart file upload
            String category = req.getParameter("category");
            if (category == null || category.trim().isEmpty()) category = "通用囨。";
            User user = (User) req.getSession().getAttribute("user");
            String username = user != null ? user.getUsername() : "system";

            for (Part part : req.getParts()) {
                String filename = part.getSubmittedFileName();
                if (filename == null || filename.trim().isEmpty()) continue;
                String ext = filename.substring(filename.lastIndexOf('.')).toLowerCase();
                if (!ext.equals(".txt") && !ext.equals(".md") && !ext.equals(".docx")) {
                    errors.append("Skipped ").append(filename).append(": unsupported format\n");
                    continue;
                }
                // For .docx files, forward to Flask for parsing via multipart/form-data
                if (".docx".equals(ext)) {
                    try {
                        java.io.File tempFile = java.io.File.createTempFile("docx_", ".docx");
                        part.write(tempFile.getAbsolutePath());
                        
                        // Build multipart/form-data request using pure OutputStream
                        String boundary = "----WebKitFormBoundary" + System.currentTimeMillis();
                        java.net.URL url = new java.net.URL(FlaskConfig.getBaseUrl() + "/ingest");
                        java.net.HttpURLConnection conn = (java.net.HttpURLConnection) url.openConnection();
                        conn.setRequestMethod("POST");
                        conn.setDoOutput(true);
                        conn.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary);
                        
                        java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
                        
                        // file part header
                        baos.write(("--" + boundary + "\r\n").getBytes("UTF-8"));
                        baos.write(("Content-Disposition: form-data; name=\"file\"; filename=\"" + filename + "\"\r\n").getBytes("UTF-8"));
                        baos.write("Content-Type: application/vnd.openxmlformats-officedocument.wordprocessingml.document\r\n\r\n".getBytes("UTF-8"));
                        
                        // file content
                        java.io.FileInputStream fis = new java.io.FileInputStream(tempFile);
                        byte[] buf = new byte[8192]; int len;
                        while ((len = fis.read(buf)) > 0) baos.write(buf, 0, len);
                        fis.close();
                        
                        // category part
                        baos.write(("\r\n--" + boundary + "\r\n").getBytes("UTF-8"));
                        baos.write("Content-Disposition: form-data; name=\"category\"\r\n\r\n".getBytes("UTF-8"));
                        baos.write(category.getBytes("UTF-8"));
                        baos.write(("\r\n--" + boundary + "--\r\n").getBytes("UTF-8"));
                        
                        // Send request
                        conn.setRequestProperty("Content-Length", String.valueOf(baos.size()));
                        java.io.OutputStream os = conn.getOutputStream();
                        baos.writeTo(os);
                        os.flush();
                        os.close();
                        
                        int code = conn.getResponseCode();
                        tempFile.delete();
                        if (code == 200) {
                            java.io.BufferedReader br = new java.io.BufferedReader(new java.io.InputStreamReader(conn.getInputStream(), "UTF-8"));
                            StringBuilder respBody = new StringBuilder();
                            String line;
                            while ((line = br.readLine()) != null) respBody.append(line);
                            br.close();
                            String respStr = respBody.toString();
                            if (respStr.contains("\"imported\":")) {
                                int idx = respStr.indexOf("\"imported\":") + 11;
                                int end = respStr.indexOf(",", idx);
                                if (end < 0) end = respStr.indexOf("}", idx);
                                if (end > idx) imported += Integer.parseInt(respStr.substring(idx, end).trim());
                            } else {
                                imported++;
                            }
                        } else {
                            errors.append("Flask error (HTTP ").append(code).append("): ").append(filename).append("\n");
                        }
                        conn.disconnect();
                    } catch (Exception ex) { errors.append("Error: ").append(ex.getMessage()).append("\n"); }
                    continue;
                }
                try (InputStream is = part.getInputStream();
                     BufferedReader br = new BufferedReader(new InputStreamReader(is, "UTF-8"))) {
                    StringBuilder content = new StringBuilder();
                    String line;
                    while ((line = br.readLine()) != null) content.append(line).append('\n');
                    String text = content.toString().trim();
                    if (text.isEmpty()) {
                        errors.append("Empty file: ").append(filename).append("\n");
                        continue;
                    }
                    // Split by double newlines for multiple entries
                    String[] chunks = text.split("(?=\\n\\s*\\n|\\n#{1,3}\\s)");
                    for (String chunk : chunks) {
                        chunk = chunk.trim();
                        if (chunk.length() < 10) continue;
                        String q = filename + ": " + chunk.substring(0, Math.min(80, chunk.length())) + "...";
                        KnowledgeEntry e = new KnowledgeEntry();
                        e.setQuestion(q);
                        e.setAnswer(chunk);
                        e.setCategory(category);
                        e.setCreatedBy(username);
                        KnowledgeBaseDAO.add(e);
                        imported++;
                    }
                } catch (Exception ex) {
                    errors.append("Error reading ").append(filename).append(": ").append(ex.getMessage()).append("\n");
                }
            }
        }

        String errStr = errors.length() > 0 ? errors.toString() : "";
        
        // Trigger Flask RAG reindex after successful import
        if (imported > 0) {
            try {
                java.net.URL reloadUrl = new java.net.URL(FlaskConfig.getBaseUrl() + "/reload");
                java.net.HttpURLConnection conn = (java.net.HttpURLConnection) reloadUrl.openConnection();
                conn.setRequestMethod("POST");
                conn.setConnectTimeout(3000);
                conn.setReadTimeout(3000);
                conn.getResponseCode(); // just fire and forget
                conn.disconnect();
            } catch (Exception ignore) { }
        }
        
        out(resp, "{\"ok\":true,\"imported\":" + imported + ",\"errors\":\"" + escapeJson(errStr) + "\"}");
    }

    private String getJsonStr(JsonObject obj, String field) {
        try {
            if (obj.has(field) && !obj.get(field).isJsonNull()) {
                return obj.get(field).getAsString();
            }
        } catch (Exception e) { /* ignore */ }
        return null;
    }

    private String extractJsonField(String json, String field) {
        String key = "\"" + field + "\"";
        int idx = json.indexOf(key);
        if (idx < 0) return null;
        int colon = json.indexOf(':', idx);
        if (colon < 0) return null;
        int start = colon + 1;
        while (start < json.length() && Character.isWhitespace(json.charAt(start))) start++;
        if (start >= json.length()) return null;
        char c = json.charAt(start);
        if (c == '"') {
            StringBuilder sb = new StringBuilder();
            int i = start + 1;
            while (i < json.length()) {
                char ch = json.charAt(i);
                if (ch == '\\' && i + 1 < json.length()) { i++; sb.append(json.charAt(i)); }
                else if (ch == '"') { break; }
                else { sb.append(ch); }
                i++;
            }
            return sb.toString();
        }
        return null;
    }

    private String escapeJson(String s) {
        if (s == null) return "";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '\\') {
                sb.append("\\\\");
            } else if (c == '"') {
                sb.append("\\\"");
            } else if (c == '\n') {
                sb.append("\\n");
            } else if (c == '\r') {
                sb.append("\\r");
            } else if (c == '\t') {
                sb.append("\\t");
            } else if (c < 0x20) {
                sb.append(String.format("\\u%04X", (int)c));
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }
}

