# Design Document

## Overview

Данный дизайн описывает изменения в `ReminderService` для:
1. Унификации формата текста уведомлений о напоминаниях
2. Удаления функционала кастомных напоминаний (CUSTOM)

Основные цели:
- Упростить и стандартизировать заголовки уведомлений
- Убрать эмодзи типа события из заголовка
- Использовать специфичные эмодзи для каждого типа:
  - 🌙 для напоминаний накануне (EVENING_BEFORE)
  - ⚡ для напоминаний за 1 час (ONE_HOUR_BEFORE)
  - 🔥 для напоминаний за 15 минут (FIFTEEN_MINUTES_BEFORE)
- Удалить возможность создания кастомных напоминаний
- Оставить только три фиксированных типа напоминаний

## Architecture

Изменения затрагивают несколько методов в `ReminderService`:
1. `formatReminderMessageByType()` - изменение форматирования текста
2. `createCustomReminder()` - удаление метода (deprecated)
3. `calculateReminderTimeWithTimezone()` - удаление обработки CUSTOM типа
4. `getReminderTimeInfo()` - удаление обработки CUSTOM типа

Архитектура:
```
ReminderService
  ├── sendReminderNotification()
  │    └── formatReminderMessageByType()  [ИЗМЕНЯЕТСЯ]
  ├── createReminders()  [БЕЗ ИЗМЕНЕНИЙ]
  ├── createDefaultReminders()  [БЕЗ ИЗМЕНЕНИЙ]
  ├── createCustomReminder()  [УДАЛЯЕТСЯ/DEPRECATED]
  ├── calculateReminderTimeWithTimezone()  [ИЗМЕНЯЕТСЯ]
  └── getReminderTimeInfo()  [ИЗМЕНЯЕТСЯ]
```

## Components and Interfaces

### 1. Изменяемый компонент: ReminderService.formatReminderMessageByType()

**Текущая реализация:**
```java
public String formatReminderMessageByType(Reminder reminder, ZoneId recipientTimezone) {
    // ...
    switch (reminder.getReminderType()) {
        case EVENING_BEFORE:
            message.append("🌙 ").append(bold("Напоминание: завтра в " + formattedTime + " у вас событие "));
            break;
        case ONE_HOUR_BEFORE:
            message.append("⏰ ").append(bold("Напоминание: через 1 час начнется событие "));
            break;
        // ... другие типы с разными эмодзи
    }
    
    // Добавление эмодзи типа события
    if (event.getIsPersonal()) {
        message.append("👤 ");
    } else {
        message.append("👨‍👩‍👧‍👦 ");
    }
    
    message.append(bold(event.getTitle())).append("\n\n");
    // ...
}
```

**Новая реализация:**
```java
public String formatReminderMessageByType(Reminder reminder, ZoneId recipientTimezone) {
    // ...
    
    switch (reminder.getReminderType()) {
        case EVENING_BEFORE:
            message.append("🌙 ").append(bold("Напоминание: завтра в " + formattedTime + " у вас событие - "));
            break;
        case ONE_HOUR_BEFORE:
            message.append("⚡ ").append(bold("Напоминание: через 1 час начнется событие - "));
            break;
        case FIFTEEN_MINUTES_BEFORE:
            message.append("🔥 ").append(bold("Напоминание: через 15 минут начнется событие - "));
            break;
        // УДАЛЕНЫ: MORNING_OF_DAY, TEN_MINUTES_BEFORE, CUSTOM
        default:
            // Fallback для старых типов в БД
            message.append("🔔 ").append(bold("Напоминание о событии - "));
    }
    
    // Название события БЕЗ эмодзи типа события
    message.append(bold(event.getTitle())).append("\n\n");
    // ...
}
```

### 2. Удаляемый компонент: ReminderService.createCustomReminder()

**Текущая реализация:**
```java
public Reminder createCustomReminder(Long eventId, int minutesBefore) {
    // ... создание кастомного напоминания
}
```

**Новая реализация:**
```java
@Deprecated
public Reminder createCustomReminder(Long eventId, int minutesBefore) {
    log.error("Попытка создать кастомное напоминание для события ID {}: " +
             "функционал удален", eventId);
    throw new UnsupportedOperationException(
        "Кастомные напоминания больше не поддерживаются. " +
        "Используйте фиксированные типы: EVENING_BEFORE, ONE_HOUR_BEFORE, FIFTEEN_MINUTES_BEFORE"
    );
}
```

### 3. Изменяемый компонент: ReminderService.calculateReminderTimeWithTimezone()

**Изменения в switch:**
```java
switch (type) {
    case EVENING_BEFORE:
        // ... без изменений
        break;
    case ONE_HOUR_BEFORE:
        // ... без изменений
        break;
    case FIFTEEN_MINUTES_BEFORE:
        // ... без изменений
        break;
    // УДАЛЕНЫ: MORNING_OF_DAY, TEN_MINUTES_BEFORE, CUSTOM
    default:
        throw new IllegalArgumentException(
            "Неподдерживаемый тип напоминания: " + type + ". " +
            "Поддерживаются только: EVENING_BEFORE, ONE_HOUR_BEFORE, FIFTEEN_MINUTES_BEFORE"
        );
}
```

### 4. Изменяемый компонент: ReminderService.getReminderTimeInfo()

**Изменения:**
```java
private String getReminderTimeInfo(Reminder reminder) {
    switch (reminder.getReminderType()) {
        case EVENING_BEFORE:
            return "Напоминание: вечером накануне";
        case ONE_HOUR_BEFORE:
            return "Напоминание: за 1 час до события";
        case FIFTEEN_MINUTES_BEFORE:
            return "Напоминание: за 15 минут до события";
        // УДАЛЕНЫ: MORNING_OF_DAY, TEN_MINUTES_BEFORE, CUSTOM
        default:
            return "Напоминание о событии";
    }
}
```

### Ключевые изменения:

1. **Специфичные эмодзи для трех типов**: 
   - 🌙 для EVENING_BEFORE (накануне)
   - ⚡ для ONE_HOUR_BEFORE (за 1 час)
   - 🔥 для FIFTEEN_MINUTES_BEFORE (за 15 минут)
2. **Тире в заголовке**: Добавлено " - " перед названием события для разделения
3. **Удаление эмодзи типа**: Убраны 👤 и 👨‍👩‍👧‍� из заголовка
4. **Сохранение форматирования**: Жирное форматирование (bold) остается для заголовка и названия события
5. **Удаление кастомных напоминаний**: Метод `createCustomReminder()` помечен @Deprecated и выбрасывает UnsupportedOperationException
6. **Удаление устаревших типов**: MORNING_OF_DAY, TEN_MINUTES_BEFORE больше не поддерживаются
7. **Fallback для старых данных**: Default case обрабатывает старые типы напоминаний в БД

## Data Models

Изменения не затрагивают структуру моделей данных, но влияют на использование:
- `Reminder` - модель напоминания (без изменений структуры)
- `Event` - модель события (без изменений)
- `Reminder.ReminderType` - enum типов напоминаний (рекомендуется удалить неиспользуемые значения в будущем)

**Рекомендация для будущего**: Удалить из enum `Reminder.ReminderType` значения:
- `MORNING_OF_DAY`
- `TEN_MINUTES_BEFORE`
- `CUSTOM`

Однако это требует миграции БД и выходит за рамки текущей задачи.

## Correctness Properties

*Свойство - это характеристика или поведение, которое должно выполняться для всех допустимых выполнений системы - по сути, формальное утверждение о том, что система должна делать. Свойства служат мостом между человекочитаемыми спецификациями и машинно-проверяемыми гарантиями корректности.*

### Property 1: Правильные эмодзи для поддерживаемых типов напоминаний

*For any* reminder с поддерживаемым типом:
- Если тип EVENING_BEFORE, сообщение должно начинаться с "🌙 "
- Если тип ONE_HOUR_BEFORE, сообщение должно начинаться с "⚡ "
- Если тип FIFTEEN_MINUTES_BEFORE, сообщение должно начинаться с "🔥 "

**Validates: Requirements 1.1, 1.2, 1.3, 2.2**

### Property 2: Отсутствие эмодзи типа события в заголовке

*For any* reminder для события любого типа (персональное или семейное), заголовок уведомления (первая строка до "\n\n") НЕ должен содержать эмодзи 👤 или 👨‍👩‍👧‍👦

**Validates: Requirements 2.1**

### Property 3: Наличие тире в заголовке

*For any* reminder, заголовок уведомления должен содержать " - " между текстом напоминания и названием события

**Validates: Requirements 2.3**

### Property 4: Сохранение жирного форматирования

*For any* reminder, заголовок уведомления должен содержать markdown-теги жирного форматирования (**) вокруг текста напоминания и названия события

**Validates: Requirements 4.4**

### Property 5: Сохранение информации о дате и времени

*For any* reminder, отформатированное сообщение должно содержать информацию о дате и времени события в том же формате, что и в текущей реализации (в зависимости от типа напоминания)

**Validates: Requirements 4.1, 4.4**

### Property 6: Сохранение информации о создателе для семейных событий

*For any* reminder для семейного события (isPersonal = false), отформатированное сообщение должно содержать строку "👤 Создал: {имя создателя}"

**Validates: Requirements 4.3**

### Property 7: Отклонение создания кастомных напоминаний

*For any* попытка создать кастомное напоминание через `createCustomReminder()` должна выбрасывать `UnsupportedOperationException`

**Validates: Requirements 3.1, 3.2**

### Property 8: Поддержка только трех типов напоминаний

*For any* попытка рассчитать время напоминания для неподдерживаемого типа (не EVENING_BEFORE, ONE_HOUR_BEFORE, FIFTEEN_MINUTES_BEFORE) должна выбрасывать `IllegalArgumentException`

**Validates: Requirements 3.2, 3.3**

## Error Handling

Обработка ошибок остается без изменений:
- При ошибке конвертации timezone используется fallback на UTC
- При критической ошибке используется deprecated метод `formatReminderMessage()`
- Все ошибки логируются с соответствующим уровнем (ERROR, WARN)

## Testing Strategy

### Unit Tests

Необходимо обновить существующие unit-тесты и добавить новые:

1. **Тест форматирования для каждого поддерживаемого типа напоминания**:
   - Проверить правильный эмодзи для EVENING_BEFORE (🌙)
   - Проверить правильный эмодзи для ONE_HOUR_BEFORE (⚡)
   - Проверить правильный эмодзи для FIFTEEN_MINUTES_BEFORE (�)
   - Проверить наличие тире " - " в заголовке
   - Проверить отсутствие эмодзи типа события в заголовке
   - Проверить сохранение жирного форматирования

2. **Тест для персональных событий**:
   - Проверить отсутствие 👤 в заголовке
   - Проверить отсутствие информации о создателе в конце сообщения

3. **Тест для семейных событий**:
   - Проверить отсутствие 👨‍👩‍👧‍👦 в заголовке
   - Проверить наличие информации о создателе в конце сообщения

4. **Тест сохранения остальной информации**:
   - Проверить наличие даты и времени
   - Проверить наличие описания (если есть)

5. **Тест отклонения кастомных напоминаний**:
   - Проверить, что `createCustomReminder()` выбрасывает `UnsupportedOperationException`
   - Проверить сообщение об ошибке

6. **Тест отклонения неподдерживаемых типов**:
   - Проверить, что `calculateReminderTimeWithTimezone()` выбрасывает `IllegalArgumentException` для MORNING_OF_DAY
   - Проверить, что `calculateReminderTimeWithTimezone()` выбрасывает `IllegalArgumentException` для TEN_MINUTES_BEFORE
   - Проверить, что `calculateReminderTimeWithTimezone()` выбрасывает `IllegalArgumentException` для CUSTOM

7. **Тест fallback для старых данных**:
   - Проверить, что форматирование работает для старых типов в БД (default case)

### Property-Based Tests

Используем библиотеку jqwik для property-based тестирования:

1. **Property Test 1: Правильные эмодзи для типов**
   - Генерируем случайные Reminder с поддерживаемыми типами (EVENING_BEFORE, ONE_HOUR_BEFORE, FIFTEEN_MINUTES_BEFORE)
   - Проверяем, что каждый тип использует правильный эмодзи (🌙, ⚡, 🔥)
   - **Feature: reminder-notification-text-format-unification, Property 1: Правильные эмодзи для поддерживаемых типов напоминаний**

2. **Property Test 2: Отсутствие эмодзи типа события**
   - Генерируем случайные Reminder для персональных и семейных событий
   - Проверяем, что заголовок не содержит 👤 или 👨‍👩‍👧‍👦
   - **Feature: reminder-notification-text-format-unification, Property 2: Отсутствие эмодзи типа события в заголовке**

3. **Property Test 3: Наличие тире**
   - Генерируем случайные Reminder с поддерживаемыми типами
   - Проверяем, что заголовок содержит " - "
   - **Feature: reminder-notification-text-format-unification, Property 3: Наличие тире в заголовке**

4. **Property Test 4: Жирное форматирование**
   - Генерируем случайные Reminder
   - Проверяем наличие markdown-тегов ** в заголовке
   - **Feature: reminder-notification-text-format-unification, Property 4: Сохранение жирного форматирования**

5. **Property Test 5: Информация о дате и времени**
   - Генерируем случайные Reminder с поддерживаемыми типами
   - Проверяем наличие информации о дате/времени в соответствующем формате
   - **Feature: reminder-notification-text-format-unification, Property 5: Сохранение информации о дате и времени**

6. **Property Test 6: Информация о создателе**
   - Генерируем случайные Reminder для семейных событий
   - Проверяем наличие строки "👤 Создал: " в сообщении
   - **Feature: reminder-notification-text-format-unification, Property 6: Сохранение информации о создателе для семейных событий**

7. **Property Test 7: Отклонение кастомных напоминаний**
   - Генерируем случайные eventId и minutesBefore
   - Проверяем, что `createCustomReminder()` всегда выбрасывает `UnsupportedOperationException`
   - **Feature: reminder-notification-text-format-unification, Property 7: Отклонение создания кастомных напоминаний**

8. **Property Test 8: Поддержка только трех типов**
   - Генерируем случайные Reminder с неподдерживаемыми типами (MORNING_OF_DAY, TEN_MINUTES_BEFORE, CUSTOM)
   - Проверяем, что `calculateReminderTimeWithTimezone()` выбрасывает `IllegalArgumentException`
   - **Feature: reminder-notification-text-format-unification, Property 8: Поддержка только трех типов напоминаний**

### Конфигурация Property Tests

- Минимум 100 итераций на каждый property test
- Использование jqwik для генерации случайных данных
- Каждый тест должен быть помечен комментарием с ссылкой на property из дизайна
- Генераторы должны создавать только поддерживаемые типы напоминаний для большинства тестов

## Implementation Notes

1. **Минимальные изменения форматирования**: Изменения в `formatReminderMessageByType()` затрагивают только switch-блок и удаление блока с эмодзи типа события
2. **Deprecation вместо удаления**: Метод `createCustomReminder()` помечается @Deprecated и выбрасывает исключение вместо полного удаления для обратной совместимости на уровне компиляции
3. **Обработка старых данных**: Default case в switch обрабатывает старые типы напоминаний, которые могут остаться в БД
4. **Обратная совместимость**: Все остальные части методов остаются без изменений
5. **Логирование**: Существующее логирование сохраняется, добавляется логирование ошибок при попытке создать кастомное напоминание
6. **Timezone**: Логика работы с timezone остается без изменений
7. **Будущие улучшения**: Рекомендуется создать миграцию БД для удаления старых типов напоминаний и обновления enum `Reminder.ReminderType`
