#!/bin/bash
# ===========================================
# 囚牛 (QiuNiu) 一键安装脚本
# 适用系统：CentOS 7/8, Ubuntu 18/20/22, Debian 10/11
# ===========================================

set -e

# 颜色定义
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# 默认配置
INSTALL_DIR="/opt/qiuniu"
TOMCAT_VERSION="9.0.80"
TOMCAT_URL="https://archive.apache.org/dist/tomcat/tomcat-9/${TOMCAT_VERSION}/apache-tomcat-${TOMCAT_VERSION}.tar.gz"
MYSQL_HOST="localhost"
MYSQL_PORT="3306"
MYSQL_DB="qiuniu_db"
MYSQL_USER="root"
MYSQL_PASS=""
SERVICE_PORT=8080
QIUNIU_PORT=8088

# 应用名称
APP_NAME="qiuniu"
APP_WAR="${APP_NAME}.war"

# 日志函数
log_info() {
    echo -e "${GREEN}[INFO]${NC} $1"
}

log_warn() {
    echo -e "${YELLOW}[WARN]${NC} $1"
}

log_error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

# 显示欢迎信息
show_banner() {
    echo ""
    echo -e "${BLUE}========================================${NC}"
    echo -e "${BLUE}   囚牛 (QiuNiu) 一键安装脚本${NC}"
    echo -e "${BLUE}========================================${NC}"
    echo ""
}

# 检查是否为 root 用户
check_root() {
    if [[ $EUID -ne 0 ]]; then
        log_error "请使用 root 用户运行此脚本！"
        echo "或者使用: sudo $0"
        exit 1
    fi
}

# 检查系统类型
check_os() {
    log_info "检测操作系统..."
    
    if [[ -f /etc/redhat-release ]]; then
        OS="centos"
        log_info "检测到 CentOS/RHEL 系统"
    elif [[ -f /etc/debian_version ]]; then
        OS="debian"
        log_info "检测到 Debian/Ubuntu 系统"
    else
        log_error "不支持的操作系统"
        exit 1
    fi
}

# 安装依赖
install_dependencies() {
    log_info "安装系统依赖..."
    
    if [[ "$OS" == "centos" ]]; then
        yum install -y wget unzip git curl mysql-server mysql-devel
        systemctl enable mysqld
        systemctl start mysqld
    else
        apt-get update
        apt-get install -y wget unzip git curl mysql-server libmysqlclient-dev
        systemctl enable mysql
        systemctl start mysql
    fi
    
    # 检查 Java
    if ! command -v java &> /dev/null; then
        log_info "安装 Java 11..."
        if [[ "$OS" == "centos" ]]; then
            yum install -y java-11-openjdk java-11-openjdk-devel
        else
            apt-get install -y openjdk-11-jdk
        fi
    fi
    
    # 设置 JAVA_HOME
    if ! grep -q "JAVA_HOME" /etc/profile; then
        echo "export JAVA_HOME=\$(dirname \$(dirname \$(readlink -f \$(which java))))" >> /etc/profile
        echo "export PATH=\$PATH:\$JAVA_HOME/bin" >> /etc/profile
    fi
    export JAVA_HOME=$(dirname $(dirname $(readlink -f $(which java))))
    
    log_info "依赖安装完成"
}

# 创建目录结构
create_dirs() {
    log_info "创建目录结构..."
    
    mkdir -p ${INSTALL_DIR}
    mkdir -p ${INSTALL_DIR}/tomcat
    mkdir -p ${INSTALL_DIR}/logs
    mkdir -p ${INSTALL_DIR}/backup
    mkdir -p ${INSTALL_DIR}/config
    
    log_info "目录创建完成"
}

# 下载并配置 Tomcat
setup_tomcat() {
    log_info "下载 Apache Tomcat ${TOMCAT_VERSION}..."
    
    cd /tmp
    if [[ ! -f "apache-tomcat-${TOMCAT_VERSION}.tar.gz" ]]; then
        wget -q ${TOMCAT_URL}
    fi
    
    log_info "解压 Tomcat..."
    tar -xzf apache-tomcat-${TOMCAT_VERSION}.tar.gz
    rm -rf ${INSTALL_DIR}/tomcat/*
    mv apache-tomcat-${TOMCAT_VERSION}/* ${INSTALL_DIR}/tomcat/
    
    # 配置 Tomcat
    cat > ${INSTALL_DIR}/tomcat/conf/server.xml << EOF
<?xml version="1.0" encoding="UTF-8"?>
<Server port="8005" shutdown="SHUTDOWN">
  <Listener className="org.apache.catalina.startup.VersionLoggerListener" />
  <Listener className="org.apache.catalina.core.AprLifecycleListener" SSLEngine="on" />
  <Listener className="org.apache.catalina.core.JreMemoryLeakPreventionListener" />
  <Listener className="org.apache.catalina.mbeans.GlobalResourcesLifecycleListener" />
  <Listener className="org.apache.catalina.core.ThreadLocalLeakPreventionListener" />

  <GlobalNamingResources>
    <Resource name="UserDatabase" auth="Container"
              type="org.apache.catalina.UserDatabase"
              description="User database that can be updated and saved"
              factory="org.apache.catalina.users.MemoryUserDatabaseFactory"
              pathname="conf/tomcat-users.xml" />
  </GlobalNamingResources>

  <Service name="Catalina">
    <Connector port="${SERVICE_PORT}" protocol="HTTP/1.1"
               connectionTimeout="20000"
               redirectPort="8443"
               maxThreads="200"
               minSpareThreads="10"
               acceptCount="100" />

    <Engine name="Catalina" defaultHost="localhost">
      <Realm className="org.apache.catalina.realm.LockOutRealm">
        <Realm className="org.apache.catalina.realm.UserDatabaseRealm"
               resourceName="UserDatabase"/>
      </Realm>

      <Host name="localhost"  appBase="webapps"
            unpackWARs="true" autoDeploy="true">
        <Valve className="org.apache.catalina.valves.AccessLogValve" directory="logs"
               prefix="localhost_access_log" suffix=".txt"
               pattern="%h %l %u %t &quot;%r&quot; %s %b" />
      </Host>
    </Engine>
  </Service>
</Server>
EOF

    # 设置 Tomcat 内存
    cat > ${INSTALL_DIR}/tomcat/bin/setenv.sh << 'EOF'
#!/bin/bash
JAVA_OPTS="-Xms512m -Xmx1024m -XX:+UseG1GC"
CATALINA_HOME=/opt/qiuniu/tomcat
CATALINA_BASE=/opt/qiuniu/tomcat
EOF
    chmod +x ${INSTALL_DIR}/tomcat/bin/setenv.sh
    
    # 清理默认应用
    rm -rf ${INSTALL_DIR}/tomcat/webapps/ROOT
    rm -rf ${INSTALL_DIR}/tomcat/webapps/docs
    rm -rf ${INSTALL_DIR}/tomcat/webapps/examples
    rm -rf ${INSTALL_DIR}/tomcat/webapps/host-manager
    rm -rf ${INSTALL_DIR}/tomcat/webapps/manager
    
    log_info "Tomcat 配置完成"
}

# 配置数据库
setup_database() {
    log_info "配置 MySQL 数据库..."
    
    # 创建数据库
    if [[ "$OS" == "centos" ]]; then
        mysql -u root << EOF
CREATE DATABASE IF NOT EXISTS ${MYSQL_DB} DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
EOF
    else
        mysql -u root << EOF
CREATE DATABASE IF NOT EXISTS ${MYSQL_DB} DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
EOF
    fi
    
    log_info "数据库 ${MYSQL_DB} 创建完成"
}

# 配置应用
setup_app() {
    log_info "配置应用程序..."
    
    # 创建数据库配置文件
    cat > ${INSTALL_DIR}/config/db.properties << EOF
# ===========================================
# 囚牛 (QiuNiu) 数据库配置文件
# ===========================================

# MySQL 数据库连接 URL
db.url=jdbc:mysql://${MYSQL_HOST}:${MYSQL_PORT}/${MYSQL_DB}?useSSL=false&serverTimezone=Asia/Shanghai&characterEncoding=utf-8&allowPublicKeyRetrieval=true

# MySQL 用户名
db.username=${MYSQL_USER}

# MySQL 密码
db.password=${MYSQL_PASS}

# MySQL 驱动类名
db.driver=com.mysql.cj.jdbc.Driver

# 数据库连接池配置
db.pool.initialSize=5
db.pool.maxActive=20
db.pool.maxIdle=10
db.pool.minIdle=5
db.pool.maxWait=3000

# AI 大模型配置（可选）
ai.provider=openai
ai.api.url=https://api.siliconflow.cn/v1
ai.api.key=your_api_key_here
ai.model=Qwen/Qwen2.5-72B-Instruct
ai.timeout=60
ai.maxTokens=4096
ai.enabled=false
EOF

    # 复制 WAR 文件到 Tomcat
    if [[ -f "${INSTALL_DIR}/${APP_WAR}" ]]; then
        cp ${INSTALL_DIR}/${APP_WAR} ${INSTALL_DIR}/tomcat/webapps/
        log_info "WAR 文件已部署"
    fi
    
    # 创建日志目录软链接
    ln -sf ${INSTALL_DIR}/logs ${INSTALL_DIR}/tomcat/logs/app_logs
    
    log_info "应用配置完成"
}

# 创建服务脚本
create_service() {
    log_info "创建系统服务..."
    
    cat > /etc/systemd/system/${APP_NAME}.service << EOF
[Unit]
Description=QiuNiu Application
After=network.target mysql.service
Wants=mysql.service

[Service]
Type=forking
User=root
ExecStart=${INSTALL_DIR}/tomcat/bin/startup.sh
ExecStop=${INSTALL_DIR}/tomcat/bin/shutdown.sh
Restart=on-failure
StandardOutput=append:${INSTALL_DIR}/logs/catalina.out
StandardError=append:${INSTALL_DIR}/logs/catalina.out

[Install]
WantedBy=multi-user.target
EOF

    systemctl daemon-reload
    systemctl enable ${APP_NAME}.service
    
    log_info "系统服务创建完成"
}

# 创建便捷管理脚本
create_management_scripts() {
    log_info "创建管理脚本..."
    
    # 启动脚本
    cat > ${INSTALL_DIR}/start.sh << 'EOF'
#!/bin/bash
cd /opt/qiuniu/tomcat/bin
./startup.sh
echo "请访问: http://localhost:8080/qiuniu/"
EOF

    # 停止脚本
    cat > ${INSTALL_DIR}/stop.sh << 'EOF'
#!/bin/bash
cd /opt/qiuniu/tomcat/bin
./shutdown.sh
EOF

    # 状态检查脚本
    cat > ${INSTALL_DIR}/status.sh << 'EOF'
#!/bin/bash
if pgrep -f "tomcat" > /dev/null; then
    echo "囚牛服务运行中"
    curl -s -o /dev/null -w "HTTP Status: %{http_code}\n" http://localhost:8080/qiuniu/
else
    echo "囚牛服务未运行"
fi
EOF

    # 查看日志脚本
    cat > ${INSTALL_DIR}/logs.sh << 'EOF'
#!/bin/bash
tail -f /opt/qiuniu/logs/catalina.out
EOF

    chmod +x ${INSTALL_DIR}/*.sh
    log_info "管理脚本创建完成"
}

# 配置防火墙
configure_firewall() {
    log_info "配置防火墙..."
    
    if [[ "$OS" == "centos" ]]; then
        if systemctl is-active --quiet firewalld; then
            firewall-cmd --permanent --add-port=${SERVICE_PORT}/tcp
            firewall-cmd --reload
            log_info "防火墙端口 ${SERVICE_PORT} 已开放"
        fi
    else
        if systemctl is-active --quiet ufw; then
            ufw allow ${SERVICE_PORT}/tcp
            log_info "防火墙端口 ${SERVICE_PORT} 已开放"
        fi
    fi
}

# 显示完成信息
show_complete() {
    echo ""
    echo -e "${GREEN}========================================${NC}"
    echo -e "${GREEN}   囚牛 (QiuNiu) 安装完成！${NC}"
    echo -e "${GREEN}========================================${NC}"
    echo ""
    echo -e "安装目录: ${BLUE}${INSTALL_DIR}${NC}"
    echo -e "访问地址: ${BLUE}http://<服务器IP>:${SERVICE_PORT}/qiuniu/${NC}"
    echo ""
    echo -e "${YELLOW}管理命令:${NC}"
    echo "  启动服务: systemctl start ${APP_NAME}  或  ${INSTALL_DIR}/start.sh"
    echo "  停止服务: systemctl stop ${APP_NAME}   或  ${INSTALL_DIR}/stop.sh"
    echo "  查看状态: ${INSTALL_DIR}/status.sh"
    echo "  查看日志: ${INSTALL_DIR}/logs.sh"
    echo ""
    echo -e "${YELLOW}配置文件:${NC}"
    echo "  数据库配置: ${INSTALL_DIR}/config/db.properties"
    echo "  Tomcat配置: ${INSTALL_DIR}/tomcat/conf/server.xml"
    echo ""
    echo -e "${RED}首次使用请修改数据库密码！${NC}"
    echo ""
}

# 主函数
main() {
    show_banner
    check_root
    check_os
    
    # 交互式配置
    echo ""
    read -p "请输入 MySQL 密码 [直接回车跳过]: " MYSQL_PASS
    read -p "请输入服务端口 [默认 ${SERVICE_PORT}]: " INPUT_PORT
    SERVICE_PORT=${INPUT_PORT:-${SERVICE_PORT}}
    
    install_dependencies
    create_dirs
    setup_tomcat
    setup_database
    setup_app
    create_service
    create_management_scripts
    configure_firewall
    
    # 启动服务
    log_info "启动服务..."
    systemctl start ${APP_NAME}
    sleep 5
    
    show_complete
}

# 运行主函数
main "$@"
