# 囚牛 (QiuNiu) 内网部署指南

## 修复清单（2026-06-15）

本次部署包已修复以下内网迁移问题：

| # | 问题 | 修复 | 文件 |
|---|------|------|------|
| 1 | 文档下载绝对路径硬编码 | `file_path` 存相对路径，`upload.dir` 配置还原 | `document_import_bp.py`, `DocumentDownloadServlet.java` |
| 2 | `db.properties` localhost 硬编码 | 提供 `db.properties.intranet.template`，部署时替换 | `db.properties` |
| 3 | `AIModelClient.java` 硬编码 Windows 路径 | 优先从 classpath 读取配置，移除硬编码依赖 | `AIModelClient.java` |
| 4 | `init.py` PyPDF2 依赖检查误报 | 修正为 `pypdf`（实际 import 名） | `init.py` |
| 5 | Flask 下载端点不支持相对路径 | 新增 `_resolve_upload_path()` 兼容相对/绝对路径 | `document_import_bp.py` |

## 部署步骤

### 1. 准备配置文件

```bash
# 复制模板
cp db.properties.intranet.template db.properties

# 修改以下字段：
# - db.password         内网 MySQL 密码
# - flask.base.url     内网 Flask 服务地址
# - upload.dir         内网文档上传目录（绝对路径）
# - ai.api.key         内网 AI API Key
```

### 2. 部署 Java WAR

```bash
# 停止 Tomcat
$TOMCAT_HOME/bin/shutdown.sh

# 复制 WAR
cp qiuniu.war $TOMCAT_HOME/webapps/

# 复制配置文件（WAR 内的 db.properties 会被覆盖）
cp db.properties $TOMCAT_HOME/webapps/qiuniu/WEB-INF/classes/

# 启动 Tomcat
$TOMCAT_HOME/bin/startup.sh
```

### 3. 部署 Flask 服务

```bash
# 安装依赖
pip install -r requirements.txt

# 设置环境变量（或直接修改 config.py）
export UPLOAD_DIR=/opt/qiuniu/bank_kb/uploads/documents
export MYSQL_HOST=128.148.17.78
export FLASK_BASE_URL=http://128.148.17.78:5001

# 启动 Flask
python app.py
```

### 4. 初始化 BM25 索引

```bash
curl -X POST http://128.148.17.78:5001/reload
```

## 配置对照表

| 配置项 | 本地开发 | 内网部署 |
|---------|-----------|-----------|
| `db.url` | `localhost:3306` | `128.148.17.78:3306` |
| `flask.base.url` | `http://localhost:5001` | `http://128.148.17.78:5001` |
| `upload.dir` | Windows 路径 | Linux 路径 |
| `ai.api.url` | `https://openapi.ai.jh/v1` | 不变 |

## 验证步骤

```bash
# 1. 验证 Flask 健康
curl http://128.148.17.78:5001/status

# 2. 验证 Tomcat 启动
curl http://128.148.17.78:8080/qiuniu/login

# 3. 验证登录
curl -X POST http://128.148.17.78:8080/qiuniu/login \
  -d "username=admin&password=admin123" -v

# 4. 验证文档下载（需先登录获取 session）
# 见完整测试脚本
```

## 注意事项

1. **文档路径迁移**：若从本地迁移到内网，需将 `document_imports` 表的 `file_path` 字段从绝对路径更新为相对路径：
   ```sql
   UPDATE document_imports SET file_path = SUBSTRING_INDEX(file_path, 'uploads\\documents\\', -1);
   ```
   （Windows 路径含 `\\`，Linux 路径含 `/`，需按实际情况调整）

2. **MySQL 端口**：必须使用 3306，禁止创建其他实例（见 MEMORY.md）

3. **Flask 索引**：每次重启 Flask 后需调用 `POST /reload` 重建 BM25 索引

4. **浏览器缓存**：部署后前端需 `Ctrl+Shift+R` 强刷
