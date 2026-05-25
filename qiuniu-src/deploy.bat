# ===========================================
# 囚牛 (QiuNiu) 快速部署脚本
# ===========================================
# 使用方法：
# 1. 确保已安装 Maven 和 JDK
# 2. 修改下面的 MySQL 配置
# 3. 运行：.\deploy.bat
# ===========================================

@echo off
chcp 65001 >nul
echo ==========================================
echo   囚牛 (QiuNiu) 快速部署脚本
echo ==========================================
echo.

:: 配置 MySQL 连接
set MYSQL_HOST=localhost
set MYSQL_PORT=3306
set MYSQL_USER=root
set MYSQL_PASS=your_password_here
set MYSQL_DB=qiuniu_db

:: 1. 检查 Maven
echo [1/5] 检查 Maven...
mvn -version >nul 2>&1
if %errorlevel% neq 0 (
    echo [错误] 未找到 Maven，请先安装 Maven
    pause
    exit /b 1
)
echo [OK] Maven 已安装

:: 2. 编译项目
echo.
echo [2/5] 编译项目...
call mvn clean package -DskipTests
if %errorlevel% neq 0 (
    echo [错误] 编译失败
    pause
    exit /b 1
)
echo [OK] 编译完成

:: 3. 检查 MySQL 连接
echo.
echo [3/5] 检查 MySQL 连接...
echo 请确保 MySQL 服务已启动，并已修改数据库配置
echo 配置文件：src\main\resources\db.properties
echo.

:: 4. 提示部署
echo [4/5] 部署说明
echo.
echo WAR 文件位置：target\qiuniu.war
echo.
echo 请将 WAR 文件复制到 Tomcat 的 webapps 目录：
echo   copy target\qiuniu.war %TOMCAT_HOME%\webapps\
echo.
echo 然后启动 Tomcat 并访问：
echo   http://localhost:8080/qiuniu/
echo.

:: 5. 数据库初始化
echo [5/5] 数据库初始化
echo.
echo 请登录 MySQL 并执行以下命令创建数据库：
echo   CREATE DATABASE %MYSQL_DB% DEFAULT CHARACTER SET utf8mb4;
echo.
echo 或者运行 init-db.sql 脚本
echo.

echo ==========================================
echo   部署准备完成！
echo ==========================================
echo.
pause
