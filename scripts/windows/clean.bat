@echo off
chcp 65001 >nul 2>&1
REM Переход в корневую директорию проекта
cd /d "%~dp0\..\.."

REM Скрипт для полной очистки Docker Compose в Windows (включая volumes)
REM Использование: clean.bat

echo [ОЧИСТКА] Очистка Family Calendar Bot...
echo.
echo [ПРЕДУПРЕЖДЕНИЕ] ВНИМАНИЕ: Это удалит все данные PostgreSQL!
echo.

REM Запрос подтверждения
set /p CONFIRM="Вы уверены? (yes/no): "
echo.

if /i not "%CONFIRM%"=="yes" (
    echo [ОТМЕНА] Очистка отменена
    exit /b 0
)

REM Проверка наличия Docker Compose
docker-compose --version >nul 2>&1
if errorlevel 1 (
    docker compose version >nul 2>&1
    if errorlevel 1 (
        echo [ОШИБКА] Docker Compose не установлен!
        exit /b 1
    )
)

REM Остановка и удаление контейнеров, сетей, volumes
echo [УДАЛЕНИЕ] Удаление контейнеров, сетей и volumes...
docker-compose down -v

REM Удаление образов (опционально)
set /p REMOVE_IMAGES="Удалить также Docker образы? (yes/no): "
echo.

if /i "%REMOVE_IMAGES%"=="yes" (
    echo [УДАЛЕНИЕ] Удаление Docker образов...
    docker-compose down --rmi all -v
)

echo.
echo [УСПЕХ] Очистка завершена!
echo.
echo [ИНФО] Для повторного запуска используйте: start.bat
echo.
