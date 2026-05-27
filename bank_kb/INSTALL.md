# BankKB RAG 知识库安装手册（Windows）

Flask RAG 服务是囚牛系统的 AI 问答引擎，运行在 Tomcat 之外（独立端口 5001）。

---

## 1. 系统要求

| 组件 | 版本 | 说明 |
|------|------|------|
| Windows | 10 / 11 x64 | 操作系统 |
| Python | 3.10+ | Flask 运行环境 |
| MySQL | 8.0+ | 共享 Tomcat 使用的 qiuniu_db 数据库 |
| Tomcat | 9.0.x | 已部署 QiuNiu 主应用 |
| JDK | 17 | Tomcat 运行依赖 |

磁盘空间 ≥ 2GB

---

## 2. MySQL 准备

Flask 服务与主应用**共用同一个数据库** `qiuniu_db`，无需单独建库。

```powershell
# 确认数据库已存在
mysql -u root -pNewPassword123! -e "USE qiuniu_db; SHOW TABLES;"

# 如未初始化，先执行（只需执行一次）：
mysql -u root -pNewPassword123! < "D:\qiuniu20260506\qiuniu-src\init-db.sql"
```

**默认管理员账号：** `admin` / `admin123`  
**默认员工工号：** `EMP001`（密码 `123456`）

---

## 3. 安装 Python 依赖

```powershell
cd D:\qiuniu20260506\bank_kb

# 使用国内镜像加速
pip install -r requirements.txt -i https://mirrors.aliyun.com/pypi/simple/
```

**requirements.txt 包含：**
```
flask
flask-cors
mysql-connector-python
bcrypt
requests
jieba
numpy
```

---

## 4. 配置 config.py

编辑 `D:\qiuniu20260506\bank_kb\config.py`：

```python
# ── MySQL 连接（与主应用共用同一个数据库）──
MYSQL_HOST = 'localhost'
MYSQL_PORT = 3306
MYSQL_USER = 'root'
MYSQL_PASSWORD = 'NewPassword123!'
MYSQL_DATABASE = 'qiuniu_db'

# ── AI 大模型（硅基流动 API）──
LLM_API_URL = "https://api.siliconflow.cn/v1/chat/completions"
LLM_API_KEY = "sk-xxx"          # ← 填写你的 API Key
LLM_MODEL = "Qwen/Qwen2.5-72B-Instruct"

# ── 检索配置 ──
RETRIEVAL_MODE = "bm25"   # bm25 / vector / hybrid
                          #   bm25：纯关键词，无需额外依赖（当前推荐）
                          #   vector：需 Ollama + Milvus（当前网络不通，暂不可用）
                          #   hybrid：混合模式（需 vector 先可用）
TOP_K = 5                   # 每次检索返回最大片段数
CONFIDENCE_THRESHOLD = 0.2  # 低于此分数触发「向本人提问」

# ── Tomcat 部署路径（头像上传用）──
TOMCAT_CONTEXT = "/qiuniu"
WEBAPP_ROOT = "C:/apache-tomcat-9.0.96/webapps/qiuniu"
# 头像将上传到：WEBAPP_ROOT/static/avatars/
```

> ⚠️ **LLM_API_KEY 必须填写**，否则 AI 回答功能不可用（会走降级模式，直接返回检索原文）。

---

## 5. 启动 Flask 服务（测试）

```powershell
cd D:\qiuniu20260506\bank_kb
python app.py
```

正常启动输出示例：
```
 * Running on http://0.0.0.0:5001
[App] Using HybridSearchEngine (mode=bm25)
[DB] Loaded 42 knowledge entries.
[App] Index ready: 42 entries (mode=bm25)
```

**验证服务健康：**
```powershell
Invoke-RestMethod -Uri "http://localhost:5001/status" -TimeoutSec 5
# 预期输出：{"status":"ok","indexed_count":42,"mode":"bm25"}
```

---

## 6. 开机自启（生产环境）

推荐使用 `startup.bat` 放入 Windows 启动文件夹：

**创建启动脚本** `D:\qiuniu20260506\bank_kb\start_flask.bat`：
```batch
@echo off
cd /d D:\qiuniu20260506\bank_kb
start "BankKB-Flask" /min python app.py
```

**放入启动文件夹：**
```powershell
# 当前用户自启
Copy-Item "D:\qiuniu20260506\bank_kb\start_flask.bat" `
  -Destination "C:\Users\botao\AppData\Roaming\Microsoft\Windows\Start Menu\Programs\Startup\"
```

> 💡 **生产环境推荐**：使用 `waitress-serve` 替代 `app.py` 直接运行：
> ```bash
> pip install waitress
> waitress-serve --host=0.0.0.0 --port=5001 app:app
> ```

---

## 7. 验证完整安装

```powershell
# 检查所有服务
Write-Host "=== 服务状态检查 ==="

# 1. MySQL
try {
    python -c "
import mysql.connector
c = mysql.connector.connect(host='localhost',port=3306,user='root',password='NewPassword123!',database='qiuniu_db')
print('MySQL: OK')
c.close()
"
} catch { Write-Host "MySQL: FAILED" -ForegroundColor Red }

# 2. Flask RAG
try {
    $r = Invoke-RestMethod -Uri "http://localhost:5001/status" -TimeoutSec 5
    Write-Host "Flask RAG: OK (indexed: $($r.indexed_count), mode: $($r.mode))" -ForegroundColor Green
} catch { Write-Host "Flask RAG: FAILED" -ForegroundColor Red }

# 3. Tomcat
try {
    $r = Invoke-WebRequest -Uri "http://localhost:8080/qiuniu/" -TimeoutSec 5 -UseBasicParsing
    Write-Host "Tomcat: OK (status $($r.StatusCode))" -ForegroundColor Green
} catch { Write-Host "Tomcat: FAILED" -ForegroundColor Red }
```

---

## 8. API 接口概览

Flask 服务提供以下 API（Tomcat 应用通过 AJAX 调用）：

| 路径 | 方法 | 说明 |
|------|------|------|
| `/status` | GET | 服务健康状态 |
| `/query` | POST | 单轮问答（RAG 检索 + LLM 生成） |
| `/api/chat` | POST | 多轮对话（支持上下文） |
| `/api/characters` | GET | 获取所有启用角色 |
| `/api/characters/<id>` | GET | 获取单个角色详情 |
| `/api/unanswered/submit` | POST | 提交「向本人提问」问题 |
| `/api/unanswered/stats` | GET | 未答问题统计 |
| `/api/employee/questions` | GET | 员工查看待回答问题 |
| `/api/employee/questions/<id>/answer` | POST | 员工回答问题（自动录入知识库） |
| `/api/employee/questions/<id>/read` | POST | 标记问题为已读 |
| `/api/employee/profile` | GET/POST | 员工个人信息查询/修改 |
| `/api/employee/avatar/upload` | POST | 员工上传头像 |

---

## 9. 常见问题

### Q：启动报 `ModuleNotFoundError: No module named 'flask'`？
A：Python 环境未安装依赖，执行：
```powershell
cd D:\qiuniu20260506\bank_kb
pip install -r requirements.txt
```

### Q：启动后访问 `/status` 返回 404？
A：Flask 未正常启动，检查：
1. 端口 5001 是否被占用：`netstat -ano | findstr 5001`
2. `config.py` 中 MySQL 密码是否正确
3. 查看 Flask 控制台报错信息

### Q：AI 回答返回「系统暂不可用，请稍后重试」？
A：
1. 检查 `LLM_API_KEY` 是否填写正确
2. 检查硅基流动账号是否有余额
3. 查看 Flask 日志中的具体报错

### Q：知识库检索无结果？
A：
1. 确认 `knowledge_base` 表有已启用（`enabled=1`）的数据
2. 调用 `/status` 接口确认 `indexed_count > 0`
3. 如刚导入数据，调用 `POST /reload` 重建索引

---

## 10. 测试账号

| 类型 | 账号 | 密码 | 说明 |
|------|------|------|------|
| 管理员 | `admin` | `admin123` | 登录 Tomcat 应用 |
| 员工工号 | `EMP001` | `123456` | 登录员工门户 |

访问地址：
- 管理员：`http://localhost:8080/qiuniu/login.jsp`
- 知识库：`http://localhost:8080/qiuniu/knowledge-base.jsp`
- 员工登录：`http://localhost:8080/qiuniu/employee/employee-login.jsp`

---

安装完成后，参考 `OPS.md` 进行日常运维。

**BankKB RAG 安装手册 v2.0 | 2026-05-27**
