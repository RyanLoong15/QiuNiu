@echo off
chcp 65001 >nul
echo =========================================
echo  QiuNiu Flask RAG 服务初始化脚本 (Windows)
echo =========================================
echo.

REM ========================================
REM 环境配置（优先使用已设置的环境变量）
REM ========================================

REM Flask 基础 URL：若已设置则跳过自动检测
if not defined FLASK_BASE_URL (
    REM 自动检测本机 IP（用于内网访问）
    for /f "tokens=2 delims=:" %%a in ('ipconfig ^| findstr "IPv4" ^| findstr /V "127.0.0.1"') do (
        set LOCAL_IP=%%a
    )
    set LOCAL_IP=%LOCAL_IP:~1%
    if "%LOCAL_IP%"=="" set LOCAL_IP=localhost
    set FLASK_BASE_URL=http://%LOCAL_IP%:5001
    echo [自动检测] 本机IP: %LOCAL_IP%
) else (
    echo [环境变量] 使用已设置的 FLASK_BASE_URL: %FLASK_BASE_URL%
)

set FLASK_HOST=0.0.0.0
set FLASK_PORT=5001
set TOMCAT_HOST=localhost
set TOMCAT_PORT=8080
set TOMCAT_CONTEXT=/qiuniu
set ENV_TYPE=development

echo [配置]
echo   FLASK_BASE_URL: %FLASK_BASE_URL%
echo   TOMCAT_HOST: %TOMCAT_HOST%
echo   ENV_TYPE: %ENV_TYPE%
echo.

echo [1/3] 安装 Python 依赖...
pip install -r requirements.txt
if errorlevel 1 (
    echo [ERROR] 依赖安装失败
    pause
    exit /b 1
)

echo.
echo [2/3] 初始化知识库索引...
python -c "from app import app; from init import build_index; app.app_context().push(); build_index(); print('[OK] Index built.')"
if errorlevel 1 (
    echo [ERROR] 索引初始化失败
    pause
    exit /b 1
)

echo.
echo [3/3] 启动 Flask 服务...
echo   访问地址: %FLASK_BASE_URL%
echo   健康检查: %FLASK_BASE_URL%/health
echo   重建索引: curl -X POST %FLASK_BASE_URL%/reload
echo.
start /b python app.py > flask_out.log 2>&1
echo Flask 已启动
timeout /t 3 /nobreak >nul
curl -s %FLASK_BASE_URL%/health
echo.
echo =========================================
echo  初始化完成
echo =========================================
pause
