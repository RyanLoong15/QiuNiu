package com.qiuniu.servlet;

import com.qiuniu.dao.DBUtil;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.*;
import java.net.URLEncoder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

/**
 * 文档下载 Servlet
 * GET /api/document/download?import_id=xxx
 * 
 * 从 document_imports 表查询文件路径，以附件形式返回原始文件。
 * 仅允许已完成的导入记录（status='completed'）。
 * 
 * 路径处理逻辑：
 * - DB 中 file_path 存的是相对路径（相对于 upload.dir）
 * - 下载时拼接 upload.dir + file_path 得到绝对路径
 * - 兼容旧数据（绝对路径）直接读取
 */
@WebServlet("/api/document/download")
public class DocumentDownloadServlet extends HttpServlet {

    private static final long serialVersionUID = 1L;

    /** 从 db.properties 读取的文档上传根目录 */
    private static final String UPLOAD_DIR;

    static {
        String dir = null;
        try (InputStream is = DocumentDownloadServlet.class
                .getClassLoader().getResourceAsStream("db.properties")) {
            if (is != null) {
                java.util.Properties p = new java.util.Properties();
                p.load(is);
                dir = p.getProperty("upload.dir");
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        UPLOAD_DIR = (dir != null && !dir.isEmpty()) ? dir : null;
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        String importIdStr = request.getParameter("import_id");
        if (importIdStr == null || importIdStr.trim().isEmpty()) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Missing import_id parameter");
            return;
        }

        int importId;
        try {
            importId = Integer.parseInt(importIdStr.trim());
        } catch (NumberFormatException e) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Invalid import_id: " + importIdStr);
            return;
        }

        try (Connection conn = DBUtil.getConnection()) {
            String sql = "SELECT file_path, orig_filename FROM document_imports WHERE id = ? AND status = 'completed'";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setInt(1, importId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) {
                        response.sendError(HttpServletResponse.SC_NOT_FOUND,
                                "Document not found with id: " + importId);
                        return;
                    }

                    String filePath = rs.getString("file_path");
                    String origFilename = rs.getString("orig_filename");

                    // 解析为绝对路径：
                    // 1. 如果是相对路径（不含盘符/根路径），拼接 UPLOAD_DIR
                    // 2. 如果是绝对路径（旧数据），直接使用
                    Path path = resolveUploadPath(filePath);
                    if (!Files.exists(path) || !Files.isRegularFile(path)) {
                        response.sendError(HttpServletResponse.SC_NOT_FOUND,
                                "File not found on disk: " + filePath);
                        return;
                    }

                    // 检测 MIME 类型
                    String mimeType = Files.probeContentType(path);
                    if (mimeType == null) {
                        mimeType = "application/octet-stream";
                    }
                    response.setContentType(mimeType);

                    // 设置 Content-Disposition（RFC 5987 支持中文文件名）
                    String encodedFilename = URLEncoder.encode(origFilename, "UTF-8")
                            .replace("+", "%20");
                    response.setHeader("Content-Disposition",
                            "attachment; filename=\"" + origFilename
                                    .replace("\"", "\\\"") + "\"; filename*=UTF-8''"
                                    + encodedFilename);

                    response.setContentLengthLong(Files.size(path));

                    // 流式输出文件
                    try (InputStream in = Files.newInputStream(path);
                            OutputStream out = response.getOutputStream()) {
                        byte[] buffer = new byte[8192];
                        int bytesRead;
                        while ((bytesRead = in.read(buffer)) != -1) {
                            out.write(buffer, 0, bytesRead);
                        }
                        out.flush();
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
            response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                    "Download failed: " + e.getMessage());
        }
    }

    /**
     * 将 DB 中的 file_path 解析为绝对路径
     * - 相对路径：拼接 UPLOAD_DIR
     * - 绝对路径（旧数据）：直接使用
     */
    private Path resolveUploadPath(String dbPath) {
        if (dbPath == null || dbPath.isEmpty()) {
            throw new IllegalArgumentException("file_path is empty");
        }
        Path p = Paths.get(dbPath);
        // Windows：含冒号（C:）或 Linux：以 / 开头 → 绝对路径
        if (p.isAbsolute()) {
            return p;
        }
        // 相对路径：拼接 UPLOAD_DIR
        if (UPLOAD_DIR != null) {
            return Paths.get(UPLOAD_DIR, dbPath);
        }
        // 无 UPLOAD_DIR 配置：降级为当前工作目录拼接
        return Paths.get(System.getProperty("user.dir"), dbPath);
    }
}
