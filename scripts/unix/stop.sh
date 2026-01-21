#!/bin/bash

# Переход в корневую директорию проекта
cd "$(dirname "$0")/../.." || exit 1

# Скрипт для остановки Docker Compose
# Использование: ./scripts/unix/stop.sh

set -e

echo "🛑 Остановка Family Calendar Bot..."
echo ""

# Проверка наличия Docker Compose
if ! command -v docker-compose &> /dev/null && ! docker compose version &> /dev/null; then
    echo "❌ Docker Compose не установлен!"
    exit 1
fi

# Остановка контейнеров
docker-compose down

echo ""
echo "✅ Контейнеры успешно остановлены!"
echo ""
echo "💡 Примечание: Данные PostgreSQL сохранены в volume"
echo "   Для полной очистки используйте: ./scripts/unix/clean.sh"
echo ""
