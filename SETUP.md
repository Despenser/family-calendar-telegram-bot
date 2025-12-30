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

## Следующие шаги

После успешной настройки:

1. ✅ Протестируйте все команды бота
2. ✅ Добавьте всех членов семьи в БД
3. ✅ Создайте тестовые события
4. ✅ Проверьте работу уведомлений
5. ✅ Настройте мониторинг и алерты (для production)
6. ✅ Настройте регулярные бэкапы БД (для production)

Удачи в использовании Family Calendar Bot! 🎉
