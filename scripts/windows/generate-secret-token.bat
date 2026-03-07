@echo off
REM Скрипт для генерации безопасного secret token для Telegram webhook
REM Использование: scripts\windows\generate-secret-token.bat

setlocal enabledelayedexpansion

echo ==================================================
echo Генерация Secret Token для Telegram Webhook
echo ==================================================
echo.

REM Проверка наличия PowerShell
where powershell >nul 2>nul
if %ERRORLEVEL% neq 0 (
    echo ❌ Ошибка: PowerShell не найден
    echo PowerShell должен быть установлен в системе
    exit /b 1
)

REM Генерация токена через PowerShell
for /f "delims=" %%i in ('powershell -Command "-join ((48..57) + (65..90) + (97..122) | Get-Random -Count 64 | ForEach-Object {[char]$_})"') do set TOKEN=%%i

echo ✅ Secret token успешно сгенерирован:
echo.
echo ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
echo %TOKEN%
echo ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
echo.
echo 📋 Инструкции:
echo.
echo 1. Скопируйте токен выше
echo.
echo 2. Добавьте его в файл .env:
echo    TELEGRAM_BOT_WEBHOOK_SECRET_TOKEN=%TOKEN%
echo.
echo 3. Или используйте команду для автоматического добавления:
echo    echo TELEGRAM_BOT_WEBHOOK_SECRET_TOKEN=%TOKEN% ^>^> .env
echo.
echo 4. Перезапустите приложение:
echo    docker-compose restart app
echo.
echo ⚠️  ВАЖНО:
echo    - НЕ коммитьте этот токен в git
echo    - НЕ меняйте токен после установки webhook
echo    - Храните токен в безопасном месте
echo.
echo ==================================================

endlocal
pause
