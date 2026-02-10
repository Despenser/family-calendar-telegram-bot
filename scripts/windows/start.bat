@echo off
setlocal enabledelayedexpansion
chcp 65001 >nul 2>&1
REM Переход в корневую директорию проекта
cd /d "%~dp0\..\.."

REM Скрипт для запуска Docker Compose в Windows
REM Использование: 
REM   start.bat          - запуск в dev режиме (без nginx)
REM   start.bat prod     - запуск в prod режиме (с nginx)

REM Определение профиля
set PROFILE=%1
if "%PROFILE%"=="" set PROFILE=dev

echo [ЗАПУСК] Family Calendar Bot (профиль: %PROFILE%)...
echo:

REM Проверка наличия .env файла
if not exist .env (
    echo [ОШИБКА] Файл .env не найден!
    echo [ИНФО] Создайте .env файл на основе .env.example
    echo:
    echo Пример команды:
    echo   copy .env.example .env
    echo:
    exit /b 1
)

REM Проверка наличия Docker
docker --version >nul 2>&1
if errorlevel 1 (
    echo [ОШИБКА] Docker не установлен!
    echo Установите Docker: https://docs.docker.com/get-docker/
    exit /b 1
)

REM Проверка наличия Docker Compose
docker-compose --version >nul 2>&1
if errorlevel 1 (
    docker compose version >nul 2>&1
    if errorlevel 1 (
        echo [ОШИБКА] Docker Compose не установлен!
        echo Установите Docker Compose: https://docs.docker.com/compose/install/
        exit /b 1
    )
)

REM Проверка SSL сертификатов для prod
if "%PROFILE%"=="prod" (
    if not exist nginx\ssl\server.crt (
        echo [ПРЕДУПРЕЖДЕНИЕ] SSL сертификаты не найдены!
        echo [ИНФО] Сгенерируйте сертификаты командой:
        echo   ssl.bat [ВАШ_IP]
        echo:
        exit /b 1
    )
)

REM Сборка образов
echo [СБОРКА] Сборка Docker образов...
call docker-compose build >nul 2>&1
if errorlevel 1 (
    echo [ОШИБКА] Ошибка при сборке образов
    exit /b 1
)
echo [DEBUG] После сборки
echo:

REM Запуск контейнеров
echo [DEBUG] Перед запуском контейнеров
echo [DEBUG] PROFILE=%PROFILE%
if /i "%PROFILE%"=="prod" (
    echo [ЗАПУСК] Запуск контейнеров ^(с nginx^)...
    docker-compose --profile prod up -d
) else (
    echo [ЗАПУСК] Запуск контейнеров ^(без nginx, для ngrok^)...
    docker-compose up -d
)
echo [DEBUG] После запуска контейнеров

REM Ожидание готовности PostgreSQL
echo:
echo [ОЖИДАНИЕ] Ожидание готовности PostgreSQL...
timeout /t 5 /nobreak >nul

REM Проверка статуса контейнеров
echo:
echo [СТАТУС] Статус контейнеров:
call docker-compose ps

echo:
echo [УСПЕХ] Family Calendar Bot успешно запущен!
echo:

if "%PROFILE%"=="prod" (
    echo [РЕЖИМ] Production (с nginx и SSL)
    echo    Webhook URL должен быть: https://[ВАШ_IP]/webhook
) else (
    echo [РЕЖИМ] Development (с ngrok)
    echo    1. Запустите ngrok: ngrok http 8080
    echo    2. Скопируйте HTTPS URL в .env (TELEGRAM_BOT_WEBHOOK_URL)
    echo    3. Перезапустите приложение если нужно
)

echo:
echo [КОМАНДЫ] Полезные команды:
echo   - Просмотр логов: logs.bat
echo   - Остановка: stop.bat
echo   - Очистка данных: clean.bat
echo:
