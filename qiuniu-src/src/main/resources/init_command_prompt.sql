-- =====================
-- MCP命令表
-- =====================
CREATE TABLE IF NOT EXISTS mcp_command (
    command_id   VARCHAR(10)  NOT NULL PRIMARY KEY COMMENT '命令编号',
    command      VARCHAR(1999) NOT NULL COMMENT '命令内容',
    description  TEXT COMMENT '命令描述'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- =====================
-- MCP提示词表
-- =====================
CREATE TABLE IF NOT EXISTS mcp_prompt (
    prompt_id    VARCHAR(10)  NOT NULL PRIMARY KEY COMMENT '提示词编号',
    content      TEXT NOT NULL COMMENT '提示词内容'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- =====================
-- 种子数据（可按需调整）
-- =====================
INSERT INTO mcp_command (command_id, command, description) VALUES
('CMD001', 'cat /proc/meminfo | grep MemTotal', '查看服务器内存总量'),
('CMD002', 'df -h', '查看磁盘使用情况'),
('CMD003', 'ps aux | grep java', '查看Java进程状态'),
('CMD004', 'netstat -tlnp | grep 8080', '检查Tomcat端口监听状态'),
('CMD005', 'tail -100 /var/log/app.log', '查看应用最近100行日志')
ON DUPLICATE KEY UPDATE command=VALUES(command), description=VALUES(description);

INSERT INTO mcp_prompt (prompt_id, content) VALUES
('P001', '你是一个银行核心系统运维助手。请根据用户输入的命令和目标环境，执行相应的系统查询操作，并返回结构化的结果。'),
('P002', '请分析以下系统指标，判断是否存在异常，并给出简要的风险评估。'),
('P003', '你是金融业务系统的问题诊断专家。请根据提供的日志片段，定位可能的故障原因。')
ON DUPLICATE KEY UPDATE content=VALUES(content);
