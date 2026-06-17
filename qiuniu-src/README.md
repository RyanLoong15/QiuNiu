# 🦞 囚牛 (QiuNiu) — 投产风险管理平台

基于 Tomcat + Flask 的企业级双架构系统，面向银行/金融场景，覆盖提示词管理、知识库 RAG 检索、虚拟人助手、投产事故管理与代码关联、Git 版本比对、代码风险分析、文档导入等完整功能。

## 功能总览

| 模块 | 功能 | 说明 |
|------|------|------|
| 🏠 控制台 | 系统概览、快捷入口 | `dashboard.jsp` |
| 💬 知识库 | RAG 对话 + 文档检索 + 知识条目管理 | `knowledge-base.jsp` + Flask `/api/chat` |
| 👤 虚拟人 | 多角色 AI 助手（囚牛/唐佳艺/陈军） | `VirtualHumanChatServlet` |
| 🔥 投产事故 | 事故录入、关联代码段、历史查询 | `incidents.jsp`, `incident-codes.jsp` |
| 📊 版本比对 | Git 项目版本差异分析与风险告警 | `version-compare.jsp`, `git-projects.jsp` |
| 📋 提示词管理 | 提示词 CRUD + 分类搜索 | `PromptServlet` |
| 👥 团队管理 | Git 团队/项目配置 | `team-dashboard.jsp` |
| 📄 文档导入 | DOCX/XLSX/PPTX/PDF/TXT 导入 + OCR | Flask `/api/document/import` |
| 👨‍💼 员工门户 | 独立员工登录 + 个人看板 | `employee-portal.jsp` |
| 🔧 MCP 服务 | MCP 环境/命令/提示词管理 | `mcp-service.jsp` |
| ⚙️ 系统配置 | 运行时参数管理 | `admin_config_bp` / `dashboard.jsp` |
| 🔍 代码分析 | 基于规则集的代码风险扫描 | `CodeAnalysisServlet` |

---

## 🏗 架构

```
┌─────────────────────────────────────────────────┐
│  浏览器 (http://localhost:8080/qiuniu/)          │
├──────────────────┬──────────────────────────────┤
│  Tomcat :8080     │  Flask :5001 (RAG/LLM)       │
│  Java Servlet     │  Python 3.11                 │
│  JSP 视图层       │  BM25 + TF-IDF 混合检索      │
│  连接池 DBCP2     │  LLM 对话代理                │
└────────┬─────────┴────────────┬─────────────────┘
         │                      │
         └────── MySQL :3306 ───┘
               qiuniu_db (23 张表)
```

**职责分工**：
- **Tomcat**：认证/鉴权、业务 CRUD、JSP 视图、会话管理
- **Flask**：RAG 检索、文档解析导入、AI 对话代理、静态头像

---

## 📁 项目结构

```
QiuNiu/
├── qiuniu-src/                  # Java Web (Maven)
│   └── src/main/
│       ├── java/com/qiuniu/
│       │   ├── dao/             # 数据访问 (17个DAO)
│       │   ├── model/           # 数据模型 (15个Entity)
│       │   ├── servlet/         # Servlet 控制器 (15个)
│       │   ├── service/         # 业务逻辑 (CodeAnalyzer, IncidentCheck)
│       │   ├── util/            # 工具类 (JsonUtil, FlaskConfig, AIModelClient)
│       │   ├── filter/          # AuthFilter, EncodingFilter
│       │   └── listener/        # InitListener, MySQLCleanupListener
│       ├── resources/
│       │   └── db.properties    # 数据库/Flask/AI 配置
│       └── webapp/
│           ├── WEB-INF/web.xml
│           ├── *.jsp            # 各功能页面
│           └── images/css/js/   # 前端资源
├── bank_kb/                     # Flask RAG 后端 (Python)
│   ├── app.py                   # Flask 主应用 + 路由注册
│   ├── config.py                # 全局配置
│   ├── env_config.py            # 环境自适应(内网IP检测)
│   ├── db_loader.py             # DB 数据加载
│   ├── hybrid_search.py         # BM25+TF-IDF 混合检索
│   ├── retriever.py             # 检索器核心
│   ├── generator.py             # 响应生成 + LLM 调用
│   ├── llm_client.py            # LLM API 客户端
│   ├── ingest.py                # 知识入库
│   ├── init.py                  # 依赖检查 + 启动初始化
│   ├── reindex_worker.py        # 异步重建索引
│   ├── document_import_bp.py    # 文档导入 Blueprint
│   ├── employee_portal_bp.py    # 员工门户 Blueprint
│   ├── admin_config_bp.py       # 系统配置 Blueprint
│   ├── static/avatars/          # 虚拟角色头像
│   └── requirements.txt         # Python 依赖
├── db/                          # 数据库脚本
│   └── migrations/
│       └── multi_character_v1.sql
├── pom.xml
└── README.md
```

---

## ⚙️ 配置说明

### db.properties（核心配置文件）

```properties
# MySQL
db.url=jdbc:mysql://localhost:3306/qiuniu_db?useSSL=false&serverTimezone=Asia/Shanghai&characterEncoding=utf-8
db.username=root
db.password=your_password

# Flask RAG 服务地址
flask.base.url=http://localhost:5001

# AI 模型配置
ai.api.url=https://api.silicon.com/v1
ai.api.key=sk-xxxx
ai.model=Qwen/Qwen2.5-72B-Instruct

# 文档上传目录（绝对路径，用正斜杠）
upload.dir=D:/qiuniu20260506/bank_kb/uploads/documents
```

> **⚠️ `upload.dir` 必须用正斜杠** — Java `Properties.load()` 会将 `\u` 当作 Unicode 转义解析导致启动崩溃。

---

## 🚀 本地开发部署

### 环境要求

| 组件 | 版本 |
|------|------|
| Java | JDK 17 |
| Tomcat | 9.x |
| MySQL | 8.0+ / 9.x |
| Python | 3.11 |
| Maven | 3.6+ |

### 启动步骤

```bash
# 1. 启动 MySQL
mysqld --defaults-file=my.ini

# 2. 启动 Flask
cd bank_kb
python app.py
curl -X POST http://127.0.0.1:5001/reload   # 重建 BM25 索引

# 3. 编译并启动 Tomcat
cd qiuniu-src
mvn clean package
copy target\qiuniu.war %TOMCAT_HOME%\webapps\
%TOMCAT_HOME%\bin\startup.bat
```

### 默认账号

| 用户名 | 密码 | 角色 |
|--------|------|------|
| admin | admin123 | ADMIN |

---

## 📦 内网 ARM64 部署

### 部署包内容

```
qiuniu_intranet_deploy/
├── qiuniu.war                    # Java 应用包
├── init-sql.sql                  # 23 表DDL + 初始数据
├── source/                       # 完整源码（已清理）
├── wheels/                       # ARM64 离线依赖包 (54个)
├── requirements-arm64.txt        # ARM64 依赖清单
├── install_deps_arm64.sh         # 一键安装脚本
├── db.properties.intranet.template  # 内网配置模板
└── INTRANET_DEPLOY.md            # 详细部署指南
```

### 快速部署（ARM64 麒麟）

```bash
# 1. 安装 Python 依赖
pip install --no-index --find-links=./wheels -r requirements-arm64.txt

# 2. 初始化数据库
mysql -u root -p < init-sql.sql

# 3. 配置
cp db.properties.intranet.template db.properties
# 编辑 db.properties，填入内网 IP 和密码

# 4. 部署 WAR
cp qiuniu.war $TOMCAT_HOME/webapps/

# 5. 启动 Flask
cd bank_kb && python app.py

# 6. 重建索引
curl -X POST http://内网IP:5001/reload
```

---

## 🔐 安全

- 密码 BCrypt 加密（`$2a$` 前缀，rounds=12）
- Session 会话管理（30 分钟超时）
- AuthFilter 登录拦截
- 角色权限控制（ADMIN / VIEWER）

---

## 📖 API 速查

| 端点 | 方法 | 说明 |
|------|------|------|
| `/login` | POST | 登录 |
| `/register` | POST | 注册 |
| `/logout` | GET | 退出 |
| `/api/chat` | POST | 知识库 RAG 对话 (Tomcat→Flask) |
| `/api/knowledge-base/list` | GET | 知识库列表 |
| `/api/knowledge-base/add` | POST | 添加知识点 |
| `/api/knowledge-base/delete` | POST | 删除知识点 |
| `/api/characters` | GET | 虚拟角色列表 |
| `/api/documents/import` | POST | 文档导入 |
| `/api/document/<id>/download` | GET | 文档下载 (Flask 直连) |
| `/api/unanswered` | GET | 未回复问题列表 |
| `/api/system-config` | GET/POST | 系统配置管理 |
| `/api/employee-portal/*` | GET/POST | 员工门户 |
| `/api/incidents/*` | GET/POST | 投产事故管理 |
| `/api/git/*` | GET/POST | Git 项目/版本管理 |
| `/api/code-analysis/*` | POST | 代码风险分析 |
| `/api/prompts/*` | GET/POST | 提示词管理 |
| `/api/mcp/*` | GET/POST | MCP 服务管理 |
| `/status` | GET | Flask 健康检查 |
| `/reload` | POST | 重建 BM25 索引 |

---

## 🐛 已知问题

| 问题 | 影响 | 状态 |
|------|------|------|
| AuthFilter 被注释 | 无登录校验 | 待恢复 |
| GitHub 443 阻断 | 无法 push/pull | 需代理 |
| VirtualHumanChatServlet 与 ChatServlet 路由重叠 | 可能混淆 | 待评估 |
| 知识库手动添加字段名 `file`(单数) vs 后端 `files`(复数) | 手动添加无文件上传 | 次要 |
| 中文 form data 编码丢失 | 含特殊字符时异常 | 已加 Flask bullet 过滤 |

---

**🦞 囚牛 — 投产风险管理，从代码到知识的全链路守护**
