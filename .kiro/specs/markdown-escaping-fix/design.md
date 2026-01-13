# Design Document

## Overview

Данный документ описывает решение проблемы некорректного экранирования специальных символов MarkdownV2 в сообщениях Telegram бота. Проблема возникает из-за того, что при формировании сообщений с динамическим содержимым экранируются только переменные части, но не статический текст, содержащий специальные символы.

Решение включает:
1. Добавление нового метода `formatMessage()` в `MarkdownFormatter` для безопасного форматирования сообщений
2. Исправление всех мест в коде, где формируются сообщения с использованием `String.format()`
3. Добавление тестов для проверки корректности экранирования

## Architecture

Архитектура решения основана на централизованном подходе к экранированию через утилитный класс `MarkdownFormatter`. Все сообщения, отправляемые через `TelegramMessageService`, должны быть предварительно экранированы.

```
┌─────────────────┐
│ UpdateProcessor │
│  (и другие)     │
└────────┬────────┘
         │ формирует сообщение
         ▼
┌─────────────────────┐
│ MarkdownFormatter   │
│ .formatMessage()    │ ◄── Новый метод
└────────┬────────────┘
         │ экранированный текст
         ▼
┌─────────────────────┐
│TelegramMessageService│
│ .sendMessage()      │
└────────┬────────────┘
         │
         ▼
   Telegram API
```

## Components and Interfaces

### MarkdownFormatter (расширение)

Добавляется новый метод для безопасного форматирования сообщений:

```java
/**
 * Форматирует сообщение с автоматическим экранированием всех частей.
 * 
 * <p>Метод работает аналогично String.format(), но автоматически экранирует
 * все специальные символы MarkdownV2 как в шаблоне, так и в аргументах.</p>
 * 
 * <p>Важно: если аргумент уже экранирован (например, результат вызова escape()),
 * используйте специальный маркер для предотвращения двойного экранирования.</p>
 * 
 * @param template шаблон сообщения с плейсхолдерами %s
 * @param args аргументы для подстановки (будут экранированы автоматически)
 * @return полностью экранированное сообщение
 * 
 * @example
 * <pre>{@code
 * // Простое использование
 * String msg = formatMessage("Дата: %s", "12.01.2026");
 * // Результат: "Дата: 12\\.01\\.2026"
 * 
 * // С несколькими аргументами
 * String msg = formatMessage("Событие: %s в %s", "Встреча!", "14:30");
 * // Результат: "Событие: Встреча\\! в 14:30"
 * }</pre>
 */
public static String formatMessage(String template, Object... args)
```

### UpdateProcessor (изменения)

Метод `handleDateSelection()` будет изменен для использования нового метода форматирования:

```java
// Было:
String message = String.format("✅ Дата выбрана: %s\n\nТеперь выберите час:", 
    MarkdownFormatter.escape(formattedDate));

// Станет:
String message = MarkdownFormatter.formatMessage(
    "✅ Дата выбрана: %s\n\nТеперь выберите час:", 
    formattedDate);
```

## Data Models

Изменений в моделях данных не требуется.

## Correctness Properties

*A property is a characteristic or behavior that should hold true across all valid executions of a system-essentially, a formal statement about what the system should do. Properties serve as the bridge between human-readable specifications and machine-verifiable correctness guarantees.*

### Property 1: Полное экранирование специальных символов

*For any* строка, содержащая специальные символы MarkdownV2, после применения `MarkdownFormatter.escape()` все специальные символы должны быть экранированы обратным слешем.

**Validates: Requirements 1.1, 1.4**

### Property 2: Корректность форматирования с переменными

*For any* шаблон сообщения и набор аргументов, `MarkdownFormatter.formatMessage()` должен вернуть строку, где все специальные символы (как в шаблоне, так и в аргументах) экранированы.

**Validates: Requirements 1.1, 1.2, 2.1, 2.2, 2.4**

### Property 3: Отсутствие двойного экранирования

*For any* уже экранированная строка (содержащая `\` перед специальными символами), повторное применение `escape()` не должно добавлять дополнительные обратные слеши перед уже экранированными символами.

**Validates: Requirements 2.3**

### Property 4: Идемпотентность экранирования

*For any* строка `s`, результат `escape(escape(s))` должен быть эквивалентен `escape(s)` с точки зрения отображения в Telegram (т.е. двойное экранирование не должно изменять визуальное представление).

**Validates: Requirements 2.3**

### Property 5: Корректность экранирования дат

*For any* дата в формате `dd.MM.yyyy`, после экранирования все точки должны быть заменены на `\.`

**Validates: Requirements 3.1, 3.4, 4.4**

## Error Handling

### Ошибки форматирования

- **Null аргументы**: Если `template` или любой из `args` равен `null`, метод должен обработать это корректно (заменить на пустую строку или выбросить `IllegalArgumentException`)
- **Несоответствие количества плейсхолдеров**: Если количество `%s` в шаблоне не совпадает с количеством аргументов, выбросить `IllegalArgumentException`

### Ошибки отправки сообщений

Существующий механизм retry в `TelegramMessageService` остается без изменений. Однако после исправления экранирования количество ошибок 400 (Bad Request) должно значительно снизиться.

## Testing Strategy

### Unit Tests

1. **MarkdownFormatterTest** (расширение существующих тестов):
   - Тест экранирования всех специальных символов по отдельности
   - Тест экранирования строки со всеми специальными символами одновременно
   - Тест `formatMessage()` с различными шаблонами и аргументами
   - Тест обработки null значений
   - Тест экранирования дат в различных форматах
   - Тест идемпотентности экранирования

2. **UpdateProcessorTest** (новые тесты):
   - Тест корректности формирования сообщения при выборе даты
   - Тест корректности формирования сообщения при выборе часа
   - Тест корректности формирования сообщения при выборе минут

### Property-Based Tests

Для property-based тестирования будет использоваться библиотека **jqwik** (стандартная библиотека для PBT в Java).

Каждый property-based тест должен выполнять минимум 100 итераций для обеспечения достаточного покрытия.

#### Property Test 1: Полное экранирование специальных символов

```java
@Property
void allSpecialCharactersAreEscaped(@ForAll String text) {
    String escaped = MarkdownFormatter.escape(text);
    
    // Проверяем, что все специальные символы экранированы
    char[] specialChars = {'_', '*', '[', ']', '(', ')', '~', '`', 
                          '>', '#', '+', '-', '=', '|', '{', '}', '.', '!'};
    
    for (char special : specialChars) {
        // Если в исходном тексте есть специальный символ,
        // в результате перед ним должен быть обратный слеш
        int index = text.indexOf(special);
        if (index >= 0) {
            int escapedIndex = escaped.indexOf(special);
            assertTrue(escapedIndex > 0 && escaped.charAt(escapedIndex - 1) == '\\',
                "Символ '" + special + "' не экранирован");
        }
    }
}
```

**Feature: markdown-escaping-fix, Property 1: Полное экранирование специальных символов**

#### Property Test 2: Корректность форматирования с переменными

```java
@Property
void formatMessageEscapesAllParts(
    @ForAll @StringLength(min = 1, max = 100) String template,
    @ForAll @Size(min = 0, max = 5) List<@StringLength(max = 50) String> args) {
    
    // Заменяем случайные части шаблона на %s
    String formattedTemplate = template;
    for (int i = 0; i < args.size(); i++) {
        formattedTemplate = formattedTemplate + " %s";
    }
    
    String result = MarkdownFormatter.formatMessage(formattedTemplate, args.toArray());
    
    // Проверяем, что результат не вызовет ошибку парсинга в Telegram
    // (все специальные символы экранированы)
    assertDoesNotThrow(() -> validateMarkdownV2(result));
}
```

**Feature: markdown-escaping-fix, Property 2: Корректность форматирования с переменными**

#### Property Test 3: Идемпотентность экранирования

```java
@Property
void escapingIsIdempotent(@ForAll String text) {
    String escaped1 = MarkdownFormatter.escape(text);
    String escaped2 = MarkdownFormatter.escape(escaped1);
    
    // Двойное экранирование не должно изменять результат
    // с точки зрения отображения в Telegram
    assertEquals(escaped1, escaped2);
}
```

**Feature: markdown-escaping-fix, Property 3: Отсутствие двойного экранирования**

#### Property Test 4: Корректность экранирования дат

```java
@Property
void dateFormatsAreEscapedCorrectly(
    @ForAll @IntRange(min = 1, max = 31) int day,
    @ForAll @IntRange(min = 1, max = 12) int month,
    @ForAll @IntRange(min = 2020, max = 2030) int year) {
    
    String date = String.format("%02d.%02d.%d", day, month, year);
    String escaped = MarkdownFormatter.escape(date);
    
    // Проверяем, что все точки экранированы
    assertFalse(escaped.matches(".*[^\\\\]\\..*"), 
        "Найдена неэкранированная точка в дате: " + escaped);
    
    // Проверяем, что экранированная дата содержит правильное количество точек
    long dotCount = escaped.chars().filter(ch -> ch == '.').count();
    assertEquals(2, dotCount, "Дата должна содержать ровно 2 точки");
}
```

**Feature: markdown-escaping-fix, Property 5: Корректность экранирования дат**

### Integration Tests

Интеграционные тесты не требуются, так как изменения касаются только утилитного класса и его использования.

## Implementation Notes

### Приоритет исправлений

1. **Высокий приоритет**: Исправить `UpdateProcessor.handleDateSelection()` - это место, где возникает текущая ошибка
2. **Средний приоритет**: Найти и исправить все другие места, где используется `String.format()` для формирования сообщений
3. **Низкий приоритет**: Рефакторинг существующего кода для использования нового метода `formatMessage()`

### Поиск проблемных мест

Необходимо найти все места в коде, где:
- Используется `String.format()` для формирования сообщений
- Текст содержит специальные символы MarkdownV2
- Экранирование применяется только к переменным частям

Команда для поиска:
```bash
grep -r "String.format" src/main/java --include="*.java" | grep -i "message\|text"
```

### Обратная совместимость

Существующие методы `MarkdownFormatter` остаются без изменений. Новый метод `formatMessage()` является дополнением, не нарушающим существующий код.

## Performance Considerations

Экранирование символов - это операция O(n), где n - длина строки. Для типичных сообщений бота (до 4096 символов) это не создаст проблем с производительностью.

Метод `formatMessage()` выполняет:
1. Экранирование шаблона: O(m), где m - длина шаблона
2. Экранирование каждого аргумента: O(k₁ + k₂ + ... + kₙ)
3. Форматирование: O(m + k₁ + k₂ + ... + kₙ)

Общая сложность: O(m + Σkᵢ), что является линейной и приемлемой для данной задачи.

## Security Considerations

Корректное экранирование специальных символов предотвращает:
- Ошибки парсинга MarkdownV2
- Потенциальные проблемы с отображением сообщений
- Возможные атаки через инъекцию специальных символов (хотя в данном случае риск минимален)

## Future Enhancements

1. **Автоматическая валидация**: Добавить валидацию экранирования в `TelegramMessageService` перед отправкой
2. **Метрики**: Добавить метрики для отслеживания ошибок парсинга MarkdownV2
3. **Логирование**: Улучшить логирование для отладки проблем с экранированием
4. **Статический анализ**: Добавить правила для статического анализатора, чтобы предупреждать о потенциальных проблемах с экранированием
