# -*- coding: utf-8 -*-
"""
环境配置 - 支持开发/测试/生产环境切换

使用方法:
1. 开发环境: 不设置 ENV_TYPE，默认使用 localhost
2. 生产环境: 设置 ENV_TYPE=production，使用 FLASK_BASE_URL 和 TOMCAT_BASE_URL

环境变量:
- ENV_TYPE: 环境类型 (development/production)
- FLASK_HOST: Flask 服务监听地址 (默认 0.0.0.0)
- FLASK_PORT: Flask 服务端口 (默认 5001)
- TOMCAT_HOST: Tomcat 服务地址 (默认 localhost)
- TOMCAT_PORT: Tomcat 服务端口 (默认 8080)
- TOMCAT_CONTEXT: Tomcat 上下文路径 (默认 /qiuniu)
"""

import os
import socket

# ─── 环境类型 ─────────────────────────────────────────────────────
ENV_TYPE = os.getenv("ENV_TYPE", "development")

# ─── Flask 服务配置 ────────────────────────────────────────────────
FLASK_HOST = os.getenv("FLASK_HOST", "0.0.0.0")
FLASK_PORT = int(os.getenv("FLASK_PORT", "5001"))

def _is_private_ip(ip):
    """判断是否为私网 IP（loopback/公网 IP 返回 False，私网返回 True）"""
    if ip.startswith("127."):
        return False  # loopback
    if ip.startswith("10."):
        return True
    if ip.startswith("192.168."):
        return True
    if ip.startswith("172."):
        # 172.16.0.0 - 172.31.255.255 是私网，但 172.16-31.x 是私网
        # 172.0-15.x 和 172.32+ 是公网（实际上 172.16-172.31 是 RFC 1918 私网）
        second = int(ip.split(".")[1])
        return 16 <= second <= 31  # 只排除 172.0-15 和 172.32+
    return False  # 公网 IP

def _detect_internal_ip():
    """自动检测本机 IP（用于生产环境 fallback）"""
    # 开发环境：直接返回 localhost（避免自动检测局域网 IP）
    if ENV_TYPE == "development":
        return f"http://localhost:{FLASK_PORT}"
    # 生产环境：优先读取环境变量 FLASK_BASE_URL（最可靠）
    env_url = os.getenv("FLASK_BASE_URL", "")
    if env_url and env_url.startswith("http"):
        return env_url

    # 自动检测本机 IP（排除 loopback）
    try:
        # 方法：连接外网 DNS 获取本机出口 IP
        s = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
        s.settimeout(3)
        s.connect(("8.8.8.8", 80))
        ip = s.getsockname()[0]
        s.close()
        if ip.startswith("127."):
            return f"http://localhost:{FLASK_PORT}"
        return f"http://{ip}:{FLASK_PORT}"
    except Exception:
        pass

    # Fallback：hostname 解析
    try:
        hostname = socket.gethostname()
        _, _, ips = socket.gethostbyname_ex(hostname)
        for ip in ips:
            if not ip.startswith(("127.", "169.254")):
                return f"http://{ip}:{FLASK_PORT}"
    except Exception:
        pass

    return f"http://localhost:{FLASK_PORT}"

# Flask 基础 URL（用于生成静态资源访问路径）
# 生产环境：自动检测本机 IP
# 开发环境：设置 FLASK_BASE_URL=http://localhost:5001
# 优先级：环境变量 FLASK_BASE_URL > 自动检测 > localhost
FLASK_BASE_URL = _detect_internal_ip()

# ─── Tomcat 服务配置 ────────────────────────────────────────────────
TOMCAT_HOST = os.getenv("TOMCAT_HOST", "localhost")
TOMCAT_PORT = int(os.getenv("TOMCAT_PORT", "8080"))
TOMCAT_CONTEXT = os.getenv("TOMCAT_CONTEXT", "/qiuniu")

# Tomcat 基础 URL
# 开发环境: http://localhost:8080/qiuniu
# 生产环境: http://内网IP:8080/qiuniu 或 http://域名
TOMCAT_BASE_URL = os.getenv(
    "TOMCAT_BASE_URL",
    f"http://{TOMCAT_HOST}:{TOMCAT_PORT}{TOMCAT_CONTEXT}"
)

# ─── CORS 配置 ─────────────────────────────────────────────────────
CORS_ORIGINS = os.getenv("CORS_ORIGINS", f"http://{TOMCAT_HOST}:{TOMCAT_PORT}")

# ─── 静态资源路径 ───────────────────────────────────────────────────
# 头像访问 URL 前缀
AVATAR_URL_PREFIX = f"{FLASK_BASE_URL}/static/avatars"

# ─── 辅助函数 ───────────────────────────────────────────────────────
def get_avatar_url(filename):
    """生成头像完整 URL"""
    if not filename:
        return ""
    if filename.startswith("http"):
        return filename
    return f"{AVATAR_URL_PREFIX}/{filename}"

def get_tomcat_url(path):
    """生成 Tomcat 资源完整 URL"""
    return f"{TOMCAT_BASE_URL}{path}"
