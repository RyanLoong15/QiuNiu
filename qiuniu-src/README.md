# 🐲 囚牛 (QiuNiu) - 智能知识库与提示词管理系统

基于 Tomcat + Flask 的双引擎 Web 应用，集成多角色 AI 问答、知识库 RAG 检索、员工门户和提示词管理。

---

## 功能特性

### 核心功能
- ✅ **双登录体系**：管理员登录（提示词系统）+ 员工工号登录（员工门户）
- ✅ **多角色 AI 问答**：支持多个虚拟角色（不同人设/系统提示词）并行对话
- ✅ **知识库 RAG 检索**：BM25 + 向量混合检索，基于知识库内容回答
- ✅ **知识库管理**：手动录入、文档导入（Word/PDF/TXT）、启用/禁用
- ✅ **员工门户**：员工登录查看待回答问题、回答后自动录入知识库
- ✅ **向本人提问**：用户可标记「不满意 AI 回答」，问题自动推送给关联员工
- ✅ **头像上传**：员工/角色支持上传头像，替代 AI 生成头像
- ✅ **Git 版本对比**：批量对比多仓库分支差异（需配置 Git 环境）

### 提示词管理（原有）
- ✅ 用户注册与登录（BCrypt 密码加密）
- ✅ 提示词 CRUD（分类、搜索、筛选）
- ✅ MySQL 持久化存储

---

## 📁 项目结构

```
QiuNiu/
├── qiuniu-src/                    # Tomcat 主应用（Java/JSP）
│   ├── pom.xml                   # Maven 构建配置
│   ├── deploy.bat                # Windows 一键部署脚本
│   ├── init-db.sql              # 数据库初始化脚本（15 张表）
│   ├── src/main/
│   │   ├── java/com/qiuniu/
│   │   │   ├── dao/            # 数据访问层
│   │   │   ├── model/          # 数据模型
│   │   │   ├── servlet/        # Servlet 控制器
│   │   │   ├── filter/         # 登录过滤器
│   │   │   └── listener/       # 应用监听器
│   │   ├── resources/
│   │   │   └── db.properties   # 【需配置】数据库连接
│   │   └── webapp/
│   │       ├── WEB-INF/web.xml
│   │       ├── knowledge-base.jsp      # 知识库聊天页面
│   │       ├── employee/              # 员工门户页面
│   │       │   ├── employee-login.jsp
│   │       │   └── employee-portal.jsp
│   │       ├── images/avatar/        # 角色头像 SVG/PNG
│   │       ├── css/
│   │       └── js/
│   └── qiuniu-deploy/          # 部署包模板
│       ├── scripts/install.sh   # Linux 一键安装脚本
│       ├── sql/init-db.sql      # 数据库初始化脚本（同 ../init-db.sql）
│       └── docs/INSTALL.md      # 安装文档
│
├── bank_kb/                      # Flask RAG 服务（Python）
│   ├── app.py                   # Flask 主应用（API 服务）
│   ├── config.py                # 【需配置】Flask 服务配置
│   ├── db_loader.py            # 数据库操作层
│   ├── generator.py            # AI 大模型调用（硅基流动 API）
│   ├── hybrid_search.py        # BM25 + 向量混合检索
│   ├── bm25_search.py          # BM25 关键词检索（纯 Python 实现）
│   ├── requirements.txt         # Python 依赖列表
│   ├── INSTALL.md             # Flask 服务安装文档
│   ├── OPS.md                 # Flask 服务运维文档
│   └── data/                  # 知识库原始文档存放目录
│
└── README.md                    # 本文件
```

---

## ⚙️ 配置说明

### 1. Tomcat 应用配置

**文件：** `qiuniu-src/src/main/resources/db.properties`

```properties
# MySQL 连接配置
db.url=jdbc:mysql://localhost:3306/qiuniu_db?useSSL=false&serverTimezone=Asia/Shanghai&characterEncoding=utf-8&allowPublicKeyRetrieval=true
db.username=root
db.password=NewPassword123!

# AI 大模型配置（可选，用于提示词分析）
ai.api.url=https://api.siliconflow.cn/v1
ai.api.key=your_api_key_here
ai.model=Qwen/Qwen2.5-72B-Instruct
```

### 2. Flask RAG 服务配置

**文件：** `bank_kb/config.py`

```python
# MySQL 连接配置（需与 Tomcat 应用使用同一个数据库）
MYSQL_HOST = 'localhost'
MYSQL_PORT = 3306
MYSQL_USER = 'root'
MYSQL_PASSWORD = 'NewPassword123!'
MYSQL_DATABASE = 'qiuniu_db'

# AI 大模型配置（硅基流动 API）
LLM_API_URL = "https://api.siliconflow.cn/v1/chat/completions"
LLM_API_KEY = "sk-xxx"   # ← 填写你的 API Key
LLM_MODEL = "Qwen/Qwen2.5-72B-Instruct"

# 检索配置
RETRIEVAL_MODE = "bm25"   # bm25 / vector / hybrid（需 Ollama 支持才可用 vector/hybrid）
TOP_K = 5                   # 每次检索返回的最大片段数
CONFIDENCE_THRESHOLD = 0.2  # 低于此分数触发「向本人提问」

# Tomcat 部署路径（头像上传需写入此目录）
TOMCAT_CONTEXT = "/qiuniu"
WEBAPP_ROOT = "C:/apache-tomcat-9.0.96/webapps/qiuniu"
```

### 3. 头像上传目录

Flask 服务需要将上传的头像写入 Tomcat 部署目录：

```
# 自动创建的目录：
C:\apache-tomcat-9.0.96\webapps\qiuniu\static\avatars\
```

---

## 🚀 部署步骤

### 环境要求

| 组件 | 版本要求 | 说明 |
|------|----------|------|
| JDK | 17+ | Tomcat 9 要求 JDK 11+，项目使用 JDK 17 编译 |
| Apache Tomcat | 9.x | Web 应用服务器 |
| MySQL | 8.0+ | 数据库（需支持 utf8mb4） |
| Python | 3.9+ | Flask RAG 服务运行环境 |
| pip | 最新 | Python 包管理工具 |
| Maven | 3.6+ | Java 项目构建工具 |
| Git | 可选 | 仅在使用「版本对比」功能时需要 |

### 步骤 1：初始化数据库

```bash
# 登录 MySQL
mysql -u root -p

# 执行初始化脚本（自动创建 15 张表 + 默认管理员账号）
source D:\qiuniu20260506\qiuniu-src\init-db.sql
```

**默认管理员账号：**
- 用户名：`admin`
- 密码：`admin123`

### 步骤 2：配置 Tomcat 应用

编辑 `qiuniu-src/src/main/resources/db.properties`，填写 MySQL 密码。

### 步骤 3：编译并部署 Tomcat 应用

```bash
# 编译项目（跳过测试加快速度）
cd D:\qiuniu20260506\qiuniu-src
mvn clean package -DskipTests

# 将 WAR 包复制到 Tomcat
copy target\qiuniu.war C:\apache-tomcat-9.0.96\webapps\

# 启动 Tomcat（如果未启动）
cd C:\apache-tomcat-9.0.96\bin
startup.bat
```

访问：`http://localhost:8080/qiuniu/`

### 步骤 4：安装并启动 Flask RAG 服务

```bash
# 安装 Python 依赖
cd D:\qiuniu20260506\bank_kb
pip install -r requirements.txt

# 配置 config.py（填写 LLM_API_KEY）

# 启动 Flask 服务（默认端口 5001）
python app.py
```

访问 RAG 服务：`http://localhost:5001/`（API 基础路径）

> 💡 **生产环境建议**：使用 `waitress` 或 `gunicorn` 运行 Flask，而非直接运行 `app.py`。

### 步骤 5：验证部署

1. 打开 `http://localhost:8080/qiuniu/login.jsp`
2. 使用 `admin / admin123` 登录
3. 进入「知识库」页面，上传一份文档测试 RAG 检索
4. 打开 `http://localhost:8080/qiuniu/employee/employee-login.jsp`，用工号登录员工门户

---

## 📖 使用指南

### 管理员使用指南

#### 1. 知识库管理

登录后点击顶部「📖 知识库」进入知识库页面。

**手动录入知识点：**
1. 点击「+ 新增」按钮
2. 填写「问题」和「答案」
3. 选择分类
4. 保存

**从文档导入知识点：**
1. 点击「📄 文档导入」
2. 上传 Word（.docx）、PDF（.pdf）或纯文本（.txt）文件
3. 系统自动解析文档内容，按段落拆分为知识点
4. 导入完成后可逐个审核并启用

**检索测试：**
- 在底部输入框输入问题，观察 AI 回答是否命中知识库

#### 2. 虚拟角色管理

每个「虚拟角色」对应一套独立的人设（系统提示词）和关联的知识库。

**配置角色（通过数据库或管理后台）：**

```sql
INSERT INTO virtual_characters (name, title, description, system_prompt, categories, confidence_threshold)
VALUES (
  '智能客服',
  '智能客服助手',
  '回答客户常见问题',
  '你是某银行的智能客服助手，基于知识库回答客户问题。如无法回答，引导客户联系人工客服。',
  '开户,转账,理财',
  0.2
);
```

**关联员工：**
```sql
-- 将角色 ID=2 分配给员工 ID=3
UPDATE virtual_characters SET employee_id = 3 WHERE id = 2;
```

#### 3. 提示词管理

登录后进入「控制台」，进行提示词的增删改查。

---

### 员工使用指南

#### 1. 登录员工门户

1. 访问 `http://localhost:8080/qiuniu/employee/employee-login.jsp`
2. 输入工号（如 `EMP001`）和密码
3. 首次登录默认密码为 `123456`，登录后请修改

#### 2. 回答客户问题

1. 登录后自动进入「待回答问题」列表
2. 点击「回答」按钮，填写答案
3. 提交后，系统自动将 Q&A 录入知识库
4. 该问题同时从「待回答」列表消失

#### 3. 上传头像

1. 点击页面右上角头像区域
2. 选择本地图片上传
3. 上传成功后，客户在知识库聊天页面看到的就是上传的头像

---

### 最终用户（客户）使用指南

#### 1. 与 AI 对话

1. 访问 `http://localhost:8080/qiuniu/knowledge-base.jsp`
2. 在底部输入框输入问题
3. AI 基于知识库内容回答，并附上参考来源（知识点 N）

#### 2. 向本人提问

如果 AI 回答不满意：
1. 点击 AI 回复下方的「没有我满意的答案，我要向本人提问」
2. 问题自动推送到关联员工的「待回答问题」列表
3. 员工回答后，答案自动录入知识库，下次 AI 就能直接回答

---

## 🔧 开发说明

### 技术栈

| 层级 | 技术 |
|------|------|
| 前端 | JSP, HTML5, CSS3, Vanilla JavaScript |
| Web 框架 | Java Servlet 4.0 + Apache Tomcat 9 |
| RAG 服务 | Python 3 + Flask + Flask-CORS |
| 数据库 | MySQL 8.0（InnoDB 引擎，utf8mb4 字符集） |
| 检索引擎 | BM25（关键词）+ Milvus（向量，可选） |
| AI 大模型 | 硅基流动 API（兼容 OpenAI 格式） |
| 密码加密 | BCrypt（Java: jbcrypt / Python: bcrypt） |
| 构建工具 | Maven 3.6+ |
| 文档解析 | Apache POI（Word）+ PyPDF2/PDFMiner（PDF） |

### API 接口概览

#### Tomcat 应用（端口 8080）

| 路径 | 方法 | 说明 |
|------|------|------|
| `/login` | POST | 管理员登录 |
| `/employee/login` | POST | 员工登录（工号 + 密码） |
| `/api/prompt` | GET/POST | 提示词 CRUD |
| `/api/git/*` | GET/POST | Git 版本对比 |

#### Flask RAG 服务（端口 5001）

| 路径 | 方法 | 说明 |
|------|------|------|
| `/query` | POST | 单轮问答（RAG 检索 + LLM 生成） |
| `/api/chat` | POST | 多轮对话（支持上下文） |
| `/api/characters` | GET | 获取所有启用角色 |
| `/api/unanswered/submit` | POST | 提交「向本人提问」问题 |
| `/api/employee/questions` | GET | 员工查看待回答问题 |
| `/api/employee/questions/<id>/answer` | POST | 员工回答问题（自动录入知识库） |
| `/api/employee/avatar/upload` | POST | 员工上传头像 |

---

## 🔐 安全说明

- 管理员密码和员工密码均使用 **BCrypt** 加密存储，不可逆
- 管理员会话超时：**30 分钟**
- 员工会话超时：**8 小时**（适合工作时间）
- 所有页面需要登录访问（除登录/注册页）
- 支持「记住我」功能（管理员 7 天免登录）
- Flask RAG 服务仅接受来自 Tomcat（localhost:8080）的跨域请求（`CORS(origins=["http://localhost:8080"])`）
- 生产环境建议为 Flask 服务添加 API 鉴权（Token 或 JWT）

---

## 📝 常见问题

### Q: Flask 服务启动后知识库回答提示「服务不可用」？
A: 检查以下内容：
1. Flask 是否运行在 `localhost:5001`（默认端口）
2. Tomcat 应用是否能访问 `localhost:5001/query`（可在浏览器直接访问测试）
3. 检查 `bank_kb/config.py` 中的 `LLM_API_KEY` 是否填写正确
4. 查看 Flask 控制台日志，确认是否有报错

### Q: 员工登录提示「工号或密码错误」？
A: 
1. 确认员工记录已录入 `employees` 表
2. 密码是经过 BCrypt 加密存储的，不可直接写入明文
3. 可使用 `bcrypt.hashpw('123456'.encode(), bcrypt.gensalt())` 生成密码哈希

### Q: 「向本人提问」提交后员工看不到问题？
A: 
1. 确认 `virtual_characters` 表的 `employee_id` 字段已正确关联员工 ID
2. 员工登录后，系统根据 `employee_id` 反向查找 `character_id`，再查询 `unanswered_questions` 表
3. 可在数据库直接查询验证：
   ```sql
   SELECT q.* FROM unanswered_questions q
   JOIN virtual_characters v ON q.character_id = v.id
   WHERE v.employee_id = <员工ID> AND q.status = 'pending';
   ```

### Q: 文档导入后知识点内容为空白？
A: 
1. 检查文档是否为扫描版 PDF（需 OCR 才能提取文字）
2. Word 文档需为 `.docx` 格式（`.doc` 格式不支持）
3. 查看 `bank_kb/logs/` 下的错误日志

### Q: Git 版本对比功能报错「Network is unreachable」？
A: Git 版本对比需要从服务器访问 GitHub/GitLab，如果服务器没有配置代理或没有互联网访问，功能将无法使用。可忽略此功能，不影响其他模块。

---

## 📄 许可证

本项目仅供企业内部使用。

---

**🐲 囚牛 - 让知识触手可及**
