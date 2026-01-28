# Design Document

## Обзор

Текущая реализация метода `completeEventWithReordering` в `EventService` отправляет **новое сообщение** при завершении последнего события в списке "Мои события". Это создаёт лишние сообщения в чате и нарушает единообразие пользовательского опыта.

Проблема:
- При завершении **последнего события**: вызывается `sendCompletedEventWithNote`, который отправляет НОВОЕ сообщение
- При завершении **не последнего события**: вызывается `reorderMyEventsList`, который удаляет и пересоздаёт все сообщения

Решение: изменить логику для последнего события так, чтобы существующее сообщение **редактировалось** вместо создания нового. Это обеспечит:
1. Единообразие пользовательского опыта
2. Отсутствие лишних сообщений в чате
3. Сохранение messageId для последующих операций

## Архитектура

Текущая архитектура (с проблемой):

```
EventCallbackHandler.handleCompleteEvent()
  └─> EventService.completeEventWithReordering()
      ├─> completeEventWithoutHeaderUpdate()
      ├─> if (!isLastEvent && activeEventsBefore.size() > 1)
      │   └─> reorderMyEventsList()
      │       └─> resendMyEventsWithHeader()
      │           └─> sendCompletedEventWithNote()
      └─> else
          └─> sendCompletedEventWithNote() // ❌ ПРОБЛЕМА: Отправляет НОВОЕ сообщение!
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
          └─> editCompletedEventWithNote() // ✅ РЕШЕНИЕ: Редактирует существующее сообщение!
              ├─> if (messageId exists)
              │   └─> tryEditMessageText() // Пытается отредактировать
              │       ├─> success → сохраняет messageId
              │       └─> failure → fallback to sendCompletedEventWithNote()
              └─> else
                  └─> sendCompletedEventWithNote() // Fallback для событий без messageId
```

## Компоненты и интерфейсы

### EventService

Новый приватный метод `editCompletedEventWithNote`:

```java
/**
 * Редактирует сообщение последнего события, показывая статус завершения и кнопки для добавления заметки.
 * 
 * <p>Метод пытается отредактировать существующее сообщение события. Если редактирование не удаётся
 * (сообщение удалено, messageId отсутствует), отправляет новое сообщение как fallback.</p>
 * 
 * <p><b>Алгоритм:</b></p>
 * <ol>
 *   <li>Проверяет наличие messageId у события</li>
 *   <li>Формирует текст завершённого события</li>
 *   <li>Создаёт клавиатуру с кнопками для добавления заметки</li>
 *   <li>Пытается отредактировать сообщение через tryEditMessageText</li>
 *   <li>При успехе - сохраняет messageId (он уже есть)</li>
 *   <li>При неудаче - отправляет новое сообщение через sendCompletedEventWithNote</li>
 * </ol>
 * 
 * <p><b>Требования:</b> 1.1, 1.2, 1.3, 3.1, 3.2, 3.3, 4.1, 4.2, 4.3, 4.4</p>
 * 
 * @param chatId идентификатор чата пользователя
 * @param event завершённое событие
 * @param userId идентификатор пользователя
 */
private void editCompletedEventWithNote(Long chatId, Event event, Long userId) {
    log.debug("Редактирование сообщения последнего завершённого события ID={} для пользователя ID={}", 
             event.getId(), userId);
    
    // Проверяем наличие messageId
    if (event.getMessageId() == null) {
        log.info("У события ID={} отсутствует messageId, отправляем новое сообщение", event.getId());
        sendCompletedEventWithNote(chatId, event, userId);
        return;
    }
    
    try {
        // Формируем сообщение о завершённом событии
        String completedMessage = botMessageBuilder.buildCompletedEventMessage(event);
        
        // Создаем клавиатуру с кнопками для добавления заметки
        InlineKeyboardMarkup keyboard = keyboardService.createCompletionNoteKeyboard(event.getId());
        
        // Пытаемся отредактировать существующее сообщение
        boolean edited = telegramMessageService.tryEditMessageText(
            chatId, 
            event.getMessageId().intValue(), 
            completedMessage, 
            keyboard
        );
        
        if (edited) {
            log.info("Сообщение последнего завершённого события ID={} успешно отредактировано, messageId={}", 
                    event.getId(), event.getMessageId());
        } else {
            // Сообщение не найдено или удалено - отправляем новое
            log.info("Не удалось отредактировать сообщение события ID={} (удалено или не найдено), отправляем новое", 
                    event.getId());
            sendCompletedEventWithNote(chatId, event, userId);
        }
        
    } catch (Exception e) {
        // При любой ошибке отправляем новое сообщение
        log.error("Ошибка при редактировании сообщения последнего завершённого события ID={}: {}, отправляем новое сообщение", 
                 event.getId(), e.getMessage(), e);
        sendCompletedEventWithNote(chatId, event, userId);
    }
}
```

Изменяемый метод `completeEventWithReordering`:

```java
public Event completeEventWithReordering(Long eventId, Long userId) {
    // ... существующий код до строки 945 ...
    
    // 6. Если событие не последнее и есть другие события - переупорядочиваем список
    if (!isLastEvent && activeEventsBefore.size() > 1) {
        log.info("Событие ID={} не является последним (позиция {} из {}), начинаем переупорядочивание", 
                eventId, eventPosition, activeEventsBefore.size());
        reorderMyEventsList(userId, completedEvent, activeEventsBefore);
        log.info("Переупорядочивание списка завершено для пользователя ID={}", userId);
    } else {
        if (isLastEvent) {
            log.info("Событие ID={} является последним в списке, редактируем сообщение", 
                    eventId);
        } else if (activeEventsBefore.size() <= 1) {
            log.info("В списке только одно событие, редактируем сообщение");
        }
        
        // Получаем пользователя
        User user = userRepository.findById(userId)
            .orElseThrow(() -> {
                log.error("Пользователь с ID={} не найден при редактировании сообщения последнего события", userId);
                return new UserNotFoundException(userId);
            });
        
        Long chatId = user.getTelegramId();
        
        if (chatId != null) {
            log.debug("Редактирование сообщения с предложением добавить заметку для последнего события ID={}", eventId);
            // ✅ ИЗМЕНЕНИЕ: Используем editCompletedEventWithNote вместо sendCompletedEventWithNote
            editCompletedEventWithNote(chatId, completedEvent, userId);
            log.info("Сообщение последнего события ID={} обработано (отредактировано или отправлено новое)", eventId);
        } else {
            log.warn("Не удалось получить chatId для пользователя ID={}, сообщение не отправлено", userId);
        }
        
        // НЕ вызываем updateMyEventsHeaderAfterRemoval здесь для последнего события!
        // (комментарий остаётся без изменений)
        log.debug("Обновление шапки /my_events отложено до выбора пользователя (добавить заметку или пропустить)");
    }
    
    log.info("Завершение события ID={} с переупорядочиванием успешно выполнено для пользователя ID={}", 
            eventId, userId);
    
    return completedEvent;
}
```

### TelegramMessageService

Используется существующий метод `tryEditMessageText`:

```java
/**
 * Редактирует текст существующего сообщения с обработкой ошибок удалённых сообщений.
 * 
 * @param chatId ID чата, где находится сообщение
 * @param messageId ID сообщения для редактирования
 * @param newText новый текст сообщения (поддерживает MarkdownV2)
 * @param replyMarkup новая inline клавиатура
 * @return true если редактирование успешно, false если сообщение не найдено/удалено/старое
 * @throws TelegramApiException при других ошибках (сетевые, парсинга и т.д.)
 */
public boolean tryEditMessageText(Long chatId, Integer messageId, String newText, 
                                  InlineKeyboardMarkup replyMarkup) throws TelegramApiException
```

### BotMessageBuilder

Используется существующий метод `buildCompletedEventMessage`:

```java
/**
 * Формирует сообщение о завершённом событии.
 * 
 * @param event завершённое событие
 * @return отформатированное сообщение
 */
public String buildCompletedEventMessage(Event event)
```

### KeyboardService

Используется существующий метод `createCompletionNoteKeyboard`:

```java
/**
 * Создаёт клавиатуру с кнопками для добавления заметки к завершённому событию.
 * 
 * @param eventId идентификатор события
 * @return inline клавиатура с кнопками "📝 Добавить заметку" и "⏭️ Пропустить"
 */
public InlineKeyboardMarkup createCompletionNoteKeyboard(Long eventId)
```

## Модели данных

Используются существующие модели:
- `Event` - модель события с полями `messageId`, `completionNote`, `status`
- `Event.messageId` - Long, идентификатор сообщения в Telegram

Изменений в моделях данных не требуется.

## Свойства корректности

*Свойство - это характеристика или поведение, которое должно выполняться во всех допустимых выполнениях системы - по сути, формальное утверждение о том, что система должна делать. Свойства служат мостом между человекочитаемыми спецификациями и машинно-проверяемыми гарантиями корректности.*

### Property 1: Редактирование сообщения для последнего события с messageId

*Для любого* пользователя и последнего события в списке "Мои события", если у события есть messageId, то при завершении события система должна отредактировать существующее сообщение, а не создавать новое.

**Validates: Requirements 1.1, 1.2, 1.3, 3.1, 3.2**

### Property 2: Fallback на отправку нового сообщения при отсутствии messageId

*Для любого* пользователя и последнего события в списке "Мои события", если у события отсутствует messageId, то при завершении события система должна отправить новое сообщение.

**Validates: Requirements 3.3, 4.1**

### Property 3: Fallback на отправку нового сообщения при ошибке редактирования

*Для любого* пользователя и последнего события в списке "Мои события", если редактирование сообщения завершилось ошибкой (сообщение удалено, API ошибка), то система должна отправить новое сообщение.

**Validates: Requirements 4.2, 4.3, 4.4**

### Property 4: Сохранение messageId после редактирования

*Для любого* успешно отредактированного сообщения последнего события, messageId в Event.messageId должен остаться неизменным.

**Validates: Requirements 1.3, 3.2**

### Property 5: Единообразие формата сообщения

*Для любого* завершённого события (последнего или не последнего), формат отображаемого сообщения должен быть одинаковым (статус завершения + кнопки для добавления заметки).

**Validates: Requirements 2.2, 2.3**

### Property 6: Сохранение логики переупорядочивания для не последнего события

*Для любого* пользователя и не последнего события в списке "Мои события", при завершении события система должна использовать существующую логику переупорядочивания (удаление и пересоздание сообщений).

**Validates: Requirements 1.5**

## Обработка ошибок

1. **messageId отсутствует**: Если у события нет messageId, система отправляет новое сообщение через `sendCompletedEventWithNote`
2. **Сообщение удалено пользователем**: `tryEditMessageText` возвращает false, система отправляет новое сообщение
3. **Сообщение слишком старое**: `tryEditMessageText` возвращает false, система отправляет новое сообщение
4. **Ошибка Telegram API**: Перехватывается в catch блоке, логируется, система отправляет новое сообщение
5. **Пользователь не найден**: Выбрасывается `UserNotFoundException` (существующее поведение)
6. **chatId отсутствует**: Логируется warning, сообщение не отправляется (существующее поведение)

Все ошибки обрабатываются gracefully с fallback на отправку нового сообщения, что обеспечивает надёжность работы системы.

## Стратегия тестирования

### Unit тесты

1. **Тест редактирования сообщения последнего события с messageId**:
   - Создать пользователя с одним активным событием, у которого есть messageId
   - Замокать `tryEditMessageText` для возврата true
   - Завершить событие через `completeEventWithReordering`
   - Проверить, что `tryEditMessageText` был вызван с правильными параметрами
   - Проверить, что `sendCompletedEventWithNote` НЕ был вызван
   - Проверить, что messageId события не изменился

2. **Тест fallback на отправку нового сообщения при отсутствии messageId**:
   - Создать пользователя с одним активным событием без messageId
   - Завершить событие через `completeEventWithReordering`
   - Проверить, что `tryEditMessageText` НЕ был вызван
   - Проверить, что `sendCompletedEventWithNote` был вызван
   - Проверить, что messageId события был установлен

3. **Тест fallback на отправку нового сообщения при ошибке редактирования**:
   - Создать пользователя с одним активным событием с messageId
   - Замокать `tryEditMessageText` для возврата false (сообщение удалено)
   - Завершить событие через `completeEventWithReordering`
   - Проверить, что `tryEditMessageText` был вызван
   - Проверить, что `sendCompletedEventWithNote` был вызван как fallback
   - Проверить, что новый messageId был установлен

4. **Тест сохранения логики переупорядочивания для не последнего события**:
   - Создать пользователя с несколькими активными событиями
   - Завершить первое событие через `completeEventWithReordering`
   - Проверить, что `reorderMyEventsList` был вызван
   - Проверить, что `editCompletedEventWithNote` НЕ был вызван

### Property-based тесты

Минимум 100 итераций для каждого теста.

1. **Property тест: Редактирование для последнего события с messageId**:
   - Генерировать случайное количество событий (1-10)
   - Для последнего события устанавливать случайный messageId
   - Завершать последнее событие
   - Проверять, что `tryEditMessageText` был вызван
   - Проверять, что messageId не изменился
   - **Feature: last-event-completion-message-editing, Property 1: Редактирование сообщения для последнего события с messageId**

2. **Property тест: Fallback при отсутствии messageId**:
   - Генерировать случайное количество событий (1-10)
   - Для последнего события НЕ устанавливать messageId (null)
   - Завершать последнее событие
   - Проверять, что `sendCompletedEventWithNote` был вызван
   - Проверять, что новый messageId был установлен
   - **Feature: last-event-completion-message-editing, Property 2: Fallback на отправку нового сообщения при отсутствии messageId**

3. **Property тест: Единообразие формата сообщения**:
   - Генерировать случайные события (последние и не последние)
   - Завершать события
   - Проверять, что формат сообщения одинаковый (используется `buildCompletedEventMessage`)
   - Проверять, что клавиатура одинаковая (используется `createCompletionNoteKeyboard`)
   - **Feature: last-event-completion-message-editing, Property 5: Единообразие формата сообщения**

### Integration тесты

1. **Интеграционный тест полного цикла редактирования последнего события**:
   - Создать пользователя и событие с messageId
   - Завершить событие через callback
   - Проверить, что сообщение было отредактировано (не создано новое)
   - Проверить, что messageId остался прежним
   - Добавить заметку
   - Проверить, что сообщение снова отредактировано с заметкой

2. **Интеграционный тест fallback при удалённом сообщении**:
   - Создать пользователя и событие с messageId
   - Симулировать удаление сообщения пользователем
   - Завершить событие через callback
   - Проверить, что было отправлено новое сообщение
   - Проверить, что новый messageId был сохранён

## Примечания

- Изменение минимально и затрагивает только один метод в `EventService` (добавление нового приватного метода и изменение одной строки в `completeEventWithReordering`)
- Все существующие методы (`tryEditMessageText`, `buildCompletedEventMessage`, `createCompletionNoteKeyboard`, `sendCompletedEventWithNote`) используются без изменений
- Решение обеспечивает graceful degradation: при любой ошибке система отправляет новое сообщение
- Логика переупорядочивания для не последнего события остаётся без изменений
- Решение улучшает пользовательский опыт, уменьшая количество сообщений в чате

