#!/bin/bash
# ===========================================
# 囚牛 (QiuNiu) Linux 一键安装脚本
# 适用系统：CentOS 7/8, Ubuntu 18/20/22, Debian 10/11
# 用法: sudo bash install.sh
# ===========================================

set -e

# ── 默认配置 ──────────────────────────────────
INSTALL_DIR="/opt/qiuniu"
TOMCAT_VERSION="9.0.96"
TOMCAT_URL="https://archive.apache.org/dist/tomcat/tomcat-9/${TOMCAT_VERSION}/apache-tomcat-${TOMCAT_VERSION}.tar.gz"
MYSQL_HOST="localhost"
MYSQL_PORT="3306"
MYSQL_DB="qiuniu_db"
MYSQL_USER="root"
MYSQL_PASS=""
SERVICE_PORT=8080
FLASK_PORT=5001
PYTHON_VER="python3"

APP_NAME="qiuniu"
APP_WAR="${APP_NAME}.war"

# ── 颜色定义 ──────────────────────────────────
RED='\033[0;31m'; GREEN='\033[0;32m'; YELLOW='\033[1;33m'
BLUE='\033[0;34m'; NC='\033[0m'

log_info()    { echo -e "${GREEN}[INFO]${NC} $1"; }
log_warn()    { echo -e "${YELLOW}[WARN]${NC} $1"; }
log_error()   { echo -e "${RED}[ERROR]${NC} $1"; }
log_header()  { echo -e "\n${BLUE}========================================${NC}"; }

show_banner() {
    log_header
    echo -e "${BLUE}   囚牛 (QiuNiu) Linux 一键安装${NC}"
    log_header
    echo ""
}

check_root() {
    [[ $EUID -ne 0 ]] && { log_error "请使用 root 运行：sudo $0"; exit 1; }
}

detect_os() {
    log_info "检测操作系统..."
    if [[ -f /etc/redhat-release ]]; then
        OS="centos"; log_info "检测到 CentOS/RHEL"
    elif [[ -f /etc/debian_version ]]; then
        OS="debian"; log_info "检测到 Debian/Ubuntu"
    else
        log_error "不支持的操作系统"; exit 1
    fi
}

install_deps() {
    log_info "安装系统依赖..."
    if [[ "$OS" == "centos" ]]; then
        yum install -y wget unzip git curl mysql-server mysql-devel
        systemctl enable mysqld; systemctl start mysqld
    else
        apt-get update -y
        apt-get install -y wget unzip git curl mysql-server libmysqlclient-dev
        systemctl enable mysql; systemctl start mysql
    fi

    # Java 17
    if ! command -v java &>/dev/null; then
        log_info "安装 OpenJDK 17..."
        if [[ "$OS" == "centos" ]]; then
            yum install -y java-17-openjdk java-17-openjdk-devel
        else
            apt-get install -y openjdk-17-jdk
        fi
    fi

    # Python 3.9+
    if ! command -v python3 &>/dev/null; then
        log_info "安装 Python 3..."
        if [[ "$OS" == "centos" ]]; then
            yum install -y python3 python3-pip
        else
            apt-get install -y python3 python3-pip
        fi
    fi

    # Maven
    if ! command -v mvn &>/dev/null; then
        log_info "安装 Maven..."
        cd /tmp
        wget -q https://dlcdn.apache.org/maven/maven-3/3.9.9/binaries/apache-maven-3.9.9-bin.tar.gz
        tar -xzf apache-maven-3.9.9-bin.tar.gz -C /opt/
        ln -sf /opt/apache-maven-3.9.9/bin/mvn /usr/bin/mvn
        rm -f apache-maven-3.9.9-bin.tar.gz
    fi

    export JAVA_HOME=$(dirname $(dirname $(readlink -f $(which java))))
    echo "export JAVA_HOME=${JAVA_HOME}" >> /etc/profile
    echo 'export PATH=$PATH:$JAVA_HOME/bin' >> /etc/profile
    log_info "依赖安装完成"
}

create_dirs() {
    log_info "创建目录结构..."
    mkdir -p ${INSTALL_DIR}/{tomcat,logs,backup,config,flask}
    log_info "目录创建完成"
}

setup_tomcat() {
    log_info "安装 Apache Tomcat ${TOMCAT_VERSION}..."
    cd /tmp
    if [[ ! -f "apache-tomcat-${TOMCAT_VERSION}.tar.gz" ]]; then
        wget -q --no-check-certificate "$TOMCAT_URL" || {
            log_warn "从镜像站下载 Tomcat..."
            wget -q "https://mirrors.tuna.tsinghua.edu.cn/apache/tomcat/tomcat-9/${TOMCAT_VERSION}/apache-tomcat-${TOMCAT_VERSION}.tar.gz"
        }
    fi
    tar -xzf apache-tomcat-${TOMCAT_VERSION}.tar.gz
    cp -r apache-tomcat-${TOMCAT_VERSION}/* ${INSTALL_DIR}/tomcat/
    rm -rf apache-tomcat-${TOMCAT_VERSION}

    # 优化 Tomcat 配置
    sed -i "s/port=\"8080\"/port=\"${SERVICE_PORT}\"/" ${INSTALL_DIR}/tomcat/conf/server.xml
    cat > ${INSTALL_DIR}/tomcat/bin/setenv.sh << 'EOF'
#!/bin/bash
export JAVA_OPTS="-Xms512m -Xmx1024m -XX:+UseG1GC"
EOF
    chmod +x ${INSTALL_DIR}/tomcat/bin/setenv.sh

    # 清理无用应用
    rm -rf ${INSTALL_DIR}/tomcat/webapps/{ROOT,docs,examples,host-manager,manager}
    log_info "Tomcat 配置完成"
}

setup_database() {
    log_info "初始化 MySQL 数据库..."
    mysql -h"$MYSQL_HOST" -P"$MYSQL_PORT" -u"$MYSQL_USER" -p"${MYSQL_PASS}" << EOF
CREATE DATABASE IF NOT EXISTS ${MYSQL_DB} DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
EOF
    # 导入初始化脚本（假设 init-db.sql 在安装包中）
    if [[ -f "${INSTALL_DIR}/config/init-db.sql" ]]; then
        mysql -h"$MYSQL_HOST" -P"$MYSQL_PORT" -u"$MYSQL_USER" -p"${MYSQL_PASS}" < "${INSTALL_DIR}/config/init-db.sql"
        log_info "数据库表结构导入完成"
    else
        log_warn "未找到 init-db.sql，请手动导入数据库"
    fi
}

setup_flask() {
    log_info "配置 Flask RAG 服务..."
    FLASK_DIR="${INSTALL_DIR}/flask"

    # 安装 Python 依赖
    if [[ -f "${FLASK_DIR}/requirements.txt" ]]; then
        pip3 install -r "${FLASK_DIR}/requirements.txt" -i https://mirrors.aliyun.com/pypi/simple/ -q
        log_info "Flask 依赖安装完成"
    else
        log_warn "未找到 requirements.txt"
    fi

    # 创建 Flask systemd 服务
    cat > /etc/systemd/system/qiuniu-flask.service << EOF
[Unit]
Description=QiuNiu Flask RAG Service
After=network.target mysql.service
Wants=mysql.service

[Service]
Type=simple
User=root
WorkingDirectory=${FLASK_DIR}
ExecStart=/usr/bin/python3 ${FLASK_DIR}/app.py
Restart=on-failure
RestartSec=5
Environment="PYTHONUNBUFFERED=1"

[Install]
WantedBy=multi-user.target
EOF

    systemctl daemon-reload
    systemctl enable qiuniu-flask.service
    log_info "Flask 系统服务配置完成"
}

setup_app() {
    log_info "部署 Tomcat 应用..."
    if [[ -f "${INSTALL_DIR}/${APP_WAR}" ]]; then
        cp "${INSTALL_DIR}/${APP_WAR}" "${INSTALL_DIR}/tomcat/webapps/"
        log_info "WAR 包已部署"
    else
        log_warn "未找到 ${APP_WAR}，请手动放入 ${INSTALL_DIR}/"
    fi

    # 创建配置文件模板
    cat > ${INSTALL_DIR}/config/db.properties << EOF
# 囚牛数据库配置
db.url=jdbc:mysql://${MYSQL_HOST}:${MYSQL_PORT}/${MYSQL_DB}?useSSL=false&serverTimezone=Asia/Shanghai&characterEncoding=utf-8&allowPublicKeyRetrieval=true
db.username=${MYSQL_USER}
db.password=${MYSQL_PASS}
db.driver=com.mysql.cj.jdbc.Driver
db.pool.initialSize=5
db.pool.maxActive=20
db.pool.maxIdle=10
db.pool.minIdle=5
db.pool.maxWait=3000
EOF

    cat > ${INSTALL_DIR}/config/flask_config.py << EOF
# Flask RAG 服务配置（将此文件复制到 flask/config.py）
MYSQL_HOST = '${MYSQL_HOST}'
MYSQL_PORT = ${MYSQL_PORT}
MYSQL_USER = '${MYSQL_USER}'
MYSQL_PASSWORD = '${MYSQL_PASS}'
MYSQL_DATABASE = '${MYSQL_DB}'

LLM_API_URL = "https://api.siliconflow.cn/v1/chat/completions"
LLM_API_KEY = "your_api_key_here"
LLM_MODEL = "Qwen/Qwen2.5-72B-Instruct"

RETRIEVAL_MODE = "bm25"
TOP_K = 5
CONFIDENCE_THRESHOLD = 0.2

TOMCAT_CONTEXT = "/qiuniu"
WEBAPP_ROOT = "${INSTALL_DIR}/tomcat/webapps/qiuniu"
EOF

    log_info "配置文件模板已生成：${INSTALL_DIR}/config/"
}

create_tomcat_service() {
    log_info "创建 Tomcat 系统服务..."
    cat > /etc/systemd/system/${APP_NAME}.service << EOF
[Unit]
Description=QiuNiu Tomcat Service
After=network.target mysql.service
Wants=mysql.service

[Service]
Type=forking
Environment="JAVA_HOME=${JAVA_HOME}"
ExecStart=${INSTALL_DIR}/tomcat/bin/startup.sh
ExecStop=${INSTALL_DIR}/tomcat/bin/shutdown.sh
Restart=on-failure
RestartSec=5

[Install]
WantedBy=multi-user.target
EOF
    systemctl daemon-reload
    systemctl enable ${APP_NAME}.service
    log_info "Tomcat 系统服务创建完成"
}

create_mgmt_scripts() {
    log_info "创建管理脚本..."
    cat > ${INSTALL_DIR}/start.sh << 'EOF'
#!/bin/bash
systemctl start qiuniu
systemctl start qiuniu-flask
echo "囚牛服务已启动"
EOF

    cat > ${INSTALL_DIR}/stop.sh << 'EOF'
#!/bin/bash
systemctl stop qiuniu
systemctl stop qiuniu-flask
echo "囚牛服务已停止"
EOF

    cat > ${INSTALL_DIR}/restart.sh << 'EOF'
#!/bin/bash
systemctl restart qiuniu
systemctl restart qiuniu-flask
echo "囚牛服务已重启"
EOF

    cat > ${INSTALL_DIR}/status.sh << 'EOF'
#!/bin/bash
echo "── Tomcat 服务 ─────────────────"
systemctl status qiuniu --no-pager
echo ""
echo "── Flask 服务 ──────────────────"
systemctl status qiuniu-flask --no-pager
echo ""
echo "── 端口检测 ───────────────────"
curl -s -o /dev/null -w "Tomcat (:%{http_code})\n" http://localhost:8080/qiuniu/ || echo "Tomcat: 未响应"
curl -s -o /dev/null -w "Flask  (:%{http_code})\n" http://localhost:5001/status || echo "Flask: 未响应"
EOF

    cat > ${INSTALL_DIR}/logs.sh << 'EOF'
#!/bin/bash
tail -f ${INSTALL_DIR}/tomcat/logs/catalina.out
EOF

    chmod +x ${INSTALL_DIR}/*.sh
    log_info "管理脚本创建完成"
}

configure_firewall() {
    log_info "配置防火墙..."
    if [[ "$OS" == "centos" ]] && systemctl is-active --quiet firewalld; then
        firewall-cmd --permanent --add-port=${SERVICE_PORT}/tcp
        firewall-cmd --permanent --add-port=${FLASK_PORT}/tcp
        firewall-cmd --reload
        log_info "防火墙端口 ${SERVICE_PORT}, ${FLASK_PORT} 已开放"
    elif [[ "$OS" == "debian" ]] && command -v ufw &>/dev/null && ufw status | grep -q "active"; then
        ufw allow ${SERVICE_PORT}/tcp
        ufw allow ${FLASK_PORT}/tcp
        log_info "防火墙端口 ${SERVICE_PORT}, ${FLASK_PORT} 已开放"
    fi
}

show_complete() {
    echo ""
    log_header
    echo -e "${GREEN}   囚牛 (QiuNiu) 安装完成！${NC}"
    log_header
    echo ""
    echo -e "安装目录: ${BLUE}${INSTALL_DIR}${NC}"
    echo -e "访问地址: ${BLUE}http://<服务器IP>:${SERVICE_PORT}/qiuniu/${NC}"
    echo ""
    echo -e "${YELLOW}管理命令:${NC}"
    echo "  启动所有服务:  ${INSTALL_DIR}/start.sh"
    echo "  停止所有服务:  ${INSTALL_DIR}/stop.sh"
    echo "  重启服务:      ${INSTALL_DIR}/restart.sh"
    echo "  查看状态:      ${INSTALL_DIR}/status.sh"
    echo "  查看日志:      ${INSTALL_DIR}/logs.sh"
    echo ""
    echo -e "${YELLOW}配置文件:${NC}"
    echo "  数据库配置:    ${INSTALL_DIR}/config/db.properties"
    echo "  Flask 配置:    ${INSTALL_DIR}/config/flask_config.py"
    echo "  Tomcat 配置:  ${INSTALL_DIR}/tomcat/conf/server.xml"
    echo ""
    echo -e "${YELLOW}下一步:${NC}"
    echo "  1. 将 qiuniu.war 放入: ${INSTALL_DIR}/"
    echo "  2. 将 Flask 目录放入: ${INSTALL_DIR}/flask/"
    echo "  3. 编辑 Flask 配置，填写 LLM_API_KEY"
    echo "  4. 运行: ${INSTALL_DIR}/start.sh"
    echo ""
}

# ── 交互式配置 ────────────────────────────────
read_config() {
    echo ""
    read -p "MySQL 密码 [直接回车跳过]: " MYSQL_PASS
    read -p "服务端口 [默认 ${SERVICE_PORT}]: " input_port
    SERVICE_PORT=${input_port:-$SERVICE_PORT}
    read -p "Flask 端口 [默认 ${FLASK_PORT}]: " input_flask
    FLASK_PORT=${input_flask:-$FLASK_PORT}
}

# ── 主流程 ────────────────────────────────────
main() {
    show_banner
    check_root
    detect_os
    read_config
    install_deps
    create_dirs
    setup_tomcat
    setup_database
    setup_flask
    setup_app
    create_tomcat_service
    create_mgmt_scripts
    configure_firewall
    show_complete
}

main "$@"
