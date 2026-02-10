@echo off
chcp 65001 >nul 2>&1
REM Переход в корневую директорию проекта
cd /d "%~dp0\..\.."

REM Скрипт для остановки Docker Compose в Windows
REM Использование: stop.bat

echo [ОСТАНОВКА] Остановка Family Calendar Bot...
echo.

REM Проверка наличия Docker Compose
docker-compose --version >nul 2>&1
if errorlevel 1 (
    docker compose version >nul 2>&1
    if errorlevel 1 (
        echo [ОШИБКА] Docker Compose не установлен!
        exit /b 1
    )
)

REM Остановка контейнеров
docker-compose down

echo.
echo [УСПЕХ] Контейнеры успешно остановлены!
echo.
echo [ИНФО] Примечание: Данные PostgreSQL сохранены в volume
echo    Для полной очистки используйте: clean.bat
echo.
