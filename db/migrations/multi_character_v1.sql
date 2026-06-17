-- ============================================================
-- 多角色虚拟人问答系统 — 数据库迁移脚本
-- 版本：v1.1 | 日期：2026-05-16 | 作者：Loong
-- 基于：multi-character-design-dev-v1.1.md Step-01
-- ============================================================

-- 说明：
-- 1. 本脚本需在 qiuniu_db 数据库中执行
-- 2. 执行前请确认 knowledge_base 表已存在
-- 3. 执行顺序：先建表，后 ALTER，最后插入初始数据

-- ============================================================
-- Part 1: 创建 virtual_characters 表（虚拟角色表）
-- ============================================================

CREATE TABLE IF NOT EXISTS virtual_characters (
    id              INT AUTO_INCREMENT PRIMARY KEY,
    slug            VARCHAR(50) NOT NULL UNIQUE COMMENT 'URL/文件路径标识，如 tangjiayi',
    name            VARCHAR(50) NOT NULL COMMENT '角色显示名',
    title           VARCHAR(100) COMMENT '岗位/角色描述',
    staff_user_id   INT COMMENT '绑定的真人员工ID（关联现有staff_users表）',
    system_prompt   TEXT NOT NULL COMMENT '角色人设 System Prompt',
    avatar_style    VARCHAR(50) DEFAULT 'cartoon' COMMENT '头像风格：cartoon/watercolor/anime',
    categories      VARCHAR(500) DEFAULT '' COMMENT '绑定的知识分类，逗号分隔，空=全库',
    confidence_threshold  DECIMAL(3,2) DEFAULT 0.50 COMMENT '该角色的置信度阈值',
    is_active       TINYINT(1) DEFAULT 1 COMMENT '是否启用',
    sort_order      INT DEFAULT 0 COMMENT '排序权重，数字越小越靠前',
    created_at      DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_vc_active_sort (is_active, sort_order),
    INDEX idx_vc_staff (staff_user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='虚拟角色表';

-- ============================================================
-- Part 2: 创建 unanswered_questions 表（未答问题记录表）
-- ============================================================

CREATE TABLE IF NOT EXISTS unanswered_questions (
    id              INT AUTO_INCREMENT PRIMARY KEY,
    character_id    INT NOT NULL COMMENT '关联虚拟角色ID',
    question        TEXT NOT NULL COMMENT '用户提问原文',
    session_id      VARCHAR(100) COMMENT '会话ID，便于追踪对话上下文',
    score           DECIMAL(5,4) COMMENT '检索最高置信度分数（触发记录时的值）',
    status          ENUM('pending','answered','ignored','auto_resolved') DEFAULT 'pending' COMMENT '处理状态',
    answer          TEXT COMMENT '真人员工回答',
    answered_by     VARCHAR(50) COMMENT '回答者姓名',
    answered_at     DATETIME COMMENT '回答时间',
    ignored_by      VARCHAR(50) COMMENT '忽略操作人',
    ignored_at      DATETIME,
    kb_entry_id     INT COMMENT '回答后写入 knowledge_base 的记录ID',
    notification_sent TINYINT(1) DEFAULT 0 COMMENT '是否已推送通知',
    created_at      DATETIME DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (character_id) REFERENCES virtual_characters(id),
    INDEX idx_uq_status (status),
    INDEX idx_uq_character_status (character_id, status),
    INDEX idx_uq_created (created_at),
    -- 防并发写入：同一角色+问题+pending状态的记录只能有一条
    -- 注意：TEXT 字段不能直接建索引，取前 255 字符
    UNIQUE INDEX idx_uq_unique_pending (character_id, question(255), status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='未答问题记录表';

-- ============================================================
-- Part 3: ALTER knowledge_base 表（新增溯源字段）
-- ============================================================

-- 注意：MySQL 不支持 ADD COLUMN IF NOT EXISTS 语法
-- 如果字段已存在，以下 ALTER 会报错，但可安全忽略
-- 建议先执行 Part 5 的检查脚本，确认字段不存在后再执行

-- 新增：source_type（数据来源类型）
ALTER TABLE knowledge_base
ADD COLUMN source_type 
    ENUM('manual','qa_import','unanswered_resolved') 
    DEFAULT 'manual' COMMENT '数据来源';

-- 新增：source_question_id（来源的 unanswered_questions ID）
ALTER TABLE knowledge_base
ADD COLUMN source_question_id 
    INT COMMENT '来源的 unanswered_questions ID';

-- 新增：character_id（回答该问题的角色ID）
ALTER TABLE knowledge_base
ADD COLUMN character_id 
    INT COMMENT '回答该问题的角色ID';

-- 新增索引（如果已存在会报错，可忽略）
ALTER TABLE knowledge_base
ADD INDEX idx_kb_character (character_id);

ALTER TABLE knowledge_base
ADD INDEX idx_kb_source (source_type);

-- ============================================================
-- Part 4: 插入虚拟角色初始数据
-- ============================================================

-- 角色1：囚牛（默认角色，全库检索）
INSERT INTO virtual_characters (slug, name, title, staff_user_id, system_prompt, categories, confidence_threshold, sort_order, is_active)
VALUES (
    'qiuniu', 
    '囚牛', 
    '银行核心系统AI助手', 
    NULL,
    '你是银行核心系统智能助手「囚牛」，致力于帮助用户解答核心系统相关问题。回答时保持专业、准确、简洁，遇到不确定的问题会坦诚说明，不编造信息。',
    '',  -- 空=全库检索
    0.50,
    0,
    1
) ON DUPLICATE KEY UPDATE updated_at = NOW();

-- 角色2：唐佳艺（信创工作负责人）
INSERT INTO virtual_characters (slug, name, title, staff_user_id, system_prompt, categories, confidence_threshold, sort_order, is_active)
VALUES (
    'tangjiayi', 
    '唐佳艺', 
    '信创工作负责人', 
    NULL,
    '你是信创工作负责人「唐佳艺」，负责银行核心系统的信创改造工作。回答时以信创政策、国产化替代、迁移适配为主要视角，语言专业但亲和，遇到不确定的问题会坦诚说明。对于涉及国产芯片、国产数据库、国产操作系统等话题有深入见解。',
    '信创',
    0.55,
    1,
    1
) ON DUPLICATE KEY UPDATE updated_at = NOW();

-- 角色3：陈军（首席程序员）
INSERT INTO virtual_characters (slug, name, title, staff_user_id, system_prompt, categories, confidence_threshold, sort_order, is_active)
VALUES (
    'chenjun', 
    '陈军', 
    '首席程序员', 
    NULL,
    '你是首席程序员「陈军」，精通银行核心系统技术架构、代码实现和运维排障。回答时以技术深度见长，能给出具体的代码示例、架构建议和排障思路，语言简洁精准。对于涉及系统架构、数据库优化、性能调优等话题有丰富实战经验。',
    '技术',
    0.55,
    2,
    1
) ON DUPLICATE KEY UPDATE updated_at = NOW();

-- ============================================================
-- Part 5: 验证脚本
-- ============================================================

-- 执行后运行以下验证语句：
-- SHOW TABLES LIKE 'virtual_characters';
-- SHOW TABLES LIKE 'unanswered_questions';
-- SELECT * FROM virtual_characters;
-- DESCRIBE knowledge_base;

-- ============================================================
-- End of migration script
-- ============================================================