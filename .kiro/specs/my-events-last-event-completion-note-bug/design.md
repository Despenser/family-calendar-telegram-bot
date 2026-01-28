# Design Document

## Обзор

Проблема заключается в том, что метод `completeEventWithReordering` в `EventService` вызывает `updateMyEventsHeaderAfterRemoval` сразу после отправки сообщения с предложением добавить заметку для последнего события. На этот момент событие уже завершено (статус COMPLETED), поэтому `getUserEvents` возвращает пустой список активных событий, и система отправляет сообщение "У вас пока нет созданных событий" **до того**, как пользователь выбрал "Добавить заметку" или "Пропустить".

В результате:
1. Пользователь видит сообщение о пустом состоянии преждевременно
2. Пользовательский опыт нарушен - сообщение появляется в неправильный момент
3. Логика обновления шапки вызывается в неподходящее время

Решение: **НЕ вызывать** `updateMyEventsHeaderAfterRemoval` для последнего события в методе `completeEventWithReordering`. Вместо этого, обновление шапки должно происходить **после** того, как пользователь добавит заметку или пропустит её (в `UpdateProcessor` и `EventCallbackHandler`).

## Архитектура

Текущая архитектура (с багом):

```
EventCallbackHandler.handleCompleteEvent()
  └─> EventService.completeEventWithReordering()
      ├─> completeEventWithoutHeaderUpdate()
      ├─> if (!isLastEvent && activeEventsBefore.size() > 1)
      │   └─> reorderMyEventsList()
      │       └─> resendMyEventsWithHeader()
      │           └─> sendCompletedEventWithNote()
      └─> else
          ├─> sendCompletedEventWithNote() // Отправляет сообщение с кнопками
          └─> updateMyEventsHeaderAfterRemoval() // ❌ БАГ: Отправляет "У вас пока нет событий" СРАЗУ!
```

Исправленная архитектура:

```
EventCallbackHandler.handleCompleteEvent()
  └─> EventService.completeEventWithReordering()
      ├─> completeEventWithoutHeaderUpdate()
      ├─> if (!isLastEvent && activeEventsBefore.size() > 1)
      │   └─> reorderMyEventsList()
      │       └─> resendMyEventsWithHeader()
      │           └─> sendCompletedEventWithNote()
      └─> else
          └─> sendCompletedEventWithNote() // Отправляет сообщение с кнопками
          // ✅ НЕ вызываем updateMyEventsHeaderAfterRemoval здесь!

// Обновление шапки происходит ПОСЛЕ выбора пользователя:

UpdateProcessor.handleCompletionNoteInput()
  ├─> eventService.addCompletionNote()
  └─> eventService.updateMyEventsHeaderAfterRemoval() // ✅ Вызывается ПОСЛЕ добавления заметки

EventCallbackHandler.handleSkipCompletionNote()
  └─> eventService.updateMyEventsHeaderAfterRemoval() // ✅ Вызывается ПОСЛЕ пропуска
```

## Компоненты и интерфейсы

### EventService

Изменяемый метод `completeEventWithReordering`:

```java
public Event completeEventWithReordering(Long eventId, Long userId) {
    // ... существующий код ...
    
    // 6. Если событие не последнее и есть другие события - переупорядочиваем список
    if (!isLastEvent && activeEventsBefore.size() > 1) {
        reorderMyEventsList(userId, completedEvent, activeEventsBefore);
    } else {
        // Отправляем сообщение с предложением добавить заметку
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new UserNotFoundException(userId));
        Long chatId = user.getTelegramId();
        
        if (chatId != null) {
            sendCompletedEventWithNote(chatId, completedEvent, userId);
        }
        
        // ❌ УДАЛЯЕМ вызов updateMyEventsHeaderAfterRemoval отсюда!
        // Обновление шапки будет выполнено после выбора пользователя
    }
    
    return completedEvent;
}
```

### UpdateProcessor

Метод `handleCompletionNoteInput` уже вызывает `updateMyEventsHeaderAfterRemoval` (строка 1116):

```java
private void handleCompletionNoteInput(Message message, Long userId, Long eventId, 
                                      Long chatId, Integer messageId) {
    // ... существующий код добавления заметки ...
    
    // Обновляем шапку /my_events после добавления заметки
    eventService.updateMyEventsHeaderAfterRemoval(userId);
    
    // ... остальной код ...
}
```

### EventCallbackHandler

Метод `handleSkipCompletionNote` уже вызывает `updateMyEventsHeaderAfterRemoval` (строка 1033):

```java
private void handleSkipCompletionNote(String callbackData, Long userId, Long chatId, 
                                     Integer messageId, String callbackQueryId) {
    // ... существующий код пропуска заметки ...
    
    // Обновляем шапку /my_events после пропуска заметки
    eventService.updateMyEventsHeaderAfterRemoval(userId);
    
    // ... остальной код ...
}
```

## Модели данных

Используются существующие модели:
- `Event` - модель события с полями `messageId`, `completionNote`, `status`
- `ConversationStateService.CompletionNoteContext` - контекст ожидания заметки

## Свойства корректности

*Свойство - это характеристика или поведение, которое должно выполняться во всех допустимых выполнениях системы - по сути, формальное утверждение о том, что система должна делать. Свойства служат мостом между человекочитаемыми спецификациями и машинно-проверяемыми гарантиями корректности.*

### Property 1: Завершение последнего события НЕ вызывает updateMyEventsHeaderAfterRemoval

*Для любого* пользователя и последнего события в списке "Мои события", когда пользователь завершает это событие через `completeEventWithReordering`, метод `updateMyEventsHeaderAfterRemoval` НЕ должен быть вызван внутри `completeEventWithReordering`.

**Validates: Requirements 1.4, 3.1**

### Property 2: Завершение не последнего события вызывает updateMyEventsHeaderAfterRemoval через reorderMyEventsList

*Для любого* пользователя и не последнего события в списке "Мои события", когда пользователь завершает это событие через `completeEventWithReordering`, метод `updateMyEventsHeaderAfterRemoval` должен быть вызван через `reorderMyEventsList`.

**Validates: Requirements 3.2**

### Property 3: Добавление заметки вызывает updateMyEventsHeaderAfterRemoval

*Для любого* завершенного события, после добавления заметки через `UpdateProcessor.handleCompletionNoteInput`, метод `updateMyEventsHeaderAfterRemoval` должен быть вызван.

**Validates: Requirements 2.1, 3.3**

### Property 4: Пропуск заметки вызывает updateMyEventsHeaderAfterRemoval

*Для любого* завершенного события, после пропуска заметки через `EventCallbackHandler.handleSkipCompletionNote`, метод `updateMyEventsHeaderAfterRemoval` должен быть вызван.

**Validates: Requirements 2.2, 3.4**

### Property 5: Сообщение о пустом состоянии отправляется только после выбора пользователя

*Для любого* пользователя, завершающего последнее событие, сообщение "У вас пока нет созданных событий" должно быть отправлено ТОЛЬКО после того, как пользователь добавил заметку или пропустил её, но НЕ сразу после завершения события.

**Validates: Requirements 2.3, 2.4**

## Обработка ошибок

1. **Пользователь не найден**: Если пользователь не найден при отправке сообщения, выбрасывается `UserNotFoundException`
2. **chatId отсутствует**: Если у пользователя нет chatId, сообщение не отправляется, но процесс завершения продолжается
3. **Ошибка отправки сообщения**: Логируется как error, но не прерывает процесс завершения события
4. **Событие не найдено**: При попытке добавить заметку к несуществующему событию выбрасывается `EventNotFoundException`

## Стратегия тестирования

### Unit тесты

1. **Тест завершения последнего события**:
   - Создать пользователя с одним активным событием
   - Завершить это событие через `completeEventWithReordering`
   - Проверить, что `sendCompletedEventWithNote` был вызван
   - Проверить, что messageId сохранен в событии
   - Проверить, что `updateMyEventsHeaderAfterRemoval` был вызван

2. **Тест завершения не последнего события**:
   - Создать пользователя с несколькими активными событиями
   - Завершить первое событие через `completeEventWithReordering`
   - Проверить, что `reorderMyEventsList` был вызван
   - Проверить, что messageId сохранен в событии

3. **Тест установки контекста с messageId**:
   - Завершить событие
   - Проверить, что контекст ожидания заметки установлен с корректным messageId
   - Проверить, что messageId соответствует messageId из события

### Property-based тесты

Минимум 100 итераций для каждого теста.

1. **Property тест: Завершение любого события устанавливает контекст**:
   - Генерировать случайное количество событий (1-10)
   - Выбирать случайное событие для завершения
   - Проверять, что после завершения контекст установлен с непустым messageId
   - **Feature: my-events-last-event-completion-note-bug, Property 3: Контекст ожидания заметки устанавливается с корректным messageId**

2. **Property тест: Очистка контекста после обработки заметки**:
   - Генерировать случайное событие и завершать его
   - Устанавливать контекст ожидания заметки
   - Добавлять заметку или пропускать (случайный выбор)
   - Проверять, что контекст очищен
   - **Feature: my-events-last-event-completion-note-bug, Property 4: Очистка контекста после добавления заметки**

### Integration тесты

1. **Интеграционный тест полного цикла завершения последнего события**:
   - Создать пользователя и событие
   - Завершить событие через callback
   - Проверить, что сообщение отправлено с кнопками
   - Проверить, что контекст установлен
   - Добавить заметку
   - Проверить, что контекст очищен
   - Отправить команду и проверить, что она обработана как команда, а не как заметка

## Примечания

- Изменение минимально и затрагивает только один метод в `EventService`
- Логика отправки сообщения уже существует в `sendCompletedEventWithNote`
- Не требуется изменений в `EventCallbackHandler`, так как он уже корректно использует messageId из возвращенного события
- Решение обеспечивает единообразие обработки последнего и не последнего события
