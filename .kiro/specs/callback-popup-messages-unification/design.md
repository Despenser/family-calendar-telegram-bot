# Design Document

## Overview

Данный документ описывает дизайн системы унификации всплывающих сообщений (answerCallbackQuery) в Telegram боте семейного календаря. Цель - сократить количество однотипных сообщений с 50+ уникальных строк до ~15-20 унифицированных констант, улучшить консистентность пользовательского опыта и упростить поддержку кода.

## Architecture

### Текущее состояние

В настоящее время всплывающие сообщения разбросаны по всем callback handlers в виде строковых литералов:

```java
// В разных местах кода
telegramMessageService.answerCallbackQuery(callbackQuery.getId(), "Произошла ошибка");
telegramMessageService.answerCallbackQuery(callbackQuery.getId(), "Ошибка");
telegramMessageService.answerCallbackQuery(callbackQuery.getId(), "❌ Ошибка при настройке напоминаний");
```

Проблемы:
- Дублирование похожих сообщений
- Несогласованность формулировок
- Сложность изменения сообщений
- Отсутствие централизованного контроля

### Целевая архитектура

Создание централизованного класса констант с категоризацией сообщений:

```
CallbackMessages (класс констант)
├── Success (успешные операции)
├── Error (ошибки)
├── Cancellation (отмены)
├── Information (информационные)
├── Confirmation (подтверждения)
└── Empty (пустые ответы)
```

## Components and Interfaces

### 1. CallbackMessages (Класс констант)

Центральный класс для хранения всех всплывающих сообщений.

```java
package ru.golubyatnikov.family.calendar.bot.util;

/**
 * Централизованное хранилище всплывающих сообщений для callback queries.
 * Все сообщения категоризированы для удобства использования и поддержки.
 */
public final class CallbackMessages {

    private CallbackMessages() {
        // Utility class
    }

    // ============ УСПЕШНЫЕ ОПЕРАЦИИ ============
    
    /** Универсальное сообщение об успехе */
    public static final String SUCCESS = "✅ Готово";
    
    /** Подтверждение выбора */
    public static final String SELECTED = "✅ Выбрано";
    
    /** Успешное создание */
    public static final String CREATED = "✅ Создано";
    
    /** Успешное удаление */
    public static final String DELETED = "✅ Удалено";
    
    /** Успешное изменение */
    public static final String UPDATED = "✅ Обновлено";

    // ============ ОШИБКИ ============
    
    /** Общая ошибка */
    public static final String ERROR = "❌ Произошла ошибка";
    
    /** Ошибка доступа */
    public static final String NO_ACCESS = "❌ Нет прав доступа";
    
    /** Сущность не найдена - используется с форматированием */
    public static final String NOT_FOUND = "❌ %s не найдено";
    
    /** Ошибка валидации - используется с форматированием */
    public static final String VALIDATION_ERROR = "❌ Ошибка: %s";
    
    /** Неизвестное действие */
    public static final String UNKNOWN_ACTION = "❌ Неизвестное действие";
    
    /** Некорректный запрос */
    public static final String INVALID_REQUEST = "❌ Некорректный запрос";

    // ============ ОТМЕНЫ ============
    
    /** Универсальная отмена */
    public static final String CANCELLED = "🚫 Отменено";
    
    /** Отмена конкретного действия - используется с форматированием */
    public static final String ACTION_CANCELLED = "🚫 %s отменено";

    // ============ ИНФОРМАЦИОННЫЕ ============
    
    /** Подсказка - используется с форматированием */
    public static final String HINT = "ℹ️ %s";
    
    /** Запрос выбора - используется с форматированием */
    public static final String SELECT_PROMPT = "Выберите %s";
    
    /** Требование валидации - используется с форматированием */
    public static final String VALIDATION_REQUIRED = "Выберите хотя бы %s";

    // ============ ПОДТВЕРЖДЕНИЯ ============
    
    /** Подтверждение выбора элемента - используется с форматированием */
    public static final String ITEM_SELECTED = "✅ %s выбрано";

    // ============ ПУСТЫЕ ОТВЕТЫ ============
    
    /** Пустой ответ (когда UI обновление достаточно) */
    public static final String EMPTY = "";
    
    // ============ СПЕЦИФИЧНЫЕ СООБЩЕНИЯ ============
    
    /** Для времени - слишком поздно для сегодня */
    public static final String TOO_LATE_TODAY = "Слишком поздно для сегодня";
    
    /** Для напоминаний - требуется время события */
    public static final String REMINDER_NEEDS_TIME = "ℹ️ Добавьте время события для автоматических напоминаний";
    
    /** Для напоминаний - событие слишком близко */
    public static final String REMINDER_TOO_SOON = "ℹ️ Событие уже скоро, напоминания не созданы";
    
    /** Для ввода минут */
    public static final String ENTER_MINUTES = "Введите количество минут";
    
    /** Для выбора следующего часа */
    public static final String SELECT_NEXT_HOUR = "Выберите следующий час";
}
```

### 2. Вспомогательные методы форматирования

Для сообщений с параметрами создадим вспомогательный класс:

```java
package ru.golubyatnikov.family.calendar.bot.util;

/**
 * Утилиты для форматирования всплывающих сообщений.
 */
public final class CallbackMessageFormatter {

    private CallbackMessageFormatter() {
        // Utility class
    }

    /**
     * Форматирует сообщение "не найдено" для конкретной сущности.
     * 
     * @param entityName название сущности (например, "Событие", "Вложение")
     * @return отформатированное сообщение
     */
    public static String notFound(String entityName) {
        return String.format(CallbackMessages.NOT_FOUND, entityName);
    }

    /**
     * Форматирует сообщение об ошибке валидации.
     * 
     * @param reason причина ошибки
     * @return отформатированное сообщение
     */
    public static String validationError(String reason) {
        return String.format(CallbackMessages.VALIDATION_ERROR, reason);
    }

    /**
     * Форматирует сообщение об отмене действия.
     * 
     * @param action название действия (например, "Создание", "Редактирование")
     * @return отформатированное сообщение
     */
    public static String actionCancelled(String action) {
        return String.format(CallbackMessages.ACTION_CANCELLED, action);
    }

    /**
     * Форматирует информационную подсказку.
     * 
     * @param hint текст подсказки
     * @return отформатированное сообщение
     */
    public static String hint(String hint) {
        return String.format(CallbackMessages.HINT, hint);
    }

    /**
     * Форматирует запрос выбора.
     * 
     * @param item что нужно выбрать
     * @return отформатированное сообщение
     */
    public static String selectPrompt(String item) {
        return String.format(CallbackMessages.SELECT_PROMPT, item);
    }

    /**
     * Форматирует подтверждение выбора элемента.
     * 
     * @param item что было выбрано
     * @return отформатированное сообщение
     */
    public static String itemSelected(String item) {
        return String.format(CallbackMessages.ITEM_SELECTED, item);
    }
}
```

## Data Models

### Маппинг старых сообщений на новые

| Старое сообщение | Новая константа | Примечание |
|-----------------|----------------|-----------|
| "Произошла ошибка" | `CallbackMessages.ERROR` | Универсальная ошибка |
| "Ошибка" | `CallbackMessages.ERROR` | Унификация |
| "❌ Ошибка при настройке напоминаний" | `CallbackMessages.ERROR` | Упрощение |
| "❌ Ошибка при удалении" | `CallbackMessages.ERROR` | Упрощение |
| "Нет прав доступа" | `CallbackMessages.NO_ACCESS` | Стандартизация |
| "У вас нет прав для редактирования этого события" | `CallbackMessages.NO_ACCESS` | Упрощение |
| "Событие не найдено" | `notFound("Событие")` | С форматированием |
| "Вложение не найдено" | `notFound("Вложение")` | С форматированием |
| "Напоминание не найдено" | `notFound("Напоминание")` | С форматированием |
| "Ошибка: пользователь не найден" | `notFound("Пользователь")` | С форматированием |
| "Ошибка: некорректные данные" | `validationError("некорректные данные")` | С форматированием |
| "Ошибка: некорректный формат данных" | `validationError("некорректный формат данных")` | С форматированием |
| "Ошибка: некорректный формат ID" | `validationError("некорректный формат ID")` | С форматированием |
| "Ошибка: не указан ID вложения" | `validationError("не указан ID вложения")` | С форматированием |
| "Некорректный запрос" | `CallbackMessages.INVALID_REQUEST` | Стандартизация |
| "Неизвестное действие" | `CallbackMessages.UNKNOWN_ACTION` | Стандартизация |
| "Выбрано" | `CallbackMessages.SELECTED` | Стандартизация |
| "✅ Напоминание удалено" | `CallbackMessages.DELETED` | Унификация |
| "✅ Вложение удалено" | `CallbackMessages.DELETED` | Унификация |
| "🗑️ Пункт удален" | `CallbackMessages.DELETED` | Унификация эмодзи |
| "Событие удалено" | `CallbackMessages.DELETED` | Добавление эмодзи |
| "✅ Напоминания отключены" | `CallbackMessages.SUCCESS` | Упрощение |
| "✅ Напоминания включены" | `CallbackMessages.SUCCESS` | Упрощение |
| "✅ Напоминания созданы" | `CallbackMessages.CREATED` | Стандартизация |
| "✅ Событие создано" | `CallbackMessages.CREATED` | Стандартизация |
| "✅ Статус изменен" | `CallbackMessages.UPDATED` | Стандартизация |
| "Дата обновлена" | `CallbackMessages.UPDATED` | Добавление эмодзи |
| "Время обновлено" | `CallbackMessages.UPDATED` | Добавление эмодзи |
| "Повторение настроено" | `CallbackMessages.SUCCESS` | Упрощение |
| "Обработано" | `CallbackMessages.SUCCESS` | Упрощение |
| "Отменено" | `CallbackMessages.CANCELLED` | Добавление эмодзи |
| "Удаление отменено" | `actionCancelled("Удаление")` | С форматированием |
| "Редактирование отменено" | `actionCancelled("Редактирование")` | С форматированием |
| "Создание отменено" | `actionCancelled("Создание")` | С форматированием |
| "Дата выбрана" | `itemSelected("Дата")` | С форматированием |
| "Час выбран" | `itemSelected("Час")` | С форматированием |
| "Время выбрано" | `itemSelected("Время")` | С форматированием |
| "Выберите типы напоминаний" | `selectPrompt("типы напоминаний")` | С форматированием |
| "Выберите хотя бы один тип" | `CallbackMessages.VALIDATION_REQUIRED` | С форматированием |
| "" (пустая строка) | `CallbackMessages.EMPTY` | Константа для ясности |

### Специфичные сообщения (остаются как есть)

Некоторые сообщения слишком специфичны для унификации и остаются отдельными константами:
- `TOO_LATE_TODAY` - "Слишком поздно для сегодня"
- `REMINDER_NEEDS_TIME` - "ℹ️ Добавьте время события для автоматических напоминаний"
- `REMINDER_TOO_SOON` - "ℹ️ Событие уже скоро, напоминания не созданы"
- `ENTER_MINUTES` - "Введите количество минут"
- `SELECT_NEXT_HOUR` - "Выберите следующий час"


## Correctness Properties

*Свойство (property) - это характеристика или поведение, которое должно выполняться для всех допустимых выполнений системы - по сути, формальное утверждение о том, что система должна делать. Свойства служат мостом между человекочитаемыми спецификациями и машинно-проверяемыми гарантиями корректности.*

### Property 1: Категории сообщений используют правильные эмодзи

*For any* константа сообщения в CallbackMessages, если она относится к категории успеха, она должна начинаться с "✅", если к категории ошибки - с "❌", если к категории отмены - с "🚫", если к категории информации - с "ℹ️".

**Validates: Requirements 1.5, 2.1, 3.1, 4.1, 5.1**

### Property 2: Форматирующие методы создают сообщения в правильном формате

*For any* входная строка, переданная в форматирующий метод:
- `validationError(reason)` должен возвращать строку формата "❌ Ошибка: {reason}"
- `notFound(entity)` должен возвращать строку формата "❌ {entity} не найдено"
- `actionCancelled(action)` должен возвращать строку формата "🚫 {action} отменено"
- `itemSelected(item)` должен возвращать строку формата "✅ {item} выбрано"
- `hint(text)` должен возвращать строку формата "ℹ️ {text}"
- `selectPrompt(item)` должен возвращать строку формата "Выберите {item}"

**Validates: Requirements 2.4, 3.3, 3.5, 4.3, 6.3**

### Property 3: Константы имеют непустые значения

*For any* публичная константа в CallbackMessages (кроме EMPTY), значение константы не должно быть null и не должно быть пустой строкой (за исключением константы EMPTY, которая специально предназначена для пустого ответа).

**Validates: Requirements 1.1**

## Error Handling

### Обработка ошибок форматирования

Форматирующие методы в `CallbackMessageFormatter` должны корректно обрабатывать граничные случаи:

1. **Null параметры**: Если в форматирующий метод передан `null`, метод должен вернуть сообщение с текстом "null" вместо выброса исключения
2. **Пустые строки**: Пустые строки обрабатываются как обычные параметры
3. **Специальные символы**: Все специальные символы в параметрах должны корректно отображаться в итоговом сообщении

### Обработка отсутствующих констант

При миграции на новую систему сообщений:

1. **Компиляция**: Если константа используется неправильно, код не скомпилируется
2. **Code Review**: Все изменения должны проверяться на корректность использования констант
3. **Тестирование**: Существующие тесты callback handlers должны продолжать проходить

## Testing Strategy

### Unit Tests

Unit тесты будут проверять:

1. **Значения констант**: Проверка, что каждая константа имеет ожидаемое значение
   - Пример: `assertEquals("✅ Готово", CallbackMessages.SUCCESS)`
   - Пример: `assertEquals("❌ Произошла ошибка", CallbackMessages.ERROR)`
   - Пример: `assertEquals("", CallbackMessages.EMPTY)`

2. **Форматирующие методы**: Проверка корректности форматирования для конкретных примеров
   - Пример: `assertEquals("❌ Событие не найдено", notFound("Событие"))`
   - Пример: `assertEquals("🚫 Создание отменено", actionCancelled("Создание"))`
   - Пример: `assertEquals("✅ Дата выбрано", itemSelected("Дата"))`

3. **Граничные случаи**: Проверка обработки null и пустых строк
   - Пример: `notFound(null)` должен вернуть "❌ null не найдено"
   - Пример: `notFound("")` должен вернуть "❌  не найдено"

4. **Интеграция с callback handlers**: Проверка, что callback handlers корректно используют новые константы
   - Существующие тесты callback handlers должны продолжать проходить после миграции
   - Проверка, что все answerCallbackQuery вызовы используют константы из CallbackMessages

### Property-Based Tests

Property тесты будут проверять универсальные свойства:

1. **Property 1: Категории сообщений используют правильные эмодзи**
   - Генерировать список всех констант успеха/ошибки/отмены/информации
   - Проверять, что каждая константа начинается с правильного эмодзи
   - Минимум 100 итераций

2. **Property 2: Форматирующие методы создают сообщения в правильном формате**
   - Генерировать случайные строки для параметров
   - Вызывать форматирующие методы
   - Проверять, что результат соответствует ожидаемому формату (regex matching)
   - Минимум 100 итераций

3. **Property 3: Константы имеют непустые значения**
   - Получить все публичные константы через рефлексию
   - Проверить, что каждая (кроме EMPTY) не null и не пустая
   - Минимум 100 итераций

### Testing Framework

Для property-based тестирования будет использоваться **jqwik** - библиотека для property-based testing в Java.

Пример конфигурации теста:
```java
@Property(tries = 100)
@Label("Feature: callback-popup-messages-unification, Property 2: Форматирующие методы создают сообщения в правильном формате")
void formatterMethodsFollowCorrectPattern(@ForAll String input) {
    // Test implementation
}
```

### Test Coverage

Цель покрытия:
- **Unit tests**: 100% покрытие всех констант и форматирующих методов
- **Property tests**: Проверка всех универсальных свойств
- **Integration tests**: Проверка корректной работы с существующими callback handlers

### Migration Testing

После миграции на новые константы:
1. Запустить все существующие тесты - они должны пройти
2. Запустить новые property тесты - они должны пройти
3. Выполнить ручное тестирование основных сценариев в боте
4. Проверить логи на отсутствие ошибок, связанных с сообщениями
