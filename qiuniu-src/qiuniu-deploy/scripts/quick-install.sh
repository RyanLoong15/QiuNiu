#!/bin/bash
# ===========================================
# 囚牛 (QiuNiu) 快速安装脚本（静默模式）
# 使用方法: ./quick-install.sh [MYSQL_PASSWORD]
# ===========================================

MYSQL_PASS=${1:-qiuniu123}

echo "========================================="
echo "   囚牛 (QiuNiu) 快速安装"
echo "========================================="
echo ""

# 安装 Java
if ! command -v java &> /dev/null; then
    echo "[1/6] 安装 Java..."
    apt-get update && apt-get install -y openjdk-11-jdk wget unzip
fi

# 安装 MySQL
echo "[2/6] 安装 MySQL..."
apt-get update && apt-get install -y mysql-server

# 启动 MySQL
systemctl start mysql
systemctl enable mysql

# 配置 MySQL
echo "[3/6] 配置数据库..."
mysql -e "ALTER USER 'root'@'localhost' IDENTIFIED WITH mysql_native_password BY '${MYSQL_PASS}';"
mysql -e "CREATE DATABASE IF NOT EXISTS qiuniu_db DEFAULT CHARACTER SET utf8mb4;"

# 下载 Tomcat
echo "[4/6] 下载 Tomcat..."
mkdir -p /opt/qiuniu
cd /opt/qiuniu
wget -q https://archive.apache.org/dist/tomcat/tomcat-9/v9.0.80/bin/apache-tomcat-9.0.80.tar.gz
tar -xzf apache-tomcat-9.0.80.tar.gz
mv apache-tomcat-9.0.80 tomcat
rm apache-tomcat-9.0.80.tar.gz

# 配置 Tomcat
cat > /opt/qiuniu/tomcat/conf/server.xml << 'EOF'
<?xml version="1.0" encoding="UTF-8"?>
<Server port="8005" shutdown="SHUTDOWN">
  <Service name="Catalina">
    <Connector port="8080" protocol="HTTP/1.1" connectionTimeout="20000"/>
    <Engine name="Catalina" defaultHost="localhost">
      <Host name="localhost" appBase="webapps" unpackWARs="true" autoDeploy="true"/>
    </Engine>
  </Service>
</Server>
EOF

# 创建配置文件
mkdir -p /opt/qiuniu/config
cat > /opt/qiuniu/config/db.properties << EOF
db.url=jdbc:mysql://localhost:3306/qiuniu_db?useSSL=false&serverTimezone=Asia/Shanghai&characterEncoding=utf-8&allowPublicKeyRetrieval=true
db.username=root
db.password=${MYSQL_PASS}
db.driver=com.mysql.cj.jdbc.Driver
db.pool.initialSize=5
db.pool.maxActive=20
db.pool.maxIdle=10
db.pool.minIdle=5
db.pool.maxWait=3000
ai.enabled=false
EOF

# 部署 WAR（如果存在）
if [ -f "/opt/qiuniu/qiuniu.war" ]; then
    cp /opt/qiuniu/qiuniu.war /opt/qiuniu/tomcat/webapps/
fi

# 清理默认应用
rm -rf /opt/qiuniu/tomcat/webapps/ROOT /opt/qiuniu/tomcat/webapps/docs

# 启动 Tomcat
echo "[5/6] 启动服务..."
chmod +x /opt/qiuniu/tomcat/bin/*.sh
/opt/qiuniu/tomcat/bin/startup.sh

echo "[6/6] 完成！"
echo ""
echo "========================================="
echo "   安装完成！"
echo "========================================="
echo "访问地址: http://localhost:8080/qiuniu/"
echo "默认账号: admin / admin123"
echo ""
