package com.qiuniu.dao;

import com.qiuniu.model.PromptAnalysis;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class PromptAnalysisDAO {

    private static final String DEFAULT_TEAM_ANALYSIS = "你是一位资深代码审查专家，专注于分析团队版本发布的代码变更风险。你的职责是：\n" +
        "1. 识别高风险变更（破坏性修改、安全漏洞、严重性能问题）\n" +
        "2. 评估每个代码仓变更对整体系统的影响\n" +
        "3. 给出具体可执行的风险缓解建议\n\n" +
        "输出要求：\n" +
        "- 使用中文输出\n" +
        "- 每个高风险项必须给出风险等级（高/中/低）和具体位置\n" +
        "- 中风险及以上必须给出建议的缓解措施\n" +
        "- 总结部分要给出版本是否可以上线的评估意见\n" +
        "- 结论必须明确：建议发布 / 需要复查 / 阻塞发布";

    public boolean createTable() {
        String sql = "CREATE TABLE IF NOT EXISTS prompt_analysis (" +
                "id BIGINT AUTO_INCREMENT PRIMARY KEY, " +
                "file_type VARCHAR(50) NOT NULL, " +
                "display_name VARCHAR(100), " +
                "system_prompt TEXT NOT NULL, " +
                "user_prompt_template TEXT NOT NULL, " +
                "max_diff_lines INT DEFAULT 300, " +
                "priority INT DEFAULT 100, " +
                "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, " +
                "UNIQUE KEY uk_file_type (file_type)" +
                ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4";
        try (Connection conn = DBUtil.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute(sql);
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    public boolean seedDefaultPrompts() {
        // 检查是否已有数据
        try (Connection conn = DBUtil.getConnection();
             Statement stmt = conn.createStatement()) {
            ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM prompt_analysis");
            if (rs.next() && rs.getInt(1) > 0) return true;
        } catch (Exception e) {
            e.printStackTrace();
        }

        List<PromptAnalysis> defaults = buildDefaultPrompts();
        for (PromptAnalysis p : defaults) {
            try {
                insert(p);
            } catch (Exception e) {
                System.err.println("[PromptAnalysisDAO] Failed to insert default: " + p.getFileType() + " - " + e.getMessage());
            }
        }
        System.out.println("[PromptAnalysisDAO] Seeded " + defaults.size() + " default prompt templates");
        return true;
    }

    private List<PromptAnalysis> buildDefaultPrompts() {
        List<PromptAnalysis> list = new ArrayList<>();

        list.add(javaPrompt());
        list.add(sqlPrompt());
        list.add(yamlPrompt());
        list.add(jsonConfigPrompt());
        list.add(jsPrompt());
        list.add(tsPrompt());
        list.add(shellPrompt());
        list.add(goPrompt());
        list.add(pythonPrompt());
        list.add(defaultPrompt());

        return list;
    }

    private PromptAnalysis javaPrompt() {
        PromptAnalysis p = new PromptAnalysis();
        p.setFileType("java");
        p.setDisplayName("Java 代码风险审查");
        p.setSystemPrompt("你是一位资深 Java 架构师，专注于代码安全与质量审查。");
        p.setUserPromptTemplate(
            "【版本信息】\n" +
            "版本名称：{versionName}\n" +
            "基础分支：{baseBranch} → 对比分支：{compareBranch}\n" +
            "项目名称：{projectName}\n\n" +
            "【审查要求】\n" +
            "请审查以下 Java 代码变更，识别：\n" +
            "1. 安全性问题（SQL注入、XSS、敏感信息泄露、硬编码密码等）\n" +
            "2. 破坏性修改（删除核心方法、修改接口签名、删除数据库表结构等）\n" +
            "3. 严重性能问题（N+1查询、大量循环内数据库操作、内存泄漏风险等）\n" +
            "4. 事务与并发问题（错误的锁使用、事务边界不当等）\n\n" +
            "【代码变更内容】\n" +
            "{diff}\n\n" +
            "请用中文输出详细的代码审查报告。");
        p.setMaxDiffLines(300);
        p.setPriority(10);
        return p;
    }

    private PromptAnalysis sqlPrompt() {
        PromptAnalysis p = new PromptAnalysis();
        p.setFileType("sql");
        p.setDisplayName("SQL / DDL 脚本分析");
        p.setSystemPrompt("你是一位资深数据库架构师，专注于 SQL 脚本的安全与性能审查。");
        p.setUserPromptTemplate(
            "【版本信息】\n" +
            "版本名称：{versionName}\n" +
            "基础分支：{baseBranch} → 对比分支：{compareBranch}\n" +
            "项目名称：{projectName}\n\n" +
            "【审查要求】\n" +
            "请审查以下 SQL 变更，识别：\n" +
            "1. 破坏性 DDL（删除表、清空数据、修改主键、删除索引等）\n" +
            "2. 性能风险（缺索引的 WHERE 条件、全表扫描、大事务、长事务等）\n" +
            "3. 数据安全问题（明文存储敏感字段、不当的数据暴露等）\n" +
            "4. 兼容性风险（使用新版本 MySQL 特有语法等）\n\n" +
            "【SQL 变更内容】\n" +
            "{diff}\n\n" +
            "请用中文输出详细的 SQL 审查报告。");
        p.setMaxDiffLines(200);
        p.setPriority(5);
        return p;
    }

    private PromptAnalysis yamlPrompt() {
        PromptAnalysis p = new PromptAnalysis();
        p.setFileType("yaml");
        p.setDisplayName("YAML 配置文件分析");
        p.setSystemPrompt("你是一位资深 DevOps 工程师，专注于配置文件的安全与正确性审查。");
        p.setUserPromptTemplate(
            "【版本信息】\n" +
            "版本名称：{versionName}\n" +
            "基础分支：{baseBranch} → 对比分支：{compareBranch}\n" +
            "项目名称：{projectName}\n\n" +
            "【审查要求】\n" +
            "请审查以下 YAML 配置变更，识别：\n" +
            "1. 配置错误风险（端口冲突、路径错误、配置项拼写错误等）\n" +
            "2. 安全风险（暴露敏感端口、关闭认证、错误的安全策略等）\n" +
            "3. 性能问题（内存/线程池配置不当、超时设置不合理等）\n" +
            "4. 环境差异风险（开发/测试/生产配置混用等）\n\n" +
            "【YAML 变更内容】\n" +
            "{diff}\n\n" +
            "请用中文输出详细的配置审查报告。");
        p.setMaxDiffLines(300);
        p.setPriority(20);
        return p;
    }

    private PromptAnalysis jsonConfigPrompt() {
        PromptAnalysis p = new PromptAnalysis();
        p.setFileType("json");
        p.setDisplayName("JSON 配置文件分析");
        p.setSystemPrompt("你是一位资深 DevOps 工程师，专注于 JSON 配置的安全与正确性审查。");
        p.setUserPromptTemplate(
            "【版本信息】\n" +
            "版本名称：{versionName}\n" +
            "基础分支：{baseBranch} → 对比分支：{compareBranch}\n" +
            "项目名称：{projectName}\n\n" +
            "【审查要求】\n" +
            "请审查以下 JSON 配置变更，识别：\n" +
            "1. 配置错误（字段缺失、类型错误、枚举值越界等）\n" +
            "2. 安全风险（暴露密钥、关闭安全开关、错误 CORS 配置等）\n" +
            "3. 逻辑错误（条件判断错误、阈值设置不当等）\n\n" +
            "【JSON 变更内容】\n" +
            "{diff}\n\n" +
            "请用中文输出详细的配置审查报告。");
        p.setMaxDiffLines(300);
        p.setPriority(25);
        return p;
    }

    private PromptAnalysis jsPrompt() {
        PromptAnalysis p = new PromptAnalysis();
        p.setFileType("js");
        p.setDisplayName("JavaScript 代码风险审查");
        p.setSystemPrompt("你是一位资深前端安全专家，专注于 JavaScript 代码的安全与质量审查。");
        p.setUserPromptTemplate(
            "【版本信息】\n" +
            "版本名称：{versionName}\n" +
            "基础分支：{baseBranch} → 对比分支：{compareBranch}\n" +
            "项目名称：{projectName}\n\n" +
            "【审查要求】\n" +
            "请审查以下 JavaScript 代码变更，识别：\n" +
            "1. 安全风险（XSS、CSRF、不安全的 eval/useEffect 依赖等）\n" +
            "2. 前端性能问题（内存泄漏、大量重复渲染、无防抖/节流的事件处理等）\n" +
            "3. 破坏性修改（删除核心组件、修改路由、改变认证逻辑等）\n\n" +
            "【JS 变更内容】\n" +
            "{diff}\n\n" +
            "请用中文输出详细的代码审查报告。");
        p.setMaxDiffLines(300);
        p.setPriority(30);
        return p;
    }

    private PromptAnalysis tsPrompt() {
        PromptAnalysis p = new PromptAnalysis();
        p.setFileType("ts");
        p.setDisplayName("TypeScript 代码风险审查");
        p.setSystemPrompt("你是一位资深 TypeScript 专家，专注于类型安全和代码质量审查。");
        p.setUserPromptTemplate(
            "【版本信息】\n" +
            "版本名称：{versionName}\n" +
            "基础分支：{baseBranch} → 对比分支：{compareBranch}\n" +
            "项目名称：{projectName}\n\n" +
            "【审查要求】\n" +
            "请审查以下 TypeScript 代码变更，识别：\n" +
            "1. 类型安全风险（any 滥用、类型断言错误、泛型约束不当等）\n" +
            "2. 运行时错误风险（null/undefined 未检查、数组越界访问等）\n" +
            "3. 破坏性修改（删除类型定义、修改接口/类型签名等）\n\n" +
            "【TS 变更内容】\n" +
            "{diff}\n\n" +
            "请用中文输出详细的代码审查报告。");
        p.setMaxDiffLines(300);
        p.setPriority(30);
        return p;
    }

    private PromptAnalysis shellPrompt() {
        PromptAnalysis p = new PromptAnalysis();
        p.setFileType("sh");
        p.setDisplayName("Shell 脚本风险分析");
        p.setSystemPrompt("你是一位资深运维工程师，专注于 Shell 脚本的安全与可靠性审查。");
        p.setUserPromptTemplate(
            "【版本信息】\n" +
            "版本名称：{versionName}\n" +
            "基础分支：{baseBranch} → 对比分支：{compareBranch}\n" +
            "项目名称：{projectName}\n\n" +
            "【审查要求】\n" +
            "请审查以下 Shell 脚本变更，识别：\n" +
            "1. 安全风险（命令注入、路径遍历、敏感信息硬编码、错误权限设置等）\n" +
            "2. 可靠性问题（缺少错误处理、磁盘空间检查缺失、进程已存在检查等）\n" +
            "3. 破坏性操作（rm -rf、数据清理脚本误用等）\n\n" +
            "【Shell 脚本变更内容】\n" +
            "{diff}\n\n" +
            "请用中文输出详细的脚本审查报告。");
        p.setMaxDiffLines(200);
        p.setPriority(15);
        return p;
    }

    private PromptAnalysis goPrompt() {
        PromptAnalysis p = new PromptAnalysis();
        p.setFileType("go");
        p.setDisplayName("Go 代码风险审查");
        p.setSystemPrompt("你是一位资深 Go 语言专家，专注于 Go 代码的安全与并发正确性审查。");
        p.setUserPromptTemplate(
            "【版本信息】\n" +
            "版本名称：{versionName}\n" +
            "基础分支：{baseBranch} → 对比分支：{compareBranch}\n" +
            "项目名称：{projectName}\n\n" +
            "【审查要求】\n" +
            "请审查以下 Go 代码变更，识别：\n" +
            "1. 并发安全（goroutine 泄漏、竞态条件、channel 泄漏、mutex 使用错误等）\n" +
            "2. 错误处理（error 未检查、panic/recover 滥用等）\n" +
            "3. 资源泄漏（defer 顺序错误、连接未关闭等）\n\n" +
            "【Go 代码变更内容】\n" +
            "{diff}\n\n" +
            "请用中文输出详细的代码审查报告。");
        p.setMaxDiffLines(300);
        p.setPriority(30);
        return p;
    }

    private PromptAnalysis pythonPrompt() {
        PromptAnalysis p = new PromptAnalysis();
        p.setFileType("py");
        p.setDisplayName("Python 代码风险审查");
        p.setSystemPrompt("你是一位资深 Python 工程师，专注于 Python 代码的安全与质量审查。");
        p.setUserPromptTemplate(
            "【版本信息】\n" +
            "版本名称：{versionName}\n" +
            "基础分支：{baseBranch} → 对比分支：{compareBranch}\n" +
            "项目名称：{projectName}\n\n" +
            "【审查要求】\n" +
            "请审查以下 Python 代码变更，识别：\n" +
            "1. 安全风险（pickle 反序列化、eval/exec 使用、路径遍历、硬编码密钥等）\n" +
            "2. Python 习惯问题（GIL 限制认知不足、生成器未使用、字典循环遍历等）\n" +
            "3. 破坏性修改（删除核心函数、修改入口点、改变依赖等）\n\n" +
            "【Python 变更内容】\n" +
            "{diff}\n\n" +
            "请用中文输出详细的代码审查报告。");
        p.setMaxDiffLines(300);
        p.setPriority(30);
        return p;
    }

    private PromptAnalysis defaultPrompt() {
        PromptAnalysis p = new PromptAnalysis();
        p.setFileType("default");
        p.setDisplayName("通用代码风险分析");
        p.setSystemPrompt("你是一位资深代码审查专家，专注于代码变更的安全与质量评估。");
        p.setUserPromptTemplate(
            "【版本信息】\n" +
            "版本名称：{versionName}\n" +
            "基础分支：{baseBranch} → 对比分支：{compareBranch}\n" +
            "项目名称：{projectName}\n\n" +
            "【审查要求】\n" +
            "请审查以下代码变更，识别：\n" +
            "1. 高风险变更（破坏性修改、安全漏洞、严重性能问题）\n" +
            "2. 评估变更对整体系统的影响\n" +
            "3. 给出具体可执行的风险缓解建议\n\n" +
            "【代码变更内容】\n" +
            "{diff}\n\n" +
            "请用中文输出详细的代码审查报告。");
        p.setMaxDiffLines(200);
        p.setPriority(999);
        return p;
    }

    public boolean insert(PromptAnalysis p) {
        String sql = "INSERT INTO prompt_analysis " +
                "(file_type, display_name, system_prompt, user_prompt_template, max_diff_lines, priority) " +
                "VALUES (?, ?, ?, ?, ?, ?)";
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, p.getFileType());
            ps.setString(2, p.getDisplayName());
            ps.setString(3, p.getSystemPrompt());
            ps.setString(4, p.getUserPromptTemplate());
            ps.setInt(5, p.getMaxDiffLines() != null ? p.getMaxDiffLines() : 300);
            ps.setInt(6, p.getPriority() != null ? p.getPriority() : 100);
            return ps.executeUpdate() > 0;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    public boolean update(PromptAnalysis p) {
        String sql = "UPDATE prompt_analysis SET display_name=?, system_prompt=?, " +
                "user_prompt_template=?, max_diff_lines=?, priority=? WHERE id=?";
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, p.getDisplayName());
            ps.setString(2, p.getSystemPrompt());
            ps.setString(3, p.getUserPromptTemplate());
            ps.setInt(4, p.getMaxDiffLines() != null ? p.getMaxDiffLines() : 300);
            ps.setInt(5, p.getPriority() != null ? p.getPriority() : 100);
            ps.setLong(6, p.getId());
            return ps.executeUpdate() > 0;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    public PromptAnalysis getById(Long id) {
        String sql = "SELECT * FROM prompt_analysis WHERE id=?";
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, id);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return mapRow(rs);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    public PromptAnalysis getByFileType(String fileType) {
        String sql = "SELECT * FROM prompt_analysis WHERE file_type=?";
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, fileType);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return mapRow(rs);
        } catch (Exception e) {
            e.printStackTrace();
        }
        // fallback to default
        sql = "SELECT * FROM prompt_analysis WHERE file_type='default'";
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return mapRow(rs);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    public List<PromptAnalysis> getAll() {
        List<PromptAnalysis> list = new ArrayList<>();
        String sql = "SELECT * FROM prompt_analysis ORDER BY priority ASC";
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ResultSet rs = ps.executeQuery();
            while (rs.next()) list.add(mapRow(rs));
        } catch (Exception e) {
            e.printStackTrace();
        }
        return list;
    }

    public List<PromptAnalysis> search(String keyword) {
        List<PromptAnalysis> list = new ArrayList<>();
        String sql = "SELECT * FROM prompt_analysis WHERE display_name LIKE ? OR system_prompt LIKE ? OR user_prompt_template LIKE ? ORDER BY priority ASC";
        String kw = "%" + keyword + "%";
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, kw);
            ps.setString(2, kw);
            ps.setString(3, kw);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) list.add(mapRow(rs));
        } catch (Exception e) {
            e.printStackTrace();
        }
        return list;
    }

    public boolean delete(Long id) {
        String sql = "DELETE FROM prompt_analysis WHERE id=?";
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, id);
            return ps.executeUpdate() > 0;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    private PromptAnalysis mapRow(ResultSet rs) throws SQLException {
        PromptAnalysis p = new PromptAnalysis();
        p.setId(rs.getLong("id"));
        p.setFileType(rs.getString("file_type"));
        p.setDisplayName(rs.getString("display_name"));
        p.setSystemPrompt(rs.getString("system_prompt"));
        p.setUserPromptTemplate(rs.getString("user_prompt_template"));
        p.setMaxDiffLines(rs.getInt("max_diff_lines"));
        p.setPriority(rs.getInt("priority"));
        Timestamp ts = rs.getTimestamp("created_at");
        p.setCreatedAt(ts != null ? ts.toString() : null);
        return p;
    }

    public String getDefaultTeamAnalysisPrompt() {
        return DEFAULT_TEAM_ANALYSIS;
    }
}
