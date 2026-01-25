# Дизайн: Исправление ошибок экранирования MarkdownV2

## Overview

Данный дизайн описывает исправление критических ошибок экранирования специальных символов MarkdownV2 в двух местах:
1. В `UpdateProcessor` при отправке подсказки пользователю в режиме ожидания файла
2. В `WeeklySummaryScheduler` при формировании еженедельной сводки

Проблема возникает из-за того, что специальные символы `(`, `)` и `-` не экранируются через функцию `escape()` из `MarkdownFormatter`, а экранируются вручную или не экранируются вообще, что приводит к ошибкам парсинга MarkdownV2 в Telegram API.

## Architecture

Решение основано на использовании существующей функции `MarkdownFormatter.escape()` для всех строк, содержащих специальные символы MarkdownV2.

### Компоненты для изменения:

1. **UpdateProcessor** - метод `processUpdate()`, строка с сообщением-подсказкой
2. **WeeklySummaryScheduler** - метод формирования диапазона дат для заголовка сводки

## Components and Interfaces

### UpdateProcessor

**Текущая реализация:**
```java
String hintMessage = formatMessage(
    "📎 Пожалуйста, отправьте файл \\(документ, фото, видео или аудио\\)\n\n" +
    "_Для отмены нажмите кнопку 'Отмена' в списке вложений_"
);
```

**Проблема:** Символы `(` и `)` экранированы вручную как `\\(` и `\\)`, но это не работает корректно с `formatMessage()`.

**Решение:**
```java
String hintMessage = formatMessage(
    "📎 Пожалуйста, отправьте файл " + escape("(документ, фото, видео или аудио)") + "\n\n" +
    "_Для отмены нажмите кнопку 'Отмена' в списке вложений_"
);
```

### WeeklySummaryScheduler

**Текущая реализация:**
```java
String dateRange = startDate.format(DATE_RANGE_FORMATTER) + " - " + endDate.format(DATE_RANGE_FORMATTER);
String header = EventFormatter.formatCommandHeader("Еженедельная сводка", dateRange);
```

**Проблема:** Символ `-` в диапазоне дат не экранирован, что приводит к ошибке парсинга.

**Решение:**
```java
String dateRange = startDate.format(DATE_RANGE_FORMATTER) + " \\- " + endDate.format(DATE_RANGE_FORMATTER);
String header = EventFormatter.formatCommandHeader("Еженедельная сводка", dateRange);
```

Или использовать `escape()`:
```java
String dateRange = escape(startDate.format(DATE_RANGE_FORMATTER) + " - " + endDate.format(DATE_RANGE_FORMATTER));
String header = EventFormatter.formatCommandHeader("Еженедельная сводка", dateRange);
```

## Data Models

Изменений в моделях данных не требуется.

## Correctness Properties

*A property is a characteristic or behavior that should hold true across all valid executions of a system-essentially, a formal statement about what the system should do. Properties serve as the bridge between human-readable specifications and machine-verifiable correctness guarantees.*

### Property 1: Корректное экранирование в подсказке о файле

*For any* пользователь в режиме ожидания файла, когда система отправляет сообщение-подсказку, все специальные символы MarkdownV2 должны быть корректно экранированы, и сообщение должно успешно отправляться без ошибок парсинга.

**Validates: Requirements 1.1, 1.2, 1.3**

### Property 2: Корректное экранирование в еженедельной сводке

*For any* диапазон дат в еженедельной сводке, символ `-` должен быть корректно экранирован, и сообщение должно успешно отправляться без ошибок парсинга.

**Validates: Requirements 2.1, 2.2, 2.3**

## Error Handling

Текущая обработка ошибок в `TelegramMessageService` уже логирует ошибки парсинга MarkdownV2. После исправления эти ошибки должны исчезнуть.

## Testing Strategy

### Unit Tests

1. **UpdateProcessor**: Проверить, что сообщение-подсказка формируется с корректно экранированными символами
2. **WeeklySummaryScheduler**: Проверить, что диапазон дат в заголовке сводки формируется с корректно экранированным символом `-`

### Integration Tests

1. Проверить отправку сообщения-подсказки через `TelegramMessageService` (mock)
2. Проверить отправку еженедельной сводки через `TelegramMessageService` (mock)

### Manual Testing

1. Перевести пользователя в режим ожидания файла и нажать на кнопку команды
2. Дождаться отправки еженедельной сводки и проверить корректность отображения
