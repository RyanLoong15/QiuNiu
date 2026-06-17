# 囚牛 (QiuNiu) Linux 安装指南

## 系统要求

- **操作系统**: CentOS 7/8, Ubuntu 18/20/22, Debian 10/11
- **CPU**: 2 核以上
- **内存**: 4GB 以上（推荐 8GB）
- **磁盘**: 10GB 以上可用空间
- **Java**: OpenJDK 11 或更高版本
- **数据库**: MySQL 5.7 或 8.0

## 快速安装

### 方式一：一键安装（推荐）

```bash
# 1. 上传部署包到服务器
scp qiuniu-deploy.tar.gz root@your-server:/opt/

# 2. 解压部署包
cd /opt
tar -xzf qiuniu-deploy.tar.gz

# 3. 运行安装脚本
cd qiuniu-deploy/scripts
chmod +x install.sh
./install.sh
```

安装脚本会自动：
- 安装 Java 11
- 安装 MySQL
- 配置数据库
- 部署应用
- 创建系统服务

### 方式二：快速安装（静默模式）

```bash
# 解压并运行快速安装
cd /opt
tar -xzf qiuniu-deploy.tar.gz
cd qiuniu-deploy/scripts
chmod +x quick-install.sh
./quick-install.sh your_mysql_password
```

## 手动安装

如果自动安装失败，可以手动执行以下步骤：

### 1. 安装依赖

**CentOS/RHEL:**
```bash
yum update
yum install -y java-11-openjdk java-11-openjdk-devel wget unzip mysql-server mysql
```

**Ubuntu/Debian:**
```bash
apt update
apt install -y openjdk-11-jdk wget unzip mysql-server
```

### 2. 配置 Java 环境

```bash
# 查看 Java 安装路径
which java
readlink -f $(which java)

# 配置环境变量（根据实际路径修改）
echo 'export JAVA_HOME=/usr/lib/jvm/java-11-openjdk-amd64' >> /etc/profile
echo 'export PATH=$PATH:$JAVA_HOME/bin' >> /etc/profile
source /etc/profile
```

### 3. 启动并配置 MySQL

```bash
# 启动 MySQL
systemctl start mysql
systemctl enable mysql

# 设置 root 密码
mysql -e "ALTER USER 'root'@'localhost' IDENTIFIED WITH mysql_native_password BY 'your_password';"

# 创建数据库
mysql -u root -p'your_password' < /opt/qiuniu-deploy/sql/init-db.sql
```

### 4. 安装 Tomcat

```bash
# 下载 Tomcat
cd /opt
wget https://archive.apache.org/dist/tomcat/tomcat-9/v9.0.80/bin/apache-tomcat-9.0.80.tar.gz
tar -xzf apache-tomcat-9.0.80.tar.gz
mv apache-tomcat-9.0.80 /opt/qiuniu/tomcat

# 配置端口（可选）
vim /opt/qiuniu/tomcat/conf/server.xml
# 将端口 8080 改为你想要的端口

# 清理默认应用
rm -rf /opt/qiuniu/tomcat/webapps/ROOT
rm -rf /opt/qiuniu/tomcat/webapps/docs
rm -rf /opt/qiuniu/tomcat/webapps/examples
```

### 5. 配置数据库连接

```bash
# 编辑数据库配置文件
vim /opt/qiuniu/config/db.properties

# 修改以下配置：
db.url=jdbc:mysql://localhost:3306/qiuniu_db?useSSL=false&serverTimezone=Asia/Shanghai&characterEncoding=utf-8&allowPublicKeyRetrieval=true
db.username=root
db.password=your_password
```

### 6. 部署应用

```bash
# 复制 WAR 文件
cp /opt/qiuniu-deploy/qiuniu.war /opt/qiuniu/tomcat/webapps/

# 或者复制整个应用目录
cp -r /opt/qiuniu-deploy/qiuniu /opt/qiuniu/tomcat/webapps/
```

### 7. 配置防火墙

```bash
# CentOS/RHEL
firewall-cmd --permanent --add-port=8080/tcp
firewall-cmd --reload

# Ubuntu/Debian
ufw allow 8080/tcp
```

### 8. 启动服务

```bash
# 启动 Tomcat
/opt/qiuniu/tomcat/bin/startup.sh

# 或使用 systemd 服务
systemctl daemon-reload
systemctl start qiuniu
```

## 验证安装

### 检查服务状态

```bash
# 检查 Tomcat 是否运行
curl -I http://localhost:8080/qiuniu/

# 检查进程
ps aux | grep tomcat
ps aux | grep java
```

### 访问应用

打开浏览器访问：`http://your-server-ip:8080/qiuniu/`

默认账号：`admin`
默认密码：`admin123`

## 常用管理命令

### 启动/停止服务

```bash
# 使用 Tomcat 脚本
/opt/qiuniu/tomcat/bin/startup.sh   # 启动
/opt/qiuniu/tomcat/bin/shutdown.sh  # 停止

# 使用 systemd（如果配置了服务）
systemctl start qiuniu   # 启动
systemctl stop qiuniu    # 停止
systemctl restart qiuniu  # 重启
```

### 查看日志

```bash
# Tomcat 日志
tail -f /opt/qiuniu/tomcat/logs/catalina.out

# 应用日志
tail -f /opt/qiuniu/logs/catalina.out
```

### 备份数据

```bash
# 备份数据库
mysqldump -u root -p qiuniu_db > backup_$(date +%Y%m%d).sql

# 备份应用数据
tar -czf qiuniu_backup_$(date +%Y%m%d).tar.gz /opt/qiuniu
```

## 目录结构

```
/opt/qiuniu/
├── tomcat/                 # Tomcat 服务器
│   ├── bin/               # 启动脚本
│   ├── conf/              # 配置文件
│   └── webapps/           # 应用目录
│       └── qiuniu/        # 囚牛应用
├── config/                # 应用配置
│   └── db.properties      # 数据库配置
├── logs/                  # 日志目录
└── backup/                # 备份目录
```

## 常见问题

### 1. 数据库连接失败

```
检查：
- MySQL 是否运行：systemctl status mysql
- 数据库配置是否正确：cat /opt/qiuniu/config/db.properties
- 数据库是否存在：mysql -u root -p -e "SHOW DATABASES;"
```

### 2. 端口被占用

```
检查端口占用：
netstat -tlnp | grep 8080

修改端口：
vim /opt/qiuniu/tomcat/conf/server.xml
# 将 <Connector port="8080" 改为其他端口
```

### 3. 内存不足

```
调整 JVM 内存：
vim /opt/qiuniu/tomcat/bin/setenv.sh
# 修改：JAVA_OPTS="-Xms512m -Xmx1024m"
```

### 4. 乱码问题

```
确保：
1. MySQL 使用 utf8mb4 字符集
2. 数据库连接参数包含：characterEncoding=utf-8
3. JSP 文件使用 UTF-8 编码
```

### 5. Git 比对功能不工作

```
确保：
1. 服务器已安装 Git：git --version
2. 配置了 SSH Key（如使用私有仓库）
3. 网络可以访问 Git 仓库
```

## 卸载

```bash
# 运行卸载脚本
cd /opt/qiuniu-deploy/scripts
chmod +x uninstall.sh
./uninstall.sh
```

## 联系支持

如有问题，请查看日志文件或联系开发团队。

---
**囚牛 (QiuNiu) v1.0.0**
