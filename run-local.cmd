@echo off
setlocal
echo ============================================================
echo  Starting Housekeeping Management System (Spring Boot)
echo ============================================================
cd /d "%~dp0app\housekeeping-management-system"
if exist mvnw.cmd (
    call mvnw.cmd spring-boot:run
) else (
    call mvn spring-boot:run
)
