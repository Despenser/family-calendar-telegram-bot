# Проектирование: Исправление парсинга callback-данных при удалении вложений

## Обзор

Данный документ описывает техническое решение для исправления бага в методе `handle()` класса `AttachmentCallbackHandler`, который возникает при обработке callback-данных для подтверждения и отмены удаления вложений.

### Текущая проблема

Callback-данные имеют следующий формат:
- Отмена удаления: `attach_file_cancel_delete_9` (где 9 - eventId)
- Подтверждение удаления: `attach_file_confirm_delete_9_123` (где 9 - eventId, 123 - attachmentId)

После извлечения префикса `attach_file_` получаем payload:
- Отмена: `cancel_delete_9` → parts = ["cancel", "delete", "9"]
- Подтверждение: `confirm_delete_9_123` → parts = ["confirm", "delete", "9", "123"]

Текущий код пытается проверить `parts[2].equals("delete")`, что корректно, но затем пытается распарсить `parts[2]` как число, что приводит к `NumberFormatException`.

### Решение

Необходимо изменить логику парсинга для составных действий (confirm/cancel + delete), чтобы корректно извлекать eventId и attachmentId из правильных позиций массива parts.

## Архитектура

### Текущая структура

```
CallbackQuery → AttachmentCallbackHandler.handle()
                ↓
                Извлечение payload после префикса "attach_file_"
                ↓
                Разбиение payload по "_"
                ↓
                Определение action = parts[0]
                ↓
                Switch по action
                ↓
                Обработка конкретного действия
```

### Новая структура

```
CallbackQuery → AttachmentCallbackHandler.handle()
                ↓
                Извлечение payload после префикса "attach_file_"
                ↓
                Разбиение payload по "_"
                ↓
                Определение action = parts[0]
                ↓
                Проверка на составное действие (confirm/cancel)
                ↓
                Извлечение subAction, eventId, attachmentId
                ↓
                Switch по action + subAction
                ↓
                Обработка конкретного действия
```

## Компоненты и интерфейсы

### AttachmentCallbackHandler

**Изменяемый метод:**
```java
public void handle(CallbackQuery callbackQuery, User user) throws Exception
```

**Текущая логика для "confirm":**
```java
case "confirm" -> {
    if (parts.length < 4 || !parts[2].equals("delete")) {
        messageService.answerCallbackQuery(callbackQueryId, "❌ Ошибка: некорректный формат");
        return;
    }
    Long attachmentId = Long.parseLong(parts[3]);
    handleConfirmDelete(attachmentId, eventId, user, chatId, messageId, callbackQueryId);
}
```

**Проблема:** переменная `eventId` используется из внешнего контекста (строка 86), но она была определена как `Long eventId = Long.parseLong(parts[1])` для простых действий, что некорректно для составных.

**Новая логика для "confirm":**
```java
case "confirm" -> {
    // Проверяем, что это действие "delete"
    if (parts.length < 4 || !parts[1].equals("delete")) {
        messageService.answerCallbackQuery(callbackQueryId, "❌ Ошибка: некорректный формат");
        return;
    }
    // Для confirm_delete формат: confirm_delete_{eventId}_{attachmentId}
    Long eventId = Long.parseLong(parts[2]);
    Long attachmentId = Long.parseLong(parts[3]);
    handleConfirmDelete(attachmentId, eventId, user, chatId, messageId, callbackQueryId);
}
```

**Текущая логика для "cancel":**
```java
case "cancel" -> {
    if (parts.length < 3 || !parts[2].equals("delete")) {
        messageService.answerCallbackQuery(callbackQueryId, "❌ Ошибка: некорректный формат");
        return;
    }
    handleCancelDelete(eventId, user, chatId, messageId, callbackQueryId);
}
```

**Проблема:** та же - используется `eventId` из внешнего контекста, проверка `parts[2].equals("delete")` некорректна.

**Новая логика для "cancel":**
```java
case "cancel" -> {
    // Проверяем, что это действие "delete"
    if (parts.length < 3 || !parts[1].equals("delete")) {
        messageService.answerCallbackQuery(callbackQueryId, "❌ Ошибка: некорректный формат");
        return;
    }
    // Для cancel_delete формат: cancel_delete_{eventId}
    Long eventId = Long.parseLong(parts[2]);
    handleCancelDelete(eventId, user, chatId, messageId, callbackQueryId);
}
```

### Изменения в структуре кода

**До:**
```java
String action = parts[0];
Long eventId = Long.parseLong(parts[1]); // ❌ Некорректно для составных действий

switch (action) {
    case "confirm" -> {
        // Использует eventId из внешнего контекста
    }
    case "cancel" -> {
        // Использует eventId из внешнего контекста
    }
}
```

**После:**
```java
String action = parts[0];

switch (action) {
    case "list", "add", "view", "delete", "back" -> {
        // Простые действия: формат {action}_{eventId}[_{attachmentId}]
        Long eventId = Long.parseLong(parts[1]);
        // ... обработка
    }
    case "confirm" -> {
        // Составное действие: формат confirm_delete_{eventId}_{attachmentId}
        if (parts.length < 4 || !parts[1].equals("delete")) {
            messageService.answerCallbackQuery(callbackQueryId, "❌ Ошибка: некорректный формат");
            return;
        }
        Long eventId = Long.parseLong(parts[2]);
        Long attachmentId = Long.parseLong(parts[3]);
        handleConfirmDelete(attachmentId, eventId, user, chatId, messageId, callbackQueryId);
    }
    case "cancel" -> {
        // Составное действие: формат cancel_delete_{eventId}
        if (parts.length < 3 || !parts[1].equals("delete")) {
            messageService.answerCallbackQuery(callbackQueryId, "❌ Ошибка: некорректный формат");
            return;
        }
        Long eventId = Long.parseLong(parts[2]);
        handleCancelDelete(eventId, user, chatId, messageId, callbackQueryId);
    }
}
```

## Модели данных

### Формат callback-данных

**Простые действия:**
```
attach_file_list_{eventId}
attach_file_add_{eventId}
attach_file_view_{eventId}_{attachmentId}
attach_file_delete_{eventId}_{attachmentId}
attach_file_back_{eventId}
```

**Составные действия:**
```
attach_file_confirm_delete_{eventId}_{attachmentId}
attach_file_cancel_delete_{eventId}
```

### Структура payload после извлечения префикса

**Простые действия:**
```
list_{eventId}           → ["list", "{eventId}"]
add_{eventId}            → ["add", "{eventId}"]
view_{eventId}_{attachmentId} → ["view", "{eventId}", "{attachmentId}"]
delete_{eventId}_{attachmentId} → ["delete", "{eventId}", "{attachmentId}"]
back_{eventId}           → ["back", "{eventId}"]
```

**Составные действия:**
```
confirm_delete_{eventId}_{attachmentId} → ["confirm", "delete", "{eventId}", "{attachmentId}"]
cancel_delete_{eventId}                 → ["cancel", "delete", "{eventId}"]
```


## Correctness Properties

*Свойство (property) - это характеристика или поведение, которое должно выполняться для всех валидных входных данных системы. Свойства служат мостом между человекочитаемыми спецификациями и машинно-проверяемыми гарантиями корректности.*

### Property 1: Корректный парсинг callback-данных для отмены удаления

*Для любых* валидных callback-данных формата `attach_file_cancel_delete_{eventId}`, где eventId - положительное целое число, система должна корректно извлечь action="cancel", subAction="delete" и eventId из payload.

**Validates: Requirements 1.1, 1.2**

### Property 2: Корректный парсинг callback-данных для подтверждения удаления

*Для любых* валидных callback-данных формата `attach_file_confirm_delete_{eventId}_{attachmentId}`, где eventId и attachmentId - положительные целые числа, система должна корректно извлечь action="confirm", subAction="delete", eventId и attachmentId из payload.

**Validates: Requirements 2.1, 2.2**

### Property 3: Корректная маршрутизация вызовов методов

*Для любых* валидных callback-данных с действиями "cancel" или "confirm", система должна вызвать соответствующий метод обработки (handleCancelDelete или handleConfirmDelete) с корректными параметрами, извлеченными из правильных позиций массива parts.

**Validates: Requirements 1.3, 2.3**

### Property 4: Обработка ошибок парсинга без исключений

*Для любых* невалидных callback-данных (недостаточное количество частей, некорректный формат, невалидные числа), система должна обработать ошибку без выброса необработанного исключения и отправить пользователю сообщение об ошибке.

**Validates: Requirements 3.3, 3.4**

### Property 5: Обратная совместимость с простыми действиями

*Для любых* callback-данных с простыми действиями (list, add, view, delete, back), система должна продолжать корректно извлекать eventId из позиции parts[1] и attachmentId (если присутствует) из позиции parts[2].

**Validates: Requirements 4.1, 4.3, 4.4**

### Property 6: Логирование ошибок парсинга

*Для любых* callback-данных с некорректным форматом для действий "cancel" или "confirm", система должна залогировать предупреждение с деталями ошибки на уровне WARN.

**Validates: Requirements 3.1, 3.2**

## Обработка ошибок

### Типы ошибок

1. **Недостаточное количество частей в payload**
   - Для "cancel": parts.length < 3
   - Для "confirm": parts.length < 4
   - Обработка: логирование WARN, отправка сообщения об ошибке пользователю

2. **Некорректный subAction**
   - parts[1] != "delete"
   - Обработка: логирование WARN, отправка сообщения об ошибке пользователю

3. **Невалидный формат числа**
   - NumberFormatException при парсинге eventId или attachmentId
   - Обработка: логирование ERROR, отправка сообщения об ошибке пользователю

4. **Null или пустые callback-данные**
   - callbackData == null или callbackData.isEmpty()
   - Обработка: логирование ERROR, отправка сообщения об ошибке пользователю

### Стратегия обработки ошибок

```java
try {
    // Парсинг и обработка callback-данных
} catch (NumberFormatException e) {
    log.error("Ошибка парсинга числа в callback data: {}, error: {}", 
            callbackData, e.getMessage());
    messageService.answerCallbackQuery(callbackQueryId, "❌ Ошибка: некорректный формат");
    return;
} catch (ArrayIndexOutOfBoundsException e) {
    log.error("Недостаточно частей в callback data: {}, error: {}", 
            callbackData, e.getMessage());
    messageService.answerCallbackQuery(callbackQueryId, "❌ Ошибка: некорректный формат");
    return;
}
```

## Стратегия тестирования

### Двойной подход к тестированию

Для обеспечения полного покрытия используется комбинация unit-тестов и property-based тестов:

- **Unit-тесты**: проверяют конкретные примеры, граничные случаи и обработку ошибок
- **Property-тесты**: проверяют универсальные свойства на большом количестве сгенерированных входных данных

### Unit-тесты

**Конкретные примеры:**
1. Парсинг "attach_file_cancel_delete_9" → action="cancel", subAction="delete", eventId=9
2. Парсинг "attach_file_confirm_delete_9_123" → action="confirm", subAction="delete", eventId=9, attachmentId=123
3. Парсинг простых действий: "attach_file_list_5", "attach_file_view_5_10"

**Граничные случаи:**
1. Минимальные значения ID: eventId=1, attachmentId=1
2. Большие значения ID: eventId=Long.MAX_VALUE
3. Пустые части в payload

**Обработка ошибок:**
1. Недостаточное количество частей: "attach_file_cancel_delete"
2. Некорректный subAction: "attach_file_cancel_remove_9"
3. Невалидное число: "attach_file_cancel_delete_abc"
4. Null callback-данные

### Property-Based тесты

**Библиотека:** jqwik (рекомендуемая для Java)

**Конфигурация:** минимум 100 итераций на тест

**Property-тесты:**

1. **Property Test 1: Парсинг отмены удаления**
   ```java
   @Property
   @Label("Feature: attachment-deletion-callback-parsing-fix, Property 1: Корректный парсинг callback-данных для отмены удаления")
   void cancelDeleteCallbackParsingIsCorrect(@ForAll @Positive long eventId) {
       // Генерируем callback-данные
       // Парсим
       // Проверяем корректность извлечения action, subAction, eventId
   }
   ```

2. **Property Test 2: Парсинг подтверждения удаления**
   ```java
   @Property
   @Label("Feature: attachment-deletion-callback-parsing-fix, Property 2: Корректный парсинг callback-данных для подтверждения удаления")
   void confirmDeleteCallbackParsingIsCorrect(
           @ForAll @Positive long eventId,
           @ForAll @Positive long attachmentId) {
       // Генерируем callback-данные
       // Парсим
       // Проверяем корректность извлечения action, subAction, eventId, attachmentId
   }
   ```

3. **Property Test 3: Маршрутизация вызовов**
   ```java
   @Property
   @Label("Feature: attachment-deletion-callback-parsing-fix, Property 3: Корректная маршрутизация вызовов методов")
   void methodRoutingIsCorrect(@ForAll @Positive long eventId) {
       // Используем Mockito spy
       // Проверяем, что вызывается правильный метод с правильными параметрами
   }
   ```

4. **Property Test 4: Обработка ошибок**
   ```java
   @Property
   @Label("Feature: attachment-deletion-callback-parsing-fix, Property 4: Обработка ошибок парсинга без исключений")
   void errorHandlingDoesNotThrowExceptions(@ForAll("invalidCallbackData") String callbackData) {
       // Генерируем невалидные данные
       // Проверяем, что не выбрасывается исключение
       // Проверяем, что отправляется сообщение об ошибке
   }
   ```

5. **Property Test 5: Обратная совместимость**
   ```java
   @Property
   @Label("Feature: attachment-deletion-callback-parsing-fix, Property 5: Обратная совместимость с простыми действиями")
   void simpleActionsStillWork(
           @ForAll("simpleAction") String action,
           @ForAll @Positive long eventId) {
       // Генерируем callback-данные для простых действий
       // Проверяем, что парсинг работает корректно
   }
   ```

### Баланс тестирования

- **Unit-тесты** фокусируются на конкретных сценариях и граничных случаях
- **Property-тесты** обеспечивают широкое покрытие через рандомизацию входных данных
- Вместе они обеспечивают высокую уверенность в корректности реализации

### Интеграционные тесты

Дополнительно рекомендуется создать интеграционные тесты для проверки полного flow:
1. Создание события с вложением
2. Запрос удаления вложения
3. Отмена удаления → проверка, что вложение осталось
4. Повторный запрос удаления
5. Подтверждение удаления → проверка, что вложение удалено

## Детали реализации

### Рефакторинг метода handle()

**Текущая структура (проблемная):**
```java
String action = parts[0];
Long eventId = Long.parseLong(parts[1]); // ❌ Не работает для составных действий

switch (action) {
    case "confirm" -> {
        // Использует eventId из внешнего scope
        Long attachmentId = Long.parseLong(parts[3]);
        handleConfirmDelete(attachmentId, eventId, ...);
    }
}
```

**Новая структура (исправленная):**
```java
String action = parts[0];

switch (action) {
    case "list" -> {
        Long eventId = Long.parseLong(parts[1]);
        handleAttachmentList(eventId, ...);
    }
    case "add" -> {
        Long eventId = Long.parseLong(parts[1]);
        handleAddFile(eventId, ...);
    }
    case "view" -> {
        if (parts.length < 3) {
            messageService.answerCallbackQuery(callbackQueryId, "❌ Ошибка: не указан ID вложения");
            return;
        }
        Long eventId = Long.parseLong(parts[1]);
        Long attachmentId = Long.parseLong(parts[2]);
        handleViewFile(attachmentId, eventId, ...);
    }
    case "delete" -> {
        if (parts.length < 3) {
            messageService.answerCallbackQuery(callbackQueryId, "❌ Ошибка: не указан ID вложения");
            return;
        }
        Long eventId = Long.parseLong(parts[1]);
        Long attachmentId = Long.parseLong(parts[2]);
        handleDeleteFile(attachmentId, eventId, ...);
    }
    case "confirm" -> {
        // Составное действие: confirm_delete_{eventId}_{attachmentId}
        if (parts.length < 4 || !parts[1].equals("delete")) {
            log.warn("Некорректный формат callback data для confirm: {}", callbackData);
            messageService.answerCallbackQuery(callbackQueryId, "❌ Ошибка: некорректный формат");
            return;
        }
        Long eventId = Long.parseLong(parts[2]);
        Long attachmentId = Long.parseLong(parts[3]);
        handleConfirmDelete(attachmentId, eventId, ...);
    }
    case "cancel" -> {
        // Составное действие: cancel_delete_{eventId}
        if (parts.length < 3 || !parts[1].equals("delete")) {
            log.warn("Некорректный формат callback data для cancel: {}", callbackData);
            messageService.answerCallbackQuery(callbackQueryId, "❌ Ошибка: некорректный формат");
            return;
        }
        Long eventId = Long.parseLong(parts[2]);
        handleCancelDelete(eventId, ...);
    }
    case "back" -> {
        Long eventId = Long.parseLong(parts[1]);
        handleBackToEvent(eventId, ...);
    }
    default -> {
        log.warn("Неизвестное действие: {}", action);
        messageService.answerCallbackQuery(callbackQueryId, "❌ Неизвестное действие");
    }
}
```

### Улучшенное логирование

Добавить детальное логирование для отладки:

```java
log.debug("Обработка callback вложения: data='{}', userId={}", callbackData, user.getId());

String payload = CallbackPrefix.ATTACH_FILE.extractPayload(callbackData);
String[] parts = payload.split("_");

log.debug("Payload разобран: parts={}, action={}", Arrays.toString(parts), parts[0]);

// В каждом case:
log.debug("Обработка действия '{}': eventId={}, attachmentId={}", 
        action, eventId, attachmentId);
```

### Обработка исключений

Обернуть парсинг чисел в try-catch для graceful обработки ошибок:

```java
try {
    Long eventId = Long.parseLong(parts[2]);
    Long attachmentId = Long.parseLong(parts[3]);
    handleConfirmDelete(attachmentId, eventId, user, chatId, messageId, callbackQueryId);
} catch (NumberFormatException e) {
    log.error("Ошибка парсинга ID в callback data '{}': {}", callbackData, e.getMessage());
    messageService.answerCallbackQuery(callbackQueryId, "❌ Ошибка: некорректный формат ID");
    return;
}
```

## Влияние на производительность

Изменения не влияют на производительность:
- Парсинг строк остается O(n), где n - длина callback-данных
- Дополнительные проверки выполняются за O(1)
- Логирование выполняется только на уровне DEBUG (отключено в продакшене)

## Обратная совместимость

Изменения полностью обратно совместимы:
- Формат callback-данных в KeyboardService не меняется
- Все существующие действия (list, add, view, delete, back) продолжают работать
- Изменяется только логика парсинга для действий confirm и cancel
