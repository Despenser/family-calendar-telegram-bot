#!/bin/bash

# Скрипт для генерации безопасного secret token для Telegram webhook
# Использование: ./scripts/unix/generate-secret-token.sh

set -e

echo "=================================================="
echo "Генерация Secret Token для Telegram Webhook"
echo "=================================================="
echo ""

# Проверка наличия openssl
if ! command -v openssl &> /dev/null; then
    echo "❌ Ошибка: openssl не установлен"
    echo "Установите openssl:"
    echo "  macOS: brew install openssl"
    echo "  Ubuntu/Debian: sudo apt-get install openssl"
    exit 1
fi

# Генерация токена
TOKEN=$(openssl rand -base64 48 | tr -d '=+/' | cut -c1-64)

echo "✅ Secret token успешно сгенерирован:"
echo ""
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo "$TOKEN"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo ""
echo "📋 Инструкции:"
echo ""
echo "1. Скопируйте токен выше"
echo ""
echo "2. Добавьте его в файл .env:"
echo "   TELEGRAM_BOT_WEBHOOK_SECRET_TOKEN=$TOKEN"
echo ""
echo "3. Или используйте команду для автоматического добавления:"
echo "   echo 'TELEGRAM_BOT_WEBHOOK_SECRET_TOKEN=$TOKEN' >> .env"
echo ""
echo "4. Перезапустите приложение:"
echo "   docker-compose restart app"
echo ""
echo "⚠️  ВАЖНО:"
echo "   - НЕ коммитьте этот токен в git"
echo "   - НЕ меняйте токен после установки webhook"
echo "   - Храните токен в безопасном месте"
echo ""
echo "=================================================="
