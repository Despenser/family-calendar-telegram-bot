#!/bin/bash

# Переход в корневую директорию проекта
cd "$(dirname "$0")/../.." || exit 1

# Скрипт для запуска Docker Compose
# Использование: 
#   ./scripts/unix/start.sh          - запуск в dev режиме (без nginx)
#   ./scripts/unix/start.sh prod     - запуск в prod режиме (с nginx)

set -e

# Определение профиля
PROFILE="${1:-dev}"

echo "🚀 Запуск Family Calendar Bot (профиль: $PROFILE)..."
echo ""

# Проверка наличия .env файла
if [ ! -f .env ]; then
    echo "⚠️  Файл .env не найден!"
    echo "📝 Создайте .env файл на основе .env.example"
    echo ""
    echo "Пример команды:"
    echo "  cp .env.example .env"
    echo ""
    exit 1
fi

# Проверка наличия Docker
if ! command -v docker &> /dev/null; then
    echo "❌ Docker не установлен!"
    echo "Установите Docker: https://docs.docker.com/get-docker/"
    exit 1
fi

# Проверка наличия Docker Compose
if ! command -v docker-compose &> /dev/null && ! docker compose version &> /dev/null; then
    echo "❌ Docker Compose не установлен!"
    echo "Установите Docker Compose: https://docs.docker.com/compose/install/"
    exit 1
fi

# Проверка SSL сертификатов для prod
if [ "$PROFILE" = "prod" ]; then
    if [ ! -f nginx/ssl/server.crt ] || [ ! -f nginx/ssl/server.key ]; then
        echo "⚠️  SSL сертификаты не найдены!"
        echo "📝 Сгенерируйте сертификаты командой:"
        echo "  ./scripts/unix/ssl.sh <ВАШ_IP>"
        echo ""
        exit 1
    fi
fi

# Сборка образов
echo "🔨 Сборка Docker образов..."
docker-compose build

# Запуск контейнеров
echo ""
if [ "$PROFILE" = "prod" ]; then
    echo "▶️  Запуск контейнеров (с nginx)..."
    docker-compose --profile prod up -d
else
    echo "▶️  Запуск контейнеров (без nginx, для ngrok)..."
    docker-compose up -d
fi

# Ожидание готовности PostgreSQL
echo ""
echo "⏳ Ожидание готовности PostgreSQL..."
sleep 5

# Проверка статуса контейнеров
echo ""
echo "📊 Статус контейнеров:"
docker-compose ps

echo ""
echo "✅ Family Calendar Bot успешно запущен!"
echo ""

if [ "$PROFILE" = "prod" ]; then
    echo "🌐 Режим: Production (с nginx и SSL)"
    echo "   Webhook URL должен быть: https://<ВАШ_IP>/webhook"
else
    echo "🔧 Режим: Development (с ngrok)"
    echo "   1. Запустите ngrok: ngrok http 8080"
    echo "   2. Скопируйте HTTPS URL в .env (TELEGRAM_BOT_WEBHOOK_URL)"
    echo "   3. Перезапустите приложение если нужно"
fi

echo ""
echo "📝 Полезные команды:"
echo "  - Просмотр логов: ./scripts/unix/logs.sh"
echo "  - Остановка: ./scripts/unix/stop.sh"
echo "  - Очистка данных: ./scripts/unix/clean.sh"
echo ""
