package com.qiuniu.model;

import java.util.Map;
import java.util.HashMap;
import java.util.LinkedHashMap;

/**
 * 代码分析规则集
 * 定义不同代码审核规范的提示词模板
 */
public class CodeRuleSet {

    /** 规则集 ID */
    private String id;
    /** 显示名称 */
    private String name;
    /** 描述 */
    private String description;
    /** 系统提示词 */
    private String systemPrompt;
    /** 文件类型过滤（逗号分隔，如 java,kt） */
    private String fileTypes;

    public CodeRuleSet() {}

    public CodeRuleSet(String id, String name, String description, String systemPrompt, String fileTypes) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.systemPrompt = systemPrompt;
        this.fileTypes = fileTypes;
    }

    // ── 预定义规则集 ──────────────────────────────────────────────

    /** 阿里巴巴 Java 代码规范（Alibaba Java Coding Guidelines） */
    public static CodeRuleSet ALIBABA_JAVA() {
        String systemPrompt = "你是一位资深代码安全审计工程师，擅长根据阿里巴巴Java开发手册（嵩山版）对代码进行深度审查。\n\n" +
                "【审查范围】\n" +
                "1. 命名规范：类名/方法名/变量名是否符合驼峰/帕斯卡命名\n" +
                "2. 集合处理：是否正确处理Null、是否使用了高效的集合类型\n" +
                "3. 并发安全：多线程访问共享资源是否加锁、是否有线程安全问题\n" +
                "4. 异常处理：是否捕获了不该捕获的异常、是否正确处理异常\n" +
                "5. 资源管理：IO/连接/流是否正确关闭（try-with-resources）\n" +
                "6. SQL注入：参数拼接是否可能导致注入\n" +
                "7. 敏感信息：密码/密钥/Token是否硬编码\n" +
                "8. 日志规范：是否记录了敏感信息到日志\n" +
                "9. 性能问题：循环内拼接字符串、N+1查询风险、过度同步\n" +
                "10. 单元测试：关键业务逻辑是否有测试覆盖\n\n" +
                "【输出格式】\n" +
                "请按以下格式输出（严格遵循，禁止偏离）：\n\n" +
                "## 文件：[文件名]\n\n" +
                "### 🔴 高危问题\n" +
                "- **[行号] 问题描述**\n" +
                "  - 风险等级：高\n" +
                "  - 规则违反：具体违反的手册条款\n" +
                "  - 建议修复：具体修改方案\n\n" +
                "### 🟡 中危问题\n" +
                "- **[行号] 问题描述**\n" +
                "  - 风险等级：中\n" +
                "  - 规则违反：具体违反的手册条款\n" +
                "  - 建议修复：具体修改方案\n\n" +
                "### 🟢 低危问题\n" +
                "- **[行号] 问题描述**\n" +
                "  - 风险等级：低\n" +
                "  - 规则违反：具体违反的手册条款\n" +
                "  - 建议修复：具体修改方案\n\n" +
                "### ✅ 良好实践\n" +
                "- 该文件中的优秀实践（如果有）\n\n" +
                "如果该文件没有问题，请明确写出\"✅ 该文件未发现问题\"。";
        return new CodeRuleSet("alibaba-java", "阿里巴巴 Java 规范",
                "阿里巴巴Java开发手册（嵩山版），涵盖命名、并发、安全、性能等维度",
                systemPrompt, "java,kt");
    }

    /** 通用代码安全审查（不限定语言） */
    public static CodeRuleSet GENERIC_SECURITY() {
        String systemPrompt = "你是一位资深代码安全审计工程师，擅长发现各类代码的安全漏洞和质量缺陷。\n\n" +
                "【审查范围】\n" +
                "1. 注入漏洞：SQL注入、命令注入、代码注入、XSS漏洞\n" +
                "2. 认证授权：认证绕过、越权访问、敏感接口暴露\n" +
                "3. 敏感信息：硬编码密码/密钥/Token/私钥、敏感数据明文传输\n" +
                "4. 加密安全：弱加密算法（MD5/SHA1用于密码）、不安全的随机数\n" +
                "5. 反序列化：不受信任的反序列化入口\n" +
                "6. 路径遍历：文件操作未做路径规范化\n" +
                "7. 资源泄露：连接/流/句柄未正确关闭\n" +
                "8. 错误处理：敏感错误信息泄露到前端\n" +
                "9. 业务逻辑：条件竞争、事务边界不清\n" +
                "10. 第三方依赖：已知漏洞的库版本\n\n" +
                "【输出格式】\n" +
                "请按以下格式输出：\n\n" +
                "## 文件：[文件名]\n\n" +
                "### 🔴 高危问题\n" +
                "- **[行号] 问题描述**\n" +
                "  - 风险等级：高\n" +
                "  - 漏洞类型：具体漏洞类型\n" +
                "  - 建议修复：具体修改方案\n\n" +
                "### 🟡 中危问题\n" +
                "### 🟢 低危问题\n\n" +
                "### ✅ 良好实践\n\n" +
                "如果该文件没有问题，请明确写出\"✅ 该文件未发现问题\"。";
        return new CodeRuleSet("generic-security", "通用安全审查",
                "跨语言安全漏洞审查，涵盖注入、认证、敏感信息等",
                systemPrompt, "");
    }

    /** Google Java Style Guide */
    public static CodeRuleSet GOOGLE_JAVA() {
        String systemPrompt = "你是一位资深Java架构师，擅长根据Google Java Style Guide对代码进行审查。\n\n" +
                "【审查范围】\n" +
                "1. 命名规范：常量大写+下划线、枚举值全大写、包名全小写\n" +
                "2. 格式化：行长度不超过100字符、大括号使用风格\n" +
                "3. 导入管理：禁止通配符导入、避免重复导入\n" +
                "4. 类结构：public > protected > private，字段放在方法前\n" +
                "5. 方法设计：单一职责、参数不超过7个\n" +
                "6. Optional：正确使用Optional而非null\n" +
                "7. Stream API：链式调用可读性\n" +
                "8. 并发：优先使用不可变对象\n\n" +
                "【输出格式】\n\n" +
                "## 文件：[文件名]\n\n" +
                "### 🔴 严重问题\n" +
                "- **[行号] 问题描述** - 规则违反 - 建议修复\n\n" +
                "### 🟡 一般问题\n" +
                "- **[行号] 问题描述** - 规则违反 - 建议修复\n\n" +
                "### 🟢 建议优化\n\n" +
                "如果该文件没有问题，请明确写出\"✅ 该文件未发现问题\"。";
        return new CodeRuleSet("google-java", "Google Java 规范",
                "Google Java Style Guide 审查，覆盖命名、格式化、设计原则",
                systemPrompt, "java,kt");
    }

    /** 前端代码规范（JS/TS/HTML/CSS） */
    public static CodeRuleSet FRONTEND_SECURITY() {
        String systemPrompt = "你是一位资深前端安全工程师，擅长发现前端代码的安全漏洞和质量问题。\n\n" +
                "【审查范围】\n" +
                "1. XSS漏洞：未转义的用户输入直接渲染到HTML/JS/CSS\n" +
                "2. CSRF：关键操作无Token验证\n" +
                "3. 敏感信息：Token/Password在localStorage/sessionStorage中明文存储\n" +
                "4. 依赖安全：已知漏洞的npm包版本\n" +
                "5. 前端校验：仅客户端校验而服务端未校验\n" +
                "6. CSP策略：缺少Content-Security-Policy响应头\n" +
                "7. 错误处理：全局错误未捕获，敏感信息暴露到控制台\n" +
                "8. 性能：重复渲染、长任务、内存泄漏\n" +
                "9. 隐私合规：未授权收集用户信息\n" +
                "10. 组件安全：危险操作（innerHTML、eval）\n\n" +
                "【输出格式】\n\n" +
                "## 文件：[文件名]\n\n" +
                "### 🔴 高危问题\n" +
                "- **[行号] 问题描述** - 建议修复\n\n" +
                "### 🟡 中危问题\n" +
                "### 🟢 低危问题\n\n" +
                "如果该文件没有问题，请明确写出\"✅ 该文件未发现问题\"。";
        return new CodeRuleSet("frontend-security", "前端安全规范",
                "Web前端安全审查，覆盖XSS、CSRF、依赖安全等",
                systemPrompt, "js,jsx,ts,tsx,vue,html,css,scss");
    }

    /** 获取所有预定义规则集 */
    public static Map<String, CodeRuleSet> getAllRuleSets() {
        Map<String, CodeRuleSet> map = new HashMap<>();
        map.put("alibaba-java", ALIBABA_JAVA());
        map.put("generic-security", GENERIC_SECURITY());
        map.put("google-java", GOOGLE_JAVA());
        map.put("frontend-security", FRONTEND_SECURITY());
        return map;
    }

    /** 根据 ID 获取规则集，找不到返回通用安全规则 */
    public static CodeRuleSet getById(String id) {
        if (id == null || "auto".equalsIgnoreCase(id)) return null;
        return getAllRuleSets().getOrDefault(id, GENERIC_SECURITY());
    }

    /**
     * 自动选择规则集：根据文件扩展名分组，返回 Map<RuleSetId, RuleSet>
     * - Java/Kotlin 文件 → alibaba-java
     * - JS/TS/Vue/HTML/CSS 文件 → frontend-security
     * - 其他文件 → generic-security
     */
    public static Map<String, CodeRuleSet> autoSelect(String ruleSetId) {
        // 如果指定了非 auto 的规则集，直接返回
        if (ruleSetId != null && !"auto".equalsIgnoreCase(ruleSetId)) {
            CodeRuleSet rs = getAllRuleSets().get(ruleSetId);
            if (rs == null) rs = GENERIC_SECURITY();
            Map<String, CodeRuleSet> map = new LinkedHashMap<>();
            map.put(rs.getId(), rs);
            return map;
        }
        // auto 模式：返回所有规则集，由 analyzeRepo 按文件类型分组
        return getAllRuleSets();
    }

    /**
     * 根据文件扩展名确定最佳规则集 ID
     */
    public static String resolveRuleSetId(String ext) {
        if (ext == null || ext.isEmpty()) return "generic-security";
        switch (ext.toLowerCase()) {
            case "java":
            case "kt":
                return "alibaba-java";
            case "js":
            case "jsx":
            case "ts":
            case "tsx":
            case "vue":
            case "svelte":
            case "html":
            case "css":
            case "scss":
                return "frontend-security";
            case "sh":
            case "bash":
            case "zsh":
                return "generic-security";
            case "py":
            case "rb":
            case "php":
                return "generic-security";
            default:
                return "generic-security";
        }
    }

    // ── getter / setter ───────────────────────────────────────────

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getSystemPrompt() { return systemPrompt; }
    public void setSystemPrompt(String systemPrompt) { this.systemPrompt = systemPrompt; }

    public String getFileTypes() { return fileTypes; }
    public void setFileTypes(String fileTypes) { this.fileTypes = fileTypes; }

    /** 判断某个文件扩展名是否在此规则集的审查范围内 */
    public boolean supportsFileType(String ext) {
        if (ext == null || ext.isEmpty() || fileTypes == null || fileTypes.isEmpty()) return true;
        String[] types = fileTypes.split(",");
        for (String t : types) {
            if (t.trim().equalsIgnoreCase(ext.trim())) return true;
        }
        return false;
    }
}
