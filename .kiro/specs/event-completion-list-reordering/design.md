# Проектирование: Переупорядочивание списка при завершении события

## Обзор

Данная функциональность улучшает пользовательский опыт при завершении событий в списке "Мои события". Когда пользователь завершает событие, которое находится не в конце списка, система автоматически переупорядочивает список так, чтобы завершённое событие оказалось внизу, а все активные события отображались выше. Это позволяет пользователю комфортно добавлять заметку о завершении, видя контекст оставшихся активных событий.

## Архитектура

Решение интегрируется в существующую архитектуру обработки завершения событий:

1. **EventCallbackHandler** - обрабатывает callback завершения события
2. **EventService** - содержит бизнес-логику завершения и переупорядочивания
3. **TelegramMessageService** - управляет сообщениями в Telegram
4. **MyEventsCommandHandler** - отвечает за отображение списка событий

### Последовательность операций

```
Пользователь нажимает "Завершить" на событии
    ↓
EventCallbackHandler.handleCompleteEvent()
    ↓
EventService.completeEventWithReordering()
    ├─ Завершает событие (статус → COMPLETED)
    ├─ Проверяет позицию события в списке
    ├─ Если не последнее → переупорядочивает список
    │   ├─ Удаляет все сообщения активных событий
    │   ├─ Отправляет заново в правильном порядке
    │   └─ Сохраняет новые messageId
    └─ Редактирует сообщение завершённого события
    ↓
Отображается список с завершённым событием внизу
```

## Компоненты и интерфейсы

### EventService

#### Новый метод: completeEventWithReordering

```java
/**
 * Завершает событие и переупорядочивает список "Мои события" если необходимо.
 * 
 * <p>Метод выполняет следующие действия:</p>
 * <ol>
 *   <li>Завершает событие (статус → COMPLETED)</li>
 *   <li>Получает список всех активных событий пользователя</li>
 *   <li>Если завершённое событие не последнее в списке:</li>
 *   <ul>
 *     <li>Удаляет все сообщения активных событий из чата</li>
 *     <li>Формирует новый порядок: активные события + завершённое</li>
 *     <li>Отправляет события заново с обновлённой шапкой</li>
 *     <li>Сохраняет новые messageId для всех событий</li>
 *   </ul>
 *   <li>Редактирует сообщение завершённого события с предложением добавить заметку</li>
 * </ol>
 * 
 * @param eventId идентификатор события
 * @param userId идентификатор пользователя
 * @return завершённое событие
 */
@Transactional
public Event completeEventWithReordering(Long eventId, Long userId) {
    // 1. Получаем событие и проверяем права доступа
    Event event = getEventById(eventId);
    validateUserAccess(event, userId);
    
    // 2. Получаем список активных событий ДО завершения
    List<Event> activeEventsBefore = getUserEvents(userId);
    int eventPosition = findEventPosition(activeEventsBefore, eventId);
    boolean isLastEvent = (eventPosition == activeEventsBefore.size() - 1);
    
    // 3. Завершаем событие БЕЗ обновления шапки
    Event completedEvent = completeEventWithoutHeaderUpdate(eventId, userId);
    
    // 4. Если событие не последнее - переупорядочиваем список
    if (!isLastEvent && activeEventsBefore.size() > 1) {
        reorderMyEventsList(userId, completedEvent, activeEventsBefore);
    }
    
    return completedEvent;
}
```

#### Вспомогательный метод: reorderMyEventsList

```java
/**
 * Переупорядочивает список "Мои события" после завершения события.
 * 
 * <p>Алгоритм:</p>
 * <ol>
 *   <li>Получает актуальный список активных событий (без завершённого)</li>
 *   <li>Удаляет все сообщения активных событий из чата</li>
 *   <li>Формирует новый порядок отображения</li>
 *   <li>Отправляет события заново с обновлённой шапкой</li>
 *   <li>Сохраняет новые messageId</li>
 * </ol>
 * 
 * @param userId идентификатор пользователя
 * @param completedEvent завершённое событие
 * @param previousActiveEvents список активных событий до завершения
 */
private void reorderMyEventsList(Long userId, Event completedEvent, 
                                 List<Event> previousActiveEvents) {
    // 1. Получаем актуальный список активных событий (без завершённого)
    List<Event> currentActiveEvents = getUserEvents(userId);
    
    // 2. Удаляем все сообщения активных событий
    deleteActiveEventMessages(currentActiveEvents, userId);
    
    // 3. Отправляем события заново в правильном порядке
    resendMyEventsWithHeader(userId, currentActiveEvents, completedEvent);
}
```

#### Вспомогательный метод: deleteActiveEventMessages

```java
/**
 * Удаляет сообщения активных событий из чата.
 * 
 * @param events список событий для удаления сообщений
 * @param userId идентификатор пользователя
 */
private void deleteActiveEventMessages(List<Event> events, Long userId) {
    User user = userRepository.findById(userId)
        .orElseThrow(() -> new UserNotFoundException(userId));
    Long chatId = user.getTelegramId();
    
    for (Event event : events) {
        if (event.getMessageId() != null) {
            try {
                telegramMessageService.deleteMessageSilently(
                    chatId, 
                    event.getMessageId().intValue()
                );
                event.setMessageId(null);
                eventRepository.save(event);
            } catch (Exception e) {
                log.warn("Не удалось удалить сообщение события ID={}: {}", 
                        event.getId(), e.getMessage());
            }
        }
    }
}
```

#### Вспомогательный метод: resendMyEventsWithHeader

```java
/**
 * Отправляет список событий заново с обновлённой шапкой.
 * 
 * <p>Порядок отправки:</p>
 * <ol>
 *   <li>Шапка + первое активное событие (если есть активные)</li>
 *   <li>Остальные активные события</li>
 *   <li>Завершённое событие с предложением добавить заметку</li>
 * </ol>
 * 
 * @param userId идентификатор пользователя
 * @param activeEvents список активных событий
 * @param completedEvent завершённое событие
 */
private void resendMyEventsWithHeader(Long userId, List<Event> activeEvents, 
                                      Event completedEvent) {
    User user = userRepository.findById(userId)
        .orElseThrow(() -> new UserNotFoundException(userId));
    Long chatId = user.getTelegramId();
    
    // Формируем шапку с учётом активных событий
    int totalCount = activeEvents.size() + 1; // активные + завершённое
    String header = botMessageBuilder.buildMyEventsHeader(totalCount);
    
    if (!activeEvents.isEmpty()) {
        // Отправляем шапку + первое активное событие
        Event firstEvent = activeEvents.get(0);
        sendEventWithHeader(chatId, header, firstEvent, userId);
        
        // Отправляем остальные активные события
        for (int i = 1; i < activeEvents.size(); i++) {
            sendEvent(chatId, activeEvents.get(i), userId);
        }
    }
    
    // Отправляем завершённое событие с предложением добавить заметку
    sendCompletedEventWithNote(chatId, completedEvent, userId);
}
```

### EventCallbackHandler

#### Изменение метода: handleCompleteEvent

```java
/**
 * Обрабатывает завершение события с переупорядочиванием списка.
 * 
 * <p>Изменения:</p>
 * <ul>
 *   <li>Вызывает completeEventWithReordering вместо completeEventWithoutHeaderUpdate</li>
 *   <li>Не редактирует текущее сообщение (оно будет пересоздано)</li>
 *   <li>Сохраняет контекст для добавления заметки</li>
 * </ul>
 */
private void handleCompleteEvent(String callbackData, Long userId, Long chatId, 
                                 Integer messageId, String callbackQueryId) {
    Long eventId = extractEventId(callbackData, CallbackPrefix.COMPLETE_EVENT);
    
    try {
        // Завершаем событие с переупорядочиванием
        Event completedEvent = eventService.completeEventWithReordering(eventId, userId);
        
        // Сохраняем контекст для добавления заметки
        // messageId будет обновлён после переупорядочивания
        conversationStateService.setAwaitingCompletionNote(
            userId, 
            eventId, 
            chatId, 
            completedEvent.getMessageId() != null ? completedEvent.getMessageId().intValue() : null
        );
        
        // Отвечаем на callback query
        messageService.answerCallbackQuery(callbackQueryId, "");
        
    } catch (Exception e) {
        log.error("Ошибка при завершении события: eventId={}, userId={}", 
                 eventId, userId, e);
    }
}
```

## Модели данных

Используются существующие модели без изменений:

- **Event** - модель события с полями messageId, status, isMyEventsHeader
- **User** - модель пользователя с telegramId
- **ConversationState** - состояние диалога для добавления заметки

## Свойства корректности

*Свойство - это характеристика или поведение, которое должно выполняться во всех допустимых выполнениях системы - по сути, формальное утверждение о том, что система должна делать. Свойства служат мостом между человекочитаемыми спецификациями и машинно-проверяемыми гарантиями корректности.*

### Свойство 1: Переупорядочивание при завершении не последнего события

*Для любого* списка событий пользователя, если завершается событие, которое не является последним в списке, то после завершения все активные события должны отображаться выше завершённого события.

**Validates: Requirements 1.1**

### Свойство 2: Сохранение порядка активных событий

*Для любого* списка активных событий, после завершения любого события и переупорядочивания, относительный порядок активных событий должен остаться неизменным.

**Validates: Requirements 1.4**

### Свойство 3: Обновление messageId после переупорядочивания

*Для любого* события в переупорядоченном списке, его messageId должен соответствовать актуальному идентификатору сообщения в Telegram.

**Validates: Requirements 4.2**

### Свойство 4: Корректность счётчика в шапке

*Для любого* списка событий после переупорядочивания, счётчик в шапке "Мои события" должен равняться сумме активных и завершённого события.

**Validates: Requirements 1.5**

### Свойство 5: Сохранение данных события при переупорядочивании

*Для любого* события, после переупорядочивания все его данные (название, описание, дата, вложения, комментарии, чек-листы) должны остаться неизменными.

**Validates: Requirements 4.4**

### Свойство 6: Завершение последнего события не вызывает переупорядочивание

*Для любого* списка событий, если завершается последнее событие, то переупорядочивание списка не должно происходить.

**Validates: Requirements 1.3**

## Обработка ошибок

### Ошибки удаления сообщений

Если не удаётся удалить сообщение события из чата:
- Логируется предупреждение
- Процесс продолжается для остальных событий
- messageId сбрасывается в базе данных

### Ошибки отправки сообщений

Если не удаётся отправить событие заново:
- Логируется ошибка
- Используется fallback без форматирования MarkdownV2
- Процесс продолжается для остальных событий

### Ошибки доступа к Telegram API

При ошибках Telegram API (сеть, rate limiting):
- Логируется ошибка с деталями
- Транзакция откатывается
- Пользователю отправляется сообщение об ошибке

## Стратегия тестирования

### Unit-тесты

1. **EventServiceTest.testCompleteEventWithReordering()**
   - Проверка завершения события не в конце списка
   - Проверка вызова переупорядочивания
   - Проверка сохранения данных события

2. **EventServiceTest.testCompleteLastEventNoReordering()**
   - Проверка завершения последнего события
   - Проверка отсутствия переупорядочивания

3. **EventServiceTest.testReorderMyEventsList()**
   - Проверка удаления сообщений активных событий
   - Проверка отправки событий в правильном порядке
   - Проверка обновления messageId

4. **EventServiceTest.testReorderingPreservesEventData()**
   - Проверка сохранения всех полей события
   - Проверка сохранения связанных данных (вложения, комментарии)

### Property-Based тесты

1. **EventServicePropertyTest.testReorderingPreservesActiveEventsOrder()**
   - **Свойство 2: Сохранение порядка активных событий**
   - **Validates: Requirements 1.4**
   - Генерация случайных списков событий
   - Завершение случайного события (не последнего)
   - Проверка сохранения относительного порядка активных событий

2. **EventServicePropertyTest.testReorderingUpdatesMessageIds()**
   - **Свойство 3: Обновление messageId после переупорядочивания**
   - **Validates: Requirements 4.2**
   - Генерация случайных списков событий
   - Завершение случайного события
   - Проверка, что все messageId обновлены и не null

3. **EventServicePropertyTest.testReorderingPreservesEventData()**
   - **Свойство 5: Сохранение данных события**
   - **Validates: Requirements 4.4**
   - Генерация случайных событий с различными данными
   - Завершение события с переупорядочиванием
   - Проверка неизменности всех полей события

### Integration тесты

1. **EventCompletionReorderingIntegrationTest**
   - Полный цикл завершения события с переупорядочиванием
   - Проверка взаимодействия всех компонентов
   - Проверка корректности отображения в Telegram

## Примечания по реализации

### Производительность

- Переупорядочивание выполняется только при необходимости (событие не последнее)
- Удаление и отправка сообщений выполняются последовательно для избежания race conditions
- Используется транзакционность для обеспечения целостности данных

### Обратная совместимость

- Существующие методы завершения событий не изменяются
- Добавляется новый метод completeEventWithReordering
- EventCallbackHandler обновляется для использования нового метода
- Все существующие тесты должны продолжать работать

### Ограничения

- Переупорядочивание работает только для списка "Мои события"
- Не применяется к другим спискам (Today, Week, Upcoming)
- Требует наличия messageId у всех событий в списке
