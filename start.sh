#!/bin/bash

# Скрипт для запуска Docker Compose
# Использование: ./start.sh

set -e

echo "🚀 Запуск Family Calendar Bot..."
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

# Сборка образов
echo "🔨 Сборка Docker образов..."
docker-compose build

# Запуск контейнеров
echo ""
echo "▶️  Запуск контейнеров..."
docker-compose up -d

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
echo "📝 Полезные команды:"
echo "  - Просмотр логов: ./logs.sh"
echo "  - Остановка: ./stop.sh"
echo "  - Очистка данных: ./clean.sh"
echo ""
