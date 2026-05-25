#!/bin/bash
# ===========================================
# 囚牛 (QiuNiu) 卸载脚本
# ===========================================

set -e

echo "========================================="
echo "   囚牛 (QiuNiu) 卸载脚本"
echo "========================================="
echo ""

read -p "确定要卸载囚牛吗？此操作会删除所有数据！(y/N): " confirm

if [[ "$confirm" != "y" && "$confirm" != "Y" ]]; then
    echo "取消卸载"
    exit 0
fi

# 停止服务
echo "[1/4] 停止服务..."
systemctl stop qiuniu 2>/dev/null || true
systemctl disable qiuniu 2>/dev/null || true
rm -f /etc/systemd/system/qiuniu.service

# 删除文件和目录
echo "[2/4] 删除文件..."
rm -rf /opt/qiuniu

# 删除数据库（可选）
read -p "是否删除数据库？数据将被永久删除！(y/N): " deldb
if [[ "$deldb" == "y" || "$deldb" == "Y" ]]; then
    echo "[3/4] 删除数据库..."
    mysql -e "DROP DATABASE IF EXISTS qiuniu_db;"
    echo "数据库已删除"
else
    echo "[3/4] 跳过数据库删除"
fi

# 删除用户（可选）
read -p "是否删除 MySQL root 密码配置？(y/N): " delpass
if [[ "$delpass" == "y" || "$delpass" == "Y" ]]; then
    echo "[4/4] 重置 MySQL root..."
    mysql -e "ALTER USER 'root'@'localhost' IDENTIFIED WITH auth_socket;"
fi

echo ""
echo "========================================="
echo "   卸载完成！"
echo "========================================="
