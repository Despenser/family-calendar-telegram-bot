# Design Document

## Overview

Данный документ описывает проектное решение для исправления проблемы с преждевременным отображением сообщения "У вас пока нет созданных событий" при завершении последнего события в списке с добавлением заметки о завершении.

### Текущая проблема

При завершении последнего события в списке с добавлением заметки:
1. Пользователь нажимает кнопку "Завершить"
2. Вызывается `EventService.completeEventWithoutDeletion()`
3. Внутри этого метода вызывается `updateMyEventsHeaderAfterRemoval()`
4. Метод `updateMyEventsHeaderAfterRemoval()` проверяет список активных событий
5. Если список пуст, сразу отправляется сообщение "У вас пока нет созданных событий"
6. Пользователь видит это сообщение ДО того, как он напишет заметку
7. После ввода заметки сообщение о завершении отображается, но сообщение о пустом списке уже висит выше

### Желаемое поведение

1. Пользователь нажимает кнопку "Завершить"
2. Событие завершается, но проверка пустоты списка откладывается
3. Пользователю предлагается добавить заметку
4. Пользователь вводит заметку и отправляет её
5. Отображается сообщение о завершении события с заметкой
6. ТОЛЬКО ПОСЛЕ ЭТОГО отправляется сообщение "У вас пока нет созданных событий"

## Architecture

### Компоненты

1. **EventService** - сервис управления событиями
   - Метод `completeEventWithoutDeletion()` - завершает событие без удаления сообщения
   - Метод `updateMyEventsHeaderAfterRemoval()` - обновляет шапку и отправляет сообщение о пустом списке
   - Новый метод `completeEventWithoutHeaderUpdate()` - завершает событие БЕЗ обновления шапки

2. **EventCallbackHandler** - обработчик callback-запросов для событий
   - Метод `handleCompleteEvent()` - обрабатывает нажатие кнопки "Завершить"
   - Метод `handleSkipCompletionNote()` - обрабатывает пропуск заметки

3. **UpdateProcessor** - процессор обновлений от Telegram
   - Метод `processTextMessage()` - обрабатывает текстовые сообщения
   - Обработка ввода заметки о завершении

4. **ConversationStateService** - сервис управления состоянием диалога
   - Контекст `CompletionNoteContext` - хранит информацию о завершенном событии

## Components and Interfaces

### EventService

#### Новый метод: completeEventWithoutHeaderUpdate

```java
/**
 * Завершает событие без обновления шапки /my_events.
 * 
 * <p>Используется при завершении события с добавлением заметки,
 * чтобы отложить проверку пустоты списка до момента завершения
 * ввода заметки.</p>
 * 
 * @param eventId идентификатор события
 * @param userId идентификатор пользователя
 * @return завершенное событие
 */
@Transactional
public Event completeEventWithoutHeaderUpdate(Long eventId, Long userId) {
    // Та же логика, что и в completeEventWithoutDeletion,
    // но БЕЗ вызова updateMyEventsHeaderAfterRemoval
    
    Event event = eventRepository.findById(eventId)
        .orElseThrow(() -> new EventNotFoundException(eventId));
    
    if (!event.belongsToUser(userId)) {
        throw new UnauthorizedAccessException("Только создатель события может его завершить");
    }
    
    if (event.getStatus() != Event.EventStatus.ACTIVE) {
        throw new IllegalStateException("Можно завершить только активное событие");
    }
    
    event.setStatus(Event.EventStatus.COMPLETED);
    event.setCompletedAt(LocalDateTime.now());
    event.setIsMyEventsHeader(false);
    
    Event completedEvent = eventRepository.save(event);
    
    eventHistoryService.recordChange(
        eventId,
        userId,
        EventHistory.ActionType.UPDATED,
        "status",
        "ACTIVE",
        "COMPLETED"
    );
    
    handleEventCompletion(eventId);
    
    // НЕ вызываем updateMyEventsHeaderAfterRemoval!
    
    return completedEvent;
}
```

### EventCallbackHandler

#### Изменение метода handleCompleteEvent

```java
private void handleCompleteEvent(String callbackData, Long userId, Long chatId, 
                                 Integer messageId, String callbackQueryId) {
    Long eventId = extractEventId(callbackData, CallbackPrefix.COMPLETE_EVENT);
    
    try {
        // ИЗМЕНЕНИЕ: Используем новый метод БЕЗ обновления шапки
        Event completedEvent = eventService.completeEventWithoutHeaderUpdate(eventId, userId);
        
        // Редактируем сообщение с предложением добавить заметку
        String message = botMessageBuilder.buildCompletionMessage(completedEvent);
        InlineKeyboardMarkup keyboard = createCompletionNoteKeyboard(eventId);
        
        try {
            messageService.editMessageText(chatId, messageId, message, keyboard);
            conversationStateService.setAwaitingCompletionNote(userId, eventId, chatId, messageId);
        } catch (TelegramApiException e) {
            messageService.sendMessage(chatId, message, keyboard);
            conversationStateService.setAwaitingCompletionNote(userId, eventId, chatId, null);
        }
        
        messageService.answerCallbackQuery(callbackQueryId, "");
        
    } catch (Exception e) {
        log.error("Ошибка при завершении события: eventId={}, userId={}", eventId, userId, e);
    }
}
```

#### Изменение метода handleSkipCompletionNote

```java
private void handleSkipCompletionNote(Long userId, Long chatId, Integer messageId, 
                                     String callbackQueryId) {
    try {
        CompletionNoteContext context = conversationStateService.getCompletionNoteContext(userId);
        
        if (context == null) {
            log.warn("Контекст добавления заметки не найден для пользователя ID={}", userId);
            conversationStateService.clearAwaitingCompletionNote(userId);
            String errorMessage = formatMessage("❌ Время ожидания истекло. Попробуйте снова.");
            messageService.sendMessage(chatId, errorMessage);
            messageService.answerCallbackQuery(callbackQueryId, "");
            return;
        }
        
        Long eventId = context.getEventId();
        Integer contextMessageId = context.getMessageId();
        
        Event event = eventService.getEventById(eventId);
        String eventMessage = botMessageBuilder.buildCompletedEventMessage(event);
        
        Integer targetMessageId = contextMessageId != null ? contextMessageId : messageId;
        
        try {
            if (targetMessageId != null) {
                messageService.editMessageText(chatId, targetMessageId, eventMessage, null);
            } else {
                messageService.sendMessage(chatId, eventMessage);
            }
        } catch (TelegramApiException e) {
            messageService.sendMessage(chatId, eventMessage);
        }
        
        conversationStateService.clearAwaitingCompletionNote(userId);
        
        // ДОБАВЛЕНИЕ: Обновляем шапку ПОСЛЕ завершения процесса
        eventService.updateMyEventsHeaderAfterRemoval(userId);
        
        messageService.answerCallbackQuery(callbackQueryId, "");
        
    } catch (Exception e) {
        log.error("Ошибка при пропуске заметки: userId={}", userId, e);
        conversationStateService.clearAwaitingCompletionNote(userId);
    }
}
```

### UpdateProcessor

#### Изменение обработки текстовых сообщений для заметок о завершении

```java
// В методе processTextMessage, после обработки других состояний

// Обработка ввода заметки о завершении
if (conversationStateService.isAwaitingCompletionNote(userId)) {
    CompletionNoteContext context = conversationStateService.getCompletionNoteContext(userId);
    
    if (context != null) {
        Long eventId = context.getEventId();
        Long chatId = context.getChatId();
        Integer messageId = context.getMessageId();
        String noteText = message.getText();
        
        try {
            // Добавляем заметку к событию
            Event event = eventService.addCompletionNote(eventId, userId, noteText);
            
            // Формируем финальное сообщение
            String eventMessage = botMessageBuilder.buildCompletedEventWithNoteMessage(event);
            
            // Редактируем или отправляем сообщение
            if (messageId != null) {
                try {
                    telegramMessageService.editMessageText(chatId, messageId, eventMessage, null);
                } catch (TelegramApiException e) {
                    telegramMessageService.sendMessage(chatId, eventMessage);
                }
            } else {
                telegramMessageService.sendMessage(chatId, eventMessage);
            }
            
            // Очищаем контекст
            conversationStateService.clearAwaitingCompletionNote(userId);
            
            // ДОБАВЛЕНИЕ: Обновляем шапку ПОСЛЕ завершения процесса
            eventService.updateMyEventsHeaderAfterRemoval(userId);
            
        } catch (Exception e) {
            log.error("Ошибка при добавлении заметки: eventId={}, userId={}", eventId, userId, e);
            conversationStateService.clearAwaitingCompletionNote(userId);
            telegramMessageService.sendMessage(chatId, "❌ Произошла ошибка при добавлении заметки.");
        }
    }
    
    return; // Прерываем дальнейшую обработку
}
```

## Data Models

Изменений в моделях данных не требуется. Используются существующие:

- `Event` - модель события
- `ConversationState` - состояние диалога
- `CompletionNoteContext` - контекст добавления заметки (вложенный класс в ConversationStateService)

## Correctness Properties

*Свойство (property) - это характеристика или поведение, которое должно выполняться для всех допустимых выполнений системы - по сути, формальное утверждение о том, что система должна делать. Свойства служат мостом между человекочитаемыми спецификациями и машинно-проверяемыми гарантиями корректности.*

### Property 1: Отложенная отправка сообщения о пустом списке при добавлении заметки

*Для любого* завершения последнего события в списке с добавлением заметки, сообщение "У вас пока нет созданных событий" должно быть отправлено только после того, как пользователь завершит ввод заметки (отправит текст заметки).

**Validates: Requirements 1.1, 1.2, 1.3**

### Property 2: Немедленная отправка сообщения о пустом списке при пропуске заметки

*Для любого* завершения последнего события в списке с пропуском заметки (нажатие кнопки "Пропустить"), сообщение "У вас пока нет созданных событий" должно быть отправлено сразу после отображения карточки завершенного события.

**Validates: Requirements 1.4, 3.3**

### Property 3: Отсутствие сообщения о пустом списке при наличии других событий

*Для любого* завершения события, если после завершения в списке остаются другие активные события, сообщение "У вас пока нет созданных событий" НЕ должно быть отправлено.

**Validates: Requirements 1.5**

### Property 4: Последовательность сообщений при добавлении заметки

*Для любого* завершения последнего события с добавлением заметки, сообщения должны отображаться в следующем порядке:
1. Сообщение с предложением добавить заметку
2. Сообщение о завершении события с заметкой
3. Сообщение "У вас пока нет созданных событий"

**Validates: Requirements 3.1**

### Property 5: Проверка состояния диалога при определении пустоты списка

*Для любого* момента проверки пустоты списка событий, система должна учитывать текущее состояние диалога пользователя (находится ли он в процессе ввода заметки о завершении).

**Validates: Requirements 2.1, 2.2**

## Error Handling

### Ошибки при завершении события

- **EventNotFoundException**: Событие не найдено
  - Логирование ошибки
  - Очистка контекста
  - Не отправляем сообщение о пустом списке

- **UnauthorizedAccessException**: Нет прав на завершение события
  - Логирование ошибки
  - Очистка контекста
  - Не отправляем сообщение о пустом списке

- **IllegalStateException**: Событие не в статусе ACTIVE
  - Логирование ошибки
  - Очистка контекста
  - Не отправляем сообщение о пустом списке

### Ошибки при добавлении заметки

- **TelegramApiException**: Ошибка редактирования сообщения
  - Fallback на отправку нового сообщения
  - Продолжение процесса с обновлением шапки

- **Exception**: Общая ошибка при добавлении заметки
  - Логирование ошибки
  - Очистка контекста
  - Отправка сообщения об ошибке пользователю
  - Не отправляем сообщение о пустом списке (так как процесс не завершен корректно)

### Ошибки при пропуске заметки

- **EventNotFoundException**: Событие не найдено
  - Логирование ошибки
  - Очистка контекста
  - Отправка сообщения об ошибке
  - Не отправляем сообщение о пустом списке

- **TelegramApiException**: Ошибка редактирования сообщения
  - Fallback на отправку нового сообщения
  - Продолжение процесса с обновлением шапки

## Testing Strategy

### Unit Tests

1. **EventServiceTest**
   - Тест метода `completeEventWithoutHeaderUpdate()`
   - Проверка, что метод НЕ вызывает `updateMyEventsHeaderAfterRemoval()`
   - Проверка, что событие корректно завершается
   - Проверка, что messageId сохраняется

2. **EventCallbackHandlerTest**
   - Тест `handleCompleteEvent()` с использованием `completeEventWithoutHeaderUpdate()`
   - Проверка, что сообщение о пустом списке НЕ отправляется сразу
   - Тест `handleSkipCompletionNote()` с вызовом `updateMyEventsHeaderAfterRemoval()`
   - Проверка последовательности вызовов методов

3. **UpdateProcessorTest**
   - Тест обработки текстовой заметки о завершении
   - Проверка вызова `updateMyEventsHeaderAfterRemoval()` после добавления заметки
   - Проверка последовательности сообщений

### Integration Tests

1. **EventCompletionEmptyStateTimingIntegrationTest**
   - Тест полного сценария завершения последнего события с заметкой
   - Проверка, что сообщение о пустом списке отправляется ПОСЛЕ ввода заметки
   - Тест полного сценария завершения последнего события с пропуском заметки
   - Проверка, что сообщение о пустом списке отправляется сразу после пропуска
   - Тест завершения события при наличии других событий
   - Проверка, что сообщение о пустом списке НЕ отправляется

### Property-Based Tests

Для данной фичи property-based тесты не требуются, так как все свойства проверяются через интеграционные тесты с конкретными сценариями.

## Implementation Notes

### Ключевые изменения

1. **Новый метод в EventService**: `completeEventWithoutHeaderUpdate()`
   - Копирует логику `completeEventWithoutDeletion()`
   - НЕ вызывает `updateMyEventsHeaderAfterRemoval()`

2. **Изменение в EventCallbackHandler.handleCompleteEvent()**
   - Использует `completeEventWithoutHeaderUpdate()` вместо `completeEventWithoutDeletion()`

3. **Изменение в EventCallbackHandler.handleSkipCompletionNote()**
   - Добавляет вызов `updateMyEventsHeaderAfterRemoval()` ПОСЛЕ отображения карточки события

4. **Изменение в UpdateProcessor.processTextMessage()**
   - Добавляет вызов `updateMyEventsHeaderAfterRemoval()` ПОСЛЕ добавления заметки и отображения финального сообщения

### Обратная совместимость

Изменения полностью обратно совместимы:
- Существующий метод `completeEventWithoutDeletion()` не изменяется
- Добавляется новый метод `completeEventWithoutHeaderUpdate()`
- Изменяется только логика вызова методов в обработчиках

### Производительность

Изменения не влияют на производительность:
- Количество запросов к БД остается прежним
- Количество вызовов Telegram API остается прежним
- Изменяется только порядок вызовов методов
