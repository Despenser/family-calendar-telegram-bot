@echo off
REM Скрипт для запуска Docker Compose в Windows
REM Использование: start.bat

echo 🚀 Запуск Family Calendar Bot...
echo.

REM Проверка наличия .env файла
if not exist .env (
    echo ⚠️  Файл .env не найден!
    echo 📝 Создайте .env файл на основе .env.example
    echo.
    echo Пример команды:
    echo   copy .env.example .env
    echo.
    exit /b 1
)

REM Проверка наличия Docker
docker --version >nul 2>&1
if errorlevel 1 (
    echo ❌ Docker не установлен!
    echo Установите Docker: https://docs.docker.com/get-docker/
    exit /b 1
)

REM Проверка наличия Docker Compose
docker-compose --version >nul 2>&1
if errorlevel 1 (
    docker compose version >nul 2>&1
    if errorlevel 1 (
        echo ❌ Docker Compose не установлен!
        echo Установите Docker Compose: https://docs.docker.com/compose/install/
        exit /b 1
    )
)

REM Сборка образов
echo 🔨 Сборка Docker образов...
docker-compose build

REM Запуск контейнеров
echo.
echo ▶️  Запуск контейнеров...
docker-compose up -d

REM Ожидание готовности PostgreSQL
echo.
echo ⏳ Ожидание готовности PostgreSQL...
timeout /t 5 /nobreak >nul

REM Проверка статуса контейнеров
echo.
echo 📊 Статус контейнеров:
docker-compose ps

echo.
echo ✅ Family Calendar Bot успешно запущен!
echo.
echo 📝 Полезные команды:
echo   - Просмотр логов: logs.bat
echo   - Остановка: stop.bat
echo   - Очистка данных: clean.bat
echo.
