@echo off
chcp 65001 >nul 2>&1
REM Скрипт для генерации самоподписанных SSL сертификатов для Telegram Webhook
REM Использование: generate-ssl-certs.bat <PUBLIC_IP> [DAYS]
REM Пример: generate-ssl-certs.bat 176.108.254.68
REM Пример: generate-ssl-certs.bat 176.108.254.68 365

setlocal enabledelayedexpansion

REM Проверка аргументов
if "%~1"=="" (
    echo [ОШИБКА] Не указан публичный IP адрес
    echo Использование: %~nx0 ^<PUBLIC_IP^> [DAYS]
    echo Пример: %~nx0 176.108.254.68
    echo Пример: %~nx0 176.108.254.68 365
    exit /b 1
)

set PUBLIC_IP=%~1
set CERT_DAYS=%~2
if "%CERT_DAYS%"=="" set CERT_DAYS=3650
set SSL_DIR=nginx\ssl
set CERT_FILE=%SSL_DIR%\cert.pem
set KEY_FILE=%SSL_DIR%\key.pem

echo === Генерация SSL сертификатов для Telegram Webhook ===
echo Публичный IP: %PUBLIC_IP%
echo Срок действия: %CERT_DAYS% дней

REM Создаем директорию для сертификатов
if not exist "%SSL_DIR%" mkdir "%SSL_DIR%"

REM Проверяем, существуют ли уже сертификаты
if exist "%CERT_FILE%" if exist "%KEY_FILE%" (
    echo [ПРЕДУПРЕЖДЕНИЕ] Сертификаты уже существуют
    set /p REPLY="Перезаписать существующие сертификаты? (y/N): "
    if /i not "!REPLY!"=="y" (
        echo [ОТМЕНА] Генерация отменена
        exit /b 0
    )
)

REM Генерируем сертификаты
echo [ГЕНЕРАЦИЯ] Генерация сертификатов...
openssl req -newkey rsa:2048 -sha256 -nodes ^
    -keyout "%KEY_FILE%" ^
    -x509 -days %CERT_DAYS% ^
    -out "%CERT_FILE%" ^
    -subj "/C=RU/ST=Moscow/L=Moscow/O=Bot/CN=%PUBLIC_IP%"

if %ERRORLEVEL% equ 0 (
    echo [УСПЕХ] Сертификаты успешно созданы
    echo   Сертификат: %CERT_FILE%
    echo   Ключ: %KEY_FILE%
    
    echo.
    echo [ИНФО] Информация о сертификате:
    openssl x509 -in "%CERT_FILE%" -noout -subject -dates
    
    echo.
    echo === Следующие шаги ===
    echo 1. Убедитесь, что в .env файле указан правильный TELEGRAM_BOT_WEBHOOK_URL:
    echo    TELEGRAM_BOT_WEBHOOK_URL=https://%PUBLIC_IP%/webhook
    echo.
    echo 2. Запустите приложение:
    echo    docker-compose up -d
    echo.
    echo 3. Проверьте логи nginx:
    echo    docker-compose logs -f nginx
    echo.
    echo 4. Проверьте статус webhook в Telegram:
    echo    curl https://api.telegram.org/bot^<YOUR_BOT_TOKEN^>/getWebhookInfo
) else (
    echo [ОШИБКА] Ошибка при генерации сертификатов
    exit /b 1
)

endlocal
