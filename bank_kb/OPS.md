# BankKB RAG 知识库运维手册

> 版本：2026-05-27  
> 适用对象：系统运维人员

---

## 目录

1. [服务启停](#服务启停)
2. [日志位置](#日志位置)
3. [日常维护](#日常维护)
4. [常见故障排查](#常见故障排查)
5. [备份策略](#备份策略)
6. [性能监控](#性能监控)
7. [快速诊断脚本](#快速诊断脚本)

---

## 服务启停

### 启动顺序

```
1. MySQL（系统服务，通常自动启动）
2. Flask RAG 服务（端口 5001）
3. Tomcat（端口 8080）
```

### 停止顺序

```
1. Tomcat
2. Flask RAG 服务
3. MySQL（可选，通常保持运行）
```

### 启动 Flask（手动，开发/调试）

```powershell
cd D:\qiuniu20260506\bank_kb
python app.py
```

正常输出示例：
```
 * Running on http://0.0.0.0:5001
 [App] Using HybridSearchEngine (mode=bm25)
 [DB] Loaded 42 knowledge entries.
 [App] Index ready: 42 entries (mode=bm25)
```

### 启动 Flask（生产，waitress）

```powershell
pip install waitress
cd D:\qiuniu20260506\bank_kb
waitress-serve --host=0.0.0.0 --port=5001 app:app
```

### 停止 Flask

- **手动启动**：在 Flask 运行窗口按 `Ctrl+C`
- **后台运行**：在任务管理器中结束 `python.exe` 进程
- **waitress**：按 `Ctrl+C` 或结束进程

### 重启 Flask（推荐流程）

使用专用重启脚本（自动处理端口释放和缓存清理）：

```powershell
# 执行重启脚本
D:\Loong工作专区\restart-flask.ps1
```

脚本自动完成：
1. 查找占用 5001 端口的进程
2. 终止该进程
3. 清理 `bank_kb/__pycache__/` 缓存
4. 等待端口释放（最多 10 秒）
5. 启动新的 Flask 进程

### Tomcat 启停

```powershell
# 启动
C:\apache-tomcat-9.0.96\bin\startup.bat

# 停止
C:\apache-tomcat-9.0.96\bin\shutdown.bat

# 强制停止（如 shutdown 无响应）
Stop-Process -Name java -Force
```

---

## 日志位置

| 服务 | 日志路径 | 说明 |
|------|----------|------|
| Tomcat（主日志） | `C:\apache-tomcat-9.0.96\logs\catalina.*.log` | JVM 输出、应用异常 |
| Tomcat（访问日志） | `C:\apache-tomcat-9.0.96\logs\localhost_access_log.*.txt` | HTTP 访问记录 |
| Flask（stdout） | 运行窗口直接输出 | 实时日志，无文件持久化 |
| Flask（waitress） | 同上 | 建议使用 systemd / nssm 托管以捕获输出 |
| MySQL（错误日志） | `C:\ProgramData\MySQL\MySQL Server 9.6\Data\*.err` | MySQL 启动/运行错误 |

### 实时查看 Tomcat 日志

```powershell
Get-Content "C:\apache-tomcat-9.0.96\logs\catalina.$(Get-Date -Format 'yyyy-MM-dd').log" -Tail 50 -Wait
```

### 查看 Flask 日志（运行窗口）

Flask 日志直接输出到启动它的控制台窗口，建议用 `Start-Transcript` 或 `waitress` + 日志文件重定向：

```powershell
# 用 waitress 并将日志重定向到文件
cd D:\qiuniu20260506\bank_kb
waitress-serve --host=0.0.0.0 --port=5001 app:app > logs\flask.log 2>&1
```

---

## 日常维护

### 知识库条目管理

#### 新增 / 修改知识点后重建索引

通过管理员界面录入知识点后，索引会自动重建（2 秒延迟）。  
如需手动触发：

```powershell
Invoke-RestMethod -Uri "http://localhost:5001/reload" -Method POST
```

响应示例：
```json
{"status": "reloaded", "count": 45}
```

#### 查看当前索引状态

```powershell
Invoke-RestMethod -Uri "http://localhost:5001/status"
```

响应示例：
```json
{
  "status": "ok",
  "indexed_count": 45,
  "mode": "bm25",
  "llm_model": "Qwen/Qwen2.5-72B-Instruct"
}
```

#### 测试检索效果

```powershell
$body = @{query="怎么开卡"; top_k=3} | ConvertTo-Json
Invoke-RestMethod -Uri "http://localhost:5001/query" `
    -Method POST `
    -Body $body `
    -ContentType "application/json"
```

### 员工管理

#### 注册新员工（PowerShell）

```powershell
# 生成 bcrypt 密码哈希
$hash = python -c "import bcrypt; print(bcrypt.hashpw('123456'.encode(), bcrypt.gensalt()).decode())"

# 写入数据库
mysql -u root -pNewPassword123! qiuniu_db -e "
INSERT INTO employees (employee_no, name, password_hash, is_active)
VALUES ('EMP005', '张三', '$hash', 1);
"
```

#### 重置员工密码

```powershell
# 生成新密码哈希
$newHash = python -c "import bcrypt; print(bcrypt.hashpw('123456'.encode(), bcrypt.gensalt()).decode())"

# 更新数据库
mysql -u root -pNewPassword123! qiuniu_db -e "
UPDATE employees SET password_hash = '$newHash', updated_at = NOW()
WHERE employee_no = 'EMP001';
"
```

> ⚠️ **注意**：MySQL 命令行中 `$` 符号会被当作变量引用，建议使用 Python 脚本直接写入（见 `db_loader.py` 的 `register_employee` 函数）。

### 虚拟角色管理

```sql
-- 查看所有角色
SELECT id, name, title, is_active, confidence_threshold
FROM virtual_characters
ORDER BY sort_order;

-- 启用/禁用角色
UPDATE virtual_characters SET is_active = 0 WHERE id = 3;  -- 禁用
UPDATE virtual_characters SET is_active = 1 WHERE id = 3;  -- 启用

-- 关联员工（该角色收到的问题推送给此员工）
UPDATE virtual_characters SET employee_id = 3 WHERE id = 2;
```

---

## 常见故障排查

| 故障现象 | 可能原因 | 排查步骤 |
|----------|----------|----------|
| `knowledge-base.jsp` 404 | Tomcat 未运行或路径错误 | 1. 检查 Tomcat 是否运行（`netstat -ano \| findstr 8080`）<br>2. 确认已登录（未登录会跳转到登录页） |
| 点击发送后 AI 无响应 | Flask 服务未运行 | 1. 浏览器 F12 查看 Console 报错<br>2. `curl http://localhost:5001/status`<br>3. 查看 Flask 运行窗口日志 |
| AI 回答「系统暂不可用」 | LLM API 调用失败 | 1. 检查 `config.py` 中 `LLM_API_KEY` 是否正确<br>2. 检查硅基流动账号是否有余额<br>3. 查看 Flask 日志中的具体报错 |
| 检索无结果（返回默认回复） | 知识库为空或阈值过高 | 1. 检查 `knowledge_base` 表是否有 `enabled=1` 的数据<br>2. 调用 `/reload` 重建索引<br>3. 检查 `CONFIDENCE_THRESHOLD` 配置 |
| 「向本人提问」提交后员工看不到 | `employee_id` 未关联 | 检查 `virtual_characters.employee_id` 是否指向正确的员工 ID |
| 头像上传后不显示 | 路径问题或浏览器缓存 | 1. 检查 `employees.avatar_uploaded_path` 字段是否有值<br>2. 检查 `C:\apache-tomcat-9.0.96\webapps\qiuniu\static\avatars\` 是否有文件<br>3. 清除浏览器缓存 |
| Flask 启动报 `SyntaxError` | 文件编码问题 | 1. 确认文件为 UTF-8 无 BOM<br>2. 使用 `edit` 工具修改后可能出现编码损坏，建议用 IDE 修改 |
| Tomcat 重启后修改丢失 | WAR 包自动解压覆盖 | 确保 `webapps/` 下没有 `qiuniu.war`（改为 `.war.bak`） |

### Flask 启动失败速查

```powershell
# 1. 检查端口占用
netstat -ano | findstr 5001
# 如果占用，结束进程：
Stop-Process -Id <PID> -Force

# 2. 检查 Python 依赖
cd D:\qiuniu20260506\bank_kb
python -c "import flask, mysql.connector, bcrypt, requests; print('依赖正常')"

# 3. 检查配置文件
python -c "from config import *; print('配置加载正常')"

# 4. 单独检查语法错误
python -m py_compile app.py
```

---

## 备份策略

### 数据库备份（每日）

```powershell
# 创建备份目录
New-Item -ItemType Directory -Path D:\qiuniu_backup -Force

# 每日备份脚本（可加入 Windows 任务计划）
$Date = Get-Date -Format 'yyyyMMdd'
mysqldump -u root -pNewPassword123! qiuniu_db | Out-File "D:\qiuniu_backup\qiuniu_db_$Date.sql" -Encoding UTF8
```

### 应用文件备份（修改前）

```powershell
$Date = Get-Date -Format 'yyyyMMdd_HHmmss'

# 备份 Tomcat 应用目录
Compress-Archive `
  -Path "C:\apache-tomcat-9.0.96\webapps\qiuniu" `
  -DestinationPath "D:\qiuniu_backup\qiuniu_tomcat_$Date.zip"

# 备份 Flask 目录
Compress-Archive `
  -Path "D:\qiuniu20260506\bank_kb" `
  -DestinationPath "D:\qiuniu_backup\qiuniu_flask_$Date.zip"
```

### 知识库文档备份

```powershell
# 备份上传的知识库原始文档
Compress-Archive `
  -Path "D:\qiuniu20260506\bank_kb\data" `
  -DestinationPath "D:\qiuniu_backup\qiuniu_docs_$Date.zip"
```

---

## 性能监控

### 关键指标

| 指标 | 目标值 | 测量方式 |
|------|--------|----------|
| RAG 端到端延迟 | < 10s | `/query` 接口响应时间 |
| 检索延迟 | < 500ms | `hybrid_search.py` 日志 |
| LLM 生成延迟 | < 8s | `generator.py` 日志（如无 LLM 则走降级模式） |
| Tomcat 页面加载 | < 2s | 浏览器 DevTools Network 面板 |
| 内存使用（Flask） | < 500MB | 任务管理器 / `psutil` |
| 内存使用（Tomcat） | < 1GB | `jstat` / VisualVM |

### 查看 Flask 进程资源占用

```powershell
Get-Process python | Select-Object Name, Id, @{Name="Memory(MB)"; Expression={$_.WorkingSet / 1MB -as [int]}}
```

### 查看 Tomcat JVM 内存

```powershell
# 查找 Tomcat 进程 ID
Get-Process java | Select-Object Id, ProcessName

# 使用 jstat（JDK 自带）
jstat -gc <PID>
```

---

## 快速诊断脚本

将以下内容保存为 `D:\Loong工作专区\diagnose.ps1`：

```powershell
Write-Host "=== 囚牛系统诊断 ===" -ForegroundColor Cyan

# ── MySQL ────────────────────────────────
Write-Host "`n[MySQL]" -ForegroundColor Yellow
try {
    $result = python -c "
import mysql.connector
c = mysql.connector.connect(host='localhost', port=3306, user='root', password='NewPassword123!', database='qiuniu_db')
cursor = c.cursor()
cursor.execute('SELECT COUNT(*) FROM knowledge_base WHERE enabled=1')
print('MySQL: OK（知识库条目:', cursor.fetchone()[0], '）')
c.close()
"
    Write-Host $result -ForegroundColor Green
} catch {
    Write-Host "MySQL: FAILED" -ForegroundColor Red
    Write-Host $_.Exception.Message -ForegroundColor DarkRed
}

# ── Flask RAG ───────────────────────────
Write-Host "`n[Flask RAG]" -ForegroundColor Yellow
try {
    $health = Invoke-RestMethod -Uri "http://localhost:5001/status" -TimeoutSec 5
    Write-Host "Flask: OK（已索引 $($health.indexed_count) 条，模式：$($health.mode)）" -ForegroundColor Green
} catch {
    Write-Host "Flask: FAILED（未运行或端口不通）" -ForegroundColor Red
}

# ── Tomcat ──────────────────────────────
Write-Host "`n[Tomcat]" -ForegroundColor Yellow
try {
    $r = Invoke-WebRequest -Uri "http://localhost:8080/qiuniu/" -TimeoutSec 5 -UseBasicParsing
    Write-Host "Tomcat: OK（HTTP $($r.StatusCode)）" -ForegroundColor Green
} catch {
    Write-Host "Tomcat: FAILED（未运行或端口不通）" -ForegroundColor Red
}

# ── 端口占用 ────────────────────────────
Write-Host "`n[端口占用]" -ForegroundColor Yellow
$ports = netstat -ano | Select-String "8080|5001|3306"
if ($ports) {
    $ports | ForEach-Object { Write-Host $_.ToString().Trim() }
} else {
    Write-Host "未检测到关键端口监听" -ForegroundColor Red
}

# ── 磁盘空间 ────────────────────────────
Write-Host "`n[磁盘空间]" -ForegroundColor Yellow
Get-PSDrive C, D | Select-Object Name, Used, Free | Format-Table -AutoSize

Write-Host "`n=== 诊断完成 ===" -ForegroundColor Cyan
```

运行：
```powershell
powershell -ExecutionPolicy Bypass -File D:\Loong工作专区\diagnose.ps1
```

---

## 附录：配置文件速查

| 文件 | 路径 | 说明 |
|------|------|------|
| Flask 配置 | `D:\qiuniu20260506\bank_kb\config.py` | LLM API Key、检索模式、阈值 |
| Tomcat 数据库配置 | `D:\qiuniu20260506\qiuniu-src\src\main\resources\db.properties` | MySQL 连接参数 |
| Tomcat server.xml | `C:\apache-tomcat-9.0.96\conf\server.xml` | 端口、连接器配置 |
| MySQL 配置 | `C:\ProgramData\MySQL\MySQL Server 9.6\my.ini` | MySQL 服务器参数 |

---

*本手册基于 2026-05-27 版本编写，实际操作以部署版本为准。*

**BankKB RAG 运维手册 v2.0 | 2026-05-27**
