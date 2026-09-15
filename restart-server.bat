@echo off
setlocal enabledelayedexpansion
chcp 936 >nul
title 北辰ERP 后端一键重启

REM ============================================================
REM  北辰ERP 后端一键重启脚本
REM  作用：结束 8080 端口上残留的后端进程 -> 重新编译 -> 后台启动 -> 等待就绪
REM  用法：双击本文件即可（改完后端代码后跑一次）
REM ============================================================

set "ROOT=%~dp0"
set "SERVER_DIR=%ROOT%beichen-erp-server"
set "PORT=8080"
set "LOG=%TEMP%\beichen-server.log"
set "MAX_WAIT=50"

echo ============================================================
echo   北辰ERP 后端一键重启
echo   项目目录: %SERVER_DIR%
echo ============================================================
echo.

if not exist "%SERVER_DIR%" (
    echo [错误] 找不到后端目录: %SERVER_DIR%
    echo        请把本脚本放在项目根目录（beichen-erp 下）
    pause
    exit /b 1
)

REM ---------- 1. 停止旧进程 ----------
echo [1/4] 停止占用 %PORT% 端口的进程 ...
set "KILLED=0"
for /f "tokens=5" %%p in ('netstat -ano ^| findstr ":%PORT% " ^| findstr LISTENING') do (
    echo       结束进程 PID %%p
    taskkill /PID %%p /F >nul 2>&1
    set "KILLED=1"
)
if "%KILLED%"=="0" echo       端口空闲，无需停止
echo       等待端口释放 ...
call :sleep 3

REM ---------- 2. 编译 ----------
echo.
echo [2/4] 编译后端（可提前发现代码错误） ...
cd /d "%SERVER_DIR%"
call mvn -q compile -DskipTests
if errorlevel 1 (
    echo.
    echo [失败] 编译报错，已停止启动。请修复代码后重新运行本脚本。
    pause
    exit /b 1
)
echo       编译通过

REM ---------- 3. 启动 ----------
echo.
echo [3/4] 后台启动后端（日志: %LOG%） ...
if exist "%LOG%" del /q "%LOG%" >nul 2>&1
start "BeichenErpServer" /d "%SERVER_DIR%" /min cmd /c "mvn spring-boot:run > ""%LOG%"" 2>&1"

REM ---------- 4. 等待就绪 ----------
echo.
echo [4/4] 等待服务就绪（最多 %MAX_WAIT% 次探测） ...
set "READY=0"
for /l %%i in (1,1,%MAX_WAIT%) do (
    call :sleep 3
    netstat -ano | findstr ":%PORT% " | findstr LISTENING >nul
    if not errorlevel 1 (
        set "READY=1"
        echo       第 %%i 次探测：端口已监听
        goto :ready
    )
    set /a "MOD=%%i %% 5"
    if "!MOD!"=="0" echo       已等待 %%i 次探测 ...
)

:ready
echo.
if "%READY%"=="1" (
    echo ============================================================
    echo   启动成功！
    echo   后端地址: http://localhost:%PORT%
    echo   前端地址: http://localhost:5173
    echo.
    echo   提示：后端重启后浏览器登录态会失效，请刷新页面重新登录。
    echo   启动日志: %LOG%
    echo ============================================================
) else (
    echo ============================================================
    echo   [超时] %MAX_WAIT% 次探测后端口仍未监听，可能启动失败。
    echo   请查看日志: %LOG%
    echo ============================================================
)
echo.
pause
exit /b 0

REM ---------- 子程序：等待 N 秒（用 ping 计时，不依赖 stdin，兼容性好于 timeout） ----------
:sleep
ping -n 1 -w 1000 127.0.0.1 >nul
set /a "LEFT=%~1"
if "%LEFT%"=="" set "LEFT=1"
if %LEFT% LEQ 0 exit /b 0
set /a "LEFT=LEFT*1000"
ping -n 1 -w %LEFT% 127.0.0.1 >nul
exit /b 0
