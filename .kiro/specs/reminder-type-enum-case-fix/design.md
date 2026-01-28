# Design Document

## Overview

Исправление несоответствия регистра значений ENUM reminder_type между PostgreSQL и Java моделью путем создания миграции базы данных, которая обновит значения ENUM на UPPER_CASE формат, соответствующий Java константам.

## Architecture

Решение включает:
1. Создание новой Flyway миграции для обновления ENUM
2. Сохранение существующей Java модели без изменений
3. Обеспечение обратной совместимости во время деплоя

## Components and Interfaces

### Database Migration (V20__Fix_reminder_type_enum_case.sql)

Миграция выполняет следующие шаги:

1. **Добавление новых значений ENUM в UPPER_CASE**
   ```sql
   ALTER TYPE reminder_type ADD VALUE IF NOT EXISTS 'MORNING_OF_DAY';
   ALTER TYPE reminder_type ADD VALUE IF NOT EXISTS 'EVENING_BEFORE';
   ALTER TYPE reminder_type ADD VALUE IF NOT EXISTS 'ONE_HOUR_BEFORE';
   ALTER TYPE reminder_type ADD VALUE IF NOT EXISTS 'TEN_MINUTES_BEFORE';
   ALTER TYPE reminder_type ADD VALUE IF NOT EXISTS 'FIFTEEN_MINUTES_BEFORE';
   ALTER TYPE reminder_type ADD VALUE IF NOT EXISTS 'CUSTOM';
   ```

2. **Обновление существующих данных**
   ```sql
   UPDATE reminders SET reminder_type = 'MORNING_OF_DAY'::reminder_type 
   WHERE reminder_type = 'morning_of_day'::reminder_type;
   
   UPDATE reminders SET reminder_type = 'EVENING_BEFORE'::reminder_type 
   WHERE reminder_type = 'evening_before'::reminder_type;
   
   -- и так далее для всех значений
   ```

3. **Удаление старых значений** (выполняется в отдельной транзакции после обновления данных)
   
   **ВАЖНО:** PostgreSQL не позволяет удалять значения из ENUM напрямую. Вместо этого нужно:
   - Создать новый ENUM тип с правильными значениями
   - Изменить тип колонки на новый ENUM
   - Удалить старый ENUM тип

   Однако это сложная операция, которая может привести к downtime. Более безопасный подход - оставить старые значения в ENUM, но не использовать их в приложении.

### Java Model (Reminder.java)

Модель остается без изменений. Hibernate с `@Enumerated(EnumType.STRING)` будет автоматически использовать UPPER_CASE значения, которые теперь присутствуют в PostgreSQL ENUM.

```java
@Enumerated(EnumType.STRING)
@Column(name = "reminder_type", nullable = false)
private ReminderType reminderType;

public enum ReminderType {
    MORNING_OF_DAY,
    EVENING_BEFORE,
    ONE_HOUR_BEFORE,
    @Deprecated
    TEN_MINUTES_BEFORE,
    FIFTEEN_MINUTES_BEFORE,
    CUSTOM
}
```

## Data Models

### PostgreSQL ENUM reminder_type (после миграции)

```
reminder_type ENUM:
- 'morning_of_day' (старое, не используется)
- 'evening_before' (старое, не используется)
- 'one_hour_before' (старое, не используется)
- 'ten_minutes_before' (старое, не используется)
- 'custom' (старое, не используется)
- 'MORNING_OF_DAY' (новое, используется)
- 'EVENING_BEFORE' (новое, используется)
- 'ONE_HOUR_BEFORE' (новое, используется)
- 'TEN_MINUTES_BEFORE' (новое, используется)
- 'FIFTEEN_MINUTES_BEFORE' (новое, добавлено)
- 'CUSTOM' (новое, используется)
```

## Correctness Properties

*A property is a characteristic or behavior that should hold true across all valid executions of a system-essentially, a formal statement about what the system should do. Properties serve as the bridge between human-readable specifications and machine-verifiable correctness guarantees.*

### Property 1: Round-trip consistency for ReminderType

*For any* valid ReminderType value, saving a Reminder to the database and then reading it back should produce the same ReminderType value.

**Validates: Requirements 3.3**

### Property 2: All ReminderType values are supported

*For any* ReminderType enum constant (except deprecated ones), creating and saving a Reminder with that type should succeed without database errors.

**Validates: Requirements 1.1, 1.2, 2.1**

### Property 3: Migration preserves existing data

*For any* existing Reminder record before migration, after migration the record should still exist with the equivalent ReminderType value in UPPER_CASE format.

**Validates: Requirements 1.2, 1.4**

## Error Handling

### Migration Errors

- **Constraint violations**: Если существуют данные с некорректными значениями, миграция должна их обработать
- **Rollback strategy**: Flyway автоматически откатит миграцию при ошибке
- **Logging**: Все ошибки миграции логируются Flyway

### Runtime Errors

- **Invalid ENUM values**: Hibernate выбросит исключение при попытке сохранить некорректное значение
- **Database connection errors**: Обрабатываются стандартным механизмом Spring

## Testing Strategy

### Unit Tests

1. **Test saving Reminder with each ReminderType**
   - Создать Reminder с каждым типом
   - Сохранить в базу
   - Проверить отсутствие ошибок

2. **Test reading Reminder with each ReminderType**
   - Создать и сохранить Reminder
   - Прочитать из базы
   - Проверить корректность типа

### Property-Based Tests

1. **Property test for round-trip consistency**
   - Генерировать случайные Reminder объекты с разными ReminderType
   - Сохранять в базу и читать обратно
   - Проверять, что ReminderType не изменился
   - Минимум 100 итераций

### Integration Tests

1. **Test migration execution**
   - Создать тестовую базу со старыми значениями
   - Выполнить миграцию
   - Проверить, что все данные обновлены корректно

2. **Test backward compatibility**
   - Проверить, что приложение работает с обновленной базой
   - Проверить, что старые значения больше не используются

### Manual Testing

1. Запустить приложение в Docker
2. Создать событие с напоминанием
3. Проверить, что напоминание сохраняется без ошибок
4. Проверить логи на отсутствие ошибок "column reminder_type is of type reminder_type but expression is of type character varying"
