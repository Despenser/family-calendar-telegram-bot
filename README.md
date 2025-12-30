# Family Calendar Bot 📅

Telegram бот для управления семейным календарем на базе Spring Boot 3.5.3 и PostgreSQL. Позволяет членам семьи планировать события, получать уведомления и просматривать расписание через удобный интерфейс Telegram.

## 🎯 Возможности

- 📅 **Создание событий** - добавляйте события с датой, временем и описанием
- 👀 **Просмотр расписания** - смотрите все предстоящие события семьи на 7 дней вперед
- ✏️ **Управление событиями** - редактируйте и удаляйте свои события
- 🔔 **Умные уведомления** - автоматические напоминания за 1 час до события для всей семьи
- 👥 **Многопользовательский режим** - поддержка нескольких семей с изолированными календарями
- 🔐 **Безопасность** - авторизация по Telegram ID, доступ только для зарегистрированных пользователей
- ⚡ **Webhook интеграция** - мгновенная обработка команд в реальном времени
- 🐳 **Docker Ready** - простое развертывание с помощью Docker Compose

## 🛠 Технологии

- **Java 21 LTS** - современная версия Java с улучшенной производительностью
- **Spring Boot 3.5.3** - основной фреймворк приложения (декабрь 2025)
- **Spring Data JPA** - работа с базой данных через JPA/Hibernate
- **PostgreSQL 18.1** - надежная реляционная база данных (декабрь 2025)
- **Telegram Bot API 8.2.0** - интеграция с Telegram через telegrambots-spring-boot-starter
- **Flyway 11.1.0** - версионирование и миграции схемы БД (декабрь 2025)
- **Docker & Docker Compose** - контейнеризация и оркестрация
- **Testcontainers 1.21.2** - интеграционное тестирование с реальной БД (декабрь 2025)
- **Maven 3.9+** - система сборки и управления зависимостями
- **Lombok** - уменьшение boilerplate кода
- **SLF4J + Logback** - структурированное логирование

## 🚀 Быстрый старт с Docker Compose

### Предварительные требования

- Docker 20.10+ и Docker Compose 2.0+
- Telegram бот токен (получите от [@BotFather](https://t.me/BotFather))
- Публичный HTTPS URL для webhook (можно использовать ngrok для разработки)

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
   # База данных
   DB_PASSWORD=your_secure_password_here
   
   # Telegram Bot
   TELEGRAM_BOT_TOKEN=1234567890:ABCdefGHIjklMNOpqrsTUVwxyz
   TELEGRAM_BOT_USERNAME=YourFamilyCalendarBot
   
   # Webhook URL (должен быть доступен из интернета по HTTPS)
   TELEGRAM_BOT_WEBHOOK_URL=https://your-domain.com/webhook
   ```

4. **Запустите приложение**
   ```bash
   # Используйте готовый скрипт
   ./start.sh
   
   # Или напрямую через Docker Compose
   docker-compose up -d
   ```

5. **Проверьте статус и логи**
   ```bash
   # Статус контейнеров
   docker-compose ps
   
   # Логи приложения
   ./logs.sh
   
   # Или напрямую
   docker-compose logs -f app
   ```

6. **Остановите приложение**
   ```bash
   # Используйте готовый скрипт
   ./stop.sh
   
   # Или напрямую через Docker Compose
   docker-compose down
   ```

### Управляющие скрипты

Проект включает удобные скрипты для управления:

- `start.sh` / `start.bat` - запуск приложения
- `stop.sh` / `stop.bat` - остановка приложения
- `logs.sh` / `logs.bat` - просмотр логов
- `clean.sh` / `clean.bat` - полная очистка (удаление volumes с данными)

### 🔧 Локальная разработка с ngrok

Для локальной разработки с webhook используйте [ngrok](https://ngrok.com/) для создания публичного HTTPS туннеля:

1. **Установите ngrok**
   ```bash
   # macOS (Homebrew)
   brew install ngrok/ngrok/ngrok
   
   # Windows (Chocolatey)
   choco install ngrok
   
   # Linux или скачайте с https://ngrok.com/download
   ```

2. **Зарегистрируйтесь на ngrok.com и получите authtoken**
   ```bash
   ngrok config add-authtoken YOUR_AUTH_TOKEN
   ```

3. **Запустите ngrok туннель**
   ```bash
   ngrok http 8080
   ```
   
   Вы увидите вывод вроде:
   ```
   Forwarding  https://abc123def456.ngrok.io -> http://localhost:8080
   ```

4. **Скопируйте HTTPS URL** (например, `https://abc123def456.ngrok.io`)

5. **Обновите .env файл с ngrok URL**
   ```env
   TELEGRAM_BOT_WEBHOOK_URL=https://abc123def456.ngrok.io/webhook
   ```

6. **Перезапустите приложение**
   ```bash
   docker-compose restart app
   ```

7. **Проверьте регистрацию webhook в логах**
   ```bash
   docker-compose logs app | grep -i webhook
   ```

**Важно:** При каждом перезапуске ngrok генерирует новый URL (в бесплатной версии), поэтому нужно обновлять `.env` и перезапускать приложение.

## 📱 Команды бота

| Команда | Описание | Пример использования |
|---------|----------|---------------------|
| `/start` | Начать работу с ботом и проверить авторизацию | `/start` |
| `/help` | Показать список всех доступных команд | `/help` |
| `/add_event` | Создать новое событие в календаре | `/add_event` → следуйте инструкциям бота |
| `/upcoming_events` | Показать предстоящие события семьи на 7 дней | `/upcoming_events` |
| `/my_events` | Показать ваши события с кнопками редактирования/удаления | `/my_events` |

### Примеры использования

**Создание события:**
```
Вы: /add_event
Бот: Введите дату события (формат: ДД.ММ.ГГГГ)
Вы: 25.12.2025
Бот: Введите время события (формат: ЧЧ:ММ)
Вы: 18:00
Бот: Введите название и описание события
Вы: Новогодний ужин - Семейный праздник с подарками
Бот: ✅ Событие создано!
     📅 Дата: 25.12.2025
     🕐 Время: 18:00
     📝 Новогодний ужин - Семейный праздник с подарками
```

**Просмотр событий:**
```
Вы: /upcoming_events
Бот: 📅 Предстоящие события семьи:
     
     1️⃣ 25.12.2025 в 18:00
     📝 Новогодний ужин
     👤 Создал: Иван
     
     2️⃣ 31.12.2025 в 23:00
     📝 Встреча Нового Года
     👤 Создал: Мария
```

## 🏗 Архитектура

Приложение следует многоуровневой архитектуре с четким разделением ответственности:

```
┌─────────────────────────────────────────┐
│         Telegram Bot API                │
│         (External Service)              │
└──────────────┬──────────────────────────┘
               │ Webhook (HTTPS)
               ↓
┌─────────────────────────────────────────┐
│      Webhook Controller Layer           │
│  (@RestController)                      │
│  • TelegramWebhookController            │
│  • Прием Updates от Telegram           │
│  • Валидация токена                     │
└──────────────┬──────────────────────────┘
               │
               ↓
┌─────────────────────────────────────────┐
│      Processing Layer                   │
│  • UpdateProcessor (async)              │
│  • CommandDispatcher                    │
└──────────────┬──────────────────────────┘
               │
               ↓
┌─────────────────────────────────────────┐
│      Command Handler Layer              │
│  • StartCommandHandler                  │
│  • AddEventCommandHandler               │
│  • UpcomingEventsCommandHandler         │
│  • MyEventsCommandHandler               │
│  • HelpCommandHandler                   │
└──────────────┬──────────────────────────┘
               │
               ↓
┌─────────────────────────────────────────┐
│         Service Layer                   │
│  • EventService                         │
│  • UserService                          │
│  • NotificationService (@Scheduled)     │
│  • TelegramMessageService               │
└──────────────┬──────────────────────────┘
               │
               ↓
┌─────────────────────────────────────────┐
│      Repository Layer (JPA)             │
│  • EventRepository                      │
│  • UserRepository                       │
│  • FamilyRepository                     │
└──────────────┬──────────────────────────┘
               │ JDBC
               ↓
┌─────────────────────────────────────────┐
│         PostgreSQL Database             │
│  • families, users, events tables       │
│  • Индексы для оптимизации             │
└─────────────────────────────────────────┘

        ┌──────────────────────┐
        │  Scheduled Tasks     │
        │  • NotificationService│
        │  (каждые 5 минут)    │
        └──────────────────────┘
```

### Ключевые принципы

- **Webhook вместо Long Polling** - мгновенная обработка команд
- **Асинхронная обработка** - UpdateProcessor обрабатывает команды асинхронно
- **Dependency Injection** - Spring управляет всеми зависимостями
- **Разделение ответственности** - каждый слой имеет четкую роль
- **Scheduled Tasks** - автоматическая проверка и отправка уведомлений
- **Транзакционность** - JPA обеспечивает ACID гарантии

## 🗄 Структура базы данных

### ER-диаграмма

```
┌─────────────────────┐
│      families       │
├─────────────────────┤
│ PK  id              │
│     name            │
│     created_at      │
└──────────┬──────────┘
           │
           │ 1:N
           │
┌──────────┴──────────┐
│       users         │
├─────────────────────┤
│ PK  id              │
│ UK  telegram_id     │
│     username        │
│     first_name      │
│     last_name       │
│ FK  family_id       │◄────┐
│     created_at      │     │
└──────────┬──────────┘     │
           │                │
           │ 1:N            │
           │                │
┌──────────┴──────────┐     │
│       events        │     │
├─────────────────────┤     │
│ PK  id              │     │
│ FK  user_id         │     │
│ FK  family_id       │─────┘
│     title           │
│     description     │
│     event_date      │
│     event_time      │
│     notified        │
│     created_at      │
└─────────────────────┘
```

### Таблица `families`

Хранит информацию о семьях (группах пользователей).

| Поле | Тип | Описание |
|------|-----|----------|
| `id` | BIGSERIAL | Уникальный идентификатор семьи (PK) |
| `name` | VARCHAR(255) | Название семьи |
| `created_at` | TIMESTAMP | Дата создания семьи |

### Таблица `users`

Хранит информацию о пользователях бота.

| Поле | Тип | Описание |
|------|-----|----------|
| `id` | BIGSERIAL | Уникальный идентификатор пользователя (PK) |
| `telegram_id` | BIGINT | Telegram ID пользователя (UNIQUE, NOT NULL) |
| `username` | VARCHAR(255) | Telegram username |
| `first_name` | VARCHAR(255) | Имя пользователя (NOT NULL) |
| `last_name` | VARCHAR(255) | Фамилия пользователя |
| `family_id` | BIGINT | Ссылка на семью (FK → families.id) |
| `created_at` | TIMESTAMP | Дата регистрации |

**Индексы:**
- `idx_users_telegram_id` на `telegram_id` - быстрый поиск по Telegram ID
- `idx_users_family_id` на `family_id` - быстрый поиск пользователей семьи

### Таблица `events`

Хранит события календаря.

| Поле | Тип | Описание |
|------|-----|----------|
| `id` | BIGSERIAL | Уникальный идентификатор события (PK) |
| `user_id` | BIGINT | Создатель события (FK → users.id, NOT NULL) |
| `family_id` | BIGINT | Семья события (FK → families.id, NOT NULL) |
| `title` | VARCHAR(255) | Название события (NOT NULL) |
| `description` | TEXT | Описание события |
| `event_date` | DATE | Дата события (NOT NULL) |
| `event_time` | TIME | Время события (NOT NULL) |
| `notified` | BOOLEAN | Флаг отправки уведомления (DEFAULT FALSE) |
| `created_at` | TIMESTAMP | Дата создания записи |

**Индексы:**
- `idx_events_family_date` на `(family_id, event_date)` - быстрый поиск событий семьи по дате
- `idx_events_user_id` на `user_id` - быстрый поиск событий пользователя
- `idx_events_notified` на `(notified, event_date, event_time)` - оптимизация для NotificationService

**Каскадное удаление:**
- При удалении пользователя удаляются все его события (`ON DELETE CASCADE`)
- При удалении семьи удаляются все события семьи (`ON DELETE CASCADE`)

## 📁 Структура проекта

```
family-calendar-bot/
├── .kiro/                          # Спецификации и документация проекта
│   └── specs/
│       └── telegram-spring-bot/
│           ├── requirements.md     # Требования к системе
│           ├── design.md          # Документ проектирования
│           └── tasks.md           # План реализации
├── src/
│   ├── main/
│   │   ├── java/ru/golubyatnikov/family/calendar/bot/
│   │   │   ├── Application.java                    # Главный класс приложения
│   │   │   ├── config/                            # Конфигурация
│   │   │   │   ├── BotConfig.java                 # Конфигурация бота
│   │   │   │   └── WebhookRegistrar.java          # Регистрация webhook
│   │   │   ├── controller/                        # REST контроллеры
│   │   │   │   └── TelegramWebhookController.java # Прием webhook от Telegram
│   │   │   ├── handler/                           # Обработчики команд
│   │   │   │   ├── CommandHandler.java            # Интерфейс обработчика
│   │   │   │   ├── StartCommandHandler.java       # /start
│   │   │   │   ├── HelpCommandHandler.java        # /help
│   │   │   │   ├── AddEventCommandHandler.java    # /add_event
│   │   │   │   ├── UpcomingEventsCommandHandler.java # /upcoming_events
│   │   │   │   └── MyEventsCommandHandler.java    # /my_events
│   │   │   ├── service/                           # Бизнес-логика
│   │   │   │   ├── CommandDispatcher.java         # Маршрутизация команд
│   │   │   │   ├── UpdateProcessor.java           # Обработка Updates
│   │   │   │   ├── EventService.java              # Управление событиями
│   │   │   │   ├── UserService.java               # Управление пользователями
│   │   │   │   ├── NotificationService.java       # Отправка уведомлений
│   │   │   │   └── TelegramMessageService.java    # Отправка сообщений
│   │   │   ├── repository/                        # Доступ к данным
│   │   │   │   ├── EventRepository.java           # Репозиторий событий
│   │   │   │   ├── UserRepository.java            # Репозиторий пользователей
│   │   │   │   └── FamilyRepository.java          # Репозиторий семей
│   │   │   ├── model/                             # JPA Entity
│   │   │   │   ├── Event.java                     # Сущность события
│   │   │   │   ├── User.java                      # Сущность пользователя
│   │   │   │   └── Family.java                    # Сущность семьи
│   │   │   └── exception/                         # Исключения
│   │   │       ├── GlobalExceptionHandler.java    # Глобальный обработчик
│   │   │       ├── UserNotFoundException.java
│   │   │       ├── EventNotFoundException.java
│   │   │       ├── UnauthorizedAccessException.java
│   │   │       └── InvalidDateException.java
│   │   └── resources/
│   │       ├── application.yml                    # Основная конфигурация
│   │       ├── application-dev.yml                # Dev профиль
│   │       ├── application-prod.yml               # Prod профиль
│   │       ├── logback-spring.xml                 # Конфигурация логирования
│   │       └── db/migration/                      # Flyway миграции
│   │           └── V1__Initial_schema.sql         # Начальная схема БД
│   └── test/
│       ├── java/ru/golubyatnikov/family/calendar/bot/
│       │   ├── ApplicationTests.java              # Тесты контекста
│       │   ├── config/                            # Тесты конфигурации
│       │   ├── handler/                           # Тесты обработчиков
│       │   ├── service/                           # Тесты сервисов
│       │   └── model/                             # Тесты моделей
│       └── resources/
│           └── application-test.yml               # Test профиль
├── docker-compose.yml                             # Docker Compose конфигурация
├── Dockerfile                                     # Docker образ приложения
├── pom.xml                                        # Maven конфигурация
├── .env.example                                   # Пример переменных окружения
├── .gitignore                                     # Git ignore правила
├── start.sh / start.bat                           # Скрипты запуска
├── stop.sh / stop.bat                             # Скрипты остановки
├── logs.sh / logs.bat                             # Скрипты просмотра логов
├── clean.sh / clean.bat                           # Скрипты очистки
└── README.md                                      # Этот файл
```

## 💻 Разработка

### Требования для разработки

- JDK 21 (рекомендуется Eclipse Temurin или Oracle JDK)
- Maven 3.9+
- Docker и Docker Compose (для локального запуска БД)
- IDE с поддержкой Java (IntelliJ IDEA, Eclipse, VS Code)

### Сборка проекта

```bash
# Полная сборка с тестами
mvn clean package

# Сборка без тестов
mvn clean package -DskipTests

# Только компиляция
mvn clean compile
```

### Запуск тестов

```bash
# Все тесты
mvn test

# Только unit тесты
mvn test -Dtest="*Test"

# Только integration тесты
mvn test -Dtest="*IntegrationTest"

# Конкретный тест
mvn test -Dtest=EventServiceTest

# С покрытием кода
mvn clean test jacoco:report
```

### Запуск локально (без Docker)

```bash
# 1. Запустите PostgreSQL локально или через Docker
docker run -d \
  --name postgres-dev \
  -e POSTGRES_DB=family_calendar \
  -e POSTGRES_USER=botuser \
  -e POSTGRES_PASSWORD=devpassword \
  -p 5432:5432 \
  postgres:18.1-alpine

# 2. Настройте переменные окружения
export SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5432/family_calendar
export SPRING_DATASOURCE_USERNAME=botuser
export SPRING_DATASOURCE_PASSWORD=devpassword
export TELEGRAM_BOT_TOKEN=your_bot_token
export TELEGRAM_BOT_WEBHOOK_URL=https://your-ngrok-url.ngrok.io/webhook

# 3. Запустите приложение
mvn spring-boot:run

# Или с профилем dev
mvn spring-boot:run -Dspring-boot.run.profiles=dev
```

### Работа с базой данных

```bash
# Подключение к PostgreSQL в Docker
docker-compose exec postgres psql -U botuser -d family_calendar

# Просмотр таблиц
\dt

# Просмотр структуры таблицы
\d events

# Просмотр данных
SELECT * FROM events;

# Выход
\q
```

## 🐳 Docker команды

### Управление контейнерами

```bash
# Запуск в фоновом режиме
docker-compose up -d

# Запуск с просмотром логов
docker-compose up

# Остановка контейнеров
docker-compose down

# Остановка с удалением volumes (все данные будут потеряны!)
docker-compose down -v

# Перезапуск всех сервисов
docker-compose restart

# Перезапуск конкретного сервиса
docker-compose restart app
```

### Сборка образов

```bash
# Пересборка образов
docker-compose build

# Пересборка без кэша
docker-compose build --no-cache

# Пересборка и запуск
docker-compose up -d --build
```

### Просмотр логов

```bash
# Все сервисы
docker-compose logs -f

# Только приложение
docker-compose logs -f app

# Только база данных
docker-compose logs -f postgres

# Последние 100 строк
docker-compose logs --tail=100 app

# С временными метками
docker-compose logs -f -t app
```

### Мониторинг и отладка

```bash
# Статус контейнеров
docker-compose ps

# Использование ресурсов
docker stats

# Подключение к контейнеру приложения
docker-compose exec app sh

# Подключение к PostgreSQL
docker-compose exec postgres psql -U botuser -d family_calendar

# Просмотр переменных окружения
docker-compose exec app env
```

### Очистка

```bash
# Удаление остановленных контейнеров
docker-compose rm

# Удаление volumes
docker volume ls
docker volume rm family-calendar-bot_postgres_data

# Полная очистка (используйте скрипт)
./clean.sh  # или clean.bat на Windows
```

## 🧪 Тестирование

### Типы тестов

Проект включает несколько типов тестов:

1. **Unit тесты** - тестирование отдельных компонентов с моками
2. **Integration тесты** - тестирование с реальной БД через Testcontainers
3. **Configuration тесты** - проверка корректности конфигурации

### Структура тестов

```
src/test/java/
├── ApplicationTests.java                    # Тест загрузки контекста
├── config/
│   ├── BotConfigValidationTest.java        # Валидация конфигурации
│   ├── ConfigurationPropertiesTest.java    # Тест properties
│   └── WebhookRegistrarTest.java           # Тест регистрации webhook
├── handler/
│   ├── StartCommandHandlerTest.java        # Unit тесты handlers
│   ├── HelpCommandHandlerTest.java
│   └── AddEventCommandHandlerTest.java
├── service/
│   ├── NotificationServiceTest.java        # Unit тесты сервисов
│   └── UpdateProcessorTest.java
└── model/
    └── EntityRelationshipsTest.java        # Тесты JPA entities
```

### Testcontainers

Проект использует Testcontainers для интеграционных тестов с реальной PostgreSQL:

```java
@SpringBootTest
@Testcontainers
class EventServiceIntegrationTest {
    
    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(
        "postgres:18.1-alpine")
        .withDatabaseName("testdb")
        .withUsername("test")
        .withPassword("test");
    
    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }
    
    @Test
    void shouldCreateAndRetrieveEvent() {
        // Тест с реальной БД
    }
}
```

### Запуск тестов

```bash
# Все тесты
mvn test

# С подробным выводом
mvn test -X

# Конкретный класс
mvn test -Dtest=EventServiceTest

# Конкретный метод
mvn test -Dtest=EventServiceTest#shouldCreateEvent

# Пропуск тестов при сборке
mvn package -DskipTests
```

## 🔧 Переменные окружения

### Обязательные переменные

| Переменная | Описание | Пример |
|------------|----------|--------|
| `DB_PASSWORD` | Пароль для PostgreSQL | `secure_password_123` |
| `TELEGRAM_BOT_TOKEN` | Токен бота от @BotFather | `1234567890:ABCdef...` |
| `TELEGRAM_BOT_USERNAME` | Username бота | `FamilyCalendarBot` |
| `TELEGRAM_BOT_WEBHOOK_URL` | Публичный HTTPS URL для webhook | `https://example.com/webhook` |

### Опциональные переменные

| Переменная | Описание | Значение по умолчанию |
|------------|----------|-----------------------|
| `SPRING_PROFILES_ACTIVE` | Активный профиль Spring | `prod` |
| `SPRING_DATASOURCE_URL` | JDBC URL базы данных | `jdbc:postgresql://postgres:5432/family_calendar` |
| `SPRING_DATASOURCE_USERNAME` | Пользователь БД | `botuser` |
| `SERVER_PORT` | Порт приложения | `8080` |
| `LOGGING_LEVEL_ROOT` | Уровень логирования | `INFO` |

### Пример .env файла

```env
# База данных
DB_PASSWORD=my_secure_password_here

# Telegram Bot
TELEGRAM_BOT_TOKEN=1234567890:ABCdefGHIjklMNOpqrsTUVwxyz
TELEGRAM_BOT_USERNAME=MyFamilyCalendarBot
TELEGRAM_BOT_WEBHOOK_URL=https://my-domain.com/webhook

# Опционально
SPRING_PROFILES_ACTIVE=prod
LOGGING_LEVEL_ROOT=INFO
```

**Важно:** Никогда не коммитьте файл `.env` в Git! Он уже добавлен в `.gitignore`.

## ❗ Troubleshooting

### Приложение не может подключиться к базе данных

**Симптомы:**
```
Connection refused: connect
Could not connect to PostgreSQL
```

**Решения:**
1. Проверьте, что PostgreSQL контейнер запущен:
   ```bash
   docker-compose ps
   ```
2. Проверьте логи PostgreSQL:
   ```bash
   docker-compose logs postgres
   ```
3. Убедитесь, что healthcheck проходит успешно:
   ```bash
   docker-compose ps postgres
   # Должно быть "healthy" в статусе
   ```
4. Проверьте переменные окружения в `.env`
5. Попробуйте пересоздать контейнеры:
   ```bash
   docker-compose down
   docker-compose up -d
   ```

### Webhook не регистрируется

**Симптомы:**
```
Failed to register webhook
Webhook registration failed
```

**Решения:**
1. Проверьте, что `TELEGRAM_BOT_WEBHOOK_URL` доступен из интернета
2. Убедитесь, что URL использует HTTPS (не HTTP)
3. Проверьте токен бота в `.env`
4. Проверьте логи приложения:
   ```bash
   docker-compose logs app | grep -i webhook
   ```
5. Для локальной разработки убедитесь, что ngrok запущен:
   ```bash
   ngrok http 8080
   ```
6. Проверьте текущий webhook через Telegram API:
   ```bash
   curl https://api.telegram.org/bot<YOUR_TOKEN>/getWebhookInfo
   ```

### Миграции Flyway не применяются

**Симптомы:**
```
Flyway migration failed
Schema validation failed
```

**Решения:**
1. Проверьте логи Flyway в логах приложения:
   ```bash
   docker-compose logs app | grep -i flyway
   ```
2. Убедитесь, что база данных создана и доступна
3. Проверьте права пользователя БД:
   ```bash
   docker-compose exec postgres psql -U botuser -d family_calendar -c "\du"
   ```
4. Если нужно сбросить миграции (ВНИМАНИЕ: удалит все данные):
   ```bash
   docker-compose down -v
   docker-compose up -d
   ```
5. Проверьте таблицу истории миграций:
   ```sql
   SELECT * FROM flyway_schema_history;
   ```

### Бот не отвечает на команды

**Симптомы:**
- Бот онлайн, но не отвечает на `/start` и другие команды

**Решения:**
1. Проверьте, что webhook зарегистрирован:
   ```bash
   curl https://api.telegram.org/bot<YOUR_TOKEN>/getWebhookInfo
   ```
2. Проверьте логи приложения на наличие ошибок:
   ```bash
   docker-compose logs -f app
   ```
3. Убедитесь, что пользователь зарегистрирован в БД:
   ```sql
   SELECT * FROM users WHERE telegram_id = YOUR_TELEGRAM_ID;
   ```
4. Проверьте, что UpdateProcessor обрабатывает команды:
   ```bash
   docker-compose logs app | grep -i "received update"
   ```

### Уведомления не отправляются

**Симптомы:**
- События создаются, но уведомления не приходят за 1 час

**Решения:**
1. Проверьте, что NotificationService запущен:
   ```bash
   docker-compose logs app | grep -i notification
   ```
2. Проверьте события в БД:
   ```sql
   SELECT * FROM events WHERE notified = false AND event_date >= CURRENT_DATE;
   ```
3. Убедитесь, что scheduled tasks работают:
   ```bash
   docker-compose logs app | grep -i "scheduled"
   ```
4. Проверьте временную зону контейнера:
   ```bash
   docker-compose exec app date
   ```

### Ошибки при сборке Docker образа

**Симптомы:**
```
ERROR: failed to solve
Build failed
```

**Решения:**
1. Очистите Docker кэш:
   ```bash
   docker system prune -a
   ```
2. Пересоберите без кэша:
   ```bash
   docker-compose build --no-cache
   ```
3. Проверьте, что Maven сборка проходит локально:
   ```bash
   mvn clean package
   ```
4. Убедитесь, что JAR файл создан:
   ```bash
   ls -lh target/*.jar
   ```

### Высокое использование памяти

**Решения:**
1. Ограничьте память для JVM в Dockerfile:
   ```dockerfile
   ENTRYPOINT ["java", "-Xmx512m", "-Xms256m", "-jar", "app.jar"]
   ```
2. Настройте limits в docker-compose.yml:
   ```yaml
   services:
     app:
       deploy:
         resources:
           limits:
             memory: 1G
   ```

### Проблемы с правами доступа к файлам

**Симптомы:**
```
Permission denied
Cannot write to directory
```

**Решения:**
1. На Linux/Mac проверьте права на скрипты:
   ```bash
   chmod +x start.sh stop.sh logs.sh clean.sh
   ```
2. Проверьте владельца файлов:
   ```bash
   ls -la
   ```
3. Если нужно, измените владельца:
   ```bash
   sudo chown -R $USER:$USER .
   ```

## 📚 Дополнительная документация

- [SETUP.md](SETUP.md) - Подробные инструкции по настройке
- [SCRIPTS.md](SCRIPTS.md) - Описание управляющих скриптов
- [.kiro/specs/telegram-spring-bot/requirements.md](.kiro/specs/telegram-spring-bot/requirements.md) - Требования к системе
- [.kiro/specs/telegram-spring-bot/design.md](.kiro/specs/telegram-spring-bot/design.md) - Документ проектирования
- [.kiro/specs/telegram-spring-bot/tasks.md](.kiro/specs/telegram-spring-bot/tasks.md) - План реализации

## 🤝 Вклад в проект

Мы приветствуем вклад в проект! Пожалуйста:

1. Форкните репозиторий
2. Создайте ветку для вашей фичи (`git checkout -b feature/amazing-feature`)
3. Закоммитьте изменения (`git commit -m 'Add amazing feature'`)
4. Запушьте в ветку (`git push origin feature/amazing-feature`)
5. Откройте Pull Request

### Правила разработки

- Следуйте Java Code Conventions
- Пишите тесты для нового функционала
- Обновляйте документацию при необходимости
- Используйте осмысленные commit сообщения

## 📄 Лицензия

MIT License - см. файл [LICENSE](LICENSE) для деталей.

## 👥 Авторы

- Разработчик - [@golubyatnikov](https://github.com/golubyatnikov)

## 📞 Контакты

Для вопросов и предложений:
- Создавайте [Issues](https://github.com/your-repo/issues) в репозитории
- Отправляйте Pull Requests с улучшениями

## 🙏 Благодарности

- [Spring Boot](https://spring.io/projects/spring-boot) - за отличный фреймворк
- [Telegram Bot API](https://core.telegram.org/bots/api) - за мощный API
- [Testcontainers](https://www.testcontainers.org/) - за удобное тестирование
- [PostgreSQL](https://www.postgresql.org/) - за надежную БД

---

**Сделано с ❤️ для семейного планирования**
