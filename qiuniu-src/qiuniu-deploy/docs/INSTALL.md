# 囚牛 (QiuNiu) Linux 安装指南

## 系统要求

| 组件 | 最低版本 | 推荐版本 |
|------|----------|----------|
| CPU | 2 核 | 4 核 |
| 内存 | 4GB | 8GB |
| 磁盘 | 10GB 可用 | 20GB |
| 操作系统 | CentOS 7/8, Ubuntu 18/20/22, Debian 10/11 | Ubuntu 22.04 LTS |
| Java | OpenJDK 17 | OpenJDK 17 |
| Python | 3.9+ | 3.10+ |
| MySQL | 8.0+ | 8.0+ |
| Tomcat | 9.0.x | 9.0.96 |

---

## 方式一：一键安装（推荐）

将 `qiuniu-deploy.tar.gz` 上传到服务器，解压后运行安装脚本：

```bash
# 1. 上传部署包到服务器
scp qiuniu-deploy.tar.gz root@your-server:/opt/

# 2. 解压
cd /opt
tar -xzf qiuniu-deploy.tar.gz
# 解压后得到 /opt/qiuniu-deploy/ 目录

# 3. 运行安装脚本（需 root 权限）
cd /opt/qiuniu-deploy/scripts
chmod +x install.sh
./install.sh
```

安装脚本自动完成：
- 安装 OpenJDK 17、Python 3、Maven、MySQL
- 配置 MySQL 数据库（自动导入 `sql/init-db.sql`）
- 安装并配置 Tomcat 9
- 安装 Flask RAG 服务（systemd 托管）
- 配置防火墙（开放 8080、5001 端口）
- 创建管理脚本（`start.sh`、`stop.sh`、`status.sh` 等）

安装过程中会交互式询问：
- MySQL root 密码
- Tomcat 服务端口（默认 8080）
- Flask 服务端口（默认 5001）

---

## 方式二：快速安装（传入密码，无交互）

```bash
cd /opt/qiuniu-deploy/scripts
chmod +x install.sh
./install.sh --mysql-pass "YourPassword123!" --port 8080 --flask-port 5001
```

---

## 方式三：手动安装

如果自动安装失败，按顺序执行以下步骤：

### 步骤 1：安装系统依赖

**CentOS / RHEL：**
```bash
yum update -y
yum install -y java-17-openjdk java-17-openjdk-devel \
               wget unzip git curl \
               mysql-server mysql-devel \
               python3 python3-pip
systemctl enable mysqld
systemctl start mysqld
```

**Ubuntu / Debian：**
```bash
apt-get update -y
apt-get install -y openjdk-17-jdk \
               wget unzip git curl \
               mysql-server libmysqlclient-dev \
               python3 python3-pip
systemctl enable mysql
systemctl start mysql
```

### 步骤 2：安装 Maven

```bash
cd /tmp
wget -q https://dlcdn.apache.org/maven/maven-3/3.9.9/binaries/apache-maven-3.9.9-bin.tar.gz
tar -xzf apache-maven-3.9.9-bin.tar.gz -C /opt/
ln -sf /opt/apache-maven-3.9.9/bin/mvn /usr/bin/mvn
rm -f apache-maven-3.9.9-bin.tar.gz
```

### 步骤 3：配置 MySQL 并初始化数据库

```bash
# 设置 root 密码（CentOS 跳过此步，直接用临时密码）
mysql -u root -p -e "ALTER USER 'root'@'localhost' IDENTIFIED WITH mysql_native_password BY 'YourPassword123!';"

# 创建数据库
mysql -u root -p'YourPassword123!' -e "
CREATE DATABASE IF NOT EXISTS qiuniu_db
  DEFAULT CHARACTER SET utf8mb4
  DEFAULT COLLATE utf8mb4_unicode_ci;
"

# 导入初始化脚本（15 张表 + 默认管理员）
mysql -u root -p'YourPassword123!' qiuniu_db < /opt/qiuniu-deploy/sql/init-db.sql
```

**默认管理员账号：** `admin` / `admin123`

### 步骤 4：安装 Tomcat

```bash
cd /opt
wget -q https://archive.apache.org/dist/tomcat/tomcat-9/v9.0.96/bin/apache-tomcat-9.0.96.tar.gz
tar -xzf apache-tomcat-9.0.96.tar.gz
mv apache-tomcat-9.0.96 /opt/qiuniu/tomcat
rm -f apache-tomcat-9.0.96.tar.gz

# 清理无用应用
rm -rf /opt/qiuniu/tomcat/webapps/{ROOT,docs,examples,host-manager,manager}

# 配置 JVM 参数
cat > /opt/qiuniu/tomcat/bin/setenv.sh << 'EOF'
#!/bin/bash
export JAVA_OPTS="-Xms512m -Xmx2048m -XX:+UseG1GC -Dfile.encoding=UTF-8"
EOF
chmod +x /opt/qiuniu/tomcat/bin/setenv.sh

# 配置 server.xml（修改端口）
sed -i 's/port="8080"/port="8080"/' /opt/qiuniu/tomcat/conf/server.xml
```

### 步骤 5：部署 Tomcat 应用

```bash
# 将 qiuniu.war 放入 Tomcat webapps 目录
cp /opt/qiuniu-deploy/qiuniu.war /opt/qiuniu/tomcat/webapps/

# 配置数据库连接
cat > /opt/qiuniu/config/db.properties << 'EOF'
db.url=jdbc:mysql://localhost:3306/qiuniu_db?useSSL=false&serverTimezone=Asia/Shanghai&characterEncoding=utf-8&allowPublicKeyRetrieval=true
db.username=root
db.password=YourPassword123!
db.driver=com.mysql.cj.jdbc.Driver
db.pool.initialSize=5
db.pool.maxActive=20
db.pool.maxIdle=10
db.pool.minIdle=5
db.pool.maxWait=3000
EOF
```

### 步骤 6：安装并配置 Flask RAG 服务

```bash
# 复制 Flask 目录
cp -r /opt/qiuniu-deploy/flask /opt/qiuniu/flask

# 安装 Python 依赖
pip3 install -r /opt/qiuniu/flask/requirements.txt -i https://mirrors.aliyun.com/pypi/simple/

# 配置 Flask（填写 LLM API Key）
cat > /opt/qiuniu/flask/config.py << 'EOF'
import os

MYSQL_HOST = 'localhost'
MYSQL_PORT = 3306
MYSQL_USER = 'root'
MYSQL_PASSWORD = 'YourPassword123!'
MYSQL_DATABASE = 'qiuniu_db'

LLM_API_URL = "https://api.siliconflow.cn/v1/chat/completions"
LLM_API_KEY = "sk-xxx"  # ← 填写你的硅基流动 API Key
LLM_MODEL = "Qwen/Qwen2.5-72B-Instruct"

RETRIEVAL_MODE = "bm25"   # bm25 / vector / hybrid
TOP_K = 5
CONFIDENCE_THRESHOLD = 0.2

TOMCAT_CONTEXT = "/qiuniu"
WEBAPP_ROOT = "/opt/qiuniu/tomcat/webapps/qiuniu"
EOF
```

### 步骤 7：创建 systemd 服务

**Tomcat 服务：**
```bash
cat > /etc/systemd/system/qiuniu.service << 'EOF'
[Unit]
Description=QiuNiu Tomcat Service
After=network.target mysql.service
Wants=mysql.service

[Service]
Type=forking
Environment="JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64"
ExecStart=/opt/qiuniu/tomcat/bin/startup.sh
ExecStop=/opt/qiuniu/tomcat/bin/shutdown.sh
Restart=on-failure
RestartSec=5

[Install]
WantedBy=multi-user.target
EOF
```

**Flask 服务：**
```bash
cat > /etc/systemd/system/qiuniu-flask.service << 'EOF'
[Unit]
Description=QiuNiu Flask RAG Service
After=network.target mysql.service
Wants=mysql.service

[Service]
Type=simple
User=root
WorkingDirectory=/opt/qiuniu/flask
ExecStart=/usr/bin/python3 /opt/qiuniu/flask/app.py
Restart=on-failure
RestartSec=5
Environment="PYTHONUNBUFFERED=1"

[Install]
WantedBy=multi-user.target
EOF

systemctl daemon-reload
systemctl enable qiuniu.service
systemctl enable qiuniu-flask.service
```

### 步骤 8：配置防火墙

```bash
# CentOS / RHEL
firewall-cmd --permanent --add-port=8080/tcp
firewall-cmd --permanent --add-port=5001/tcp
firewall-cmd --reload

# Ubuntu / Debian（ufw）
ufw allow 8080/tcp
ufw allow 5001/tcp
ufw reload
```

### 步骤 9：启动服务

```bash
systemctl start qiuniu
systemctl start qiuniu-flask

# 验证
systemctl status qiuniu --no-pager
systemctl status qiuniu-flask --no-pager
```

---

## 验证安装

### 检查服务状态

```bash
# Tomcat
curl -s -o /dev/null -w "Tomcat HTTP Status: %{http_code}\n" http://localhost:8080/qiuniu/

# Flask
curl -s http://localhost:5001/status

# 数据库
mysql -u root -p'YourPassword123!' -e "USE qiuniu_db; SHOW TABLES;"
```

### 访问应用

打开浏览器访问：

| 功能 | 地址 |
|------|------|
| 管理员登录 | `http://服务器IP:8080/qiuniu/login.jsp` |
| 知识库聊天 | `http://服务器IP:8080/qiuniu/knowledge-base.jsp` |
| 员工登录 | `http://服务器IP:8080/qiuniu/employee/employee-login.jsp` |

**默认账号：**
- 管理员：`admin` / `admin123`
- 员工工号：`EMP001`（首次登录密码 `123456`）

---

## 管理脚本

安装完成后，以下脚本位于 `/opt/qiuniu/`：

| 脚本 | 用途 |
|------|------|
| `start.sh` | 启动 Tomcat + Flask 服务 |
| `stop.sh` | 停止所有服务 |
| `restart.sh` | 重启所有服务 |
| `status.sh` | 查看服务状态 + 端口检测 |
| `logs.sh` | 实时查看 Tomcat 日志 |

```bash
cd /opt/qiuniu
./start.sh      # 启动
./status.sh     # 查看状态
./logs.sh       # 查看日志
```

---

## 目录结构

```
/opt/qiuniu/
├── tomcat/                 # Tomcat 服务器
│   ├── bin/               # 启动脚本
│   ├── conf/              # server.xml 等配置
│   ├── logs/              # catalina.out 日志
│   └── webapps/
│       └── qiuniu/        # 主应用
│           ├── knowledge-base.jsp
│           ├── employee/
│           └── static/avatars/
├── flask/                  # Flask RAG 服务
│   ├── app.py
│   ├── config.py          # ← 需配置 LLM_API_KEY
│   ├── db_loader.py
│   ├── hybrid_search.py
│   └── requirements.txt
├── config/
│   ├── db.properties      # Tomcat 应用数据库配置
│   └── flask_config.py    # Flask 配置模板
├── logs/                  # 额外日志目录
├── backup/                # 备份目录
├── start.sh               # 管理脚本
├── stop.sh
├── restart.sh
└── status.sh
```

---

## 常见问题

### Q：Tomcat 启动后访问 404？
A：检查 WAR 包是否正确解压：
```bash
ls /opt/qiuniu/tomcat/webapps/qiuniu/
# 应该看到 WEB-INF、login.jsp 等文件
```

### Q：Flask 启动后知识库回答不可用？
A：
1. 检查 `config.py` 中 `LLM_API_KEY` 是否填写正确
2. 检查 MySQL 是否可访问
3. 查看 Flask 日志：`journalctl -u qiuniu-flask -f`

### Q：员工登录提示「工号或密码错误」？
A：确认 `employees` 表有该员工记录，且 `password_hash` 是有效的 BCrypt 哈希（以 `$2b$` 或 `$2a$` 开头）。

### Q：Git 版本对比功能报错？
A：该功能需要服务器能访问 GitHub/GitLab（443 端口）。如服务器无外网，可忽略此功能。

---

## 卸载

```bash
# 停止服务
systemctl stop qiuniu
systemctl stop qiuniu-flask

# 删除文件
rm -rf /opt/qiuniu
rm -f /etc/systemd/system/qiuniu.service
rm -f /etc/systemd/system/qiuniu-flask.service
systemctl daemon-reload

# （可选）删除数据库
mysql -u root -p -e "DROP DATABASE qiuniu_db;"
```

---

**囚牛 (QiuNiu) 安装文档 v2.0 | 2026-05-27**
