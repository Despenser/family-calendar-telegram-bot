# Проверка миграции V14

## Что было сделано

Создана миграция базы данных `V14__Add_is_my_events_header_to_events.sql`, которая:

1. **Добавляет новую колонку** `is_my_events_header` типа BOOLEAN с значением по умолчанию FALSE в таблицу `events`
2. **Устанавливает флаг TRUE** для первых событий каждого пользователя (событие с самой ранней датой и временем среди активных событий)
3. **Добавляет комментарий** к колонке для документирования её назначения

## Структура миграции

```sql
-- Добавление колонки
ALTER TABLE events 
ADD COLUMN is_my_events_header BOOLEAN NOT NULL DEFAULT FALSE;

-- Установка флага для первых событий
WITH first_events AS (
    SELECT DISTINCT ON (user_id) id
    FROM events
    WHERE status != 'DELETED'
    ORDER BY user_id, event_date NULLS LAST, event_time NULLS LAST, id
)
UPDATE events
SET is_my_events_header = TRUE
WHERE id IN (SELECT id FROM first_events);
```

## Проверка корректности

### 1. Компиляция проекта

Миграция успешно включена в сборку проекта:

```bash
mvn clean compile -DskipTests
```

Результат: ✅ BUILD SUCCESS

### 2. Проверка в target

Миграция скопирована в `target/classes/db/migration/V14__Add_is_my_events_header_to_events.sql`

### 3. Проверка на тестовой базе данных

Для проверки миграции на реальной PostgreSQL базе данных:

#### Вариант 1: Использование Docker Compose

```bash
# Запустить PostgreSQL через docker-compose
docker-compose up -d postgres

# Запустить приложение с профилем dev
mvn spring-boot:run -Dspring-boot.run.profiles=dev
```

#### Вариант 2: Ручная проверка SQL

```bash
# Подключиться к PostgreSQL
psql -h localhost -U botuser -d family_calendar_dev

# Проверить структуру таблицы
\d events

# Проверить, что колонка добавлена
SELECT column_name, data_type, is_nullable, column_default 
FROM information_schema.columns 
WHERE table_name = 'events' AND column_name = 'is_my_events_header';

# Проверить, что флаги установлены корректно
SELECT user_id, id, title, event_date, event_time, is_my_events_header, status
FROM events
WHERE status != 'DELETED'
ORDER BY user_id, event_date NULLS LAST, event_time NULLS LAST, id;
```

## Ожидаемый результат

После применения миграции:

1. В таблице `events` появится новая колонка `is_my_events_header` типа BOOLEAN
2. Для каждого пользователя первое событие (с самой ранней датой) будет иметь `is_my_events_header = TRUE`
3. Все остальные события будут иметь `is_my_events_header = FALSE`
4. Flyway зарегистрирует миграцию в таблице `flyway_schema_history`

## Откат миграции (если необходимо)

Flyway не поддерживает автоматический откат. Для отката нужно создать отдельную миграцию:

```sql
-- V15__Rollback_is_my_events_header.sql
ALTER TABLE events DROP COLUMN is_my_events_header;
```

## Следующие шаги

После успешного применения миграции можно переходить к задаче 2: "Обновление модели Event для поддержки флага шапки"
