-- 持久化 Diff AI 分析结果
CREATE TABLE IF NOT EXISTS diff_ai_results (
    id INT AUTO_INCREMENT PRIMARY KEY,
    project_name VARCHAR(255) NOT NULL COMMENT '项目名称',
    base_branch VARCHAR(128) NOT NULL COMMENT '基准分支',
    compare_branch VARCHAR(128) NOT NULL COMMENT '比较分支',
    diff_hash VARCHAR(64) NOT NULL COMMENT 'diff内容MD5哈希，用于判断是否变化',
    diff_content LONGTEXT COMMENT 'diff内容（供查阅）',
    ai_analysis LONGTEXT NOT NULL COMMENT 'AI代码分析结果',
    incident_check_result LONGTEXT COMMENT '历史事故检核结果',
    file_type VARCHAR(64) COMMENT '文件类型',
    model_used VARCHAR(128) COMMENT '使用的AI模型',
    prompt_used VARCHAR(255) COMMENT '使用的提示词模板',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_project_branches (project_name, base_branch, compare_branch)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Diff AI分析结果缓存表';
