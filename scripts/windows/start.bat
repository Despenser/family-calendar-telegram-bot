@echo off
REM Переход в корневую директорию проекта
cd /d "%~dp0\..\.."

REM Скрипт для запуска Docker Compose в Windows
REM Использование: 
REM   start.bat          - запуск в dev режиме (без nginx)
REM   start.bat prod     - запуск в prod режиме (с nginx)

REM Определение профиля
set PROFILE=%1
if "%PROFILE%"=="" set PROFILE=dev

echo 🚀 Запуск Family Calendar Bot (профиль: %PROFILE%)...
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

REM Проверка SSL сертификатов для prod
if "%PROFILE%"=="prod" (
    if not exist nginx\ssl\server.crt (
        echo ⚠️  SSL сертификаты не найдены!
        echo 📝 Сгенерируйте сертификаты командой:
        echo   ssl.bat ^<ВАШ_IP^>
        echo.
        exit /b 1
    )
)

REM Сборка образов
echo 🔨 Сборка Docker образов...
docker-compose build

REM Запуск контейнеров
echo.
if "%PROFILE%"=="prod" (
    echo ▶️  Запуск контейнеров (с nginx)...
    docker-compose --profile prod up -d
) else (
    echo ▶️  Запуск контейнеров (без nginx, для ngrok)...
    docker-compose up -d
)

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

if "%PROFILE%"=="prod" (
    echo 🌐 Режим: Production (с nginx и SSL^)
    echo    Webhook URL должен быть: https://^<ВАШ_IP^>/webhook
) else (
    echo 🔧 Режим: Development (с ngrok^)
    echo    1. Запустите ngrok: ngrok http 8080
    echo    2. Скопируйте HTTPS URL в .env (TELEGRAM_BOT_WEBHOOK_URL^)
    echo    3. Перезапустите приложение если нужно
)

echo.
echo 📝 Полезные команды:
echo   - Просмотр логов: logs.bat
echo   - Остановка: stop.bat
echo   - Очистка данных: clean.bat
echo.
