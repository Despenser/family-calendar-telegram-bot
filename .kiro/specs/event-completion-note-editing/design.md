# Design Document

## Overview

Данный дизайн описывает улучшение функциональности добавления заметок к завершенным событиям. Основная цель - обеспечить редактирование одного сообщения на всех этапах процесса вместо создания множества новых сообщений.

Ключевые изменения:
- Сохранение messageId события в контексте добавления заметки
- Редактирование сообщения события на всех этапах (завершение → предложение заметки → ввод заметки → финальное отображение)
- Добавление возможности оставить заметку при ручном завершении события
- Сохранение обратной совместимости с автоматическим завершением

## Architecture

### Компоненты системы

1. **EventCallbackHandler** - обработчик callback для завершения событий и добавления заметок
2. **EventService** - бизнес-логика завершения событий и добавления заметок
3. **ConversationStateService** - управление состоянием диалога (контекст добавления заметки)
4. **UpdateProcessor** - обработка текстовых сообщений с заметками
5. **BotMessageBuilder** - форматирование сообщений о событиях
6. **TelegramMessageService** - отправка и редактирование сообщений в Telegram

### Поток данных

```
Пользователь нажимает "✅ Завершить"
    ↓
EventCallbackHandler.handleCompleteEvent()
    ↓
EventService.completeEvent() - завершает событие, НЕ удаляет сообщение
    ↓
EventCallbackHandler редактирует сообщение с предложением добавить заметку
    ↓
ConversationStateService.setAwaitingCompletionNote() - сохраняет messageId
    ↓
Пользователь нажимает "📝 Добавить заметку" или "⏭️ Пропустить"
    ↓
Если "Добавить заметку":
    EventCallbackHandler редактирует сообщение с просьбой ввести текст
    ↓
    UpdateProcessor.handleCompletionNote() получает текст
    ↓
    EventService.addCompletionNote() сохраняет заметку
    ↓
    UpdateProcessor редактирует сообщение с финальной карточкой события
    ↓
    ConversationStateService.clearAwaitingCompletionNote()
    
Если "Пропустить":
    EventCallbackHandler редактирует сообщение с финальной карточкой события
    ↓
    ConversationStateService.clearAwaitingCompletionNote()
```

## Components and Interfaces

### 1. CompletionNoteContext (расширение)

Текущая структура:
```java
@Data
@AllArgsConstructor
public static class CompletionNoteContext {
    private Long eventId;
    private Long chatId;
}
```

Новая структура:
```java
@Data
@AllArgsConstructor
public static class CompletionNoteContext {
    private Long eventId;
    private Long chatId;
    private Integer messageId;  // НОВОЕ: для редактирования сообщения
}
```

### 2. EventCallbackHandler.handleCompleteEvent()

Текущее поведение:
```java
private void handleCompleteEvent(...) {
    eventService.completeEvent(eventId, userId);  // Удаляет сообщение
    messageService.answerCallbackQuery(callbackQueryId, "Событие завершено");
}
```

Новое поведение:
```java
private void handleCompleteEvent(String callbackData, Long userId, Long chatId, 
                                 Integer messageId, String callbackQueryId) {
    // 1. Завершаем событие БЕЗ удаления сообщения
    Event completedEvent = eventService.completeEventWithoutDeletion(eventId, userId);
    
    // 2. Редактируем сообщение с предложением добавить заметку
    String message = buildCompletionMessage(completedEvent);
    InlineKeyboardMarkup keyboard = createCompletionNoteKeyboard(eventId);
    messageService.editMessageText(chatId, messageId, message, keyboard);
    
    // 3. Сохраняем контекст с messageId
    conversationStateService.setAwaitingCompletionNote(userId, eventId, chatId, messageId);
    
    messageService.answerCallbackQuery(callbackQueryId, "");
}
```

### 3. EventCallbackHandler.handleAddCompletionNote()

Текущее поведение:
```java
private void handleAddCompletionNote(...) {
    conversationStateService.setAwaitingCompletionNote(userId, eventId, chatId);
    messageService.sendMessage(chatId, message);  // Создает НОВОЕ сообщение
    messageService.answerCallbackQuery(callbackQueryId, "Ожидаю текст заметки");
}
```

Новое поведение:
```java
private void handleAddCompletionNote(String callbackData, Long userId, Long chatId, 
                                    Integer messageId, String callbackQueryId) {
    Long eventId = extractEventId(callbackData, CallbackPrefix.ADD_COMPLETION_NOTE);
    
    // Редактируем ТЕКУЩЕЕ сообщение
    String message = formatMessage(
        "📝 Напишите заметку о том, как прошло событие.\n\n" +
        "Например, что было сделано, какие были результаты или впечатления."
    );
    messageService.editMessageText(chatId, messageId, message, null);
    
    // Обновляем контекст (messageId уже должен быть там)
    conversationStateService.updateCompletionNoteContext(userId, eventId, chatId, messageId);
    
    messageService.answerCallbackQuery(callbackQueryId, "");
}
```

### 4. EventCallbackHandler.handleSkipCompletionNote()

Текущее поведение:
```java
private void handleSkipCompletionNote(...) {
    messageService.sendMessage(chatId, message);  // Создает НОВОЕ сообщение
    messageService.answerCallbackQuery(callbackQueryId, "Заметка пропущена");
}
```

Новое поведение:
```java
private void handleSkipCompletionNote(Long userId, Long chatId, Integer messageId, 
                                     String callbackQueryId) {
    // Получаем контекст для eventId
    CompletionNoteContext context = conversationStateService.getCompletionNoteContext(userId);
    
    if (context != null) {
        // Получаем событие
        Event event = eventService.getEventById(context.getEventId());
        
        // Редактируем сообщение с финальной карточкой события
        String eventMessage = botMessageBuilder.buildCompletedEventMessage(event);
        messageService.editMessageText(chatId, messageId, eventMessage, null);
        
        // Очищаем контекст
        conversationStateService.clearAwaitingCompletionNote(userId);
    }
    
    messageService.answerCallbackQuery(callbackQueryId, "");
}
```

### 5. UpdateProcessor.handleCompletionNote()

Текущее поведение:
```java
private void handleCompletionNote(...) {
    CompletionNoteContext context = conversationStateService.getCompletionNoteContext(userId);
    Event event = eventService.addCompletionNote(eventId, userId, noteText);
    conversationStateService.clearAwaitingCompletionNote(userId);
    
    // Отправляет НОВОЕ подтверждающее сообщение
    String response = formatMessage("✅ Заметка успешно добавлена!");
    messageService.sendMessage(chatId, response);
}
```

Новое поведение:
```java
private void handleCompletionNote(Message message, User user, String noteText) {
    Long userId = user.getId();
    Long chatId = message.getChatId();
    
    CompletionNoteContext context = conversationStateService.getCompletionNoteContext(userId);
    
    if (context == null) {
        // Обработка ошибки
        return;
    }
    
    Long eventId = context.getEventId();
    Integer messageId = context.getMessageId();
    
    // Добавляем заметку
    Event event = eventService.addCompletionNote(eventId, userId, noteText);
    
    // Редактируем сообщение с финальной карточкой события
    String eventMessage = botMessageBuilder.buildCompletedEventMessage(event);
    messageService.editMessageText(chatId, messageId, eventMessage, null);
    
    // Очищаем контекст
    conversationStateService.clearAwaitingCompletionNote(userId);
}
```

### 6. EventService.completeEvent()

Текущее поведение:
```java
public Event completeEvent(Long eventId, Long userId) {
    // ... проверки ...
    event.setStatus(Event.EventStatus.COMPLETED);
    event.setCompletedAt(LocalDateTime.now());
    
    // Удаляет сообщение
    if (event.getMessageId() != null) {
        messageService.deleteMessageSilently(event.getUser().getTelegramId(), event.getMessageId());
    }
    
    event.setMessageId(null);
    event.setIsMyEventsHeader(false);
    
    Event completedEvent = eventRepository.save(event);
    updateMyEventsHeaderAfterRemoval(userId);
    
    return completedEvent;
}
```

Новое поведение - добавляем новый метод:
```java
public Event completeEventWithoutDeletion(Long eventId, Long userId) {
    // ... те же проверки ...
    event.setStatus(Event.EventStatus.COMPLETED);
    event.setCompletedAt(LocalDateTime.now());
    
    // НЕ удаляем сообщение, НЕ сбрасываем messageId
    // Сообщение будет отредактировано в EventCallbackHandler
    
    event.setIsMyEventsHeader(false);
    
    Event completedEvent = eventRepository.save(event);
    
    // Записываем в историю
    eventHistoryService.recordAction(
        eventId,
        userId,
        EventHistory.ActionType.COMPLETED,
        "status",
        Event.EventStatus.ACTIVE.name(),
        Event.EventStatus.COMPLETED.name()
    );
    
    updateMyEventsHeaderAfterRemoval(userId);
    
    return completedEvent;
}
```

Старый метод `completeEvent()` остается для обратной совместимости (автоматическое завершение).

### 7. BotMessageBuilder

Добавляем новые методы форматирования:

```java
/**
 * Формирует сообщение о завершенном событии с предложением добавить заметку.
 */
public String buildCompletionMessage(Event event) {
    StringBuilder message = new StringBuilder();
    message.append("✅ ").append(bold("Событие завершено!")).append("\n\n");
    message.append(buildEventMessage(event));
    message.append("\n\n").append("Хотите добавить заметку о том, как прошло событие?");
    return message.toString();
}

/**
 * Формирует сообщение о завершенном событии с заметкой.
 */
public String buildCompletedEventMessage(Event event) {
    StringBuilder message = new StringBuilder();
    message.append("✅ ").append(bold("Событие завершено")).append("\n\n");
    message.append(buildEventMessage(event));
    
    if (event.getCompletionNote() != null && !event.getCompletionNote().isBlank()) {
        message.append("\n\n📝 ").append(bold("Заметка:")).append("\n");
        message.append(escape(event.getCompletionNote()));
    }
    
    return message.toString();
}
```

## Data Models

### Event (изменений нет)

Модель Event уже содержит все необходимые поля:
- `completionNote` - текст заметки
- `messageId` - ID сообщения в Telegram
- `status` - статус события (COMPLETED)

### ConversationState (изменений нет)

Состояние диалога хранится в памяти через `ConversationStateService`.

### CompletionNoteContext (расширение)

```java
@Data
@AllArgsConstructor
public static class CompletionNoteContext {
    private Long eventId;      // ID события
    private Long chatId;       // ID чата
    private Integer messageId; // ID сообщения для редактирования (НОВОЕ)
}
```


## Correctness Properties

*Свойство (property) - это характеристика или поведение, которое должно выполняться для всех допустимых выполнений системы - по сути, формальное утверждение о том, что система должна делать. Свойства служат мостом между человекочитаемыми спецификациями и машинно-проверяемыми гарантиями корректности.*

### Property 1: Сохранение messageId на протяжении процесса

*For any* события и пользователя, когда пользователь завершает событие и проходит через процесс добавления заметки (добавление или пропуск), messageId сообщения должен оставаться неизменным на всех этапах.

**Validates: Requirements 1.1, 1.2, 1.3, 1.4, 1.5**

### Property 2: Завершение события создает контекст с messageId

*For any* активного события, когда пользователь завершает его вручную, система должна создать CompletionNoteContext, содержащий eventId, chatId и messageId события.

**Validates: Requirements 2.1, 3.1, 3.2**

### Property 3: Сохранение заметки при вводе

*For any* текста заметки (непустого), когда пользователь вводит его после завершения события, заметка должна быть сохранена в поле completionNote события.

**Validates: Requirements 2.3**

### Property 4: Очистка контекста после завершения процесса

*For any* пользователя в режиме ожидания заметки, когда пользователь добавляет заметку или пропускает её, система должна очистить CompletionNoteContext для этого пользователя.

**Validates: Requirements 3.3**

### Property 5: Обработка ошибок очищает контекст

*For any* пользователя в режиме ожидания заметки, когда происходит ошибка при редактировании сообщения, система должна корректно обработать ошибку и очистить контекст.

**Validates: Requirements 3.4**

### Property 6: Корректное форматирование заметки

*For any* завершенного события, отформатированное сообщение должно:
- Содержать текст заметки с эмодзи "📝", если заметка присутствует
- Не содержать секцию заметки, если заметка отсутствует
- Корректно экранировать специальные символы Telegram Markdown в тексте заметки

**Validates: Requirements 4.1, 4.2, 4.3, 4.4**

### Property 7: Статус события изменяется на COMPLETED

*For any* активного события, когда пользователь завершает его вручную, статус события должен измениться с ACTIVE на COMPLETED.

**Validates: Requirements 2.1**

### Property 8: Пропуск заметки не создает заметку

*For any* пользователя, когда он пропускает добавление заметки, поле completionNote события должно остаться null.

**Validates: Requirements 2.4**

### Property 9: Поддержка добавления заметок к завершенным событиям

*For any* события со статусом COMPLETED, система должна позволять добавить или обновить заметку через метод addCompletionNote().

**Validates: Requirements 5.4**

## Error Handling

### Ошибки редактирования сообщений

**Проблема:** Telegram API может вернуть ошибку при попытке редактирования сообщения (например, сообщение удалено пользователем, слишком старое, или нет прав).

**Решение:**
1. Оборачиваем все вызовы `editMessageText()` в try-catch блоки
2. При ошибке логируем её и очищаем контекст
3. Используем fallback на отправку нового сообщения, если редактирование невозможно
4. Аннотация `@HandleCallbackErrors` обеспечивает централизованную обработку ошибок

```java
try {
    messageService.editMessageText(chatId, messageId, message, keyboard);
} catch (TelegramApiException e) {
    log.error("Ошибка редактирования сообщения, используем fallback", e);
    conversationStateService.clearAwaitingCompletionNote(userId);
    messageService.sendMessage(chatId, message);  // Fallback
}
```

### Отсутствие messageId

**Проблема:** Событие может не иметь messageId (например, создано через API, или сообщение было удалено).

**Решение:**
1. Проверяем наличие messageId перед попыткой редактирования
2. Если messageId отсутствует, используем fallback на отправку нового сообщения
3. Логируем предупреждение для мониторинга

```java
if (event.getMessageId() != null) {
    messageService.editMessageText(chatId, event.getMessageId(), message, keyboard);
} else {
    log.warn("MessageId отсутствует для события {}, отправляем новое сообщение", eventId);
    messageService.sendMessage(chatId, message);
}
```

### Контекст не найден

**Проблема:** Пользователь может ввести текст заметки, но контекст уже очищен (например, из-за таймаута или перезапуска бота).

**Решение:**
1. Проверяем наличие контекста перед обработкой заметки
2. Если контекст отсутствует, отправляем понятное сообщение об ошибке
3. Очищаем состояние ожидания заметки

```java
CompletionNoteContext context = conversationStateService.getCompletionNoteContext(userId);
if (context == null) {
    log.warn("Контекст добавления заметки не найден для пользователя {}", userId);
    conversationStateService.clearAwaitingCompletionNote(userId);
    String response = formatMessage("❌ Время ожидания истекло. Попробуйте снова.");
    messageService.sendMessage(chatId, response);
    return;
}
```

### Событие не найдено

**Проблема:** Событие может быть удалено между завершением и добавлением заметки.

**Решение:**
1. Обрабатываем `EventNotFoundException` в `handleCompletionNote()`
2. Очищаем контекст и отправляем сообщение об ошибке
3. Логируем для мониторинга

```java
try {
    Event event = eventService.addCompletionNote(eventId, userId, noteText);
} catch (EventNotFoundException e) {
    log.error("Событие не найдено при добавлении заметки", e);
    conversationStateService.clearAwaitingCompletionNote(userId);
    String response = formatMessage("❌ Событие не найдено.");
    messageService.sendMessage(chatId, response);
    return;
}
```

## Testing Strategy

### Dual Testing Approach

Для обеспечения корректности реализации используется комбинация unit-тестов и property-based тестов:

**Unit Tests:**
- Проверяют конкретные сценарии и edge cases
- Тестируют интеграцию между компонентами
- Проверяют обработку ошибок
- Фокусируются на специфических примерах

**Property-Based Tests:**
- Проверяют универсальные свойства на множестве входных данных
- Генерируют случайные события, заметки, пользователей
- Запускаются минимум 100 итераций для каждого свойства
- Каждый тест помечен комментарием с ссылкой на свойство из дизайна

### Unit Testing Focus

**EventCallbackHandlerTest:**
- Тест завершения события с предложением заметки
- Тест нажатия кнопки "Добавить заметку"
- Тест нажатия кнопки "Пропустить"
- Тест обработки ошибок редактирования сообщения
- Тест fallback на новое сообщение при отсутствии messageId

**EventServiceTest:**
- Тест метода `completeEventWithoutDeletion()`
- Тест что messageId не сбрасывается
- Тест что сообщение не удаляется
- Тест обратной совместимости старого метода `completeEvent()`
- Тест добавления заметки к уже завершенному событию

**UpdateProcessorTest:**
- Тест обработки текста заметки
- Тест редактирования сообщения с финальной карточкой
- Тест очистки контекста после добавления заметки
- Тест обработки отсутствующего контекста

**ConversationStateServiceTest:**
- Тест создания контекста с messageId
- Тест получения контекста
- Тест очистки контекста
- Тест обновления контекста

**BotMessageBuilderTest:**
- Тест форматирования сообщения о завершении
- Тест форматирования события с заметкой
- Тест форматирования события без заметки
- Тест экранирования специальных символов в заметке

### Property-Based Testing

**Framework:** jqwik (уже используется в проекте)

**Configuration:**
- Минимум 100 итераций на тест (`@Property(tries = 100)`)
- Каждый тест помечен комментарием: `// Feature: event-completion-note-editing, Property N: <текст свойства>`

**Property Tests:**

1. **MessageId Preservation Property Test**
   - Генерирует случайные события и пользователей
   - Проходит через весь процесс (завершение → добавление/пропуск заметки)
   - Проверяет что messageId не изменился

2. **Context Creation Property Test**
   - Генерирует случайные активные события
   - Завершает их вручную
   - Проверяет что контекст создан с правильными данными

3. **Note Persistence Property Test**
   - Генерирует случайные тексты заметок
   - Добавляет их к завершенным событиям
   - Проверяет что заметка сохранена в БД

4. **Context Cleanup Property Test**
   - Генерирует случайных пользователей в режиме ожидания заметки
   - Завершает процесс (добавление или пропуск)
   - Проверяет что контекст очищен

5. **Note Formatting Property Test**
   - Генерирует случайные события с заметками и без
   - Форматирует сообщения
   - Проверяет корректность форматирования и экранирования

6. **Status Change Property Test**
   - Генерирует случайные активные события
   - Завершает их
   - Проверяет что статус изменился на COMPLETED

7. **Skip Note Property Test**
   - Генерирует случайных пользователей
   - Пропускает добавление заметки
   - Проверяет что completionNote остался null

8. **Completed Event Note Property Test**
   - Генерирует случайные завершенные события
   - Добавляет к ним заметки
   - Проверяет что заметка добавлена успешно

### Integration Testing

**EventCompletionIntegrationTest:**
- Полный сценарий: завершение события → добавление заметки → проверка результата
- Полный сценарий: завершение события → пропуск заметки → проверка результата
- Тестирование с реальной БД (Testcontainers)
- Проверка что все компоненты работают вместе корректно

### Test Coverage Goals

- Unit tests: 90%+ покрытие новых методов
- Property tests: все 9 свойств покрыты
- Integration tests: основные сценарии использования
- Edge cases: события без messageId, отсутствующий контекст, ошибки API
