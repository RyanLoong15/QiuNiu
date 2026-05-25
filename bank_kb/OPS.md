# BankKB RAG 知识库运维手册

## 1. 日常运维

### 启动顺序
```
1. MySQL（net start MySQL 或自动启动）
2. bank_kb Flask（python app.py 或开机自启）
3. Tomcat（startup.bat 或 Windows 服务）
```

### 停止顺序
```
1. Tomcat（shutdown.bat）
2. bank_kb Flask（Ctrl+C 或任务管理器结束 python.exe）
3. MySQL（net stop MySQL）
```

## 2. 日志位置

| 服务 | 日志路径 |
|------|---------|
| Tomcat | C:\apache-tomcat-9.0.96\logs\catalina.*.log |
| Tomcat 访问 | C:\apache-tomcat-9.0.96\logs\localhost_access_log.*.txt |
| Flask stdout | D:\qiuniu_install\bank_kb\logs\stdout.log |
| Flask stderr | D:\qiuniu_install\bank_kb\logs\stderr.log |

## 3. 常见故障排查

| 故障 | 症状 | 排查步骤 |
|------|------|----------|
| knowledge-base.jsp 404 | 页面无法访问 | 1. 检查 Tomcat 是否运行 2. 检查是否先登录 3. 检查 WAR 是否覆盖了展开目录 |
| 虚拟人无回复 | 发消息后无响应 | 1. 检查 Flask 5001 是否运行 2. 检查 SiliconFlow API 余额 3. 查看 Flask 日志 |
| RAG 检索无结果 | 回复"未找到相关信息" | 1. 检查 knowledge_base 表有数据 2. 调用 /reload 重建索引 3. 检查 SIMILARITY_THRESHOLD |
| MySQL 连接失败 | Flask 启动报错 | 1. net start MySQL 2. 检查密码 3. 检查端口 3306 |
| Tomcat WAR 覆盖 | 重启后文件丢失 | 确保 webapps 下没有 .war 文件（只有 .war.bak） |
| JSP EL 报错 | Function not found | 检查 JS 模板 literals 中 ${} 是否已转义为 ${'$'}{ |

## 4. 备份策略

```powershell
# 创建备份目录
New-Item -ItemType Directory -Path D:\qiuniu_install\backup -Force

# 数据库备份（每日）
$Date = Get-Date -Format 'yyyyMMdd'
mysqldump -u root -pNewPassword123! qiuniu_db > "D:\qiuniu_install\backup\qiuniu_db_$Date.sql"

# 应用备份（修改前）
Copy-Item -Recurse C:\apache-tomcat-9.0.96\webapps\qiuniu "D:\qiuniu_install\backup\qiuniu_$Date"
```

## 5. 知识库维护

```powershell
# 导入文档
cd D:\qiuniu_install\bank_kb
python ingest.py D:\path\to\documents

# 重建索引（新增/修改知识条目后）
Invoke-RestMethod -Uri "http://localhost:5001/reload" -Method POST

# 查看索引状态
Invoke-RestMethod -Uri "http://localhost:5001/status"

# 测试检索
$body = @{query="账户冻结"; top_k=3} | ConvertTo-Json
Invoke-RestMethod -Uri "http://localhost:5001/query" -Method POST -Body $body -ContentType "application/json"
```

## 6. 安全注意事项

- **API Key**：SiliconFlow API Key 存储在 config.py 明文中，生产环境应使用环境变量
- **MySQL 密码**：root 密码需定期更换
- **Tomcat 管理接口**：/manager 应限制 IP 访问
- **AuthFilter 白名单**：定期审查配置
- **降级模式**：LLM 不可用时自动返回检索原文，无安全过滤

## 7. 性能基准

| 指标 | 目标值 | 测量方式 |
|------|--------|---------|
| RAG 端到端延迟 | < 10s | /query 接口 latency_ms |
| 检索延迟 | < 500ms | retriever 模块日志 |
| LLM 生成延迟 | < 8s | generator 模块日志（当前降级模式无 LLM） |
| Tomcat 页面加载 | < 2s | 浏览器 DevTools |

## 8. 当前已知限制

1. **SiliconFlow API 余额不足** — LLM 生成走降级模式，直接返回检索原文
2. **GitHub 443 阻断** — 批量投产比对功能不可用
3. **降级模式无安全拒答** — 需要 LLM 才能过滤敏感问题

## 9. 快速诊断脚本

```powershell
# 保存为 diagnose.ps1 运行
Write-Host "=== BankKB 诊断 ===" -ForegroundColor Cyan

# MySQL
Write-Host "`n[MySQL]" -ForegroundColor Yellow
try {
    $mysql = python -c "import mysql.connector; c=mysql.connector.connect(host='localhost',port=3306,user='root',password='NewPassword123!',database='qiuniu_db'); cursor=c.cursor(); cursor.execute('SELECT COUNT(*) FROM knowledge_base'); print('Entries:', cursor.fetchone()[0]); c.close()"
    Write-Host $mysql
} catch { Write-Host "FAILED: $_" -ForegroundColor Red }

# Flask
Write-Host "`n[Flask RAG]" -ForegroundColor Yellow
try {
    $health = Invoke-RestMethod -Uri "http://localhost:5001/health" -TimeoutSec 5
    $status = Invoke-RestMethod -Uri "http://localhost:5001/status" -TimeoutSec 5
    Write-Host "Health: $health"
    Write-Host "Indexed: $($status.indexed_count) entries"
} catch { Write-Host "FAILED: $_" -ForegroundColor Red }

# Tomcat
Write-Host "`n[Tomcat]" -ForegroundColor Yellow
try {
    $r = Invoke-WebRequest -Uri "http://localhost:8080/qiuniu/" -TimeoutSec 5 -UseBasicParsing
    Write-Host "Status: $($r.StatusCode) OK"
} catch { Write-Host "FAILED: $_" -ForegroundColor Red }

Write-Host "`n=== 诊断完成 ===" -ForegroundColor Cyan
```
