# Family Calendar Bot

Telegram бот для управления семейным календарем на базе Spring Boot 3.5.3 и PostgreSQL.

## Возможности

- 📅 Создание событий в календаре
- 👀 Просмотр предстоящих событий семьи
- ✏️ Редактирование и удаление своих событий
- 🔔 Автоматические уведомления за 1 час до события
- 👥 Поддержка нескольких семей
- 🔐 Авторизация по Telegram ID

## Технологии

- **Java 21 LTS**
- **Spring Boot 3.5.3**
- **PostgreSQL 18.1**
- **Telegram Bot API 6.9.7.1**
- **Flyway 11.1.0** для миграций БД
- **Docker & Docker Compose** для развертывания
- **Testcontainers 1.21.2** для тестирования

## Быстрый старт с Docker Compose

### Предварительные требования

- Docker 20.10+
- Docker Compose 2.0+
- Telegram бот токен (получите от [@BotFather](https://t.me/BotFather))

### Шаги запуска

1. **Клонируйте репозиторий**
   ```bash
   git clone <repository-url>
   cd family-calendar-bot
   ```

2. **Создайте файл .env из примера**
   ```bash
   cp .env.example .env
   ```

3. **Настройте переменные окружения в .env**
   ```env
   DB_PASSWORD=your_secure_password
   TELEGRAM_BOT_TOKEN=your_bot_token_from_botfather
   TELEGRAM_BOT_USERNAME=YourBotUsername
   TELEGRAM_BOT_WEBHOOK_URL=https://your-domain.com/webhook
   ```

4. **Запустите приложение**
   ```bash
   docker-compose up -d
   ```

5. **Проверьте логи**
   ```bash
   docker-compose logs -f app
   ```

6. **Остановите приложение**
   ```bash
   docker-compose down
   ```

### Локальная разработка с ngrok

Для локальной разработки с webhook используйте [ngrok](https://ngrok.com/):

1. **Установите ngrok**
   ```bash
   # Следуйте инструкциям на https://ngrok.com/download
   ```

2. **Запустите ngrok**
   ```bash
   ngrok http 8080
   ```

3. **Скопируйте HTTPS URL из ngrok** (например, `https://abc123.ngrok.io`)

4. **Обновите .env файл**
   ```env
   TELEGRAM_BOT_WEBHOOK_URL=https://abc123.ngrok.io/webhook
   ```

5. **Перезапустите приложение**
   ```bash
   docker-compose restart app
   ```

## Команды бота

- `/start` - Начать работу с ботом
- `/help` - Показать список всех команд
- `/add_event` - Создать новое событие
- `/upcoming_events` - Показать предстоящие события семьи
- `/my_events` - Показать мои события с возможностью редактирования

## Архитектура

```
┌─────────────────┐
│  Telegram API   │
└────────┬────────┘
         │ Webhook
         ↓
┌─────────────────┐
│  Spring Boot    │
│   Application   │
│                 │
│  - Controllers  │
│  - Services     │
│  - Repositories │
└────────┬────────┘
         │ JDBC
         ↓
┌─────────────────┐
│   PostgreSQL    │
└─────────────────┘
```

## Структура базы данных

### Таблица `families`
- `id` - Уникальный идентификатор семьи
- `name` - Название семьи
- `created_at` - Дата создания

### Таблица `users`
- `id` - Уникальный идентификатор пользователя
- `telegram_id` - Telegram ID (уникальный)
- `username` - Telegram username
- `first_name` - Имя пользователя
- `family_id` - Ссылка на семью
- `created_at` - Дата регистрации

### Таблица `events`
- `id` - Уникальный идентификатор события
- `user_id` - Создатель события
- `family_id` - Семья события
- `title` - Название события
- `description` - Описание
- `event_date` - Дата события
- `event_time` - Время события
- `notified` - Флаг отправки уведомления
- `created_at` - Дата создания

## Разработка

### Сборка проекта

```bash
mvn clean package
```

### Запуск тестов

```bash
mvn test
```

### Запуск локально (без Docker)

```bash
# Убедитесь, что PostgreSQL запущен локально
mvn spring-boot:run
```

## Docker команды

### Пересборка образов
```bash
docker-compose build --no-cache
```

### Просмотр логов
```bash
# Все сервисы
docker-compose logs -f

# Только приложение
docker-compose logs -f app

# Только база данных
docker-compose logs -f postgres
```

### Очистка volumes (удаление всех данных)
```bash
docker-compose down -v
```

### Подключение к PostgreSQL
```bash
docker-compose exec postgres psql -U botuser -d family_calendar
```

## Troubleshooting

### Приложение не может подключиться к базе данных
- Проверьте, что PostgreSQL контейнер запущен: `docker-compose ps`
- Проверьте логи PostgreSQL: `docker-compose logs postgres`
- Убедитесь, что healthcheck проходит успешно

### Webhook не регистрируется
- Проверьте, что TELEGRAM_BOT_WEBHOOK_URL доступен из интернета
- Убедитесь, что URL использует HTTPS
- Проверьте логи приложения на наличие ошибок регистрации

### Миграции не применяются
- Проверьте логи Flyway в логах приложения
- Убедитесь, что база данных создана и доступна
- Проверьте права пользователя БД

## Лицензия

MIT

## Контакты

Для вопросов и предложений создавайте issue в репозитории.
