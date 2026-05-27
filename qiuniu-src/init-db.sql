-- ===========================================
-- 囚牛 (QiuNiu) 数据库初始化脚本
-- 自动生成于: 2026-05-27
-- ===========================================
-- 使用方法：
--   mysql -u root -p < init-db.sql
-- 或在 MySQL 客户端执行：source init-db.sql
-- ===========================================

-- 创建数据库
CREATE DATABASE IF NOT EXISTS qiuniu_db
  DEFAULT CHARACTER SET utf8mb4
  DEFAULT COLLATE utf8mb4_unicode_ci;

USE qiuniu_db;

-- ===========================================
-- 删除已有表（按依赖逆序）
-- ===========================================

DROP TABLE IF EXISTS `virtual_characters`;
DROP TABLE IF EXISTS `users`;
DROP TABLE IF EXISTS `unanswered_questions`;
DROP TABLE IF EXISTS `prompts`;
DROP TABLE IF EXISTS `prompt_analysis`;
DROP TABLE IF EXISTS `knowledge_base`;
DROP TABLE IF EXISTS `git_version_results`;
DROP TABLE IF EXISTS `git_version_comparisons`;
DROP TABLE IF EXISTS `git_teams`;
DROP TABLE IF EXISTS `git_projects`;
DROP TABLE IF EXISTS `git_diff_cache`;
DROP TABLE IF EXISTS `employees`;
DROP TABLE IF EXISTS `document_imports`;
DROP TABLE IF EXISTS `document_chunks`;
DROP TABLE IF EXISTS `code_analysis_reports`;

-- ===========================================
-- 创建表（按依赖顺序）
-- ===========================================

-- 表: code_analysis_reports
CREATE TABLE IF NOT EXISTS `code_analysis_reports` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `project_id` bigint DEFAULT NULL,
  `project_name` varchar(255) DEFAULT NULL,
  `team_id` bigint DEFAULT NULL,
  `user_id` bigint NOT NULL,
  `repo_url` varchar(500) DEFAULT NULL,
  `branch` varchar(100) DEFAULT 'main',
  `status` varchar(20) DEFAULT 'PENDING',
  `total_files` int DEFAULT '0',
  `analyzed_files` int DEFAULT '0',
  `total_issues` int DEFAULT '0',
  `report` text,
  `rule_set` varchar(50) DEFAULT 'default',
  `created_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
  `finished_at` timestamp NULL DEFAULT NULL,
  `cost_ms` bigint DEFAULT '0',
  PRIMARY KEY (`id`),
  KEY `idx_project` (`project_id`),
  KEY `idx_user` (`user_id`),
  KEY `idx_status` (`status`)
) ENGINE=InnoDB AUTO_INCREMENT=5 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- 表: document_chunks
CREATE TABLE IF NOT EXISTS `document_chunks` (
  `id` int NOT NULL AUTO_INCREMENT,
  `import_id` int NOT NULL,
  `chunk_index` int DEFAULT '0',
  `content` text NOT NULL,
  `question` varchar(500) DEFAULT NULL,
  `answer` text,
  `slide_num` int DEFAULT NULL,
  `character_id` int DEFAULT NULL,
  `source_type` varchar(50) DEFAULT 'document',
  `document_name` varchar(500) DEFAULT NULL,
  `document_path` varchar(1000) DEFAULT NULL,
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_import` (`import_id`),
  KEY `idx_character` (`character_id`),
  CONSTRAINT `document_chunks_ibfk_1` FOREIGN KEY (`import_id`) REFERENCES `document_imports` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB AUTO_INCREMENT=51 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- 表: document_imports
CREATE TABLE IF NOT EXISTS `document_imports` (
  `id` int NOT NULL AUTO_INCREMENT,
  `filename` varchar(500) NOT NULL,
  `file_path` varchar(1000) NOT NULL,
  `file_size` bigint DEFAULT '0',
  `status` varchar(50) DEFAULT 'pending',
  `created_by` varchar(100) DEFAULT 'admin',
  `character_id` int DEFAULT NULL,
  `error_message` text,
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `progress` int DEFAULT '0',
  `total_chunks` int DEFAULT '0',
  PRIMARY KEY (`id`),
  KEY `idx_status` (`status`),
  KEY `idx_character` (`character_id`)
) ENGINE=InnoDB AUTO_INCREMENT=12 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- 表: employees
CREATE TABLE IF NOT EXISTS `employees` (
  `id` int NOT NULL AUTO_INCREMENT,
  `employee_no` varchar(20) NOT NULL COMMENT '工号，登录账号',
  `name` varchar(50) NOT NULL COMMENT '姓名',
  `phone` varchar(20) DEFAULT NULL COMMENT '手机号',
  `email` varchar(100) DEFAULT NULL COMMENT '邮箱',
  `password_hash` varchar(255) NOT NULL COMMENT 'bcrypt 密码哈希',
  `avatar_uploaded_path` varchar(255) DEFAULT NULL COMMENT '上传头像相对路径',
  `last_login_at` datetime DEFAULT NULL COMMENT '最后登录时间',
  `is_active` tinyint(1) DEFAULT '1' COMMENT '是否启用',
  `character_id` int DEFAULT NULL,
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `employee_no` (`employee_no`),
  KEY `idx_emp_no` (`employee_no`),
  KEY `idx_emp_active` (`is_active`)
) ENGINE=InnoDB AUTO_INCREMENT=5 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='员工表';

-- 表: git_diff_cache
CREATE TABLE IF NOT EXISTS `git_diff_cache` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `team_id` bigint NOT NULL,
  `base_branch` varchar(100) NOT NULL,
  `compare_branch` varchar(100) NOT NULL,
  `cache_date` date NOT NULL,
  `cache_data` text NOT NULL,
  `created_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_team_branch_date` (`team_id`,`base_branch`,`compare_branch`,`cache_date`),
  KEY `idx_team` (`team_id`),
  KEY `idx_date` (`cache_date`)
) ENGINE=InnoDB AUTO_INCREMENT=21 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- 表: git_projects
CREATE TABLE IF NOT EXISTS `git_projects` (
  `id` int NOT NULL AUTO_INCREMENT,
  `user_id` bigint NOT NULL,
  `team_id` bigint DEFAULT NULL,
  `name` varchar(255) NOT NULL,
  `repo_url` varchar(500) NOT NULL,
  `base_branch` varchar(100) DEFAULT 'main',
  `compare_branch` varchar(100) DEFAULT 'develop',
  `last_sync` datetime DEFAULT NULL,
  `created_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=9 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- 表: git_teams
CREATE TABLE IF NOT EXISTS `git_teams` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` bigint NOT NULL,
  `name` varchar(100) NOT NULL,
  `code` varchar(50) NOT NULL,
  `description` varchar(255) DEFAULT NULL,
  `created_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_code` (`code`)
) ENGINE=InnoDB AUTO_INCREMENT=7 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- 表: git_version_comparisons
CREATE TABLE IF NOT EXISTS `git_version_comparisons` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` bigint NOT NULL,
  `version_name` varchar(50) NOT NULL,
  `base_branch` varchar(100) NOT NULL DEFAULT 'master',
  `compare_branch` varchar(100) NOT NULL,
  `total_repos` int DEFAULT '0',
  `success_count` int DEFAULT '0',
  `fail_count` int DEFAULT '0',
  `status` varchar(20) NOT NULL DEFAULT 'PENDING',
  `created_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
  `finished_at` timestamp NULL DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_user` (`user_id`),
  KEY `idx_status` (`status`)
) ENGINE=InnoDB AUTO_INCREMENT=18 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- 表: git_version_results
CREATE TABLE IF NOT EXISTS `git_version_results` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `task_id` bigint NOT NULL,
  `project_id` bigint NOT NULL,
  `project_name` varchar(255) NOT NULL,
  `team_id` bigint NOT NULL DEFAULT '0',
  `team_name` varchar(100) DEFAULT NULL,
  `repo_url` varchar(500) DEFAULT NULL,
  `has_changes` tinyint(1) DEFAULT '0',
  `changed_files` varchar(50) DEFAULT '0',
  `insertions` varchar(50) DEFAULT '0',
  `deletions` varchar(50) DEFAULT '0',
  `diff_summary` text,
  `diff_detail` text,
  `error` text,
  `status` varchar(20) DEFAULT 'PENDING',
  `ai_analysis` text,
  PRIMARY KEY (`id`),
  KEY `idx_task` (`task_id`),
  KEY `idx_team` (`team_id`)
) ENGINE=InnoDB AUTO_INCREMENT=51 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- 表: knowledge_base
CREATE TABLE IF NOT EXISTS `knowledge_base` (
  `id` int NOT NULL AUTO_INCREMENT,
  `question` text NOT NULL,
  `answer` text NOT NULL,
  `category` varchar(256) DEFAULT '未分类',
  `created_by` varchar(100) DEFAULT NULL,
  `enabled` tinyint DEFAULT '1',
  `created_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `source_type` enum('manual','qa_import','unanswered_resolved','document') DEFAULT 'manual',
  `source_question_id` int DEFAULT NULL,
  `character_id` int DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_kb_character` (`character_id`),
  KEY `idx_kb_source` (`source_type`)
) ENGINE=InnoDB AUTO_INCREMENT=22 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- 表: prompt_analysis
CREATE TABLE IF NOT EXISTS `prompt_analysis` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `file_type` varchar(50) NOT NULL,
  `display_name` varchar(100) DEFAULT NULL,
  `system_prompt` text NOT NULL,
  `user_prompt_template` text NOT NULL,
  `max_diff_lines` int DEFAULT '300',
  `priority` int DEFAULT '100',
  `created_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_file_type` (`file_type`)
) ENGINE=InnoDB AUTO_INCREMENT=11 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- 表: prompts
CREATE TABLE IF NOT EXISTS `prompts` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `name` varchar(100) NOT NULL,
  `content` text NOT NULL,
  `description` varchar(500) DEFAULT NULL,
  `category` varchar(50) DEFAULT NULL,
  `user_id` bigint NOT NULL,
  `create_time` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` timestamp NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_user_id` (`user_id`),
  KEY `idx_category` (`category`)
) ENGINE=InnoDB AUTO_INCREMENT=5 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- 表: unanswered_questions
CREATE TABLE IF NOT EXISTS `unanswered_questions` (
  `id` int NOT NULL AUTO_INCREMENT,
  `character_id` int NOT NULL COMMENT '关联虚拟角色ID',
  `question` text NOT NULL COMMENT '用户提问原文',
  `session_id` varchar(100) DEFAULT NULL COMMENT '会话ID，便于追踪对话上下文',
  `score` decimal(5,4) DEFAULT NULL COMMENT '检索最高置信度分数（触发记录时的值）',
  `status` enum('pending','answered','ignored','auto_resolved') DEFAULT 'pending' COMMENT '处理状态',
  `answer` text COMMENT '真人员工回答',
  `answered_by` varchar(50) DEFAULT NULL COMMENT '回答者姓名',
  `answered_at` datetime DEFAULT NULL COMMENT '回答时间',
  `ignored_by` varchar(50) DEFAULT NULL COMMENT '忽略操作人',
  `ignored_at` datetime DEFAULT NULL,
  `kb_entry_id` int DEFAULT NULL COMMENT '回答后写入 knowledge_base 的记录ID',
  `notification_sent` tinyint(1) DEFAULT '0' COMMENT '是否已推送通知',
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
  `is_read` tinyint(1) DEFAULT '0' COMMENT '员工是否已读',
  `read_at` datetime DEFAULT NULL COMMENT '已读时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `idx_uq_unique_pending` (`character_id`,`question`(255),`status`),
  KEY `idx_uq_status` (`status`),
  KEY `idx_uq_character_status` (`character_id`,`status`),
  KEY `idx_uq_created` (`created_at`),
  KEY `idx_uq_read` (`character_id`,`is_read`),
  CONSTRAINT `unanswered_questions_ibfk_1` FOREIGN KEY (`character_id`) REFERENCES `virtual_characters` (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=15 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='未答问题记录表';

-- 表: users
CREATE TABLE IF NOT EXISTS `users` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `username` varchar(50) NOT NULL,
  `password` varchar(255) NOT NULL,
  `email` varchar(100) DEFAULT NULL,
  `nickname` varchar(50) DEFAULT NULL,
  `create_time` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
  `last_login_time` timestamp NULL DEFAULT NULL,
  `role` varchar(20) DEFAULT 'VIEWER',
  PRIMARY KEY (`id`),
  UNIQUE KEY `username` (`username`)
) ENGINE=InnoDB AUTO_INCREMENT=22 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- 表: virtual_characters
CREATE TABLE IF NOT EXISTS `virtual_characters` (
  `id` int NOT NULL AUTO_INCREMENT,
  `slug` varchar(50) NOT NULL COMMENT 'URL/文件路径标识，如 tangjiayi',
  `name` varchar(50) NOT NULL COMMENT '角色显示名',
  `title` varchar(100) DEFAULT NULL COMMENT '岗位/角色描述',
  `description` text,
  `avatar` varchar(255) DEFAULT NULL,
  `staff_user_id` int DEFAULT NULL COMMENT '绑定的真人员工ID（关联现有staff_users表）',
  `system_prompt` text NOT NULL COMMENT '角色人设 System Prompt',
  `avatar_style` varchar(50) DEFAULT 'cartoon' COMMENT '头像风格：cartoon/watercolor/anime',
  `categories` varchar(500) DEFAULT '' COMMENT '绑定的知识分类，逗号分隔，空=全库',
  `confidence_threshold` decimal(3,2) DEFAULT '0.50' COMMENT '该角色的置信度阈值',
  `is_active` tinyint(1) DEFAULT '1' COMMENT '是否启用',
  `sort_order` int DEFAULT '0' COMMENT '排序权重，数字越小越靠前',
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `employee_id` int DEFAULT NULL COMMENT '绑定的真人员工ID',
  `avatar_status` enum('none','uploaded','generated') DEFAULT 'none' COMMENT '头像状态',
  `last_avatar_generate_at` datetime DEFAULT NULL COMMENT '最近一次生成卡通头像的时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `slug` (`slug`),
  KEY `idx_vc_active_sort` (`is_active`,`sort_order`),
  KEY `idx_vc_staff` (`staff_user_id`),
  KEY `fk_vc_employee` (`employee_id`),
  CONSTRAINT `fk_vc_employee` FOREIGN KEY (`employee_id`) REFERENCES `employees` (`id`) ON DELETE SET NULL
) ENGINE=InnoDB AUTO_INCREMENT=4 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='虚拟角色表';

-- ===========================================
-- 默认数据
-- ===========================================

-- 默认管理员（用户名: admin / 密码: admin123）
INSERT INTO users (username, password, email, nickname) VALUES
  ('admin', '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', 'admin@qiuniu.com', '管理员')
  ON DUPLICATE KEY UPDATE username=username;

SELECT "数据库初始化完成！" AS message;
