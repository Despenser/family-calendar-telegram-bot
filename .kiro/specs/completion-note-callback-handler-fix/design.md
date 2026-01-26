# Проектирование: Исправление обработки callback для добавления заметки к завершенному событию

## Обзор

Данный документ описывает проектное решение для исправления ошибки обработки callback `add_completion_note_{eventId}` и `skip_completion_note` в `EventCallbackHandler`. Проблема заключается в том, что методы `handleAddCompletionNote` и `handleSkipCompletionNote` реализованы, но не вызываются из главного метода `handle`, так как отсутствуют соответствующие проверки префиксов.

## Архитектура

### Текущая архитектура

```
CallbackQueryDispatcher
    ↓
EventCallbackHandler.canHandle(callbackData)
    ↓
EventCallbackHandler.handle(callbackQuery, user)
    ↓
[Проверки префиксов и маршрутизация к методам-обработчикам]
```

### Проблема

В методе `EventCallbackHandler.handle()` отсутствуют проверки для префиксов:
- `CallbackPrefix.ADD_COMPLETION_NOTE` → `handleAddCompletionNote()`
- `CallbackPrefix.SKIP_COMPLETION_NOTE` → `handleSkipCompletionNote()`

В методе `EventCallbackHandler.canHandle()` также отсутствуют проверки для этих префиксов.

### Решение

Добавить проверки префиксов `ADD_COMPLETION_NOTE` и `SKIP_COMPLETION_NOTE` в методы `canHandle()` и `handle()` класса `EventCallbackHandler`.

## Компоненты и интерфейсы

### EventCallbackHandler

**Изменяемые методы:**

1. **canHandle(String callbackData)**
   - Добавить проверку `CallbackPrefix.ADD_COMPLETION_NOTE.matches(callbackData)`
   - Добавить проверку `CallbackPrefix.SKIP_COMPLETION_NOTE.matches(callbackData)`

2. **handle(CallbackQuery callbackQuery, User user)**
   - Добавить условие для `CallbackPrefix.ADD_COMPLETION_NOTE.matches(callbackData)`
   - Добавить условие для `CallbackPrefix.SKIP_COMPLETION_NOTE.matches(callbackData)`
   - Вызвать соответствующие методы-обработчики

**Существующие методы (не требуют изменений):**
- `handleAddCompletionNote(String callbackData, Long userId, Long chatId, String callbackQueryId)` - уже реализован
- `handleSkipCompletionNote(Long userId, Long chatId, String callbackQueryId)` - уже реализован

## Модели данных

Изменения в моделях данных не требуются. Используются существующие:
- `CallbackPrefix.ADD_COMPLETION_NOTE` - уже определен
- `CallbackPrefix.SKIP_COMPLETION_NOTE` - уже определен

## Свойства корректности

*Свойство - это характеристика или поведение, которое должно выполняться во всех допустимых выполнениях системы - по сути, формальное утверждение о том, что должна делать система. Свойства служат мостом между человекочитаемыми спецификациями и машинопроверяемыми гарантиями корректности.*

### Свойство 1: Маршрутизация ADD_COMPLETION_NOTE

*Для любого* callback data с префиксом `ADD_COMPLETION_NOTE`, метод `canHandle` должен возвращать true, и метод `handle` должен вызывать `handleAddCompletionNote`

**Validates: Requirements 1.1, 1.2, 1.3, 2.1**

### Свойство 2: Маршрутизация SKIP_COMPLETION_NOTE

*Для любого* callback data с префиксом `SKIP_COMPLETION_NOTE`, метод `canHandle` должен возвращать true, и метод `handle` должен вызывать `handleSkipCompletionNote`

**Validates: Requirements 1.4, 1.5, 2.2**

### Свойство 3: Отсутствие ошибок "Неизвестный callback"

*Для любого* callback data с префиксом `ADD_COMPLETION_NOTE` или `SKIP_COMPLETION_NOTE`, система не должна логировать предупреждение "Неизвестный callback data" и не должна отправлять пользователю сообщение "❌ Неизвестная команда"

**Validates: Requirements 2.3, 2.4**

## Обработка ошибок

Обработка ошибок уже реализована в существующих методах:
- `handleAddCompletionNote` использует try-catch для обработки `TelegramApiException`
- `handleSkipCompletionNote` использует try-catch для обработки `TelegramApiException`
- Аннотация `@HandleCallbackErrors` на методе `handle` обеспечивает централизованную обработку ошибок

Дополнительная обработка ошибок не требуется.

## Стратегия тестирования

### Unit тесты

1. **Тест метода canHandle**
   - Проверить, что `canHandle("add_completion_note_123")` возвращает true
   - Проверить, что `canHandle("skip_completion_note")` возвращает true

2. **Тест метода handle для ADD_COMPLETION_NOTE**
   - Создать mock CallbackQuery с data = "add_completion_note_123"
   - Проверить, что вызывается `conversationStateService.setAwaitingCompletionNote`
   - Проверить, что отправляется сообщение с просьбой ввести заметку
   - Проверить, что вызывается `messageService.answerCallbackQuery`

3. **Тест метода handle для SKIP_COMPLETION_NOTE**
   - Создать mock CallbackQuery с data = "skip_completion_note"
   - Проверить, что отправляется подтверждающее сообщение
   - Проверить, что вызывается `messageService.answerCallbackQuery`

### Property-based тесты

1. **Свойство 1: Маршрутизация ADD_COMPLETION_NOTE**
   - Генерировать случайные eventId
   - Для каждого eventId создавать callback data формата `add_completion_note_{eventId}`
   - Проверять, что `canHandle` возвращает true
   - Проверять, что при вызове `handle` не возникает исключений

2. **Свойство 2: Маршрутизация SKIP_COMPLETION_NOTE**
   - Проверять, что `canHandle("skip_completion_note")` всегда возвращает true
   - Проверять, что при вызове `handle` с этим callback data не возникает исключений

### Integration тесты

Не требуются, так как изменения касаются только маршрутизации внутри одного класса.

## Детали реализации

### Изменения в методе canHandle

```java
@Override
public boolean canHandle(String callbackData) {
    if (callbackData == null) {
        return false;
    }
    
    return CallbackPrefix.VIEW_EVENT.matches(callbackData) ||
           CallbackPrefix.EDIT_EVENT.matches(callbackData) ||
           CallbackPrefix.DELETE_EVENT.matches(callbackData) ||
           CallbackPrefix.EDIT_FIELD.matches(callbackData) ||
           CallbackPrefix.COMPLETE_EVENT.matches(callbackData) ||
           CallbackPrefix.EDIT_CANCEL.matches(callbackData) ||
           CallbackPrefix.ADD_COMPLETION_NOTE.matches(callbackData) ||  // ДОБАВЛЕНО
           CallbackPrefix.SKIP_COMPLETION_NOTE.matches(callbackData);   // ДОБАВЛЕНО
}
```

### Изменения в методе handle

```java
@Override
@HandleCallbackErrors
public void handle(CallbackQuery callbackQuery, User user) throws Exception {
    String callbackData = callbackQuery.getData();
    Long chatId = callbackQuery.getMessage().getChatId();
    Integer messageId = callbackQuery.getMessage().getMessageId();
    String callbackQueryId = callbackQuery.getId();
    
    log.debug("Обработка callback для события: data='{}', userId={}", 
            callbackData, user.getId());
    
    if (CallbackPrefix.VIEW_EVENT.matches(callbackData)) {
        handleViewEvent(callbackData, user.getId(), chatId, callbackQueryId);
    } else if (CallbackPrefix.EDIT_EVENT.matches(callbackData)) {
        handleEditEvent(callbackData, user, chatId, messageId, callbackQueryId);
    } else if (CallbackPrefix.DELETE_EVENT.matches(callbackData)) {
        handleDeleteEvent(callbackData, user.getId(), chatId, messageId, callbackQueryId);
    } else if (CallbackPrefix.EDIT_FIELD.matches(callbackData)) {
        handleEditField(callbackData, user, chatId, messageId, callbackQueryId);
    } else if (CallbackPrefix.COMPLETE_EVENT.matches(callbackData)) {
        handleCompleteEvent(callbackData, user.getId(), chatId, messageId, callbackQueryId);
    } else if (CallbackPrefix.EDIT_CANCEL.matches(callbackData)) {
        handleEditCancel(callbackData, user.getId(), chatId, callbackQueryId);
    } else if (CallbackPrefix.ADD_COMPLETION_NOTE.matches(callbackData)) {  // ДОБАВЛЕНО
        handleAddCompletionNote(callbackData, user.getId(), chatId, callbackQueryId);
    } else if (CallbackPrefix.SKIP_COMPLETION_NOTE.matches(callbackData)) {  // ДОБАВЛЕНО
        handleSkipCompletionNote(user.getId(), chatId, callbackQueryId);
    }
}
```

## Влияние на производительность

Изменения не влияют на производительность, так как добавляются только дополнительные проверки условий в методах `canHandle` и `handle`.

## Обратная совместимость

Изменения полностью обратно совместимы. Добавляется только новая функциональность без изменения существующего поведения.

## Зависимости

Изменения не требуют добавления новых зависимостей. Используются существующие компоненты:
- `CallbackPrefix` enum
- `ConversationStateService`
- `TelegramMessageService`
- `MarkdownFormatter`
