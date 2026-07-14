@echo off
set JAVA_HOME=E:\dev\java\zulu21.50.19-ca-jdk21.0.11-win_x64
set MAVEN_HOME=E:\dev\maven\apache-maven-3.9.9
set PATH=%JAVA_HOME%\bin;%MAVEN_HOME%\bin;%PATH%
cd /d c:\Users\75629\CodeBuddy\20260710123705\beichen-erp\beichen-erp-server
echo [BACKEND] Starting Spring Boot...
mvn spring-boot:run -DskipTests
