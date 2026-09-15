@echo off
cd /d C:\Users\75629\CodeBuddy\20260710123705\beichen-erp\beichen-erp-server
mvn spring-boot:run -DskipTests -Dspring-boot.run.arguments="--server.port=8081 --spring.datasource.url=jdbc:mysql://localhost:3306/beichen_erp_test?useUnicode=true&characterEncoding=UTF-8&connectionCollation=utf8mb4_general_ci&serverTimezone=Asia/Shanghai&useSSL=false&allowPublicKeyRetrieval=true"
