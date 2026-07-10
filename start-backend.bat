@echo off
REM ====== 北辰ERP 完整启动脚本 ======
set JAVA_HOME=E:\dev\java\zulu21.50.19-ca-jdk21.0.11-win_x64
set MAVEN_HOME=E:\dev\maven\apache-maven-3.9.9
set NODE_HOME=C:\Users\75629\.workbuddy\binaries\node\versions\20.18.0.installing.11336.__extract_temp__\node-v20.18.0-win-x64
set PNPM_HOME=E:\dev\npm-global
set PATH=%JAVA_HOME%\bin;%MAVEN_HOME%\bin;%NODE_HOME%;%PNPM_HOME%;%PATH%

cd /d c:\Users\75629\CodeBuddy\20260710123705\beichen-erp\beichen-erp-server
echo [BACKEND] Starting Spring Boot...
mvn spring-boot:run -DskipTests
