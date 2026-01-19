# Design Document

## Overview

Данный документ описывает проектное решение для функции редактирования сообщений о событиях в Telegram боте семейного календаря. В настоящее время при редактировании события (изменении названия, даты, времени, описания и других полей) бот отправляет новое сообщение в чат, что приводит к засорению чата множеством сообщений. Требуется изменить поведение так, чтобы бот обновлял существующее сообщение о событии вместо отправки нового.

Решение включает:
1. Добавление поля `messageId` в модель `Event` и базу данных
2. Сохранение `messageId` при создании события
3. Использование метода `editMessageText` вместо `sendMessage` при обновлении события
4. Обработку ошибок (удалённые/старые сообщения) с fallback на отправку нового сообщения

## Architecture

### Компоненты системы

```
┌─────────────────────────────────────────────────────────────┐
│                    Telegram Bot API                          │
└─────────────────────────────────────────────────────────────┘
                            ↑ ↓
┌─────────────────────────────────────────────────────────────┐
│              TelegramMessageService                          │
│  - sendMessage()                                             │
│  - editMessageText()  ← новое использование                  │
└─────────────────────────────────────────────────────────────┘
                            ↑ ↓
┌─────────────────────────────────────────────────────────────┐
│                  EventService                                │
│  - createEvent() → сохраняет messageId                       │
│  - updateEvent() → использует editMessageText                │
│  - sendEventMessage() ← новый метод                          │
└─────────────────────────────────────────────────────────────┘
                            ↑ ↓
┌─────────────────────────────────────────────────────────────┐
│                Event Model + Repository                      │
│  + messageId: Long (новое поле)                              │
└─────────────────────────────────────────────────────────────┘
                            ↑ ↓
┌─────────────────────────────────────────────────────────────┐
│                    PostgreSQL Database                       │
│  events table + message_id column                            │
└─────────────────────────────────────────────────────────────┘
```

### Поток данных

#### Создание события

```
Пользователь создаёт событие
    ↓
EventService.createEvent()
    ↓
Формирование текста сообщения
    ↓
TelegramMessageService.sendMessage() → возвращает Message
    ↓
Извлечение messageId из Message
    ↓
Event.setMessageId(messageId)
    ↓
EventRepository.save(event)
    ↓
Событие сохранено с messageId
```

#### Редактирование события

```
Пользователь редактирует событие
    ↓
EventService.updateEvent()
    ↓
Проверка наличия messageId
    ↓
Если messageId есть:
    ├─→ Формирование нового текста
    ├─→ TelegramMessageService.editMessageText()
    ├─→ Успех → готово
    └─→ Ошибка (сообщение удалено/старое)
        ├─→ TelegramMessageService.sendMessage()
        ├─→ Получение нового messageId
        └─→ Event.setMessageId(новый messageId)
Если messageId нет:
    ├─→ TelegramMessageService.sendMessage()
    ├─→ Получение messageId
    └─→ Event.setMessageId(messageId)
```

## Components and Interfaces

### 1. Event Model

**Изменения в модели:**

```java
@Entity
@Table(name = "events")
public class Event {
    // ... существующие поля ...
    
    /**
     * Идентификатор сообщения Telegram, связанного с этим событием.
     * Используется для обновления сообщения при редактировании события.
     * NULL для событий, созданных до внедрения этой функции.
     */
    @Column(name = "message_id")
    private Long messageId;
    
    // ... остальные поля и методы ...
}
```

### 2. Database Migration

**Новая миграция Flyway:**

Файл: `src/main/resources/db/migration/V13__Add_message_id_to_events.sql`

```sql
-- Добавление поля message_id в таблицу events
ALTER TABLE events 
ADD COLUMN message_id BIGINT;

-- Комментарий для документации
COMMENT ON COLUMN events.message_id IS 
'Идентификатор сообщения Telegram, связанного с событием. Используется для обновления сообщения при редактировании.';

-- Индекс для быстрого поиска по message_id (опционально, если потребуется)
-- CREATE INDEX idx_events_message_id ON events(message_id);
```

### 3. TelegramMessageService

**Изменения в существующих методах:**

Метод `sendMessage` уже возвращает `void`, но нам нужно получать `messageId`. Telegram API возвращает объект `Message` после успешной отправки.

**Новый метод:**

```java
/**
 * Отправляет текстовое сообщение с inline кнопками и возвращает отправленное сообщение.
 * 
 * <p>Этот метод аналогичен {@link #sendMessage(Long, String, InlineKeyboardMarkup)},
 * но возвращает объект Message, содержащий messageId и другую информацию
 * об отправленном сообщении.</p>
 * 
 * @param telegramId Telegram ID пользователя-получателя
 * @param text текст сообщения (поддерживает MarkdownV2)
 * @param replyMarkup разметка inline кнопок
 * @return отправленное сообщение с messageId
 * @throws TelegramApiException если все попытки отправки не удались
 */
public Message sendMessageAndGet(Long telegramId, String text, InlineKeyboardMarkup replyMarkup) 
        throws TelegramApiException {
    validateSendMessageParams(telegramId, text);
    
    if (replyMarkup == null) {
        throw new IllegalArgumentException("ReplyMarkup не может быть null");
    }
    
    log.debug("Отправка сообщения с inline кнопками (с возвратом Message): telegramId={}, textLength={}", 
            telegramId, text.length());
    
    SendMessage message = SendMessage.builder()
            .chatId(telegramId.toString())
            .text(text)
            .parseMode("MarkdownV2")
            .replyMarkup(replyMarkup)
            .build();
    
    try {
        Message sentMessage = execute(message);
        log.debug("Сообщение успешно отправлено: telegramId={}, messageId={}", 
                telegramId, sentMessage.getMessageId());
        return sentMessage;
        
    } catch (TelegramApiRequestException e) {
        // Обработка ошибок парсинга с fallback
        if (isParseError(e)) {
            log.warn("Ошибка парсинга MarkdownV2, переключаемся на plain text: telegramId={}", 
                    telegramId);
            recordMetric("markdown_parse_error_fallback");
            
            SendMessage plainMessage = SendMessage.builder()
                    .chatId(telegramId.toString())
                    .text(text)
                    .replyMarkup(replyMarkup)
                    .build();
            
            Message sentMessage = execute(plainMessage);
            log.info("Сообщение успешно отправлено без форматирования (fallback): telegramId={}, messageId={}", 
                    telegramId, sentMessage.getMessageId());
            return sentMessage;
        }
        
        recordMetricForTelegramError(e);
        handleTelegramApiError(e, telegramId, text);
        throw e;
    }
}
```

**Изменения в методе `editMessageText`:**

Метод уже существует и работает корректно. Нужно только добавить обработку специфичных ошибок:

```java
/**
 * Редактирует текст существующего сообщения с обработкой ошибок удалённых сообщений.
 * 
 * @param chatId ID чата
 * @param messageId ID сообщения для редактирования
 * @param newText новый текст
 * @param replyMarkup новая клавиатура
 * @return true если редактирование успешно, false если сообщение не найдено/удалено
 * @throws TelegramApiException при других ошибках
 */
public boolean tryEditMessageText(Long chatId, Integer messageId, String newText, 
                                  InlineKeyboardMarkup replyMarkup) throws TelegramApiException {
    try {
        editMessageText(chatId, messageId, newText, replyMarkup);
        return true;
        
    } catch (TelegramApiRequestException e) {
        // Проверяем, не удалено ли сообщение
        if (isMessageNotFoundError(e)) {
            log.info("Сообщение не найдено или удалено: chatId={}, messageId={}", 
                    chatId, messageId);
            return false;
        }
        
        // Проверяем, не слишком ли старое сообщение
        if (isMessageTooOldError(e)) {
            log.info("Сообщение слишком старое для редактирования: chatId={}, messageId={}", 
                    chatId, messageId);
            return false;
        }
        
        // Другие ошибки пробрасываем дальше
        throw e;
    }
}

/**
 * Проверяет, является ли ошибка "сообщение не найдено".
 */
private boolean isMessageNotFoundError(TelegramApiRequestException e) {
    String message = e.getMessage();
    String apiResponse = e.getApiResponse();
    
    return (message != null && message.contains("message to edit not found")) ||
           (message != null && message.contains("message can't be edited")) ||
           (apiResponse != null && apiResponse.contains("message to edit not found")) ||
           (apiResponse != null && apiResponse.contains("message can't be edited"));
}

/**
 * Проверяет, является ли ошибка "сообщение слишком старое".
 */
private boolean isMessageTooOldError(TelegramApiRequestException e) {
    String message = e.getMessage();
    String apiResponse = e.getApiResponse();
    
    return (message != null && message.contains("message is too old")) ||
           (apiResponse != null && apiResponse.contains("message is too old"));
}
```

### 4. EventService

**Новые/изменённые методы:**

```java
/**
 * Отправляет или обновляет сообщение о событии в Telegram.
 * 
 * <p>Если событие уже имеет messageId, пытается обновить существующее сообщение.
 * Если обновление не удаётся (сообщение удалено/старое), отправляет новое сообщение.</p>
 * 
 * @param event событие для отправки/обновления
 * @param chatId ID чата для отправки
 * @return обновлённое событие с актуальным messageId
 * @throws TelegramApiException при критических ошибках отправки
 */
public Event sendOrUpdateEventMessage(Event event, Long chatId) throws TelegramApiException {
    String messageText = formatEventMessage(event);
    InlineKeyboardMarkup keyboard = keyboardService.createEventKeyboard(event);
    
    // Если есть messageId, пытаемся обновить существующее сообщение
    if (event.getMessageId() != null) {
        log.debug("Попытка обновления существующего сообщения: eventId={}, messageId={}", 
                event.getId(), event.getMessageId());
        
        boolean updated = telegramMessageService.tryEditMessageText(
                chatId, 
                event.getMessageId().intValue(), 
                messageText, 
                keyboard
        );
        
        if (updated) {
            log.info("Сообщение о событии успешно обновлено: eventId={}, messageId={}", 
                    event.getId(), event.getMessageId());
            return event;
        }
        
        // Если обновление не удалось, отправляем новое сообщение
        log.info("Не удалось обновить сообщение, отправляем новое: eventId={}, oldMessageId={}", 
                event.getId(), event.getMessageId());
    }
    
    // Отправляем новое сообщение
    Message sentMessage = telegramMessageService.sendMessageAndGet(chatId, messageText, keyboard);
    
    // Сохраняем новый messageId
    event.setMessageId((long) sentMessage.getMessageId());
    eventRepository.save(event);
    
    log.info("Новое сообщение о событии отправлено и messageId сохранён: eventId={}, messageId={}", 
            event.getId(), event.getMessageId());
    
    return event;
}

/**
 * Форматирует сообщение о событии для отправки в Telegram.
 * 
 * @param event событие для форматирования
 * @return отформатированный текст сообщения с MarkdownV2
 */
private String formatEventMessage(Event event) {
    // Используем существующую логику форматирования
    // Эта логика уже есть в различных handler'ах, нужно её централизовать
    return BotMessageBuilder.buildEventMessage(event);
}
```

**Изменения в существующих методах:**

```java
/**
 * Создаёт новое событие и отправляет сообщение о нём.
 */
public Event createEvent(Event event, Long chatId) throws TelegramApiException {
    // Сохраняем событие
    Event savedEvent = eventRepository.save(event);
    
    // Отправляем сообщение и сохраняем messageId
    return sendOrUpdateEventMessage(savedEvent, chatId);
}

/**
 * Обновляет существующее событие и обновляет сообщение о нём.
 */
public Event updateEvent(Event event, Long chatId) throws TelegramApiException {
    // Сохраняем изменения
    Event updatedEvent = eventRepository.save(event);
    
    // Обновляем сообщение
    return sendOrUpdateEventMessage(updatedEvent, chatId);
}
```

### 5. BotMessageBuilder

**Новый утилитный метод для централизованного форматирования:**

```java
/**
 * Формирует сообщение о событии с полной информацией.
 * 
 * @param event событие для форматирования
 * @return отформатированное сообщение с MarkdownV2 экранированием
 */
public static String buildEventMessage(Event event) {
    StringBuilder message = new StringBuilder();
    
    // Заголовок с эмодзи в зависимости от статуса
    String statusEmoji = switch (event.getStatus()) {
        case DRAFT -> "📝";
        case ACTIVE -> "📅";
        case COMPLETED -> "✅";
        case DELETED -> "🗑";
    };
    
    message.append(statusEmoji).append(" *");
    message.append(MarkdownFormatter.escape(event.getTitle() != null ? event.getTitle() : "Без названия"));
    message.append("*\n\n");
    
    // Дата и время
    if (event.getEventDate() != null) {
        message.append("📆 ");
        message.append(MarkdownFormatter.escape(event.getFormattedDate()));
        message.append("\n");
    }
    
    if (event.getEventTime() != null) {
        message.append("🕐 ");
        message.append(MarkdownFormatter.escape(event.getFormattedTimeInterval()));
        message.append("\n");
    }
    
    // Описание
    if (event.getDescription() != null && !event.getDescription().isBlank()) {
        message.append("\n");
        message.append(MarkdownFormatter.escape(event.getDescription()));
        message.append("\n");
    }
    
    // Дополнительная информация
    if (event.isPersonal()) {
        message.append("\n🔒 Личное событие");
    }
    
    if (event.isRecurring()) {
        message.append("\n🔄 Повторяющееся");
    }
    
    return message.toString();
}
```

## Data Models

### Event Entity

```java
@Entity
@Table(name = "events")
public class Event {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    // ... существующие поля ...
    
    /**
     * Идентификатор сообщения Telegram, связанного с этим событием.
     * Используется для обновления сообщения при редактировании события.
     * NULL для событий, созданных до внедрения этой функции или
     * для событий, сообщения о которых были удалены пользователем.
     */
    @Column(name = "message_id")
    private Long messageId;
    
    // Геттеры и сеттеры
    public Long getMessageId() {
        return messageId;
    }
    
    public void setMessageId(Long messageId) {
        this.messageId = messageId;
    }
}
```

### Database Schema

```sql
-- Таблица events с новым полем
CREATE TABLE events (
    id BIGSERIAL PRIMARY KEY,
    -- ... существующие поля ...
    message_id BIGINT,  -- новое поле
    -- ... остальные поля ...
);
```

## Correctness Properties

*Свойство - это характеристика или поведение, которое должно выполняться во всех допустимых выполнениях системы - по сути, формальное утверждение о том, что система должна делать. Свойства служат мостом между человекочитаемыми спецификациями и машинопроверяемыми гарантиями корректности.*

### Property 1: Сохранение messageId при создании события

*For any* нового события, после успешной отправки сообщения в Telegram, событие должно иметь сохранённый messageId, соответствующий ID отправленного сообщения.

**Validates: Requirements 1.1, 1.2, 1.5**

### Property 2: Обновление сообщения при редактировании

*For any* события с сохранённым messageId, при изменении любого поля события система должна вызвать `editMessageText` вместо `sendMessage`.

**Validates: Requirements 2.1, 2.2, 2.3, 2.4, 2.5**

### Property 3: Fallback на новое сообщение при ошибке редактирования

*For any* события, если попытка редактирования сообщения не удалась (сообщение удалено/старое), система должна отправить новое сообщение и обновить messageId.

**Validates: Requirements 4.1, 4.2, 4.3, 4.5**

### Property 4: Актуальность клавиатуры

*For any* обновлённого сообщения о событии, inline-клавиатура должна соответствовать текущему статусу события.

**Validates: Requirements 3.1, 3.2, 3.3, 3.4, 3.5**

### Property 5: Логирование операций

*For any* операции отправки или обновления сообщения о событии, система должна записать в лог информацию о событии, messageId и результате операции.

**Validates: Requirements 5.1, 5.2, 5.3, 5.4, 5.5**

## Error Handling

### Типы ошибок

1. **Сообщение удалено пользователем**
   - Ошибка: "message to edit not found" или "message can't be edited"
   - Обработка: отправка нового сообщения, сохранение нового messageId
   - Логирование: INFO уровень

2. **Сообщение слишком старое**
   - Ошибка: "message is too old"
   - Обработка: отправка нового сообщения, сохранение нового messageId
   - Логирование: INFO уровень

3. **Ошибка парсинга MarkdownV2**
   - Ошибка: "can't parse entities" (400 Bad Request)
   - Обработка: fallback на plain text
   - Логирование: WARN уровень

4. **Ошибки сети/Telegram API**
   - Ошибка: различные коды ошибок (429, 500+)
   - Обработка: retry с экспоненциальной задержкой
   - Логирование: ERROR уровень

### Стратегия обработки

```java
try {
    // Попытка обновления существующего сообщения
    if (event.getMessageId() != null) {
        boolean updated = telegramMessageService.tryEditMessageText(...);
        
        if (!updated) {
            // Fallback: отправка нового сообщения
            Message newMessage = telegramMessageService.sendMessageAndGet(...);
            event.setMessageId((long) newMessage.getMessageId());
            eventRepository.save(event);
        }
    } else {
        // Отправка нового сообщения
        Message newMessage = telegramMessageService.sendMessageAndGet(...);
        event.setMessageId((long) newMessage.getMessageId());
        eventRepository.save(event);
    }
    
} catch (TelegramApiException e) {
    log.error("Критическая ошибка при отправке/обновлении сообщения о событии: eventId={}, error={}", 
            event.getId(), e.getMessage(), e);
    throw e;
}
```

## Testing Strategy

### Unit Tests

1. **Тест сохранения messageId при создании события**
   - Создание события
   - Проверка, что messageId сохранён в БД
   - Проверка, что messageId соответствует ID отправленного сообщения

2. **Тест обновления сообщения при редактировании**
   - Создание события с messageId
   - Изменение поля события
   - Проверка вызова `editMessageText` вместо `sendMessage`

3. **Тест fallback при удалённом сообщении**
   - Создание события с messageId
   - Мок ошибки "message not found"
   - Проверка отправки нового сообщения
   - Проверка обновления messageId

4. **Тест fallback при старом сообщении**
   - Создание события с messageId
   - Мок ошибки "message is too old"
   - Проверка отправки нового сообщения
   - Проверка обновления messageId

5. **Тест обновления клавиатуры**
   - Изменение статуса события
   - Проверка, что клавиатура соответствует новому статусу

6. **Тест логирования операций**
   - Выполнение операций отправки/обновления
   - Проверка наличия соответствующих записей в логах

### Property-Based Tests

Для этой функциональности property-based тесты менее применимы, так как основная логика связана с взаимодействием с внешним API (Telegram). Однако можно написать несколько property tests:

1. **Property Test 1: Форматирование сообщений**
   - Генерация: случайные события с различными полями
   - Проверка: отформатированное сообщение содержит все заполненные поля
   - Проверка: все специальные символы MarkdownV2 корректно экранированы
   - Минимум 100 итераций

2. **Property Test 2: Идемпотентность обновления**
   - Генерация: случайное событие
   - Операция: двойное обновление с одинаковыми данными
   - Проверка: messageId не изменился после второго обновления
   - Минимум 100 итераций

### Integration Tests

1. **Интеграционный тест с реальной БД**
   - Создание события через EventService
   - Проверка сохранения messageId в БД
   - Обновление события
   - Проверка, что messageId не изменился

2. **Интеграционный тест с Testcontainers**
   - Запуск PostgreSQL в контейнере
   - Выполнение миграции
   - Проверка наличия поля message_id
   - CRUD операции с событиями

### Testing Framework

- **Unit tests**: JUnit 5 + Mockito
- **Property tests**: jqwik (уже используется в проекте)
- **Integration tests**: Spring Boot Test + Testcontainers

Каждый property test будет помечен комментарием:
```java
// Feature: event-message-editing, Property 1: Форматирование сообщений
```

## Implementation Notes

### 1. Обратная совместимость

- Поле `messageId` допускает NULL значения
- События, созданные до внедрения функции, будут иметь `messageId = null`
- При первом обновлении таких событий будет отправлено новое сообщение и сохранён messageId
- Существующий код продолжит работать без изменений

### 2. Производительность

- Операция `editMessageText` быстрее, чем `sendMessage`, так как не создаёт новое сообщение
- Уменьшение количества сообщений в чате улучшает UX
- Дополнительный запрос к БД для сохранения messageId незначителен

### 3. Безопасность

- messageId не содержит чувствительной информации
- Валидация messageId перед использованием
- Обработка всех возможных ошибок Telegram API

### 4. Логирование

- Детальное логирование всех операций с сообщениями
- Разделение уровней логирования (INFO для нормальных операций, ERROR для критических ошибок)
- Логирование fallback операций для мониторинга

### 5. Миграция данных

- Миграция добавляет поле без заполнения существующих записей
- Постепенное заполнение messageId при обновлении событий
- Возможность добавления скрипта для массового заполнения messageId (опционально)

### 6. Централизация форматирования

- Создание единого метода `BotMessageBuilder.buildEventMessage()`
- Рефакторинг существующих handler'ов для использования централизованного метода
- Упрощение поддержки и изменения формата сообщений

### 7. Ограничения Telegram API

- Сообщения старше 48 часов нельзя редактировать
- Удалённые пользователем сообщения нельзя редактировать
- Максимальная длина сообщения: 4096 символов
- Все эти случаи обрабатываются с fallback на отправку нового сообщения

