# BankKB RAG 知识库安装手册

## 1. 系统要求

| 组件 | 版本 | 用途 |
|------|------|------|
| Windows | 10/11 x64 | 操作系统 |
| JDK | 17 | Tomcat 运行时 |
| MySQL | 9.6 | 知识库存储 |
| Python | 3.10+ | Flask 服务 |
| Tomcat | 9.0.96 | QiuNiu Web 应用 |

磁盘空间 ≥ 2GB

## 2. MySQL 安装与初始化

```powershell
# 安装 MySQL 9.6 到 D:\Program Files\MySQL\MySQL Server 9.6\

# 初始化（无密码模式）
mysqld --initialize-insecure --console

# 启动服务
net start MySQL

# 设置 root 密码
mysql -u root -e "ALTER USER 'root'@'localhost' IDENTIFIED BY 'NewPassword123!';"

# 创建数据库
mysql -u root -pNewPassword123! -e "CREATE DATABASE qiuniu_db CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;"

# 导入初始数据
mysql -u root -pNewPassword123! qiuniu_db < D:\qiuniu_install\qiuniu-src\sql\init-db.sql
```

## 3. Tomcat 部署

```powershell
# 解压 Tomcat 到 C:\apache-tomcat-9.0.96

# 部署应用（展开目录方式，不要 WAR）
Copy-Item -Recurse D:\qiuniu_install\qiuniu安装\qiuniu C:\apache-tomcat-9.0.96\webapps\qiuniu

# 确保 webapps 下没有 .war 文件（只有 .war.bak）
Rename-Item C:\apache-tomcat-9.0.96\webapps\qiuniu.war qiuniu.war.bak -ErrorAction SilentlyContinue

# 启动
C:\apache-tomcat-9.0.96\bin\startup.bat
```

⚠️ **重要**：不要放 `.war` 文件到 webapps 目录，否则重启会覆盖手动修改！

## 4. bank_kb Flask 服务安装

```powershell
cd D:\qiuniu_install\bank_kb

# 安装依赖（国内镜像）
pip install -r requirements.txt -i https://pypi.tuna.tsinghua.edu.cn/simple

# 配置 config.py（如需修改 MySQL 密码或 API Key）
# 当前配置：
#   MySQL: root / NewPassword123! @ localhost:3306/qiuniu_db
#   Flask: 端口 5001
#   SIMILARITY_THRESHOLD: 0.4

# 启动测试
python app.py

# 验证
Invoke-RestMethod -Uri "http://localhost:5001/health"
# 预期输出：{"status":"ok"}
```

## 5. bank_kb 开机自启（启动文件夹方案）

```powershell
# 已创建启动脚本
# 路径：C:\Users\botao\AppData\Roaming\Microsoft\Windows\Start Menu\Programs\Startup\BankKBApi.bat

# 如需手动创建：
@echo off
cd /d D:\qiuniu_install\bank_kb
start /min "" "C:\Users\botao\AppData\Local\Programs\Python\Python311\python.exe" "D:\qiuniu_install\bank_kb\app.py"
```

## 6. 验证安装

```powershell
# 检查所有服务
Write-Host "=== 服务状态检查 ==="

# MySQL
try { 
    python -c "import mysql.connector; c=mysql.connector.connect(host='localhost',port=3306,user='root',password='NewPassword123!',database='qiuniu_db'); print('MySQL: OK'); c.close()"
} catch { Write-Host "MySQL: FAILED" }

# Flask RAG
try {
    $r = Invoke-RestMethod -Uri "http://localhost:5001/health" -TimeoutSec 5
    Write-Host "Flask RAG: OK ($r)"
} catch { Write-Host "Flask RAG: FAILED" }

# Tomcat
try {
    $r = Invoke-WebRequest -Uri "http://localhost:8080/qiuniu/" -TimeoutSec 5 -UseBasicParsing
    Write-Host "Tomcat: OK (status $($r.StatusCode))"
} catch { Write-Host "Tomcat: FAILED" }
```

## 7. 测试账号

| 用户名 | 密码 | 角色 |
|--------|------|------|
| RyanLoong | RyanLoong | ADMIN |

访问：http://localhost:8080/qiuniu/

---

安装完成后，参考 OPS.md 进行日常运维。
