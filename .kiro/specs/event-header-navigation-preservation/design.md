# Design Document: Event Header Navigation Preservation

## Overview

Данный документ описывает техническое решение для исправления бага с потерей шапки события при навигации между событием и его вложениями.

### Проблема

При открытии списка "Мои события" первое событие отображается с шапкой:
```
📋 Мои события

Всего событий: 3

[Информация о событии]
```

Когда пользователь нажимает кнопку "Вложения" и затем возвращается обратно к событию, шапка теряется и отображается только информация о событии без заголовка.

### Причина

Метод `AttachmentCallbackHandler.handleBackToEvent()` не сохраняет и не восстанавливает контекст шапки события. При возврате из вложений он всегда использует `buildEventMessage()` вместо `buildEventMessageWithHeader()`, что приводит к потере шапки.

### Решение

Использовать существующий механизм `ConversationStateService` для сохранения контекста шапки при переходе к вложениям и восстановления его при возврате к событию. Контекст уже сохраняется в `MyEventsCommandHandler`, но не используется в `AttachmentCallbackHandler`.

## Architecture

### Компоненты системы

```
┌─────────────────────────────────────────────────────────────┐
│                    MyEventsCommandHandler                    │
│  - Отображает список "Мои события"                          │
│  - Сохраняет контекст шапки через                           │
│    conversationStateService.saveEventHeaderContext()         │
└─────────────────────────────────────────────────────────────┘
                              │
                              │ Сохраняет контекст
                              ▼
┌─────────────────────────────────────────────────────────────┐
│                  ConversationStateService                    │
│  - saveEventHeaderContext(userId, hasHeader, count)          │
│  - getEventHeaderContext(userId) → EventHeaderContext        │
│  - clearEventHeaderContext(userId)                           │
└─────────────────────────────────────────────────────────────┘
                              │
                              │ Хранит в БД
                              ▼
┌─────────────────────────────────────────────────────────────┐
│                     ConversationState                        │
│  - eventHasMyEventsHeader: Boolean                           │
│  - eventCountForHeader: Integer                              │
└─────────────────────────────────────────────────────────────┘
                              │
                              │ Читает контекст
                              ▼
┌─────────────────────────────────────────────────────────────┐
│                 AttachmentCallbackHandler                    │
│  - handleBackToEvent():                                      │
│    1. Получает контекст через getEventHeaderContext()       │
│    2. Если контекст существует и hasMyEventsHeader=true:    │
│       - Использует buildEventMessageWithHeader()            │
│    3. Иначе:                                                 │
│       - Использует buildEventMessage()                       │
└─────────────────────────────────────────────────────────────┘
```

### Поток данных

**Сценарий 1: Открытие "Мои события" и переход к вложениям**

```
1. Пользователь → /my_events
2. MyEventsCommandHandler:
   - Получает список событий
   - Отправляет первое событие с шапкой
   - Вызывает saveEventHeaderContext(userId, true, eventCount)
3. ConversationStateService:
   - Сохраняет в БД: eventHasMyEventsHeader=true, eventCountForHeader=3
4. Пользователь → Нажимает "Вложения"
5. AttachmentCallbackHandler.handleAttachmentList():
   - Отображает список вложений
   - Контекст шапки остается в БД
```

**Сценарий 2: Возврат из вложений к событию**

```
1. Пользователь → Нажимает "Назад к событию"
2. AttachmentCallbackHandler.handleBackToEvent():
   - Вызывает getEventHeaderContext(userId)
   - Получает: EventHeaderContext(hasMyEventsHeader=true, eventCount=3)
   - Вызывает buildEventMessageWithHeader(event, 3)
   - Отображает событие с шапкой
   - Очищает attachment context
```

**Сценарий 3: Прямое открытие события (не из "Мои события")**

```
1. Пользователь → Открывает событие напрямую
2. AttachmentCallbackHandler.handleBackToEvent():
   - Вызывает getEventHeaderContext(userId)
   - Получает: null (контекст не сохранен)
   - Вызывает buildEventMessage(event)
   - Отображает событие без шапки
```

## Components and Interfaces

### ConversationStateService (уже существует)

Сервис уже содержит все необходимые методы для работы с контекстом шапки:

```java
public class ConversationStateService {
    
    /**
     * Сохраняет контекст шапки события для пользователя.
     * 
     * @param userId идентификатор пользователя
     * @param hasMyEventsHeader флаг наличия шапки "Мои события"
     * @param eventCount количество событий для отображения в шапке
     */
    @Transactional
    public void saveEventHeaderContext(Long userId, boolean hasMyEventsHeader, int eventCount);
    
    /**
     * Получает контекст шапки события для пользователя.
     * 
     * @param userId идентификатор пользователя
     * @return EventHeaderContext или null если контекст не найден
     */
    @Transactional(readOnly = true)
    public EventHeaderContext getEventHeaderContext(Long userId);
    
    /**
     * Очищает контекст шапки события для пользователя.
     * 
     * @param userId идентификатор пользователя
     */
    @Transactional
    public void clearEventHeaderContext(Long userId);
    
    /**
     * Внутренний класс для передачи контекста шапки.
     */
    @Data
    @AllArgsConstructor
    public static class EventHeaderContext {
        private boolean hasMyEventsHeader;
        private int eventCount;
    }
}
```

### AttachmentCallbackHandler (требует модификации)

Метод `handleBackToEvent()` требует модификации для использования контекста шапки:

**Текущая реализация (упрощенно):**
```java
private void handleBackToEvent(Long eventId, User user, Long chatId, 
                               Integer messageId, String callbackQueryId) throws Exception {
    Event event = eventService.getEventById(eventId);
    
    // ПРОБЛЕМА: Всегда использует buildEventMessage() без шапки
    String message = botMessageBuilder.buildEventMessage(event);
    
    var keyboard = keyboardService.createEventActionsKeyboard(event, user.getId());
    messageService.editMessageText(chatId, messageId, message, keyboard);
    conversationStateService.clearAttachmentMessageContext(user.getId());
    messageService.answerCallbackQuery(callbackQueryId, "");
}
```

**Новая реализация:**
```java
private void handleBackToEvent(Long eventId, User user, Long chatId, 
                               Integer messageId, String callbackQueryId) throws Exception {
    log.debug("Возврат к карточке события ID={}, пользователь ID={}", 
            eventId, user.getId());
    
    try {
        // Получаем контекст шапки
        ConversationStateService.EventHeaderContext headerContext = 
                conversationStateService.getEventHeaderContext(user.getId());
        
        // Получаем событие
        Event event = eventService.getEventById(eventId);
        
        // Формируем сообщение с учетом контекста шапки
        String message;
        if (headerContext != null && headerContext.isHasMyEventsHeader()) {
            log.debug("Контекст шапки найден для пользователя ID={}, включение шапки 'Мои события' " +
                    "с количеством событий: {}", user.getId(), headerContext.getEventCount());
            message = botMessageBuilder.buildEventMessageWithHeader(event, headerContext.getEventCount());
        } else {
            log.debug("Контекст шапки не найден для пользователя ID={}, отображение без шапки", 
                    user.getId());
            message = botMessageBuilder.buildEventMessage(event);
        }
        
        // Создаем клавиатуру действий события
        var keyboard = keyboardService.createEventActionsKeyboard(event, user.getId());
        
        // Редактируем сообщение
        messageService.editMessageText(chatId, messageId, message, keyboard);
        
        // Очищаем attachment message context
        conversationStateService.clearAttachmentMessageContext(user.getId());
        
        log.debug("Attachment message context очищен для пользователя ID={}", user.getId());
        
        messageService.answerCallbackQuery(callbackQueryId, "");
        
    } catch (Exception e) {
        log.error("Ошибка при возврате к карточке события ID={}: {}", 
                eventId, e.getMessage(), e);
        messageService.answerCallbackQuery(callbackQueryId, "❌ Произошла ошибка");
        throw e;
    }
}
```

### BotMessageBuilder (не требует изменений)

Класс уже содержит необходимые методы:

```java
public class BotMessageBuilder {
    
    /**
     * Формирует сообщение о событии без шапки.
     */
    public String buildEventMessage(Event event);
    
    /**
     * Формирует сообщение о событии с шапкой "Мои события".
     * 
     * @param event событие
     * @param eventCount количество событий для отображения в шапке
     * @return отформатированное сообщение с шапкой
     */
    public String buildEventMessageWithHeader(Event event, int eventCount);
    
    /**
     * Формирует шапку "Мои события".
     * 
     * @param eventCount количество событий
     * @return отформатированная шапка
     */
    public String buildMyEventsHeader(int eventCount);
}
```

## Data Models

### ConversationState (уже существует)

Entity уже содержит необходимые поля:

```java
@Entity
@Table(name = "conversation_states")
public class ConversationState {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(name = "user_id", nullable = false, unique = true)
    private Long userId;
    
    // Контекст шапки события
    @Column(name = "event_has_my_events_header")
    private Boolean eventHasMyEventsHeader;
    
    @Column(name = "event_count_for_header")
    private Integer eventCountForHeader;
    
    // Другие поля...
    
    /**
     * Проверяет наличие сохраненного контекста шапки.
     */
    public boolean hasEventHeaderContext() {
        return eventHasMyEventsHeader != null && eventCountForHeader != null;
    }
    
    /**
     * Очищает контекст шапки.
     */
    public void clearEventHeaderContext() {
        this.eventHasMyEventsHeader = null;
        this.eventCountForHeader = null;
    }
}
```

### EventHeaderContext (уже существует)

Внутренний класс в `ConversationStateService`:

```java
@Data
@AllArgsConstructor
public static class EventHeaderContext {
    /**
     * Флаг наличия шапки "Мои события"
     */
    private boolean hasMyEventsHeader;
    
    /**
     * Количество событий для отображения в шапке
     */
    private int eventCount;
}
```

## Correctness Properties

*Свойство (property) - это характеристика или поведение, которое должно выполняться для всех допустимых входных данных системы. Свойства служат мостом между человекочитаемыми спецификациями и машинно-проверяемыми гарантиями корректности.*


### Testable Properties

На основе анализа acceptance criteria, следующие свойства могут быть протестированы:

**Property 1: Round-trip сохранения контекста шапки**
*For any* userId, hasMyEventsHeader flag, и eventCount, если контекст сохраняется через saveEventHeaderContext, то последующий вызов getEventHeaderContext должен вернуть EventHeaderContext с идентичными значениями hasMyEventsHeader и eventCount
**Validates: Requirements 1.2, 1.3**

**Property 2: Восстановление шапки при наличии контекста**
*For any* события и сохраненного контекста с hasMyEventsHeader=true, метод handleBackToEvent должен использовать buildEventMessageWithHeader с eventCount из контекста для формирования сообщения
**Validates: Requirements 2.1, 2.2**

**Property 3: Сохранение messageId при операциях с вложениями**
*For any* userId, eventId, chatId и messageId, после вызова saveAttachmentMessageId контекст должен содержать корректный messageId для последующего редактирования
**Validates: Requirements 6.3**

### Edge Cases

**Edge Case 1: Отображение без шапки при отсутствии контекста**
Когда getEventHeaderContext возвращает null (контекст не найден), handleBackToEvent должен использовать buildEventMessage вместо buildEventMessageWithHeader
**Validates: Requirements 2.3, 3.1, 3.3**

**Edge Case 2: Обработка исключений при получении контекста**
Когда getEventHeaderContext выбрасывает исключение, система должна залогировать ошибку и продолжить работу, используя buildEventMessage
**Validates: Requirements 3.2**

**Edge Case 3: Контекст с hasMyEventsHeader=false**
Когда контекст существует, но hasMyEventsHeader=false, система должна использовать buildEventMessage без шапки
**Validates: Requirements 2.3**

## Error Handling

### Обработка ошибок при получении контекста

```java
try {
    ConversationStateService.EventHeaderContext headerContext = 
            conversationStateService.getEventHeaderContext(user.getId());
    
    // Используем контекст если он существует
    if (headerContext != null && headerContext.isHasMyEventsHeader()) {
        message = botMessageBuilder.buildEventMessageWithHeader(event, headerContext.getEventCount());
    } else {
        message = botMessageBuilder.buildEventMessage(event);
    }
    
} catch (Exception e) {
    // Логируем ошибку и продолжаем без шапки
    log.error("Ошибка при получении контекста шапки для пользователя ID={}: {}", 
            user.getId(), e.getMessage(), e);
    message = botMessageBuilder.buildEventMessage(event);
}
```

### Типы ошибок

1. **NullPointerException**: Если userId равен null
   - Обрабатывается в ConversationStateService через IllegalArgumentException
   - Логируется на уровне ERROR

2. **DataAccessException**: Ошибки при работе с БД
   - Обрабатывается в try-catch блоке
   - Система продолжает работу без шапки
   - Логируется на уровне ERROR

3. **Контекст не найден**: getEventHeaderContext возвращает null
   - Это нормальная ситуация (пользователь не заходил в "Мои события")
   - Система отображает событие без шапки
   - Логируется на уровне DEBUG

### Стратегия восстановления

При любой ошибке получения или использования контекста шапки:
1. Логировать ошибку с полным stack trace
2. Продолжить работу, используя `buildEventMessage()` без шапки
3. Не прерывать пользовательский flow
4. Пользователь видит событие без шапки, но может продолжить работу

## Testing Strategy

### Dual Testing Approach

Для обеспечения корректности системы используется комбинация unit-тестов и property-based тестов:

**Unit-тесты** проверяют:
- Конкретные примеры сохранения и восстановления контекста
- Edge cases (null контекст, исключения)
- Интеграцию между компонентами
- Корректность вызовов методов (через моки)

**Property-based тесты** проверяют:
- Универсальные свойства для всех возможных входных данных
- Round-trip свойства (сохранение → получение → идентичность)
- Инварианты системы

### Property-Based Testing Configuration

**Библиотека**: jqwik (для Java)
- Минимум 100 итераций на каждый property-тест
- Генерация случайных userId, eventCount, messageId
- Генерация случайных boolean значений для hasMyEventsHeader

**Формат тегов**:
```java
@Property
@Label("Feature: event-header-navigation-preservation, Property 1: Round-trip сохранения контекста шапки")
void contextRoundTripProperty(@ForAll Long userId, 
                              @ForAll boolean hasHeader, 
                              @ForAll @IntRange(min = 1, max = 100) int eventCount) {
    // Тест
}
```

### Unit Testing Strategy

**Тесты для AttachmentCallbackHandler.handleBackToEvent()**:
1. Тест с существующим контекстом (hasMyEventsHeader=true)
   - Проверка вызова buildEventMessageWithHeader
   - Проверка передачи корректного eventCount
   
2. Тест с отсутствующим контекстом (null)
   - Проверка вызова buildEventMessage
   - Проверка отсутствия вызова buildEventMessageWithHeader

3. Тест с контекстом hasMyEventsHeader=false
   - Проверка вызова buildEventMessage

4. Тест обработки исключений
   - Мокирование исключения от getEventHeaderContext
   - Проверка логирования ошибки
   - Проверка вызова buildEventMessage

5. Тест очистки attachment context
   - Проверка вызова clearAttachmentMessageContext после успешного восстановления

**Тесты для ConversationStateService**:
1. Тест saveEventHeaderContext
   - Проверка сохранения обоих полей в БД
   
2. Тест getEventHeaderContext
   - Проверка корректного извлечения данных
   - Проверка возврата null при отсутствии контекста

3. Тест clearEventHeaderContext
   - Проверка очистки полей в БД

**Integration-тесты**:
1. Полный flow: "Мои события" → Вложения → Возврат к событию
   - Использование @SpringBootTest
   - Использование Testcontainers для PostgreSQL
   - Проверка сохранения контекста в реальной БД
   - Проверка восстановления шапки

### Test Coverage Goals

- Минимум 80% покрытие кода
- 100% покрытие критических путей (handleBackToEvent)
- Все acceptance criteria покрыты тестами
- Все edge cases покрыты тестами

## Implementation Notes

### Изменения в коде

**Файлы, требующие модификации:**
1. `AttachmentCallbackHandler.java` - метод `handleBackToEvent()`
   - Добавить получение контекста через `getEventHeaderContext()`
   - Добавить условную логику для выбора метода формирования сообщения
   - Добавить обработку ошибок
   - Добавить логирование

**Файлы, не требующие изменений:**
1. `ConversationStateService.java` - уже содержит все необходимые методы
2. `ConversationState.java` - уже содержит необходимые поля
3. `BotMessageBuilder.java` - уже содержит необходимые методы
4. `MyEventsCommandHandler.java` - уже сохраняет контекст

### Миграции БД

Миграции БД не требуются - все необходимые поля уже существуют в таблице `conversation_states`:
- `event_has_my_events_header` (Boolean)
- `event_count_for_header` (Integer)

### Логирование

**Уровни логирования:**
- DEBUG: Нормальные операции (получение контекста, выбор метода формирования сообщения)
- INFO: Важные события (успешное восстановление шапки)
- ERROR: Ошибки (исключения при получении контекста)

**Формат логов:**
```java
log.debug("Возврат к карточке события ID={}, пользователь ID={}", eventId, user.getId());
log.debug("Контекст шапки найден для пользователя ID={}, включение шапки 'Мои события' с количеством событий: {}", 
        user.getId(), headerContext.getEventCount());
log.debug("Контекст шапки не найден для пользователя ID={}, отображение без шапки", user.getId());
log.error("Ошибка при получении контекста шапки для пользователя ID={}: {}", 
        user.getId(), e.getMessage(), e);
```

### Обратная совместимость

Изменения полностью обратно совместимы:
- Если контекст не найден, система работает как раньше (без шапки)
- Существующие пользователи не пострадают
- Новая функциональность активируется автоматически при наличии контекста

### Performance Considerations

**Дополнительные запросы к БД:**
- 1 дополнительный SELECT запрос при возврате к событию (getEventHeaderContext)
- Запрос выполняется по индексированному полю user_id
- Минимальное влияние на производительность

**Оптимизация:**
- Контекст хранится в той же таблице conversation_states (нет JOIN'ов)
- Используется @Transactional(readOnly = true) для read операций
- Контекст очищается после использования (не накапливается)

## Alternatives Considered

### Альтернатива 1: Передача контекста через callback data

**Описание**: Включить информацию о шапке в callback data кнопки "Вложения"

**Плюсы**:
- Не требует обращения к БД
- Контекст всегда доступен

**Минусы**:
- Ограничение на размер callback data (64 байта)
- Усложнение формата callback data
- Контекст может устареть (если количество событий изменилось)

**Решение**: Отклонено из-за ограничений Telegram API и возможности устаревания данных

### Альтернатива 2: Хранение контекста в памяти (Map)

**Описание**: Использовать in-memory Map для хранения контекста вместо БД

**Плюсы**:
- Быстрее, чем запрос к БД
- Проще реализация

**Минусы**:
- Контекст теряется при перезапуске приложения
- Проблемы при горизонтальном масштабировании
- Утечки памяти при неправильной очистке

**Решение**: Отклонено из-за проблем с персистентностью и масштабируемостью

### Альтернатива 3: Всегда отображать шапку

**Описание**: Всегда добавлять шапку "Мои события" при возврате к событию

**Плюсы**:
- Простая реализация
- Не требует сохранения контекста

**Минусы**:
- Некорректное поведение для событий, открытых не из "Мои события"
- Путаница для пользователя
- Неконсистентный UX

**Решение**: Отклонено из-за некорректного UX

### Выбранное решение

Использование существующего механизма ConversationStateService для сохранения и восстановления контекста:
- Переиспользует существующую инфраструктуру
- Обеспечивает персистентность
- Корректно обрабатывает все edge cases
- Минимальные изменения в коде
