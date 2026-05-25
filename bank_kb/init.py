#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
QiuNiu 安装初始化脚本
在新环境中部署后运行此脚本，完成目录创建和配置检查。

用法:
    python init.py                    # 使用默认配置
    python init.py --upload-dir /path/to/uploads   # 自定义上传目录
    UPLOAD_DIR=/path/to/uploads python init.py      # 通过环境变量指定
"""

import os
import sys
import platform

# 添加项目根目录到 sys.path
BASE_DIR = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, BASE_DIR)

print("=" * 60)
print("  QiuNiu 安装初始化脚本")
print("=" * 60)
print(f"项目目录: {BASE_DIR}")
print(f"操作系统: {platform.system()} {platform.release()}")
print()

# ─── 1. 加载配置 ────────────────────────────────────────────────
print("[1/5] 加载配置...")
try:
    from config import *
    print(f"  ✓ MySQL: {MYSQL_HOST}:{MYSQL_PORT}/{MYSQL_DATABASE}")
    print(f"  ✓ LLM_MODEL: {LLM_MODEL}")
    print(f"  ✓ RETRIEVAL_MODE: {RETRIEVAL_MODE}")
    UPLOAD_DIR = globals().get('UPLOAD_DIR', os.path.join(BASE_DIR, 'uploads', 'documents'))
    print(f"  ✓ UPLOAD_DIR: {UPLOAD_DIR}")
except Exception as e:
    print(f"  ✗ 配置加载失败: {e}")
    sys.exit(1)

# ─── 2. 创建所需目录 ────────────────────────────────────────────
print()
print("[2/5] 创建所需目录...")

dirs_to_create = [
    ("上传文件存储", UPLOAD_DIR),
    ("Flask 静态头像", os.path.join(BASE_DIR, 'static', 'avatars')),
    ("Flask 静态上传访问", os.path.join(BASE_DIR, 'static', 'uploads')),
]

created_count = 0
for desc, d in dirs_to_create:
    try:
        os.makedirs(d, exist_ok=True)
        if os.path.exists(d):
            print(f"  ✓ {desc}: {d}")
            created_count += 1
        else:
            print(f"  ✗ {desc}: 创建失败")
    except Exception as e:
        print(f"  ✗ {desc}: {e}")

print(f"  已创建/确认 {created_count}/{len(dirs_to_create)} 个目录")

# ─── 3. 检查 Python 依赖 ────────────────────────────────────────
print()
print("[3/5] 检查 Python 依赖...")

required_packages = [
    ("flask", "flask"),
    ("mysql.connector", "mysql-connector-python"),
    ("werkzeug", "werkzeug"),
    ("docx", "python-docx"),
    ("PyPDF2", "PyPDF2"),
    ("pptx", "python-pptx"),
    ("jieba", "jieba"),
    ("rank_bm25", "rank-bm25"),
]

missing = []
for import_name, pip_name in required_packages:
    try:
        __import__(import_name)
        print(f"  ✓ {import_name}")
    except ImportError:
        missing.append(pip_name)
        print(f"  ✗ {import_name} (pip install {pip_name})")

if missing:
    print()
    print("  缺少依赖，请运行:")
    print(f"    pip install {' '.join(missing)}")
else:
    print("  所有依赖已安装")

# ─── 4. 检查 MySQL 连接 ─────────────────────────────────────────
print()
print("[4/5] 检查 MySQL 连接...")

try:
    import mysql.connector
    conn = mysql.connector.connect(
        host=MYSQL_HOST,
        port=MYSQL_PORT,
        user=MYSQL_USER,
        password=MYSQL_PASSWORD,
        database=MYSQL_DATABASE,
        connection_timeout=5,
    )
    cursor = conn.cursor()
    cursor.execute("SELECT 1")
    cursor.fetchone()
    cursor.close()
    conn.close()
    print(f"  ✓ MySQL 连接成功: {MYSQL_HOST}:{MYSQL_PORT}/{MYSQL_DATABASE}")
except Exception as e:
    print(f"  ✗ MySQL 连接失败: {e}")
    print("  请检查 MySQL 是否启动，以及 config.py 中的连接配置")

# ─── 5. 检查 Milvus 连接（可选）────────────────────────────────
print()
print("[5/5] 检查 Milvus 连接（可选）...")

if RETRIEVAL_MODE in ("vector", "hybrid"):
    try:
        from pymilvus import connections
        connections.connect(
            alias="default",
            host=MILVUS_HOST,
            port=MILVUS_PORT,
            timeout=5,
        )
        print(f"  ✓ Milvus 连接成功: {MILVUS_HOST}:{MILVUS_PORT}")
        connections.disconnect("default")
    except Exception as e:
        print(f"  ⚠ Milvus 连接失败（将降级为 BM25-only 模式）: {e}")
        print("  （如需使用向量检索，请启动 Milvus）")
else:
    print(f"  ⊙ RETRIEVAL_MODE={RETRIEVAL_MODE}，跳过 Milvus 检查")

# ─── 完成 ────────────────────────────────────────────────────────
print()
print("=" * 60)
print("  初始化完成")
print()
print("下一步:")
print("  1. 启动 Flask:   python app.py")
print("  2. 启动 Tomcat:  C:\\apache-tomcat-9.0.96\\bin\\startup.bat")
print("  3. 访问:          http://localhost:8080/qiuniu/knowledge-base.jsp")
print("=" * 60)
