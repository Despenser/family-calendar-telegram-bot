@echo off
REM Переход в корневую директорию проекта
cd /d "%~dp0\..\.."

REM Скрипт для просмотра логов Docker Compose в Windows
REM Использование: 
REM   logs.bat           - все логи
REM   logs.bat app       - только логи приложения
REM   logs.bat postgres  - только логи PostgreSQL

REM Проверка наличия Docker Compose
docker-compose --version >nul 2>&1
if errorlevel 1 (
    docker compose version >nul 2>&1
    if errorlevel 1 (
        echo ❌ Docker Compose не установлен!
        exit /b 1
    )
)

set SERVICE=%1

if "%SERVICE%"=="" (
    echo 📋 Просмотр логов всех сервисов...
    echo    (Нажмите Ctrl+C для выхода^)
    echo.
    docker-compose logs -f
) else (
    echo 📋 Просмотр логов сервиса: %SERVICE%
    echo    (Нажмите Ctrl+C для выхода^)
    echo.
    docker-compose logs -f %SERVICE%
)
