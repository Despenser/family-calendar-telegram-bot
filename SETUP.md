# Руководство по настройке Family Calendar Bot

Это руководство поможет вам настроить и запустить Telegram бота семейного календаря с нуля.

## Содержание

1. [Предварительные требования](#предварительные-требования)
2. [Создание Telegram бота](#создание-telegram-бота)
3. [Настройка PostgreSQL](#настройка-postgresql)
4. [Настройка Webhook URL](#настройка-webhook-url)
5. [Конфигурация приложения](#конфигурация-приложения)
6. [Добавление пользователей в БД](#добавление-пользователей-в-бд)
7. [Запуск приложения](#запуск-приложения)
8. [Troubleshooting](#troubleshooting)

---

## Предварительные требования

Перед началом установки убедитесь, что у вас установлены:

- **Java 21 LTS** или выше
- **Maven 3.9+** для сборки проекта
- **Docker** и **Docker Compose** для контейнеризации
- **Git** для клонирования репозитория
- **ngrok** (для локальной разработки с webhook)

### Проверка установленных версий

```bash
java -version    # Должна быть версия 21 или выше
mvn -version     # Должна быть версия 3.9 или выше
docker --version
docker-compose --version
```

---

## Создание Telegram бота

### Шаг 1: Найдите BotFather

1. Откройте Telegram
2. Найдите бота **@BotFather** (официальный бот для создания ботов)
3. Начните диалог, отправив команду `/start`

### Шаг 2: Создайте нового бота

1. Отправьте команду `/newbot`
2. BotFather попросит вас ввести имя бота (например, "Family Calendar Bot")
3. Затем введите username бота (должен заканчиваться на "bot", например, "family_calendar_bot")

### Шаг 3: Сохраните токен

После создания бота BotFather отправит вам сообщение с **токеном доступа**:

```
Use this token to access the HTTP API:
1234567890:ABCdefGHIjklMNOpqrsTUVwxyz
```

⚠️ **ВАЖНО**: Сохраните этот токен в безопасном месте! Он понадобится для конфигурации приложения.

### Шаг 4: Настройте бота (опционально)

Вы можете настроить дополнительные параметры бота:

```
/setdescription - Установить описание бота
/setabouttext - Установить текст "О боте"
/setuserpic - Установить аватар бота
/setcommands - Установить список команд для меню
```

Пример команд для `/setcommands`:

```
start - Начать работу с ботом
help - Показать список команд
add_event - Добавить новое событие
upcoming_events - Показать предстоящие события
my_events - Мои события
```

---

## Настройка PostgreSQL

### Вариант 1: Использование Docker (рекомендуется)

PostgreSQL будет автоматически настроен при запуске через Docker Compose. Перейдите к разделу [Конфигурация приложения](#конфигурация-приложения).

### Вариант 2: Локальная установка PostgreSQL

Если вы хотите использовать локальную установку PostgreSQL:

#### Установка PostgreSQL

**Ubuntu/Debian:**
```bash
sudo apt update
sudo apt install postgresql postgresql-contrib
```

**macOS (с Homebrew):**
```bash
brew install postgresql@18
brew services start postgresql@18
```

**Windows:**
Скачайте установщик с [официального сайта PostgreSQL](https://www.postgresql.org/download/windows/)


#### Создание базы данных и пользователя

1. Подключитесь к PostgreSQL:
```bash
sudo -u postgres psql
```

2. Создайте базу данных:
```sql
CREATE DATABASE family_calendar;
```

3. Создайте пользователя:
```sql
CREATE USER botuser WITH PASSWORD 'your_secure_password';
```

4. Предоставьте права:
```sql
GRANT ALL PRIVILEGES ON DATABASE family_calendar TO botuser;
\c family_calendar
GRANT ALL ON SCHEMA public TO botuser;
```

5. Выйдите из psql:
```sql
\q
```

#### Проверка подключения

```bash
psql -h localhost -U botuser -d family_calendar
```

---

## Настройка Webhook URL

Telegram требует HTTPS URL для webhook. Есть несколько вариантов:

### Вариант 1: Локальная разработка с ngrok (рекомендуется для тестирования)

#### Установка ngrok

1. Зарегистрируйтесь на [ngrok.com](https://ngrok.com/)
2. Скачайте ngrok для вашей ОС
3. Установите authtoken:
```bash
ngrok config add-authtoken YOUR_AUTHTOKEN
```

#### Запуск ngrok

```bash
ngrok http 8080
```

Вы увидите вывод:
```
Forwarding  https://abc123.ngrok.io -> http://localhost:8080
```

Ваш webhook URL будет: `https://abc123.ngrok.io/webhook/YOUR_BOT_TOKEN`

⚠️ **ВАЖНО**: 
- URL от ngrok меняется при каждом перезапуске (в бесплатной версии)
- Не забудьте обновить переменную окружения `TELEGRAM_BOT_WEBHOOK_URL`

### Вариант 2: Production сервер

Для production развертывания вам нужен:

1. **Доменное имя** (например, `bot.example.com`)
2. **SSL сертификат** (можно получить бесплатно через Let's Encrypt)
3. **Веб-сервер** (Nginx/Apache) в качестве reverse proxy

#### Пример конфигурации Nginx

```nginx
server {
    listen 443 ssl;
    server_name bot.example.com;

    ssl_certificate /etc/letsencrypt/live/bot.example.com/fullchain.pem;
    ssl_certificate_key /etc/letsencrypt/live/bot.example.com/privkey.pem;

    location /webhook/ {
        proxy_pass http://localhost:8080/webhook/;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
    }
}
```

Ваш webhook URL: `https://bot.example.com/webhook/YOUR_BOT_TOKEN`

---

## Конфигурация приложения

### Шаг 1: Клонируйте репозиторий

```bash
git clone <repository-url>
cd family-calendar-bot
```

### Шаг 2: Создайте файл .env

Скопируйте пример файла:

```bash
cp .env.example .env
```

### Шаг 3: Заполните переменные окружения

Откройте `.env` и заполните следующие параметры:

```env
# Telegram Bot Configuration
TELEGRAM_BOT_TOKEN=1234567890:ABCdefGHIjklMNOpqrsTUVwxyz
TELEGRAM_BOT_USERNAME=family_calendar_bot
TELEGRAM_BOT_WEBHOOK_URL=https://abc123.ngrok.io/webhook/1234567890:ABCdefGHIjklMNOpqrsTUVwxyz

# Database Configuration
DB_PASSWORD=your_secure_password_here

# Spring Profile (dev, prod)
SPRING_PROFILES_ACTIVE=dev
```

#### Описание переменных:

- `TELEGRAM_BOT_TOKEN` - токен, полученный от BotFather
- `TELEGRAM_BOT_USERNAME` - username вашего бота (без @)
- `TELEGRAM_BOT_WEBHOOK_URL` - полный URL webhook (включая токен в пути)
- `DB_PASSWORD` - пароль для PostgreSQL
- `SPRING_PROFILES_ACTIVE` - профиль Spring (dev для разработки, prod для production)

⚠️ **БЕЗОПАСНОСТЬ**: 
- Никогда не коммитьте файл `.env` в Git
- Используйте сложные пароли для production
- Храните токены в безопасном месте

### Дополнительные параметры конфигурации

#### Параметры очистки черновиков

Система автоматически очищает "осиротевшие" черновики событий - записи со статусом `DRAFT` и NULL значениями в обязательных полях. Эти параметры настраиваются в файле `application.yml`:

```yaml
draft:
  cleanup:
    # Включить/выключить автоматическую очистку осиротевших черновиков
    # По умолчанию: true
    enabled: true
    
    # Пороговое значение для очистки при запуске приложения (в часах)
    # Черновики старше этого значения будут удалены при старте
    # По умолчанию: 1
    startup-threshold-hours: 1
    
    # Пороговое значение для периодической очистки (в часах)
    # Черновики старше этого значения будут удалены при периодической очистке
    # По умолчанию: 24
    periodic-threshold-hours: 24
    
    # Расписание периодической очистки в формате cron
    # Формат: секунды минуты часы день месяц день_недели
    # По умолчанию: "0 0 */6 * * *" (каждые 6 часов в 00 минут 00 секунд)
    schedule-cron: "0 0 */6 * * *"
```

**Описание параметров:**

- **`enabled`** - включает или отключает автоматическую очистку черновиков. Если установлено в `false`, очистка не будет выполняться ни при запуске, ни периодически.

- **`startup-threshold-hours`** - определяет возраст черновиков (в часах), которые будут удалены при запуске приложения. Значение `1` означает, что при старте будут удалены все осиротевшие черновики старше 1 часа. Это помогает очистить черновики, оставшиеся после предыдущего запуска.

- **`periodic-threshold-hours`** - определяет возраст черновиков (в часах) для периодической очистки. Значение `24` означает, что каждые 6 часов (по умолчанию) будут удаляться черновики старше 24 часов.

- **`schedule-cron`** - расписание периодической очистки в формате cron. По умолчанию очистка выполняется каждые 6 часов. Примеры других расписаний:
  - `"0 0 */12 * * *"` - каждые 12 часов
  - `"0 0 0 * * *"` - каждый день в полночь
  - `"0 0 2 * * *"` - каждый день в 2:00 ночи

**Примеры настройки:**

*Более агрессивная очистка (для систем с высокой нагрузкой):*
```yaml
draft:
  cleanup:
    enabled: true
    startup-threshold-hours: 0.5  # 30 минут
    periodic-threshold-hours: 12   # 12 часов
    schedule-cron: "0 0 */3 * * *" # каждые 3 часа
```

*Консервативная очистка (для систем с медленным интернетом):*
```yaml
draft:
  cleanup:
    enabled: true
    startup-threshold-hours: 2     # 2 часа
    periodic-threshold-hours: 48   # 48 часов
    schedule-cron: "0 0 0 * * *"   # раз в день в полночь
```

*Отключение автоматической очистки:*
```yaml
draft:
  cleanup:
    enabled: false
```

⚠️ **ВАЖНО**: 
- Не устанавливайте слишком маленькие значения для `startup-threshold-hours` (меньше 0.5 часа), так как это может привести к удалению активных черновиков, если пользователь медленно создает событие.
- Рекомендуется оставить значения по умолчанию для большинства случаев использования.
- Изменения в `schedule-cron` требуют перезапуска приложения.

#### Параметры системы напоминаний

```yaml
reminder:
  scheduler:
    # Включить/выключить планировщик напоминаний
    enabled: true
    # Интервал проверки напоминаний в миллисекундах (60000 = 1 минута)
    fixed-rate: 60000
  # Максимальное количество напоминаний на одно событие
  max-per-event: 10
  # Максимальное количество минут для custom напоминания (43200 = 30 дней)
  max-custom-minutes: 43200
```

#### Параметры сообщений для неавторизованных пользователей

Система использует конфигурируемые сообщения для различных категорий команд. Эти параметры настраиваются в файле `application.yml`:

```yaml
bot:
  messages:
    unauthorized:
      # Префикс для всех сообщений об ограничении доступа
      prefix: "🔒"
      # Текст с инструкциями по получению доступа
      contact-admin: "Для получения доступа обратитесь к администратору вашей семьи."
      # Сообщения для разных категорий команд
      event-creation: "Создание событий доступно только зарегистрированным пользователям семейного календаря."
      event-viewing: "Просмотр событий доступен только членам семейного календаря."
      event-management: "Управление событиями доступно только зарегистрированным пользователям."
      search-filter: "Поиск и фильтрация событий доступны только членам семейного календаря."
      trash-management: "Управление корзиной доступно только зарегистрированным пользователям."
      statistics: "Просмотр статистики доступен только членам семейного календаря."
      general: "Эта функция доступна только зарегистрированным пользователям семейного календаря."
```

**Описание параметров:**

- **`prefix`** - эмодзи или текст, который добавляется в начало каждого сообщения об ограничении доступа. По умолчанию используется эмодзи замка 🔒.

- **`contact-admin`** - текст с инструкциями о том, как пользователь может получить доступ к функционалу бота. Этот текст добавляется в конец каждого сообщения.

- **Категории сообщений:**
  - `event-creation` - используется для команды `/add_event`
  - `event-viewing` - используется для команд `/my_events`, `/upcoming_events`, `/today`, `/week`
  - `event-management` - используется для операций редактирования и удаления событий
  - `search-filter` - используется для команд `/search`, `/filter`
  - `trash-management` - используется для команды `/trash`
  - `statistics` - используется для команды `/stats`
  - `general` - используется для всех остальных команд, требующих авторизации

**Особенности:**

- Все сообщения автоматически форматируются с добавлением префикса и инструкций
- Если категория не найдена в конфигурации, используется сообщение из категории `general`
- Тексты можно изменять без перекомпиляции приложения - достаточно обновить `application.yml` и перезапустить
- Система валидирует тон сообщений, чтобы они были дружелюбными и мотивирующими

**Примеры настройки:**

*Более краткие сообщения:*
```yaml
bot:
  messages:
    unauthorized:
      prefix: "🔒"
      contact-admin: "Обратитесь к администратору для регистрации."
      event-creation: "Создание событий требует регистрации."
      event-viewing: "Просмотр событий требует регистрации."
```

*Сообщения на английском языке:*
```yaml
bot:
  messages:
    unauthorized:
      prefix: "🔒"
      contact-admin: "Please contact your family administrator to get access."
      event-creation: "Event creation is available only for registered family calendar users."
      event-viewing: "Event viewing is available only for family calendar members."
```

⚠️ **ВАЖНО**: 
- Избегайте негативных формулировок типа "доступ запрещен" или "вы не можете"
- Используйте конструктивные фразы типа "доступно после регистрации" или "станет доступно"
- Сообщения должны быть дружелюбными и мотивирующими
- Изменения в конфигурации требуют перезапуска приложения

---

## Добавление пользователей в БД

После первого запуска приложения база данных будет создана автоматически через Flyway миграции. Теперь нужно добавить пользователей.

### Вариант 1: Через SQL (рекомендуется для первоначальной настройки)

#### Шаг 1: Подключитесь к базе данных

**Если используете Docker:**
```bash
docker-compose exec postgres psql -U botuser -d family_calendar
```

**Если используете локальный PostgreSQL:**
```bash
psql -h localhost -U botuser -d family_calendar
```

#### Шаг 2: Создайте семью

```sql
INSERT INTO families (name, created_at) 
VALUES ('Моя семья', CURRENT_TIMESTAMP);
```

Запомните ID семьи:
```sql
SELECT id, name FROM families;
```

#### Шаг 3: Узнайте ваш Telegram ID

Есть несколько способов узнать свой Telegram ID:

1. **Через бота @userinfobot**:
   - Найдите бота @userinfobot в Telegram
   - Отправьте ему любое сообщение
   - Он ответит вашим ID

2. **Через бота @getmyid_bot**:
   - Найдите бота @getmyid_bot
   - Отправьте команду `/start`


#### Шаг 4: Добавьте пользователя

```sql
INSERT INTO users (telegram_id, username, first_name, family_id, created_at)
VALUES (
    123456789,           -- Ваш Telegram ID
    'your_username',     -- Ваш username (без @)
    'Ваше Имя',         -- Ваше имя
    1,                   -- ID семьи из шага 2
    CURRENT_TIMESTAMP
);
```

#### Шаг 5: Добавьте других членов семьи

Повторите шаг 4 для каждого члена семьи, используя их Telegram ID и тот же `family_id`.

#### Проверка

```sql
SELECT u.id, u.telegram_id, u.first_name, f.name as family_name
FROM users u
JOIN families f ON u.family_id = f.id;
```

### Вариант 2: Через скрипт (для автоматизации)

Создайте файл `add_user.sql`:

```sql
-- Замените значения на реальные
DO $$
DECLARE
    family_id_var BIGINT;
BEGIN
    -- Создаем семью, если её нет
    INSERT INTO families (name, created_at)
    VALUES ('Моя семья', CURRENT_TIMESTAMP)
    ON CONFLICT DO NOTHING
    RETURNING id INTO family_id_var;
    
    -- Если семья уже существует, получаем её ID
    IF family_id_var IS NULL THEN
        SELECT id INTO family_id_var FROM families WHERE name = 'Моя семья';
    END IF;
    
    -- Добавляем пользователя
    INSERT INTO users (telegram_id, username, first_name, family_id, created_at)
    VALUES (123456789, 'username', 'Имя', family_id_var, CURRENT_TIMESTAMP)
    ON CONFLICT (telegram_id) DO NOTHING;
END $$;
```

Выполните скрипт:
```bash
docker-compose exec -T postgres psql -U botuser -d family_calendar < add_user.sql
```

---

## Запуск приложения

### Вариант 1: Docker Compose (рекомендуется)

#### Сборка и запуск

```bash
# Сборка проекта
mvn clean package -DskipTests

# Запуск через Docker Compose
docker-compose up -d
```

#### Проверка логов

```bash
# Все логи
docker-compose logs -f

# Только логи приложения
docker-compose logs -f app

# Только логи PostgreSQL
docker-compose logs -f postgres
```

#### Остановка

```bash
docker-compose down
```

#### Полная очистка (включая volumes)

```bash
docker-compose down -v
```

### Вариант 2: Локальный запуск

#### Сборка проекта

```bash
mvn clean package
```

#### Запуск

```bash
java -jar target/family-calendar-bot-*.jar
```

Или через Maven:

```bash
mvn spring-boot:run
```

### Использование скриптов

Проект включает удобные скрипты для управления:

**Linux/macOS:**
```bash
./start.sh    # Запуск приложения
./stop.sh     # Остановка приложения
./logs.sh     # Просмотр логов
./clean.sh    # Очистка данных
```

**Windows:**
```cmd
start.bat     # Запуск приложения
stop.bat      # Остановка приложения
logs.bat      # Просмотр логов
clean.bat     # Очистка данных
```

### Проверка работы

1. Проверьте, что приложение запустилось:
```bash
curl http://localhost:8080/actuator/health
```

2. Проверьте логи на наличие сообщения о регистрации webhook:
```
Webhook registered successfully: https://your-url/webhook/...
```

3. Откройте Telegram и отправьте боту команду `/start`

---

## Troubleshooting

### Проблема: Бот не отвечает на команды

#### Возможные причины и решения:

1. **Webhook не зарегистрирован**
   
   Проверьте логи приложения:
   ```bash
   docker-compose logs app | grep -i webhook
   ```
   
   Если видите ошибку регистрации, проверьте:
   - Доступен ли webhook URL из интернета
   - Правильно ли указан токен в URL
   - Использует ли URL HTTPS

2. **Неправильный токен**
   
   Проверьте переменную `TELEGRAM_BOT_TOKEN` в `.env`:
   ```bash
   cat .env | grep TELEGRAM_BOT_TOKEN
   ```
   
   Убедитесь, что токен совпадает с токеном от BotFather.

3. **Пользователь не авторизован**
   
   Проверьте, есть ли пользователь в БД:
   ```sql
   SELECT * FROM users WHERE telegram_id = YOUR_TELEGRAM_ID;
   ```
   
   Если пользователя нет, добавьте его (см. раздел [Добавление пользователей](#добавление-пользователей-в-бд)).
   
   **Примечание:** Начиная с версии с поддержкой информативных сообщений, неавторизованные пользователи получают понятные уведомления вместо молчаливого игнорирования команд. Если пользователь видит сообщение с эмодзи 🔒, это означает, что он не зарегистрирован в системе.

4. **Проверка логов попыток доступа**
   
   Система логирует все попытки доступа неавторизованных пользователей:
   ```bash
   docker-compose logs app | grep -i "Unauthorized access attempt"
   ```
   
   Пример лог-записи:
   ```
   INFO  AuthorizationService - Unauthorized access attempt: telegramId=123456789, command=/add_event, timestamp=2026-01-12T10:30:00Z
   ```
   
   Это помогает администраторам отслеживать, кто пытается использовать бота и какие команды запрашивает.

### Проблема: Ошибка подключения к базе данных

#### Симптомы:
```
Connection refused: connect
Unable to obtain connection from database
```

#### Решения:

1. **Проверьте, что PostgreSQL запущен**
   ```bash
   docker-compose ps postgres
   ```
   
   Должен быть статус "Up" и "healthy".

2. **Проверьте пароль базы данных**
   
   Убедитесь, что `DB_PASSWORD` в `.env` совпадает с паролем в `docker-compose.yml`.

3. **Проверьте порт**
   
   Убедитесь, что порт 5432 не занят другим процессом:
   ```bash
   # Linux/macOS
   lsof -i :5432
   
   # Windows
   netstat -ano | findstr :5432
   ```

4. **Пересоздайте контейнеры**
   ```bash
   docker-compose down -v
   docker-compose up -d
   ```


### Проблема: Миграции Flyway не применяются

#### Симптомы:
```
FlywayException: Unable to obtain connection from database
Table 'users' doesn't exist
```

#### Решения:

1. **Проверьте логи Flyway**
   ```bash
   docker-compose logs app | grep -i flyway
   ```

2. **Проверьте файлы миграций**
   
   Убедитесь, что файлы находятся в `src/main/resources/db/migration/` и имеют правильное имя:
   ```
   V1__Initial_schema.sql
   V2__Add_something.sql
   ```

3. **Очистите схему и запустите заново**
   ```sql
   DROP SCHEMA public CASCADE;
   CREATE SCHEMA public;
   GRANT ALL ON SCHEMA public TO botuser;
   ```
   
   Затем перезапустите приложение.

### Проблема: ngrok URL изменился

#### Симптомы:
Бот перестал отвечать после перезапуска ngrok.

#### Решение:

1. Получите новый URL от ngrok:
   ```bash
   curl http://localhost:4040/api/tunnels
   ```

2. Обновите `.env`:
   ```env
   TELEGRAM_BOT_WEBHOOK_URL=https://NEW_URL.ngrok.io/webhook/YOUR_TOKEN
   ```

3. Перезапустите приложение:
   ```bash
   docker-compose restart app
   ```

### Проблема: Ошибка "Unauthorized" при регистрации webhook

#### Симптомы:
```
Failed to register webhook: 401 Unauthorized
```

#### Решения:

1. **Проверьте токен**
   
   Убедитесь, что токен в `.env` правильный и не содержит лишних пробелов.

2. **Проверьте формат webhook URL**
   
   URL должен быть в формате:
   ```
   https://your-domain.com/webhook/YOUR_FULL_TOKEN
   ```

3. **Проверьте токен через Telegram API**
   ```bash
   curl https://api.telegram.org/botYOUR_TOKEN/getMe
   ```
   
   Должен вернуть информацию о боте.

### Проблема: Высокая нагрузка на CPU/память

#### Возможные причины:

1. **Слишком частые запросы к БД**
   
   Проверьте логи на наличие N+1 проблем:
   ```bash
   docker-compose logs app | grep -i "select"
   ```

2. **Утечка памяти**
   
   Проверьте использование памяти:
   ```bash
   docker stats
   ```

3. **Неоптимальные запросы**
   
   Включите логирование SQL:
   ```yaml
   # application-dev.yml
   spring:
     jpa:
       show-sql: true
   ```

### Проблема: Уведомления не отправляются

#### Проверки:

1. **Проверьте, что NotificationService запущен**
   ```bash
   docker-compose logs app | grep -i "NotificationService"
   ```

2. **Проверьте события в БД**
   ```sql
   SELECT * FROM events 
   WHERE notified = false 
   AND event_date = CURRENT_DATE 
   AND event_time BETWEEN CURRENT_TIME AND CURRENT_TIME + INTERVAL '1 hour';
   ```

3. **Проверьте настройки @Scheduled**
   
   Убедитесь, что в конфигурации включен scheduling:
   ```java
   @EnableScheduling
   ```

### Получение дополнительной помощи

Если проблема не решена:

1. **Проверьте логи**
   ```bash
   docker-compose logs --tail=100 app
   ```

2. **Включите DEBUG логирование**
   
   В `application-dev.yml`:
   ```yaml
   logging:
     level:
       ru.golubyatnikov.family.calendar.bot: DEBUG
   ```

3. **Проверьте документацию**
   - [Spring Boot Documentation](https://docs.spring.io/spring-boot/docs/current/reference/html/)
   - [Telegram Bot API](https://core.telegram.org/bots/api)
   - [PostgreSQL Documentation](https://www.postgresql.org/docs/)

4. **Создайте issue в репозитории**
   
   Включите:
   - Описание проблемы
   - Шаги для воспроизведения
   - Логи (без токенов и паролей!)
   - Версии используемого ПО

---

## Полезные команды

### Docker

```bash
# Просмотр запущенных контейнеров
docker-compose ps

# Перезапуск конкретного сервиса
docker-compose restart app

# Просмотр логов в реальном времени
docker-compose logs -f app

# Выполнение команды в контейнере
docker-compose exec app bash

# Просмотр использования ресурсов
docker stats

# Очистка неиспользуемых образов
docker system prune -a
```

### PostgreSQL

```bash
# Подключение к БД
docker-compose exec postgres psql -U botuser -d family_calendar

# Бэкап БД
docker-compose exec postgres pg_dump -U botuser family_calendar > backup.sql

# Восстановление БД
docker-compose exec -T postgres psql -U botuser -d family_calendar < backup.sql

# Просмотр размера БД
docker-compose exec postgres psql -U botuser -d family_calendar -c "SELECT pg_size_pretty(pg_database_size('family_calendar'));"
```

### Maven

```bash
# Сборка без тестов
mvn clean package -DskipTests

# Запуск тестов
mvn test

# Запуск конкретного теста
mvn test -Dtest=UserServiceTest

# Очистка target
mvn clean

# Проверка зависимостей
mvn dependency:tree
```

---

---

## Обслуживание базы данных

### Очистка осиротевших черновиков

В процессе работы бота могут накапливаться "осиротевшие" черновики событий - записи со статусом `DRAFT` и NULL значениями в обязательных полях. Это происходит, когда процесс создания события прерывается из-за ошибки.

Начиная с версии с автоматической очисткой, система сама удаляет такие черновики:
- При запуске приложения удаляются черновики старше 1 часа
- Каждые 6 часов удаляются черновики старше 24 часов

#### Одноразовая ручная очистка

Если вы обновляете систему с более старой версии, рекомендуется выполнить одноразовую очистку существующих осиротевших черновиков.

**Шаг 1: Подключитесь к базе данных**

```bash
# Если используете Docker
docker-compose exec postgres psql -U botuser -d family_calendar

# Если используете локальный PostgreSQL
psql -h localhost -U botuser -d family_calendar
```

**Шаг 2: Просмотрите осиротевшие черновики**

```sql
SELECT 
    id,
    user_id,
    family_id,
    title,
    event_date,
    event_time,
    status,
    created_at,
    EXTRACT(EPOCH FROM (CURRENT_TIMESTAMP - created_at))/3600 AS age_hours
FROM events
WHERE status = 'DRAFT'
  AND title IS NULL
  AND event_date IS NULL
  AND event_time IS NULL
  AND created_at < CURRENT_TIMESTAMP - INTERVAL '1 hour'
ORDER BY created_at;
```

**Шаг 3: Выполните очистку**

⚠️ **ВНИМАНИЕ**: Эта операция необратима! Убедитесь, что вы проверили результаты предыдущего запроса.

```sql
DELETE FROM events
WHERE status = 'DRAFT'
  AND title IS NULL
  AND event_date IS NULL
  AND event_time IS NULL
  AND created_at < CURRENT_TIMESTAMP - INTERVAL '1 hour';
```

**Шаг 4: Проверьте результаты**

```sql
-- Проверьте, что осиротевших черновиков больше нет
SELECT COUNT(*) as remaining_orphaned_drafts
FROM events
WHERE status = 'DRAFT'
  AND title IS NULL
  AND event_date IS NULL
  AND event_time IS NULL;

-- Проверьте общее количество черновиков
SELECT COUNT(*) as total_drafts
FROM events
WHERE status = 'DRAFT';
```

#### Использование готового скрипта

Для удобства предоставлен готовый SQL-скрипт:

```bash
# Если используете Docker
docker-compose exec -T postgres psql -U botuser -d family_calendar < src/main/resources/db/scripts/cleanup_orphaned_drafts.sql

# Если используете локальный PostgreSQL
psql -h localhost -U botuser -d family_calendar -f src/main/resources/db/scripts/cleanup_orphaned_drafts.sql
```

#### Создание резервной копии перед очисткой

Рекомендуется создать резервную копию черновиков перед удалением:

```sql
-- Создание таблицы с резервной копией
CREATE TABLE events_drafts_backup AS 
SELECT * FROM events WHERE status = 'DRAFT';

-- Проверка количества скопированных записей
SELECT COUNT(*) FROM events_drafts_backup;
```

После успешной очистки и проверки работы системы резервную копию можно удалить:

```sql
DROP TABLE events_drafts_backup;
```

#### Мониторинг черновиков

Для мониторинга состояния черновиков используйте следующие запросы:

```sql
-- Общая статистика по черновикам
SELECT 
    COUNT(*) as total_drafts,
    COUNT(*) FILTER (WHERE title IS NULL AND event_date IS NULL AND event_time IS NULL) as orphaned_drafts,
    MIN(created_at) as oldest_draft,
    MAX(created_at) as newest_draft
FROM events
WHERE status = 'DRAFT';

-- Распределение черновиков по возрасту
SELECT 
    CASE 
        WHEN created_at > CURRENT_TIMESTAMP - INTERVAL '1 hour' THEN '< 1 час'
        WHEN created_at > CURRENT_TIMESTAMP - INTERVAL '24 hours' THEN '1-24 часа'
        WHEN created_at > CURRENT_TIMESTAMP - INTERVAL '7 days' THEN '1-7 дней'
        ELSE '> 7 дней'
    END as age_group,
    COUNT(*) as count
FROM events
WHERE status = 'DRAFT'
GROUP BY age_group
ORDER BY age_group;
```

---

## Работа с MarkdownV2 форматированием

### Что такое MarkdownV2?

MarkdownV2 - это формат разметки текста, используемый Telegram Bot API для форматирования сообщений. Он позволяет создавать жирный текст, курсив, моноширинный шрифт и другие стили.

### Специальные символы MarkdownV2

MarkdownV2 использует следующие специальные символы, которые **обязательно** должны быть экранированы обратным слешем (`\`):

```
_ * [ ] ( ) ~ ` > # + - = | { } . !
```

### Проблема с неэкранированными символами

Если специальные символы не экранированы, Telegram вернет ошибку парсинга:

```
Bad Request: can't parse entities: Character '.' is reserved and must be escaped with the preceding '\'
```

**Пример проблемного кода:**

```java
// ❌ НЕПРАВИЛЬНО - точки в дате не экранированы
String message = String.format("✅ Дата выбрана: %s", formattedDate);
// Если formattedDate = "12.01.2026", Telegram вернет ошибку
```

### Решение: использование MarkdownFormatter

Класс `MarkdownFormatter` предоставляет методы для безопасного форматирования сообщений:

#### 1. Экранирование отдельных строк

```java
import ru.golubyatnikov.family.calendar.bot.util.MarkdownFormatter;

// Экранирование специальных символов
String date = "12.01.2026";
String escaped = MarkdownFormatter.escape(date);
// Результат: "12\\.01\\.2026"

// Использование в сообщении
String message = "Дата: " + escaped;
```

#### 2. Форматирование с переменными (рекомендуется)

```java
// ✅ ПРАВИЛЬНО - автоматическое экранирование всех частей
String message = MarkdownFormatter.formatMessage(
    "✅ Дата выбрана: %s\n\nТеперь выберите час:", 
    formattedDate
);
// Результат: "✅ Дата выбрана: 12\\.01\\.2026\n\nТеперь выберите час:"
```

#### 3. Форматирование с несколькими переменными

```java
String message = MarkdownFormatter.formatMessage(
    "Событие: %s\nДата: %s\nВремя: %s",
    eventTitle,
    eventDate,
    eventTime
);
// Все переменные и статический текст будут автоматически экранированы
```

#### 4. Форматирование текста

```java
// Жирный текст
String bold = MarkdownFormatter.bold("Важное сообщение!");
// Результат: "*Важное сообщение\\!*"

// Курсив
String italic = MarkdownFormatter.italic("Примечание");
// Результат: "_Примечание_"

// Моноширинный шрифт (для команд)
String code = MarkdownFormatter.code("/my_events");
// Результат: "`/my_events`"

// Жирный курсив
String boldItalic = MarkdownFormatter.boldItalic("Очень важно");
// Результат: "*_Очень важно_*"
```

### Правила использования в коде

#### ✅ Правильные практики

1. **Всегда используйте `formatMessage()` для сообщений с переменными:**

```java
// Хорошо
String message = MarkdownFormatter.formatMessage(
    "Создано событие: %s на %s",
    eventTitle,
    eventDate
);
```

2. **Используйте `escape()` для отдельных строк:**

```java
// Хорошо
String escapedTitle = MarkdownFormatter.escape(event.getTitle());
String message = "Событие: " + escapedTitle;
```

3. **Используйте методы форматирования для стилизации:**

```java
// Хорошо
String header = MarkdownFormatter.bold("Список событий");
String command = MarkdownFormatter.code("/add_event");
String message = header + "\n\nИспользуйте команду " + command;
```

#### ❌ Неправильные практики

1. **НЕ используйте `String.format()` напрямую:**

```java
// Плохо - специальные символы не экранированы
String message = String.format("Дата: %s", date);
```

2. **НЕ забывайте экранировать статический текст:**

```java
// Плохо - точки и восклицательный знак не экранированы
String message = "Событие создано! Дата: " + MarkdownFormatter.escape(date);

// Хорошо
String message = MarkdownFormatter.formatMessage("Событие создано! Дата: %s", date);
```

3. **НЕ применяйте двойное экранирование:**

```java
// Плохо - двойное экранирование
String escaped = MarkdownFormatter.escape(date);
String message = MarkdownFormatter.formatMessage("Дата: %s", escaped);
// Результат будет содержать лишние обратные слеши

// Хорошо
String message = MarkdownFormatter.formatMessage("Дата: %s", date);
```

### Частые случаи использования

#### Даты

```java
// Даты содержат точки, которые нужно экранировать
String date = "12.01.2026";
String message = MarkdownFormatter.formatMessage("📅 Дата: %s", date);
```

#### Эмодзи

```java
// Эмодзи не требуют экранирования
String message = MarkdownFormatter.formatMessage("✅ Событие создано: %s", title);
```

#### Списки

```java
// Дефисы и точки требуют экранирования
String message = MarkdownFormatter.formatMessage(
    "События:\n- %s\n- %s\n- %s",
    event1,
    event2,
    event3
);
```

#### Команды в тексте

```java
// Используйте code() для команд
String command = MarkdownFormatter.code("/add_event");
String message = "Используйте команду " + command + " для создания события";
```

### Отладка проблем с форматированием

Если вы получаете ошибку парсинга MarkdownV2:

1. **Проверьте логи:**

```bash
docker-compose logs app | grep -i "Bad Request"
docker-compose logs app | grep -i "can't parse entities"
```

2. **Найдите проблемное сообщение:**

Логи содержат превью текста, который вызвал ошибку:

```
Bad Request (400): Ошибка парсинга MarkdownV2. 
telegramId=526536667, textPreview=✅ Дата выбрана: 12.01.2026
```

3. **Проверьте код:**

Найдите место, где формируется это сообщение, и убедитесь, что используется `MarkdownFormatter.formatMessage()` или `MarkdownFormatter.escape()`.

4. **Тестируйте локально:**

```java
@Test
void testMessageFormatting() {
    String date = "12.01.2026";
    String message = MarkdownFormatter.formatMessage("Дата: %s", date);
    
    // Проверяем, что точки экранированы
    assertTrue(message.contains("\\."));
    
    // Проверяем, что нет неэкранированных точек
    assertFalse(message.matches(".*[^\\\\]\\..*"));
}
```

### Дополнительные ресурсы

- [Telegram Bot API - MarkdownV2 style](https://core.telegram.org/bots/api#markdownv2-style)
- [Javadoc MarkdownFormatter](src/main/java/ru/golubyatnikov/family/calendar/bot/util/MarkdownFormatter.java)
- [Тесты MarkdownFormatter](src/test/java/ru/golubyatnikov/family/calendar/bot/util/MarkdownFormatterTest.java)

---

## Следующие шаги

После успешной настройки:

1. ✅ Протестируйте все команды бота
2. ✅ Добавьте всех членов семьи в БД
3. ✅ Создайте тестовые события
4. ✅ Проверьте работу уведомлений
5. ✅ Выполните одноразовую очистку осиротевших черновиков (если обновляете систему)
6. ✅ Проверьте работу системы авторизации:
   - Попробуйте использовать команды без регистрации
   - Убедитесь, что приходят информативные сообщения с эмодзи 🔒
   - Проверьте логи попыток доступа: `docker-compose logs app | grep "Unauthorized access"`
7. ✅ Настройте мониторинг и алерты (для production):
   - Мониторинг метрик `unauthorized_access_attempts_total`
   - Алерты на высокую частоту попыток доступа (возможная атака)
   - Мониторинг ошибок отправки сообщений
8. ✅ Настройте регулярные бэкапы БД (для production)
9. ✅ Ознакомьтесь с правилами форматирования MarkdownV2 (см. раздел выше)

## Мониторинг попыток доступа неавторизованных пользователей

Система автоматически логирует все попытки доступа неавторизованных пользователей. Это помогает:
- Отслеживать интерес к боту
- Выявлять потенциальные проблемы безопасности
- Понимать, какие пользователи хотят получить доступ

### Просмотр логов попыток доступа

```bash
# Все попытки доступа
docker-compose logs app | grep "Unauthorized access attempt"

# Попытки за последний час
docker-compose logs --since 1h app | grep "Unauthorized access attempt"

# Попытки конкретного пользователя
docker-compose logs app | grep "Unauthorized access attempt" | grep "telegramId=123456789"

# Статистика по командам
docker-compose logs app | grep "Unauthorized access attempt" | awk -F'command=' '{print $2}' | awk '{print $1}' | sort | uniq -c | sort -rn
```

### Пример лог-записи

```
2026-01-12 10:30:15.123 INFO  [scheduling-1] r.g.f.c.b.s.AuthorizationService : Unauthorized access attempt: telegramId=123456789, command=/add_event, timestamp=2026-01-12T10:30:15.123Z
```

### Анализ попыток доступа

Для анализа попыток доступа можно использовать SQL-запросы к логам или настроить систему мониторинга (Prometheus + Grafana).

**Метрики для мониторинга:**

1. **unauthorized_access_attempts_total** - общее количество попыток
   - Теги: `command` (имя команды)
   - Используйте для отслеживания популярных команд

2. **unauthorized_messages_sent_total** - количество отправленных сообщений
   - Теги: `category` (категория сообщения)
   - Используйте для проверки работы системы уведомлений

3. **authorization_check_duration_seconds** - время проверки авторизации
   - Теги: `result` (authorized/unauthorized)
   - Используйте для мониторинга производительности

**Рекомендуемые алерты:**

```yaml
# Пример конфигурации алерта в Prometheus
- alert: HighUnauthorizedAccessRate
  expr: rate(unauthorized_access_attempts_total[5m]) > 10
  for: 5m
  labels:
    severity: warning
  annotations:
    summary: "Высокая частота попыток доступа неавторизованных пользователей"
    description: "Более 10 попыток в минуту за последние 5 минут"

- alert: MessageSendErrors
  expr: rate(message_send_errors_total[5m]) > 0.05
  for: 5m
  labels:
    severity: critical
  annotations:
    summary: "Ошибки при отправке сообщений"
    description: "Более 5% сообщений не доставлены"
```

### Добавление пользователей на основе логов

Если вы видите частые попытки доступа от конкретного пользователя, вы можете добавить его в систему:

```bash
# 1. Найдите telegram_id в логах
docker-compose logs app | grep "Unauthorized access attempt" | grep "telegramId=123456789"

# 2. Добавьте пользователя в БД
docker-compose exec postgres psql -U botuser -d family_calendar -c "
INSERT INTO users (telegram_id, username, first_name, family_id, created_at)
VALUES (123456789, 'username', 'Имя', 1, CURRENT_TIMESTAMP)
ON CONFLICT (telegram_id) DO NOTHING;
"

# 3. Уведомите пользователя, что доступ предоставлен
```

Удачи в использовании Family Calendar Bot! 🎉
