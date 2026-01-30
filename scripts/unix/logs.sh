#!/bin/bash

# Переход в корневую директорию проекта
cd "$(dirname "$0")/../.." || exit 1

# Скрипт для просмотра логов Docker Compose
# Использование: 
#   ./scripts/unix/logs.sh           - все логи
#   ./scripts/unix/logs.sh app       - только логи приложения
#   ./scripts/unix/logs.sh postgres  - только логи PostgreSQL

set -e

# Проверка наличия Docker Compose
if ! command -v docker-compose &> /dev/null && ! docker compose version &> /dev/null; then
    echo "❌ Docker Compose не установлен!"
    exit 1
fi

SERVICE=$1

if [ -z "$SERVICE" ]; then
    echo "📋 Просмотр логов всех сервисов..."
    echo "   (Нажмите Ctrl+C для выхода)"
    echo ""
    docker-compose logs -f
else
    echo "📋 Просмотр логов сервиса: $SERVICE"
    echo "   (Нажмите Ctrl+C для выхода)"
    echo ""
    docker-compose logs -f "$SERVICE"
fi
