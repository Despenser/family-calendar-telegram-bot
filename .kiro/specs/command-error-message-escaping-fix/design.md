# Design Document

## Overview

Данный документ описывает решение проблемы некорректного экранирования специальных символов MarkdownV2 в сообщениях об ошибках команд. Проблема возникает в двух местах кода (`UpdateProcessor` и `CommandDispatcher`), где используется конкатенация строк с частичным экранированием только команды `/help`, но не статического текста.

Решение заключается в замене конкатенации строк на использование существующего метода `MarkdownFormatter.formatMessage()`, который автоматически экранирует все специальные символы MarkdownV2.

## Architecture

Архитектура решения основана на использовании централизованного утилитного класса `MarkdownFormatter` для всех операций форматирования сообщений.

```
┌─────────────────┐
│ UpdateProcessor │
│ CommandDispatcher│
└────────┬────────┘
         │ формирует сообщение об ошибке
         ▼
┌─────────────────────┐
│ MarkdownFormatter   │
│ .formatMessage()    │ ◄── Существующий метод
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

### UpdateProcessor (изменения)

Метод обработки некорректных команд будет изменен:

```java
// Было:
String response = "Команда должна начинаться с символа '/'. Используйте " + 
                escape("/help") + " для списка доступных команд.";

// Станет:
String response = MarkdownFormatter.formatMessage(
    "Команда должна начинаться с символа '/'. Используйте /help для списка доступных команд.");
```

### CommandDispatcher (изменения)

Метод `dispatch()` будет изменен аналогично:

```java
// Было:
return "Команда должна начинаться с символа '/'. Используйте " + escape("/help") + " для списка доступных команд.";

// Станет:
return MarkdownFormatter.formatMessage(
    "Команда должна начинаться с символа '/'. Используйте /help для списка доступных команд.");
```

### MarkdownFormatter (без изменений)

Метод `formatMessage()` уже существует и работает корректно:

```java
/**
 * Форматирует сообщение с автоматическим экранированием всех частей.
 * 
 * @param template шаблон сообщения с плейсхолдерами
 * @param args аргументы для подстановки
 * @return полностью экранированное сообщение
 */
public static String formatMessage(String template, Object... args)
```

## Data Models

Изменений в моделях данных не требуется.

## Correctness Properties

*A property is a characteristic or behavior that should hold true across all valid executions of a system-essentially, a formal statement about what the system should do. Properties serve as the bridge between human-readable specifications and machine-verifiable correctness guarantees.*

### Property 1: Полное экранирование сообщения об ошибке для некорректного ввода

*For any* текст, не начинающийся с '/', сформированное сообщение об ошибке должно содержать все специальные символы MarkdownV2 (точки, апострофы) экранированными обратным слешем.

**Validates: Requirements 1.1**

## Error Handling

### Ошибки форматирования

Метод `MarkdownFormatter.formatMessage()` уже обрабатывает ошибки:
- **Null шаблон**: Выбрасывает `IllegalArgumentException`
- **Ошибки форматирования**: Преобразует `IllegalFormatException` в понятное сообщение

### Ошибки отправки сообщений

Существующий механизм обработки ошибок в `TelegramMessageService` остается без изменений. После исправления экранирования количество ошибок 400 (Bad Request) должно снизиться до нуля.

## Testing Strategy

### Unit Tests

1. **UpdateProcessorTest** (новые тесты):
   - Тест обработки некорректной команды (текст без '/')
   - Проверка корректности экранирования сообщения об ошибке
   - Проверка отсутствия неэкранированных специальных символов

2. **CommandDispatcherTest** (новые тесты):
   - Тест обработки некорректной команды
   - Проверка корректности экранирования сообщения об ошибке
   - Проверка корректности форматирования команды /help

3. **MarkdownFormatterTest** (проверка существующих тестов):
   - Убедиться, что существующие тесты покрывают метод `formatMessage()`
   - При необходимости добавить тесты для специфичных случаев

### Property-Based Tests

Для property-based тестирования будет использоваться библиотека **jqwik**.

Каждый property-based тест должен выполнять минимум 100 итераций.

#### Property Test 1: Полное экранирование сообщения об ошибке

```java
@Property
void errorMessageIsFullyEscaped(@ForAll @StringLength(min = 1, max = 100) String text) {
    // Формируем сообщение об ошибке с произвольным текстом
    String message = MarkdownFormatter.formatMessage(
        "Команда должна начинаться с символа '/'. Используйте /help для списка доступных команд.");
    
    // Проверяем, что все специальные символы экранированы
    assertAllSpecialCharsEscaped(message);
}
```

**Feature: command-error-message-escaping-fix, Property 1: Полное экранирование сообщения об ошибке**

#### Property Test 2: Отсутствие ошибок парсинга MarkdownV2

```java
@Property
void noMarkdownParsingErrors(@ForAll @StringLength(min = 1, max = 200) String template) {
    String message = MarkdownFormatter.formatMessage(template);
    
    // Проверяем, что сообщение не вызовет ошибку парсинга в Telegram
    // (все специальные символы экранированы)
    assertDoesNotThrow(() -> validateMarkdownV2(message));
}
```

**Feature: command-error-message-escaping-fix, Property 2: Отсутствие ошибок парсинга MarkdownV2**

### Integration Tests

Интеграционные тесты не требуются, так как изменения касаются только замены способа форматирования строк.

## Implementation Notes

### Приоритет исправлений

1. **Высокий приоритет**: Исправить `UpdateProcessor` (строка 1635-1637)
2. **Высокий приоритет**: Исправить `CommandDispatcher` (строка 157)
3. **Средний приоритет**: Проверить другие места в коде на наличие аналогичных проблем

### Поиск проблемных мест

Необходимо найти все места в коде, где:
- Используется конкатенация строк с `escape()` для формирования сообщений
- Используется `String.format()` без последующего экранирования

Команды для поиска:
```bash
# Поиск конкатенации с escape()
grep -r "escape(" src/main/java --include="*.java" | grep "+"

# Поиск String.format() для сообщений
grep -r "String.format" src/main/java --include="*.java" | grep -i "message\|text"
```

### Обратная совместимость

Изменения не нарушают обратную совместимость, так как:
- Метод `formatMessage()` уже существует
- Изменяется только способ формирования строк
- Внешний API остается без изменений

## Performance Considerations

Замена конкатенации строк на `formatMessage()` не влияет на производительность:
- Метод `formatMessage()` выполняет те же операции, что и конкатенация + `escape()`
- Сложность остается O(n), где n - длина строки
- Для типичных сообщений об ошибках (до 100 символов) разница незаметна

## Security Considerations

Корректное экранирование специальных символов предотвращает:
- Ошибки парсинга MarkdownV2
- Потенциальные проблемы с отображением сообщений
- Возможные атаки через инъекцию специальных символов (хотя риск минимален)

## Future Enhancements

1. **Статический анализ**: Добавить правила для статического анализатора, чтобы предупреждать о конкатенации строк для сообщений
2. **Метрики**: Добавить метрики для отслеживания ошибок парсинга MarkdownV2
3. **Автоматическая валидация**: Добавить валидацию экранирования в `TelegramMessageService` перед отправкой
4. **Рефакторинг**: Провести полный аудит кода на наличие аналогичных проблем
