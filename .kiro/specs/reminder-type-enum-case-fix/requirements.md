# Requirements Document

## Introduction

Исправление несоответствия регистра значений ENUM reminder_type между базой данных PostgreSQL и Java моделью. В базе данных значения определены в snake_case (например, `morning_of_day`), а в Java модели используется UPPER_CASE (например, `MORNING_OF_DAY`). Это приводит к ошибке при попытке сохранить напоминание: "column reminder_type is of type reminder_type but expression is of type character varying".

## Glossary

- **Reminder**: Напоминание о событии
- **ReminderType**: ENUM тип для классификации напоминаний
- **PostgreSQL_ENUM**: Пользовательский тип данных в PostgreSQL
- **Hibernate**: ORM фреймворк для работы с базой данных
- **Flyway**: Инструмент для управления миграциями базы данных

## Requirements

### Requirement 1: Обновление ENUM в базе данных

**User Story:** Как разработчик, я хочу, чтобы значения ENUM reminder_type в базе данных соответствовали Java константам, чтобы Hibernate мог корректно сохранять напоминания.

#### Acceptance Criteria

1. WHEN миграция выполняется, THE Database SHALL добавить новые значения ENUM в UPPER_CASE формате
2. WHEN миграция выполняется, THE Database SHALL обновить существующие записи с snake_case на UPPER_CASE
3. WHEN миграция выполняется, THE Database SHALL удалить старые значения ENUM в snake_case формате
4. THE Migration SHALL выполняться без ошибок на существующих данных
5. THE Migration SHALL быть обратимо совместимой (не ломать работу приложения во время деплоя)

### Requirement 2: Добавление нового значения FIFTEEN_MINUTES_BEFORE

**User Story:** Как разработчик, я хочу добавить новое значение FIFTEEN_MINUTES_BEFORE в ENUM, чтобы заменить устаревшее TEN_MINUTES_BEFORE.

#### Acceptance Criteria

1. WHEN миграция выполняется, THE Database SHALL добавить значение FIFTEEN_MINUTES_BEFORE в ENUM
2. THE Migration SHALL сохранить обратную совместимость с TEN_MINUTES_BEFORE
3. THE Java_Model SHALL содержать оба значения (TEN_MINUTES_BEFORE помечен как @Deprecated)

### Requirement 3: Проверка корректности сохранения

**User Story:** Как разработчик, я хочу убедиться, что после миграции Hibernate корректно сохраняет и читает значения ReminderType, чтобы избежать ошибок в production.

#### Acceptance Criteria

1. WHEN Hibernate сохраняет Reminder с любым ReminderType, THE Database SHALL принять значение без ошибок
2. WHEN Hibernate читает Reminder из базы, THE Application SHALL корректно преобразовать значение в Java ENUM
3. FOR ALL ReminderType значений, сохранение и чтение SHALL работать корректно (round-trip property)
