# 📅 Family Calendar Bot

> Telegram бот для управления семейным календарем на базе Spring Boot 3.5.3 и PostgreSQL 18.1

[![Java](https://img.shields.io/badge/Java-21-orange.svg)](https://openjdk.org/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.5.3-brightgreen.svg)](https://spring.io/projects/spring-boot)
[![PostgreSQL](https://img.shields.io/badge/PostgreSQL-18.1-blue.svg)](https://www.postgresql.org/)
[![Telegram Bot API](https://img.shields.io/badge/Telegram%20Bot%20API-9.3.0-blue.svg)](https://core.telegram.org/bots/api)

## ✨ Основные возможности

### Управление событиями
- 📅 **Интерактивный календарь** - визуальный выбор даты с индикаторами существующих событий
- ⏰ **Выбор времени** - удобный интерфейс для указания часов и минут
- ✏️ **Редактирование событий** - изменение названия, даты, времени и описания
- 🗑️ **Корзина** - восстановление удаленных событий в течение 30 дней

### Напоминания и уведомления
- 🔔 **Умные напоминания** - вечером накануне (20:00), за час, за 15 минут до события
- 🌍 **Поддержка часовых поясов** - корректная работа с timezone пользователей (по умолчанию Europe/Moscow)
- 🔄 **Восстановление после сбоя** - автоматическая отправка пропущенных напоминаний
- 🔐 **Защита от дублирования** - пессимистические блокировки предотвращают повторную отправку

### Многопользовательский режим
- 👥 **Семейные календари** - изолированные календари для разных семей
- 🔒 **Персональные события** - события, видимые только вам
- 👨‍👩‍👧‍👦 **Семейные события** - события, видимые всем членам семьи
- 🔐 **Авторизация** - доступ только для зарегистрированных пользователей

### Расширенные функции
- 📎 **Вложения** - прикрепление файлов (документы, изображения, аудио и видео)
- 🔍 **Поиск** - быстрый поиск событий по названию и описанию
- 🔎 **Фильтрация** - фильтры по типу событий (все/семейные/персональные)
- 📊 **Статистика** - просмотр статистики событий за месяц
- 📜 **История изменений** - отслеживание всех изменений событий

## 🚀 Быстрый старт

### Предварительные требования

- Docker 20.10+ и Docker Compose 2.0+
- Telegram бот токен (получите от [@BotFather](https://t.me/BotFather))
- Для production: сервер с публичным IP и открытыми портами 80, 443
- Для разработки: [ngrok](https://ngrok.com/) для HTTPS туннеля

### Production развертывание (с HTTPS)

```bash
# 1. Клонируйте репозиторий
git clone <repository-url>
cd family-calendar-bot

# 2. Сгенерируйте SSL сертификаты
./scripts/unix/ssl.sh <ВАШ_ПУБЛИЧНЫЙ_IP>
# Пример: ./scripts/unix/ssl.sh 176.108.254.68

# 3. Настройте переменные окружения
cp .env.example .env
nano .env  # Отредактируйте файл

# Обязательные параметры в .env:
# TELEGRAM_BOT_TOKEN=ваш_токен_от_BotFather
# TELEGRAM_BOT_USERNAME=ИмяВашегоБота
# TELEGRAM_BOT_WEBHOOK_URL=https://<ВАШ_IP>/webhook
# DB_PASSWORD=надежный_пароль
# SPRING_PROFILES_ACTIVE=prod

# 4. Запустите приложение (с nginx)
./scripts/unix/start.sh prod

# 5. Проверьте статус webhook
curl https://api.telegram.org/bot<ВАШ_ТОКЕН>/getWebhookInfo
```

### Локальная разработка (с ngrok)

```bash
# 1. Установите ngrok
brew install ngrok  # macOS
# или скачайте с https://ngrok.com/download

# 2. Запустите ngrok туннель
ngrok http 8080
# Скопируйте HTTPS URL (например: https://abc123.ngrok.io)

# 3. Настройте .env
cp .env.example .env
nano .env

# Параметры для dev:
# TELEGRAM_BOT_TOKEN=ваш_токен
# TELEGRAM_BOT_USERNAME=ИмяБота
# TELEGRAM_BOT_WEBHOOK_URL=https://abc123.ngrok.io/webhook
# DB_PASSWORD=password
# SPRING_PROFILES_ACTIVE=dev

# 4. Запустите приложение (без nginx)
./scripts/unix/start.sh

# Примечание: при каждом перезапуске ngrok генерирует новый URL
# Обновите TELEGRAM_BOT_WEBHOOK_URL в .env и перезапустите приложение
```

## 📱 Команды бота

### Основные команды

| Команда | Описание | Авторизация |
|---------|----------|-------------|
| `/start` | Начать работу с ботом и проверить авторизацию | ❌ |
| `/help` | Показать список всех доступных команд | ❌ |
| `/add_event` | Создать новое событие через интерактивный календарь | ✅ |
| `/calendar` | Открыть интерактивный календарь для просмотра событий | ✅ |
| `/my_events` | Показать ваши события с кнопками управления (планировщик) | ✅ |

### Просмотр событий

| Команда | Описание | Авторизация |
|---------|----------|-------------|
| `/today` | Показать события на сегодня | ✅ |
| `/week` | Показать события на неделю с группировкой по дням | ✅ |
| `/month` | Показать предстоящие события на 30 дней | ✅ |

### Поиск и фильтрация

| Команда | Описание | Авторизация |
|---------|----------|-------------|
| `/search` | Поиск событий по названию или описанию | ✅ |
| `/filter` | Фильтрация событий по типу (все/семейные/персональные) | ✅ |

### Дополнительные команды

| Команда | Описание | Авторизация |
|---------|----------|-------------|
| `/trash` | Просмотр корзины удаленных событий | ✅ |
| `/stats` | Статистика событий за текущий месяц | ✅ |

> **Примечание:** Команды с ✅ требуют регистрации пользователя в системе. Неавторизованные пользователи получат информативное сообщение с инструкциями по получению доступа.

## 🛠 Технологический стек

### Backend
- **Java 21 LTS** - современная версия Java с улучшенной производительностью
- **Spring Boot 3.5.3** - основной фреймворк приложения (декабрь 2025)
- **Spring Data JPA** - работа с базой данных через JPA/Hibernate
- **Spring Retry** - механизм повторных попыток для API вызовов
- **Spring Scheduling** - планировщики для автоматических задач
- **Spring Actuator** - мониторинг и метрики приложения

### База данных
- **PostgreSQL 18.1** - надежная реляционная база данных (декабрь 2025)
- **Flyway 11.1.0** - версионирование и миграции схемы БД (декабрь 2025)
- **HikariCP** - высокопроизводительный connection pool

### Интеграции
- **Telegram Bot API 9.3.0** - интеграция с Telegram через webhook
- **Nginx 1.27** - reverse proxy для HTTPS

### Производительность
- **Caffeine Cache** - высокопроизводительное кэширование в памяти
- **HikariCP** - высокопроизводительный connection pool для БД

### Логирование и мониторинг
- **SLF4J + Logback** - структурированное логирование
- **Logstash Logback Encoder 8.0** - JSON логирование для ELK stack
- **Micrometer + Prometheus** - метрики приложения

### Инфраструктура
- **Docker & Docker Compose** - контейнеризация и оркестрация
- **Maven 3.9+** - система сборки и управления зависимостями

### Тестирование
- **JUnit 5** - unit тестирование
- **Testcontainers 1.21.2** - интеграционное тестирование с реальной БД (декабрь 2025)
- **jqwik 1.9.2** - property-based тестирование
- **H2 Database** - in-memory БД для unit тестов

### Утилиты
- **Lombok 1.18.38** - уменьшение boilerplate кода

## 🏗 Архитектура

Приложение следует чистой многоуровневой архитектуре с четким разделением ответственности:

```
┌─────────────────────────────────────────┐
│         Telegram Bot API                │
│         (External Service)              │
└──────────────┬──────────────────────────┘
               │ HTTPS Webhook
               ↓
┌─────────────────────────────────────────┐
│      Nginx Reverse Proxy (prod)         │
│  • SSL/TLS терминация                   │
│  • IP фильтрация (Telegram IPs)         │
│  • Secret token валидация               │
└──────────────┬──────────────────────────┘
               │
               ↓
┌─────────────────────────────────────────┐
│      Webhook Controller Layer           │
│  • TelegramWebhookController            │
│  • Прием Updates от Telegram            │
│  • Correlation ID генерация             │
└──────────────┬──────────────────────────┘
               │
               ↓
┌─────────────────────────────────────────┐
│      Processing Layer                   │
│  • UpdateProcessor (async)              │
│  • CommandDispatcher                    │
│  • CallbackQueryRouter                  │
└──────────────┬──────────────────────────┘
               │
               ↓
┌─────────────────────────────────────────┐
│      Handler Layer                      │
│  Command Handlers:                      │
│  • StartCommandHandler                  │
│  • AddEventCommandHandler               │
│  • CalendarCommandHandler               │
│  • PlannerCommandHandler                │
│  • MonthCommandHandler                  │
│  • WeekCommandHandler                   │
│  • TodayCommandHandler                  │
│  • SearchCommandHandler                 │
│  • FilterCommandHandler                 │
│  • TrashCommandHandler                  │
│  • StatsCommandHandler                  │
│  • HelpCommandHandler                   │
│                                         │
│  Callback Handlers:                     │
│  • EventCallbackRouter                  │
│  • AttachmentCallbackRouter             │
│  • ReminderCallbackRouter               │
│  • DateTimeCallbackHandler              │
│  • NavigationCallbackHandler            │
└──────────────┬──────────────────────────┘
               │
               ↓
┌─────────────────────────────────────────┐
│         Service Layer                   │
│  Core Services:                         │
│  • EventService                         │
│  • UserService                          │
│  • ConversationService                  │
│  • ReminderService                      │
│  • AttachmentService                    │
│  • TrashService                         │
│  • SearchService                        │
│  • StatisticsService                    │
│                                         │
│  Infrastructure Services:               │
│  • TelegramMessageService               │
│  • KeyboardService                      │
│  • AuthorizationService                 │
│  • WebhookSecurityService               │
│                                         │
│  Utility Services:                      │
│  • EventHistoryService                  │
│  • PlannerQueryService                  │
└──────────────┬──────────────────────────┘
               │
               ↓
┌─────────────────────────────────────────┐
│      Repository Layer (JPA)             │
│  • EventRepository                      │
│  • UserRepository                       │
│  • FamilyRepository                     │
│  • AttachmentRepository                 │
│  • ReminderRepository                   │
│  • EventHistoryRepository               │
│  • ConversationStateRepository          │
└──────────────┬──────────────────────────┘
               │ JDBC (HikariCP)
               ↓
┌─────────────────────────────────────────┐
│         PostgreSQL Database             │
│  • families, users, events              │
│  • attachments, reminders               │
│  • event_history                        │
│  • conversation_states                  │
│  • Оптимизированные индексы             │
└─────────────────────────────────────────┘

        ┌──────────────────────┐
        │  Scheduled Tasks     │
        │  (@Scheduled)        │
        ├──────────────────────┤
        │ • ReminderScheduler  │
        │   (каждую минуту)    │
        │ • TrashCleanup       │
        │   (ежедневно 2:00)   │
        │ • EventCompletion    │
        │   (каждые 10 минут)  │
        │ • DraftCleanup       │
        │   (каждые 6 часов)   │
        └──────────────────────┘
```

### Ключевые принципы

- **Webhook вместо Long Polling** - мгновенная обработка команд в реальном времени
- **Асинхронная обработка** - UpdateProcessor обрабатывает команды асинхронно (@Async)
- **Dependency Injection** - Spring управляет всеми зависимостями
- **Разделение ответственности** - каждый слой имеет четкую роль (SOLID)
- **Транзакционность** - JPA обеспечивает ACID гарантии
- **Retry механизм** - автоматические повторные попытки для API вызовов
- **Кэширование** - Caffeine Cache для оптимизации производительности (события семьи и пользователя)

## 🗄 База данных

### Схема базы данных

Проект использует PostgreSQL 18.1 с Flyway для управления миграциями.

#### Основные таблицы

**families** - семьи пользователей
- `id` - уникальный идентификатор
- `name` - название семьи
- `created_at` - дата создания

**users** - пользователи бота
- `id` - уникальный идентификатор
- `telegram_id` - Telegram ID (UNIQUE)
- `username`, `first_name`, `last_name` - данные пользователя
- `family_id` - ссылка на семью (FK)
- `event_filter` - фильтр событий (ALL/FAMILY/PERSONAL)
- `timezone` - часовой пояс (по умолчанию Europe/Moscow)
- `created_at` - дата регистрации

**events** - события календаря
- `id` - уникальный идентификатор
- `user_id` - создатель события (FK)
- `family_id` - семья события (FK)
- `title`, `description` - название и описание
- `event_date`, `event_time`, `end_time` - дата и время
- `is_personal` - флаг персонального события
- `status` - статус (DRAFT/ACTIVE/COMPLETED/DELETED)
- `message_id` - ID сообщения Telegram для редактирования
- `deleted_at`, `completed_at` - даты удаления/завершения
- `created_at` - дата создания

**attachments** - вложения к событиям
- `id` - уникальный идентификатор
- `event_id` - событие (FK)
- `file_id` - Telegram file_id
- `file_name`, `file_type`, `file_size` - метаданные файла
- `uploaded_at` - дата загрузки

**reminders** - напоминания о событиях
- `id` - уникальный идентификатор
- `event_id` - событие (FK)
- `reminder_type` - тип (EVENING_BEFORE/ONE_HOUR_BEFORE/FIFTEEN_MINUTES_BEFORE)
- `reminder_time` - время отправки в UTC
- `sent` - флаг отправки
- `sent_at` - дата отправки

**event_history** - история изменений событий
- `id` - уникальный идентификатор
- `event_id` - событие
- `user_id` - пользователь (FK)
- `action_type` - тип действия (created/updated/deleted/restored)
- `field_name`, `old_value`, `new_value` - детали изменения
- `changed_at` - дата изменения

**conversation_states** - состояния диалогов
- `id` - уникальный идентификатор
- `user_id` - пользователь (FK, UNIQUE)
- `state` - текущее состояние диалога
- `event_id` - ID события в контексте
- `attachment_event_id`, `attachment_chat_id`, `attachment_message_id` - контекст вложений
- `attachment_context_created_at` - время создания контекста
- `created_at`, `updated_at` - даты создания/обновления

### Индексы для производительности

Все таблицы имеют оптимизированные индексы:
- `idx_users_telegram_id` - быстрый поиск по Telegram ID
- `idx_events_family_date` - поиск событий семьи по дате
- `idx_events_user_status_deleted` - оптимизация корзины
- `idx_reminders_time_sent` - оптимизация планировщика напоминаний
- `idx_event_history_event_changed` - история изменений
- И другие индексы для оптимизации запросов

### Миграции

Все миграции находятся в `src/main/resources/db/migration/` и управляются Flyway:
- `V1__Initial_schema.sql` - начальная схема БД со всеми таблицами

## 🔐 Безопасность

### HTTPS и SSL/TLSы

Telegram Bot API требует HTTPS для webhook. Проект включает полную настройку безопасности:

- **Nginx reverse proxy** - терминация SSL/TLS и проксирование к Spring Boot
- **SSL сертификаты** - автоматическая генерация через скрипт `ssl.sh`
- **TLS 1.2/1.3** - современные протоколы шифрования
- **Безопасные cipher suites** - ECDHE, AES-GCM, ChaCha20-Poly1305

### Защита webhook

- **Secret token валидация** - дополнительная защита через заголовок `X-Telegram-Bot-Api-Secret-Token`
- **IP фильтрация** - доступ только с Telegram IP адресов (149.154.160.0/20, 91.108.4.0/22)
- **HTTPS обязателен** - все запросы только через защищенное соединение

### Авторизация пользователей

- **Telegram ID авторизация** - доступ только для зарегистрированных пользователей
- **Изоляция семей** - пользователи видят только события своей семьи
- **Персональные события** - события видимые только создателю

### Security headers

Nginx настроен с безопасными заголовками:
- `Strict-Transport-Security` - принудительное использование HTTPS
- `X-Frame-Options: DENY` - защита от clickjacking
- `X-Content-Type-Options: nosniff` - защита от MIME sniffing
- `X-XSS-Protection` - защита от XSS атак

### Хранение секретов

- Все секреты хранятся в переменных окружения (`.env` файл)
- Токены и пароли не хранятся в коде
- `.env` файл исключен из Git через `.gitignore`

### Отправка напоминаний

Планировщик `ReminderScheduler` выполняется каждую минуту:

1. Получает текущее время в UTC
2. Запрашивает напоминания где `reminder_time <= nowUTC AND sent = false`
3. Использует пессимистические блокировки для предотвращения дублирования
4. Атомарно обновляет `sent=true` и `sent_at=nowUTC`

## 📊 Мониторинг и метрики

### Spring Boot Actuator

Приложение предоставляет endpoints для мониторинга:

- `/actuator/health` - статус приложения и зависимостей
- `/actuator/metrics` - метрики Micrometer
- `/actuator/prometheus` - метрики в формате Prometheus
- `/actuator/info` - информация о приложении

### Метрики

Доступные метрики через Prometheus:
- **JVM метрики** - память, потоки, garbage collection
- **HTTP метрики** - количество запросов, время ответа
- **Database метрики** - connection pool, запросы к БД
- **Custom метрики** - авторизация, обработка команд

### Health checks

- **PostgreSQL** - проверка доступности БД
- **Disk space** - проверка свободного места
- **Application** - общий статус приложения

### Логирование

**Структурированное логирование:**
- Текстовые логи: `logs/family-calendar-bot.log`
- JSON логи: `logs/family-calendar-bot-json.log` (для ELK stack)

**Correlation ID:**
- Каждый запрос получает уникальный correlation ID
- ID прокидывается через все слои приложения
- Упрощает трейсинг запросов в логах

**Ротация логов:**
- Максимальный размер файла: 10 МБ
- Хранение: 30 дней
- Максимальный общий размер: 1 ГБ
- Автоматическое сжатие старых логов

**Уровни логирования:**
```yaml
logging:
  level:
    root: INFO
    ru.golubyatnikov.family.calendar.bot: DEBUG
    org.springframework.web: INFO
    org.hibernate.SQL: INFO
```

## 💻 Разработка

### Требования для разработки

- **JDK 21** (Eclipse Temurin или Oracle JDK)
- **Maven 3.9+**
- **Docker и Docker Compose**
- **IDE** с поддержкой Java (IntelliJ IDEA, Eclipse, VS Code)
- **ngrok** (для локальной разработки с webhook)

### Сборка проекта

```bash
# Полная сборка с тестами
mvn clean package

# Сборка без тестов (быстрее)
mvn clean package -DskipTests

# Только компиляция
mvn clean compile
```

### Запуск тестов

```bash
# Все тесты (1000+ тестов, ~5 минут)
mvn test

# Только unit тесты
mvn test -Dtest=*Test

# Только integration тесты
mvn test -Dtest=*IT

# Конкретный тест
mvn test -Dtest=EventServiceTest
```

### Локальный запуск (без Docker)

```bash
# 1. Запустите PostgreSQL
docker run -d \
  --name postgres \
  -e POSTGRES_DB=family_calendar \
  -e POSTGRES_USER=botuser \
  -e POSTGRES_PASSWORD=password \
  -p 5432:5432 \
  postgres:18.1-alpine

# 2. Настройте application-dev.yml или переменные окружения

# 3. Запустите приложение
mvn spring-boot:run -Dspring-boot.run.profiles=dev
```

### Профили Spring

- **dev** - разработка (без nginx, для ngrok)
- **prod** - production (с nginx и SSL)
- **test** - тестирование (H2 in-memory БД)

### Структура проекта

```
src/
├── main/
│   ├── java/ru/golubyatnikov/family/calendar/bot/
│   │   ├── Application.java                    # Главный класс
│   │   ├── annotation/                         # Кастомные аннотации
│   │   ├── aspect/                             # AOP аспекты
│   │   ├── config/                             # Конфигурация (@ConfigurationProperties)
│   │   ├── controller/                         # REST контроллеры (Webhook)
│   │   ├── exception/                          # Исключения и GlobalExceptionHandler
│   │   ├── filter/                             # Фильтры (CorrelationId)
│   │   ├── handler/                            # Обработчики команд и callback
│   │   │   ├── command/                        # Command handlers (/start, /add_event, etc)
│   │   │   └── callback/                       # Callback handlers (кнопки)
│   │   ├── model/                              # Модели данных
│   │   │   ├── entity/                         # JPA Entity (Event, User, Family, etc)
│   │   │   ├── dto/                            # Data Transfer Objects
│   │   │   ├── enums/                          # Перечисления
│   │   │   └── context/                        # Контексты диалогов
│   │   ├── repository/                         # JPA репозитории
│   │   ├── scheduler/                          # Планировщики (@Scheduled)
│   │   ├── service/                            # Бизнес-логика
│   │   │   ├── domain/                         # Domain сервисы (event, user, attachment, etc)
│   │   │   ├── infrastructure/                 # Инфраструктурные сервисы (telegram, auth, etc)
│   │   │   └── presentation/                   # Presentation сервисы (keyboard, formatting, etc)
│   │   └── util/                               # Утилиты
│   └── resources/
│       ├── application.yml                     # Основная конфигурация
│       ├── application-dev.yml                 # Dev профиль
│       ├── application-prod.yml                # Prod профиль
│       ├── logback-spring.xml                  # Конфигурация логирования
│       └── db/migration/                       # Flyway миграции
└── test/
    ├── java/                                   # Тесты (unit, integration, property-based)
    └── resources/
        └── application-test.yml                # Test профиль
```

## 🔧 Управляющие скрипты

Проект включает удобные скрипты для управления приложением.

### Unix/Linux/macOS

```bash
# Запуск приложения
./scripts/unix/start.sh          # Dev режим (без nginx)
./scripts/unix/start.sh prod     # Prod режим (с nginx)

# Остановка приложения
./scripts/unix/stop.sh

# Просмотр логов
./scripts/unix/logs.sh           # Логи приложения
./scripts/unix/logs.sh nginx     # Логи nginx (если запущен)

# Полная очистка (удаление volumes с данными)
./scripts/unix/clean.sh

# Генерация SSL сертификатов
./scripts/unix/ssl.sh <IP_АДРЕС> [DAYS]
# Примеры:
./scripts/unix/ssl.sh 176.108.254.68        # 10 лет (по умолчанию)
./scripts/unix/ssl.sh 176.108.254.68 365    # 1 год
```

### Windows

```cmd
REM Запуск приложения
scripts\windows\start.bat          REM Dev режим
scripts\windows\start.bat prod     REM Prod режим

REM Остановка приложения
scripts\windows\stop.bat

REM Просмотр логов
scripts\windows\logs.bat

REM Полная очистка
scripts\windows\clean.bat

REM Генерация SSL сертификатов
scripts\windows\ssl.bat <IP_АДРЕС> [DAYS]
```

### Docker Compose команды

```bash
# Запуск контейнеров
docker-compose up -d                    # Dev режим (без nginx)
docker-compose --profile prod up -d     # Prod режим (с nginx)

# Остановка контейнеров
docker-compose down

# Просмотр логов
docker-compose logs -f app              # Логи приложения
docker-compose logs -f postgres         # Логи PostgreSQL
docker-compose logs -f nginx            # Логи nginx

# Статус контейнеров
docker-compose ps

# Перезапуск контейнера
docker-compose restart app

# Пересборка образов
docker-compose build --no-cache
```

## 🐳 Docker

### Многоэтапная сборка

Dockerfile использует многоэтапную сборку для оптимизации размера образа:

1. **Builder stage** - сборка приложения с Maven
2. **Runtime stage** - финальный образ с JRE

### Оптимизации

- Использование Alpine Linux для минимального размера
- Кэширование зависимостей Maven
- Непривилегированный пользователь для безопасности
- JVM настройки для контейнеров (`-XX:+UseContainerSupport`)

### Docker Compose сервисы

**postgres** - PostgreSQL 18.1
- Персистентное хранилище через volume
- Health check для готовности
- Порт 5433 (внешний) → 5432 (внутренний)

**app** - Spring Boot приложение
- Зависит от postgres (condition: service_healthy)
- Персистентное хранилище логов
- Порт 8080
- Health check через actuator

**nginx** - Nginx reverse proxy (только prod)
- Профиль: prod
- SSL/TLS терминация
- Порты 80 (HTTP) и 443 (HTTPS)
- Персистентное хранилище логов

### Volumes

- `postgres_data` - данные PostgreSQL
- `app_logs` - логи приложения
- `nginx_logs` - логи nginx

### Network

Все сервисы работают в изолированной сети `family-calendar-network`.

## 🔄 CI/CD и развертывание

### Рекомендуемый workflow

1. **Разработка** - локальная разработка с ngrok
2. **Тестирование** - запуск всех тестов перед коммитом
3. **Сборка** - создание Docker образа
4. **Развертывание** - запуск на production сервере

### Production checklist

- [ ] Сгенерированы SSL сертификаты
- [ ] Настроен `.env` файл с production параметрами
- [ ] Установлен надежный пароль БД
- [ ] Открыты порты 80 и 443 на сервере
- [ ] Настроен firewall (только Telegram IPs для webhook)
- [ ] Проверен webhook через Telegram API
- [ ] Настроен мониторинг и алерты
- [ ] Настроена ротация логов
- [ ] Создан backup PostgreSQL данных

### Мониторинг в production

```bash
# Проверка статуса приложения
curl https://<ВАШ_IP>/actuator/health

# Проверка webhook в Telegram
curl https://api.telegram.org/bot<TOKEN>/getWebhookInfo

# Просмотр логов
docker-compose logs -f app

# Метрики Prometheus
curl http://localhost:8080/actuator/prometheus
```

### Backup и восстановление

```bash
# Backup PostgreSQL
docker exec family-calendar-postgres pg_dump \
  -U botuser family_calendar > backup.sql

# Восстановление
docker exec -i family-calendar-postgres psql \
  -U botuser family_calendar < backup.sql
```

## 📚 Дополнительная документация

### Конфигурация

Все параметры конфигурации документированы в `application.yml`:

- **Планировщики** - интервалы выполнения задач
- **Корзина** - срок хранения удаленных событий (30 дней)
- **Файлы** - максимальный размер вложений (20 МБ)
- **Напоминания** - типы и настройки по умолчанию
- **Форматирование** - форматы дат и времени
- **Часовые пояса** - default timezone (Europe/Moscow)
- **Кэширование** - Caffeine Cache для событий (настраивается через application.yml)

### Планировщики

Приложение использует 4 планировщика:

1. **ReminderScheduler** - отправка напоминаний (каждую минуту)
2. **TrashCleanupScheduler** - очистка корзины (ежедневно в 2:00)
3. **EventCompletionScheduler** - завершение событий (каждые 10 минут)
4. **DraftCleanupScheduler** - очистка черновиков (каждые 6 часов)

### Миграции базы данных

Все миграции находятся в `src/main/resources/db/migration/` и управляются Flyway:

- `V1__Initial_schema.sql` - начальная схема БД со всеми таблицами

Flyway автоматически применяет миграции при запуске приложения.

---

**Made with ❤️ for my family**

*Family Calendar Bot - управляйте семейным расписанием легко и удобно!*
