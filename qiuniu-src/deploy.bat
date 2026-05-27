@echo off
chcp 65001 >nul
echo ==========================================
echo   囚牛 (QiuNiu) Windows 一键部署脚本
echo ==========================================
echo.

:: ── 配置区 ──────────────────────────────────
set MYSQL_HOST=localhost
set MYSQL_PORT=3306
set MYSQL_USER=root
set MYSQL_PASS=NewPassword123!
set MYSQL_DB=qiuniu_db
set TOMCAT_HOME=C:\apache-tomcat-9.0.96
set QIUNIU_SRC=D:\qiuniu20260506\qiuniu-src
set FLASK_DIR=D:\qiuniu20260506\bank_kb
:: ────────────────────────────────────────────────

:: 1. 检查 Java / Maven / Python
echo [1/7] 检查运行环境...
where java >nul 2>&1 || (echo [错误] 未找到 Java，请先安装 JDK 17+ & pause & exit /b 1)
where mvn >nul 2>&1 || (echo [错误] 未找到 Maven & pause & exit /b 1)
where python >nul 2>&1 || (echo [错误] 未找到 Python，Flask 服务需要 Python 3.9+ & pause & exit /b 1)
echo [OK] Java / Maven / Python 均已安装

:: 2. 初始化数据库
echo.
echo [2/7] 初始化数据库...
mysql -h %MYSQL_HOST% -P %MYSQL_PORT% -u%MYSQL_USER% -p%MYSQL_PASS% < "%QIUNIU_SRC%\init-db.sql" 2>&1
if errorlevel 1 (
    echo [警告] 数据库初始化失败，请手动执行：
    echo   mysql -u root -p ^< %QIUNIU_SRC%\init-db.sql
) else (
    echo [OK] 数据库初始化完成（15 张表 + 默认管理员 admin/admin123）
)

:: 3. 编译 Java 项目
echo.
echo [3/7] 编译 Java 项目（Maven）...
cd /d "%QIUNIU_SRC%"
call mvn clean package -DskipTests
if errorlevel 1 (
    echo [错误] Maven 编译失败
    pause
    exit /b 1
)
echo [OK] 编译完成：target\qiuniu.war

:: 4. 停止 Tomcat
echo.
echo [4/7] 停止 Tomcat（如正在运行）...
cd /d "%TOMCAT_HOME%\bin"
call catalina.bat stop 2>nul
timeout /t 5 >nul

:: 5. 部署 WAR 包
echo.
echo [5/7] 部署到 Tomcat...
copy /Y "%QIUNIU_SRC%\target\qiuniu.war" "%TOMCAT_HOME%\webapps\" >nul
echo [OK] WAR 包已复制到 %TOMCAT_HOME%\webapps\

:: 6. 启动 Tomcat
echo.
echo [6/7] 启动 Tomcat...
cd /d "%TOMCAT_HOME%\bin"
start "" cmd /k "catalina.bat run"
echo [OK] Tomcat 启动中，请等待 10 秒...
timeout /t 10 >nul

:: 7. 启动 Flask RAG 服务
echo.
echo [7/7] 启动 Flask RAG 服务（新窗口）...
cd /d "%FLASK_DIR%"
pip install -r requirements.txt -q 2>nul
start "QiuNiu-Flask" cmd /k "python app.py"
echo [OK] Flask 服务窗口已打开（端口 5001）

:: 完成提示
echo.
echo ==========================================
echo   部署完成！
echo ==========================================
echo.
echo   访问地址：
echo   - 登录页：  http://localhost:8080/qiuniu/login.jsp
echo   - 知识库：  http://localhost:8080/qiuniu/knowledge-base.jsp
echo   - 员工登录：http://localhost:8080/qiuniu/employee/employee-login.jsp
echo.
echo   默认管理员账号：admin / admin123
echo   默认员工工号：EMP001 / 密码 123456
echo.
echo   Flask RAG 服务运行在：http://localhost:5001/
echo   如 Flask 启动失败，请检查 bank_kb\config.py 中的 LLM_API_KEY
echo.
pause
