# Design Document

## Overview

Данный документ описывает проектное решение для исправления функции редактирования полей событий в Telegram боте семейного календаря. Проблема заключается в некорректной обработке callback data в методе `handleEditField` класса `EventCallbackHandler`.

Текущая реализация использует метод `extractPayload()` для извлечения данных из callback data формата `edit_field_{field}_{eventId}`, который возвращает всю строку после префикса `edit_field_`, то есть `{field}_{eventId}`. Затем эта строка используется в switch-выражении для определения поля, что приводит к несовпадению и выполнению ветки `default` с сообщением "Неизвестное поле для редактирования".

## Architecture

Решение включает модификацию метода `handleEditField` в классе `EventCallbackHandler` для корректного извлечения имени поля из callback data.

### Компоненты

1. **EventCallbackHandler** - обработчик callback queries для операций с событиями
2. **CallbackPrefix** - enum для типизации callback data prefixes
3. **ConversationStateService** - сервис для управления состоянием диалогов

### Поток данных

```
Пользователь нажимает кнопку редактирования поля
    ↓
Telegram отправляет callback query с данными "edit_field_{field}_{eventId}"
    ↓
EventCallbackHandler.handle() определяет тип callback
    ↓
EventCallbackHandler.handleEditField() извлекает поле и eventId
    ↓
Система устанавливает состояние редактирования через ConversationStateService
    ↓
Пользователь получает сообщение с инструкцией
```

## Components and Interfaces

### EventCallbackHandler

**Модифицируемый метод:**

```java
private void handleEditField(String callbackData, Long userId, Long chatId, 
                             Integer messageId, String callbackQueryId)
```

**Текущая реализация (проблемная):**

```java
String payload = CallbackPrefix.EDIT_FIELD.extractPayload(callbackData);
// payload = "title_123" для callbackData = "edit_field_title_123"

String message = switch (payload) {
    case "date" -> "📅 Редактирование даты...";
    case "time" -> "🕐 Редактирование времени...";
    case "title" -> "📝 Редактирование названия...";
    case "description" -> "📄 Редактирование описания...";
    default -> "❌ Неизвестное поле для редактирования";
};
```

**Новая реализация:**

```java
String payload = CallbackPrefix.EDIT_FIELD.extractPayload(callbackData);
// payload = "title_123" для callbackData = "edit_field_title_123"

// Разделяем payload на поле и eventId
String[] parts = payload.split("_", 2);
if (parts.length != 2) {
    log.error("Некорректный формат callback data: {}", callbackData);
    // Обработка ошибки
    return;
}

String field = parts[0];  // "title"
Long eventId = Long.parseLong(parts[1]);  // 123

String message = switch (field) {
    case "date" -> "📅 Редактирование даты\n\nВыберите новую дату из календаря:";
    case "time" -> "🕐 Редактирование времени\n\nВыберите новое время:";
    case "title" -> "📝 Редактирование названия\n\nОтправьте новое название события:";
    case "description" -> "📄 Редактирование описания\n\nОтправьте новое описание события:";
    default -> "❌ Неизвестное поле для редактирования";
};

// Устанавливаем состояние редактирования
ConversationStateService.EditField editField = mapToEditField(field);
if (editField != null) {
    conversationStateService.setEditingField(userId, editField);
}
```

### Вспомогательный метод

Добавляется новый приватный метод для преобразования строкового представления поля в enum:

```java
/**
 * Преобразует строковое представление поля в EditField enum.
 * 
 * @param fieldName строковое имя поля (date, time, title, description)
 * @return соответствующий EditField или null если поле неизвестно
 */
private ConversationStateService.EditField mapToEditField(String fieldName) {
    return switch (fieldName) {
        case "date" -> ConversationStateService.EditField.DATE;
        case "time" -> ConversationStateService.EditField.TIME;
        case "title" -> ConversationStateService.EditField.TITLE;
        case "description" -> ConversationStateService.EditField.DESCRIPTION;
        default -> null;
    };
}
```

## Data Models

### Callback Data Format

```
edit_field_{field}_{eventId}
```

Где:
- `field` - имя поля (date, time, title, description)
- `eventId` - идентификатор события (положительное число)

**Примеры:**
- `edit_field_title_123`
- `edit_field_date_456`
- `edit_field_time_789`
- `edit_field_description_101`

### EditField Enum

```java
public enum EditField {
    TITLE,
    DATE,
    TIME,
    DESCRIPTION
}
```

## Correctness Properties

*Свойство - это характеристика или поведение, которое должно выполняться во всех допустимых выполнениях системы - по сути, формальное утверждение о том, что система должна делать. Свойства служат мостом между человекочитаемыми спецификациями и машинопроверяемыми гарантиями корректности.*


### Property 1: Корректное извлечение поля из callback data

*For any* валидного callback data в формате `edit_field_{field}_{eventId}`, где field принадлежит множеству {title, date, time, description} и eventId является положительным числом, система должна корректно извлечь имя поля.

**Validates: Requirements 1.1, 1.2, 1.3, 1.4, 1.5**

### Property 2: Соответствие полей и сообщений

*For any* валидного поля из множества {title, date, time, description}, система должна вернуть соответствующее сообщение с инструкцией для пользователя.

**Validates: Requirements 2.1, 2.2, 2.3, 2.4**

### Property 3: Обработка некорректных полей

*For any* строки, не принадлежащей множеству {title, date, time, description}, система должна вернуть сообщение "❌ Неизвестное поле для редактирования".

**Validates: Requirements 2.5**

### Property 4: Обработка некорректного формата callback data

*For any* callback data, не соответствующего формату `edit_field_{field}_{eventId}` (например, отсутствует разделитель, недостаточно частей), система должна обработать ошибку без падения и записать информацию в лог.

**Validates: Requirements 4.1, 4.2**

## Error Handling

### Типы ошибок

1. **Некорректный формат callback data**
   - Недостаточно частей после split (менее 2)
   - Отсутствует разделитель underscore
   - Обработка: логирование ошибки, отправка сообщения об ошибке пользователю

2. **Некорректный eventId**
   - eventId не является числом
   - eventId отрицательный или ноль
   - Обработка: перехват NumberFormatException, логирование, сообщение об ошибке

3. **Неизвестное поле**
   - Поле не входит в множество {title, date, time, description}
   - Обработка: возврат сообщения "Неизвестное поле для редактирования"

4. **Ошибки Telegram API**
   - Ошибка при отправке сообщения
   - Обработка: перехват TelegramApiException, логирование, использование @HandleCallbackErrors

### Стратегия обработки

```java
try {
    // Извлечение и валидация данных
    String payload = CallbackPrefix.EDIT_FIELD.extractPayload(callbackData);
    String[] parts = payload.split("_", 2);
    
    if (parts.length != 2) {
        log.error("Некорректный формат callback data: {}", callbackData);
        messageService.editMessageText(chatId, messageId, 
            "❌ Произошла ошибка при обработке запроса", null);
        return;
    }
    
    String field = parts[0];
    Long eventId = Long.parseLong(parts[1]);
    
    // Обработка поля
    // ...
    
} catch (NumberFormatException e) {
    log.error("Некорректный eventId в callback data: {}, error: {}", 
        callbackData, e.getMessage());
    messageService.editMessageText(chatId, messageId, 
        "❌ Произошла ошибка при обработке запроса", null);
} catch (TelegramApiException e) {
    log.error("Ошибка Telegram API: {}", e.getMessage(), e);
    throw new RuntimeException("Ошибка при редактировании поля", e);
}
```

## Testing Strategy

### Unit Tests

Будут написаны unit-тесты для проверки конкретных примеров и edge cases:

1. **Тест извлечения поля title**
   - Входные данные: `edit_field_title_123`
   - Ожидаемый результат: field = "title", eventId = 123

2. **Тест извлечения поля date**
   - Входные данные: `edit_field_date_456`
   - Ожидаемый результат: field = "date", eventId = 456

3. **Тест извлечения поля time**
   - Входные данные: `edit_field_time_789`
   - Ожидаемый результат: field = "time", eventId = 789

4. **Тест извлечения поля description**
   - Входные данные: `edit_field_description_101`
   - Ожидаемый результат: field = "description", eventId = 101

5. **Тест сообщения для поля title**
   - Входные данные: field = "title"
   - Ожидаемый результат: "📝 Редактирование названия\n\nОтправьте новое название события:"

6. **Тест сообщения для поля date**
   - Входные данные: field = "date"
   - Ожидаемый результат: "📅 Редактирование даты\n\nВыберите новую дату из календаря:"

7. **Тест сообщения для поля time**
   - Входные данные: field = "time"
   - Ожидаемый результат: "🕐 Редактирование времени\n\nВыберите новое время:"

8. **Тест сообщения для поля description**
   - Входные данные: field = "description"
   - Ожидаемый результат: "📄 Редактирование описания\n\nОтправьте новое описание события:"

9. **Тест обработки некорректного формата (без underscore)**
   - Входные данные: `edit_field_title123`
   - Ожидаемый результат: обработка ошибки, логирование

10. **Тест обработки некорректного eventId (не число)**
    - Входные данные: `edit_field_title_abc`
    - Ожидаемый результат: перехват NumberFormatException, логирование

11. **Тест обработки неизвестного поля**
    - Входные данные: `edit_field_unknown_123`
    - Ожидаемый результат: "❌ Неизвестное поле для редактирования"

### Property-Based Tests

Будут написаны property-based тесты для проверки универсальных свойств:

1. **Property Test 1: Корректное извлечение поля**
   - Генерация: случайное поле из {title, date, time, description}, случайный положительный eventId
   - Проверка: извлеченное поле совпадает с сгенерированным
   - Минимум 100 итераций

2. **Property Test 2: Соответствие полей и сообщений**
   - Генерация: случайное поле из {title, date, time, description}
   - Проверка: возвращаемое сообщение содержит соответствующий эмодзи и текст
   - Минимум 100 итераций

3. **Property Test 3: Обработка некорректных полей**
   - Генерация: случайная строка, не входящая в {title, date, time, description}
   - Проверка: возвращается сообщение об ошибке
   - Минимум 100 итераций

4. **Property Test 4: Обработка некорректного формата**
   - Генерация: случайные строки с некорректным форматом
   - Проверка: система не падает, обрабатывает ошибку
   - Минимум 100 итераций

### Testing Framework

Для property-based тестирования будет использоваться библиотека **jqwik** (уже присутствует в проекте, о чём свидетельствует файл `.jqwik-database`).

Каждый property test будет помечен комментарием:
```java
// Feature: event-field-editing-fix, Property 1: Корректное извлечение поля из callback data
```

## Implementation Notes

1. **Обратная совместимость**: изменения не влияют на другие части системы, так как модифицируется только внутренняя логика метода `handleEditField`.

2. **Производительность**: операция split выполняется за O(n), где n - длина строки. Для коротких callback data это незначительно.

3. **Безопасность**: добавлена валидация формата данных и обработка исключений для предотвращения падения приложения.

4. **Логирование**: добавлено детальное логирование для отладки и мониторинга.

5. **Состояние диалога**: после успешного извлечения поля устанавливается состояние редактирования через `ConversationStateService`, что позволяет системе отслеживать текущий контекст диалога с пользователем.
