@echo off
REM Скрипт для полной очистки Docker Compose в Windows (включая volumes)
REM Использование: clean.bat

echo 🧹 Очистка Family Calendar Bot...
echo.
echo ⚠️  ВНИМАНИЕ: Это удалит все данные PostgreSQL!
echo.

REM Запрос подтверждения
set /p CONFIRM="Вы уверены? (yes/no): "
echo.

if /i not "%CONFIRM%"=="yes" (
    echo ❌ Очистка отменена
    exit /b 0
)

REM Проверка наличия Docker Compose
docker-compose --version >nul 2>&1
if errorlevel 1 (
    docker compose version >nul 2>&1
    if errorlevel 1 (
        echo ❌ Docker Compose не установлен!
        exit /b 1
    )
)

REM Остановка и удаление контейнеров, сетей, volumes
echo 🗑️  Удаление контейнеров, сетей и volumes...
docker-compose down -v

REM Удаление образов (опционально)
set /p REMOVE_IMAGES="Удалить также Docker образы? (yes/no): "
echo.

if /i "%REMOVE_IMAGES%"=="yes" (
    echo 🗑️  Удаление Docker образов...
    docker-compose down --rmi all -v
)

echo.
echo ✅ Очистка завершена!
echo.
echo 💡 Для повторного запуска используйте: start.bat
echo.
