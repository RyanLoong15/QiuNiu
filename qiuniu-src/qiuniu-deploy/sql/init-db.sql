-- ===========================================
-- 囚牛 (QiuNiu) 数据库初始化脚本
-- ===========================================
-- 使用方法：
-- mysql -u root -p < init-db.sql
-- 或在 MySQL 客户端执行：source init-db.sql
-- ===========================================

-- 创建数据库
CREATE DATABASE IF NOT EXISTS qiuniu_db 
DEFAULT CHARACTER SET utf8mb4 
DEFAULT COLLATE utf8mb4_unicode_ci;

USE qiuniu_db;

-- 用户表
CREATE TABLE IF NOT EXISTS users (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    username VARCHAR(50) UNIQUE NOT NULL,
    password VARCHAR(255) NOT NULL,
    email VARCHAR(100),
    nickname VARCHAR(50),
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    last_login_time TIMESTAMP NULL,
    INDEX idx_username (username)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 提示词表
CREATE TABLE IF NOT EXISTS prompts (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    name VARCHAR(100) NOT NULL,
    content TEXT NOT NULL,
    description VARCHAR(500),
    category VARCHAR(50),
    user_id BIGINT NOT NULL,
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_user_id (user_id),
    INDEX idx_category (category)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Git 版本对比任务表
CREATE TABLE IF NOT EXISTS git_version_comparisons (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    version_name VARCHAR(50) NOT NULL,
    base_branch VARCHAR(100) NOT NULL DEFAULT 'master',
    compare_branch VARCHAR(100) NOT NULL,
    total_repos INT DEFAULT 0,
    success_count INT DEFAULT 0,
    fail_count INT DEFAULT 0,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    finished_at TIMESTAMP NULL,
    INDEX idx_user (user_id),
    INDEX idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Git 版本对比结果表
CREATE TABLE IF NOT EXISTS git_version_results (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    task_id BIGINT NOT NULL,
    project_id BIGINT NOT NULL,
    project_name VARCHAR(255) NOT NULL,
    team_id BIGINT NOT NULL DEFAULT 0,
    team_name VARCHAR(100),
    repo_url VARCHAR(500),
    has_changes TINYINT(1) DEFAULT 0,
    changed_files VARCHAR(50) DEFAULT '0',
    insertions VARCHAR(50) DEFAULT '0',
    deletions VARCHAR(50) DEFAULT '0',
    diff_summary TEXT,
    diff_detail TEXT,
    error TEXT,
    status VARCHAR(20) DEFAULT 'PENDING',
    ai_analysis TEXT,
    INDEX idx_task (task_id),
    INDEX idx_team (team_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Git 差异缓存表
CREATE TABLE IF NOT EXISTS git_diff_cache (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    team_id BIGINT NOT NULL,
    base_branch VARCHAR(100) NOT NULL,
    compare_branch VARCHAR(100) NOT NULL,
    cache_date DATE NOT NULL,
    cache_data TEXT NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_team_branch_date (team_id, base_branch, compare_branch, cache_date),
    INDEX idx_team (team_id),
    INDEX idx_date (cache_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Git 项目表
CREATE TABLE IF NOT EXISTS git_projects (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    team_id BIGINT NOT NULL,
    team_name VARCHAR(100),
    name VARCHAR(100) NOT NULL,
    repo_url VARCHAR(500) NOT NULL,
    base_branch VARCHAR(100) DEFAULT 'master',
    compare_branch VARCHAR(100),
    last_sync TIMESTAMP NULL,
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_user (user_id),
    INDEX idx_team (team_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Git 团队表
CREATE TABLE IF NOT EXISTS git_teams (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    name VARCHAR(100) NOT NULL,
    description VARCHAR(500),
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_user (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 提示词分析表
CREATE TABLE IF NOT EXISTS prompt_analysis (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    prompt_id BIGINT NOT NULL,
    analysis TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_user (user_id),
    INDEX idx_prompt (prompt_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 插入默认管理员用户（密码：admin123）
-- 密码使用 BCrypt 加密
INSERT INTO users (username, password, email, nickname) VALUES 
('admin', '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', 'admin@qiuniu.com', '管理员')
ON DUPLICATE KEY UPDATE username=username;

-- 插入示例团队
INSERT INTO git_teams (user_id, name, description) VALUES 
(1, '默认团队', '默认团队')
ON DUPLICATE KEY UPDATE name=name;

SELECT '数据库初始化完成！' AS message;
