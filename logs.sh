#!/bin/bash

# Скрипт для просмотра логов Docker Compose
# Использование: 
#   ./logs.sh           - все логи
#   ./logs.sh app       - только логи приложения
#   ./logs.sh postgres  - только логи PostgreSQL

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
