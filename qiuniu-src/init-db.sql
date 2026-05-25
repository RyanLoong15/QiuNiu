-- ===========================================
-- 囚牛 (QiuNiu) 数据库初始化脚本
-- ===========================================
-- 使用方法：
-- 1. 登录 MySQL: mysql -u root -p
-- 2. 执行：source D:\OpenclawCode\QiuNiu\init-db.sql
-- 或复制内容到 MySQL 客户端执行
-- ===========================================

-- 创建数据库
CREATE DATABASE IF NOT EXISTS qiuniu_db 
DEFAULT CHARACTER SET utf8mb4 
DEFAULT COLLATE utf8mb4_unicode_ci;

-- 使用数据库
USE qiuniu_db;

-- 创建用户表
CREATE TABLE IF NOT EXISTS users (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '用户 ID',
    username VARCHAR(50) UNIQUE NOT NULL COMMENT '用户名',
    password VARCHAR(255) NOT NULL COMMENT '密码 (BCrypt 加密)',
    email VARCHAR(100) COMMENT '邮箱',
    nickname VARCHAR(50) COMMENT '昵称',
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    last_login_time TIMESTAMP NULL COMMENT '最后登录时间',
    INDEX idx_username (username)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户表';

-- 创建提示词表
CREATE TABLE IF NOT EXISTS prompts (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '提示词 ID',
    name VARCHAR(100) NOT NULL COMMENT '提示词名称',
    content TEXT NOT NULL COMMENT '提示词内容',
    description VARCHAR(500) COMMENT '描述',
    category VARCHAR(50) COMMENT '分类',
    user_id BIGINT NOT NULL COMMENT '所属用户 ID',
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    INDEX idx_user_id (user_id),
    INDEX idx_category (category),
    INDEX idx_name (name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='提示词表';

-- 插入示例数据（可选）
-- INSERT INTO users (username, password, email, nickname) VALUES 
-- ('admin', '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', 'admin@example.com', '管理员');

-- 查询表结构
-- DESCRIBE users;
-- DESCRIBE prompts;

-- 查询数据
-- SELECT * FROM users;
-- SELECT * FROM prompts;

-- Git 差异缓存表（按团队+日期缓存）
CREATE TABLE IF NOT EXISTS git_diff_cache (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    team_id BIGINT NOT NULL COMMENT '团队 ID',
    base_branch VARCHAR(100) NOT NULL COMMENT '基准分支',
    compare_branch VARCHAR(100) NOT NULL COMMENT '对比分支',
    cache_date DATE NOT NULL COMMENT '缓存日期',
    cache_data TEXT NOT NULL COMMENT '缓存的差异数据（JSON）',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_team_branch_date (team_id, base_branch, compare_branch, cache_date),
    INDEX idx_team (team_id),
    INDEX idx_date (cache_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Git 差异缓存表';
