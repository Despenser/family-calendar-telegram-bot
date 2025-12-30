# Скрипты управления Docker Compose

Этот проект включает набор скриптов для упрощения управления Docker Compose.

## Доступные скрипты

### 🚀 Запуск приложения

**Linux/Mac/Git Bash:**
```bash
./start.sh
```

**Windows CMD:**
```cmd
start.bat
```

Этот скрипт:
- Проверяет наличие `.env` файла
- Проверяет установку Docker и Docker Compose
- Собирает Docker образы
- Запускает контейнеры в фоновом режиме
- Показывает статус контейнеров

### 🛑 Остановка приложения

**Linux/Mac/Git Bash:**
```bash
./stop.sh
```

**Windows CMD:**
```cmd
stop.bat
```

Этот скрипт:
- Останавливает все контейнеры
- Сохраняет данные PostgreSQL в volume

### 📋 Просмотр логов

**Linux/Mac/Git Bash:**
```bash
# Все логи
./logs.sh

# Только логи приложения
./logs.sh app

# Только логи PostgreSQL
./logs.sh postgres
```

**Windows CMD:**
```cmd
REM Все логи
logs.bat

REM Только логи приложения
logs.bat app

REM Только логи PostgreSQL
logs.bat postgres
```

Для выхода из режима просмотра логов нажмите `Ctrl+C`.

### 🧹 Полная очистка

**Linux/Mac/Git Bash:**
```bash
./clean.sh
```

**Windows CMD:**
```cmd
clean.bat
```

Этот скрипт:
- Останавливает все контейнеры
- Удаляет все volumes (включая данные PostgreSQL)
- Опционально удаляет Docker образы

⚠️ **ВНИМАНИЕ:** Эта операция удалит все данные из базы данных!

## Первый запуск

1. Создайте файл `.env` на основе `.env.example`:
   ```bash
   cp .env.example .env
   ```

2. Заполните необходимые переменные окружения в `.env`:
   - `BOT_TOKEN` - токен вашего Telegram бота
   - `WEBHOOK_URL` - URL для webhook
   - `DB_PASSWORD` - пароль для PostgreSQL

3. Запустите приложение:
   ```bash
   ./start.sh    # Linux/Mac/Git Bash
   start.bat     # Windows CMD
   ```

## Требования

- Docker 20.10+
- Docker Compose 2.0+

## Устранение неполадок

### Скрипт не запускается (Linux/Mac)

Если вы получаете ошибку "Permission denied", сделайте скрипты исполняемыми:

```bash
chmod +x start.sh stop.sh logs.sh clean.sh
```

### Docker не найден

Убедитесь, что Docker установлен и запущен:
- [Установка Docker](https://docs.docker.com/get-docker/)
- [Установка Docker Compose](https://docs.docker.com/compose/install/)

### Порт уже занят

Если порт 8080 или 5432 уже используется, измените порты в `docker-compose.yml`.

## Полезные команды Docker Compose

Если вы предпочитаете использовать Docker Compose напрямую:

```bash
# Запуск
docker-compose up -d

# Остановка
docker-compose down

# Просмотр логов
docker-compose logs -f

# Просмотр статуса
docker-compose ps

# Перезапуск сервиса
docker-compose restart app

# Пересборка образов
docker-compose build --no-cache
```
