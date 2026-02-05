#!/bin/bash

# Скрипт для генерации самоподписанных SSL сертификатов для Telegram Webhook
# Использование: ./generate-ssl-certs.sh <PUBLIC_IP> [DAYS]
# Пример: ./generate-ssl-certs.sh 176.108.254.68
# Пример: ./generate-ssl-certs.sh 176.108.254.68 365

set -e

# Цвета для вывода
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# Проверка аргументов
if [ -z "$1" ]; then
    echo -e "${RED}Ошибка: Не указан публичный IP адрес${NC}"
    echo "Использование: $0 <PUBLIC_IP> [DAYS]"
    echo "Пример: $0 176.108.254.68"
    echo "Пример: $0 176.108.254.68 365"
    exit 1
fi

PUBLIC_IP=$1
CERT_DAYS=${2:-3650}  # По умолчанию 3650 дней (10 лет)
SSL_DIR="nginx/ssl"
CERT_FILE="$SSL_DIR/cert.pem"
KEY_FILE="$SSL_DIR/key.pem"

echo -e "${GREEN}=== Генерация SSL сертификатов для Telegram Webhook ===${NC}"
echo -e "Публичный IP: ${YELLOW}$PUBLIC_IP${NC}"
echo -e "Срок действия: ${YELLOW}$CERT_DAYS дней${NC}"

# Создаем директорию для сертификатов
mkdir -p "$SSL_DIR"

# Проверяем, существуют ли уже сертификаты
if [ -f "$CERT_FILE" ] && [ -f "$KEY_FILE" ]; then
    echo -e "${YELLOW}Внимание: Сертификаты уже существуют${NC}"
    read -p "Перезаписать существующие сертификаты? (y/N): " -n 1 -r
    echo
    if [[ ! $REPLY =~ ^[Yy]$ ]]; then
        echo -e "${GREEN}Генерация отменена${NC}"
        exit 0
    fi
fi

# Генерируем сертификаты
echo -e "${GREEN}Генерация сертификатов...${NC}"
openssl req -newkey rsa:2048 -sha256 -nodes \
    -keyout "$KEY_FILE" \
    -x509 -days "$CERT_DAYS" \
    -out "$CERT_FILE" \
    -subj "/C=RU/ST=Moscow/L=Moscow/O=Bot/CN=$PUBLIC_IP"

# Проверяем результат
if [ $? -eq 0 ]; then
    echo -e "${GREEN}✓ Сертификаты успешно созданы${NC}"
    echo -e "  Сертификат: ${YELLOW}$CERT_FILE${NC}"
    echo -e "  Ключ: ${YELLOW}$KEY_FILE${NC}"
    
    # Устанавливаем правильные права доступа
    chmod 644 "$CERT_FILE"
    chmod 600 "$KEY_FILE"
    
    echo -e "${GREEN}✓ Права доступа установлены${NC}"
    
    # Показываем информацию о сертификате
    echo -e "\n${GREEN}Информация о сертификате:${NC}"
    openssl x509 -in "$CERT_FILE" -noout -subject -dates
    
    echo -e "\n${GREEN}=== Следующие шаги ===${NC}"
    echo "1. Убедитесь, что в .env файле указан правильный TELEGRAM_BOT_WEBHOOK_URL:"
    echo -e "   ${YELLOW}TELEGRAM_BOT_WEBHOOK_URL=https://$PUBLIC_IP/webhook${NC}"
    echo ""
    echo "2. Запустите приложение:"
    echo -e "   ${YELLOW}docker-compose up -d${NC}"
    echo ""
    echo "3. Проверьте логи nginx:"
    echo -e "   ${YELLOW}docker-compose logs -f nginx${NC}"
    echo ""
    echo "4. Проверьте статус webhook в Telegram:"
    echo -e "   ${YELLOW}curl https://api.telegram.org/bot<YOUR_BOT_TOKEN>/getWebhookInfo${NC}"
    
else
    echo -e "${RED}✗ Ошибка при генерации сертификатов${NC}"
    exit 1
fi
