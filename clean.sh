#!/bin/bash

# Скрипт для полной очистки Docker Compose (включая volumes)
# Использование: ./clean.sh

set -e

echo "🧹 Очистка Family Calendar Bot..."
echo ""
echo "⚠️  ВНИМАНИЕ: Это удалит все данные PostgreSQL!"
echo ""

# Запрос подтверждения
read -p "Вы уверены? (yes/no): " -r
echo ""

if [[ ! $REPLY =~ ^[Yy][Ee][Ss]$ ]]; then
    echo "❌ Очистка отменена"
    exit 0
fi

# Проверка наличия Docker Compose
if ! command -v docker-compose &> /dev/null && ! docker compose version &> /dev/null; then
    echo "❌ Docker Compose не установлен!"
    exit 1
fi

# Остановка и удаление контейнеров, сетей, volumes
echo "🗑️  Удаление контейнеров, сетей и volumes..."
docker-compose down -v

# Удаление образов (опционально)
read -p "Удалить также Docker образы? (yes/no): " -r
echo ""

if [[ $REPLY =~ ^[Yy][Ee][Ss]$ ]]; then
    echo "🗑️  Удаление Docker образов..."
    docker-compose down --rmi all -v
fi

echo ""
echo "✅ Очистка завершена!"
echo ""
echo "💡 Для повторного запуска используйте: ./start.sh"
echo ""
