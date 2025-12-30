@echo off
REM Скрипт для остановки Docker Compose в Windows
REM Использование: stop.bat

echo 🛑 Остановка Family Calendar Bot...
echo.

REM Проверка наличия Docker Compose
docker-compose --version >nul 2>&1
if errorlevel 1 (
    docker compose version >nul 2>&1
    if errorlevel 1 (
        echo ❌ Docker Compose не установлен!
        exit /b 1
    )
)

REM Остановка контейнеров
docker-compose down

echo.
echo ✅ Контейнеры успешно остановлены!
echo.
echo 💡 Примечание: Данные PostgreSQL сохранены в volume
echo    Для полной очистки используйте: clean.bat
echo.
