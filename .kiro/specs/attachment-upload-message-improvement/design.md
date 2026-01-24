# Документ проектирования

## Обзор

Данный документ описывает проектное решение для улучшения UX при добавлении вложений к событиям в Telegram боте. Основная цель - заменить создание нового сообщения на редактирование текущего сообщения события и добавить возможность отмены процесса загрузки файла.

### Текущая проблема

При нажатии кнопки "Добавить вложение" система отправляет новое сообщение с инструкцией, что приводит к:
- Засорению чата дополнительными сообщениями
- Отсутствию возможности отменить процесс
- Плохому пользовательскому опыту

### Предлагаемое решение

Изменить поведение так, чтобы:
1. Текущее сообщение события редактировалось вместо создания нового
2. Добавить кнопку "Отмена" для возврата к стандартному виду события
3. Сохранять контекст сообщения для корректного восстановления

## Архитектура

### Компоненты системы

```
┌─────────────────────────────────────────────────────────────┐
│                    AttachmentCallbackHandler                 │
│  ┌───────────────────────────────────────────────────────┐  │
│  │  handleAddFile()                                       │  │
│  │  - Проверка прав доступа                              │  │
│  │  - Редактирование сообщения с инструкцией            │  │
│  │  - Установка состояния ожидания файла                │  │
│  └───────────────────────────────────────────────────────┘  │
│  ┌───────────────────────────────────────────────────────┐  │
│  │  handleCancelAddFile()                                 │  │
│  │  - Очистка состояния ожидания файла                   │  │
│  │  - Восстановление стандартного вида события           │  │
│  └───────────────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────────────┘
                            │
                            ↓
┌─────────────────────────────────────────────────────────────┐
│                  TelegramMessageService                      │
│  ┌───────────────────────────────────────────────────────┐  │
│  │  editMessageText()                                     │  │
│  │  - Редактирование существующего сообщения             │  │
│  │  - Обработка ошибок (сообщение удалено/старое)       │  │
│  └───────────────────────────────────────────────────────┘  │
│  ┌───────────────────────────────────────────────────────┐  │
│  │  tryEditMessageText()                                  │  │
│  │  - Попытка редактирования с fallback                  │  │
│  └───────────────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────────────┘
                            │
                            ↓
┌─────────────────────────────────────────────────────────────┐
│                    KeyboardService                           │
│  ┌───────────────────────────────────────────────────────┐  │
│  │  createAttachmentUploadKeyboard()                      │  │
│  │  - Создание клавиатуры с кнопкой "Отмена"            │  │
│  └───────────────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────────────┘
                            │
                            ↓
┌─────────────────────────────────────────────────────────────┐
│                ConversationStateService                      │
│  ┌───────────────────────────────────────────────────────┐  │
│  │  setAwaitingFile()                                     │  │
│  │  - Сохранение контекста (eventId, chatId, messageId) │  │
│  └───────────────────────────────────────────────────────┘  │
│  ┌───────────────────────────────────────────────────────┐  │
│  │  clearAwaitingFile()                                   │  │
│  │  - Очистка состояния ожидания файла                   │  │
│  └───────────────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────────────┘
```

### Поток данных

#### Сценарий 1: Успешное редактирование сообщения

```
User нажимает "Добавить вложение"
    ↓
AttachmentCallbackHandler.handleAddFile()
    ↓
Проверка прав доступа (только создатель события)
    ↓
ConversationStateService.setAwaitingFile(userId, eventId, chatId, messageId)
    ↓
KeyboardService.createAttachmentUploadKeyboard(eventId)
    ↓
TelegramMessageService.editMessageText(chatId, messageId, instruction, keyboard)
    ↓
Telegram API редактирует сообщение
    ↓
User видит инструкцию и кнопку "Отмена"
```

#### Сценарий 2: Отмена загрузки файла

```
User нажимает "Отмена"
    ↓
AttachmentCallbackHandler.handleCancelAddFile()
    ↓
ConversationStateService.clearAwaitingFile(userId)
    ↓
EventService.getEventById(eventId)
    ↓
BotMessageBuilder.buildEventMessage(event)
    ↓
KeyboardService.createEventActionsKeyboard(event, userId)
    ↓
TelegramMessageService.editMessageText(chatId, messageId, eventMessage, keyboard)
    ↓
Telegram API редактирует сообщение
    ↓
User видит стандартную карточку события
```

#### Сценарий 3: Fallback при ошибке редактирования

```
User нажимает "Добавить вложение"
    ↓
AttachmentCallbackHandler.handleAddFile()
    ↓
TelegramMessageService.tryEditMessageText() возвращает false
    ↓
TelegramMessageService.sendMessageAndGet() отправляет новое сообщение
    ↓
ConversationStateService.setAwaitingFile() с новым messageId
    ↓
User видит новое сообщение с инструкцией
```

## Компоненты и интерфейсы

### AttachmentCallbackHandler

#### Изменения в handleAddFile()

**Текущая реализация:**
```java
private void handleAddFile(Long eventId, User user, Long chatId, 
                          Integer messageId, String callbackQueryId) throws Exception {
    // Проверка прав доступа
    // ...
    
    // Сохранение контекста
    conversationStateService.saveAttachmentMessageId(user.getId(), eventId, chatId, messageId);
    conversationStateService.setAwaitingFile(user.getId(), eventId, chatId, messageId);
    
    // Отправка НОВОГО сообщения
    String message = "📎 *Отправьте файл для прикрепления к событию*\n\n...";
    messageService.sendMessage(chatId, message);
    messageService.answerCallbackQuery(callbackQueryId, "");
}
```

**Новая реализация:**
```java
private void handleAddFile(Long eventId, User user, Long chatId, 
                          Integer messageId, String callbackQueryId) throws Exception {
    // Проверка прав доступа
    Event event = eventService.getEventById(eventId);
    if (!event.getUser().getId().equals(user.getId())) {
        messageService.answerCallbackQuery(callbackQueryId, "❌ Нет прав доступа");
        return;
    }
    
    // Формирование инструкции
    String instruction = buildAttachmentUploadInstruction();
    
    // Создание клавиатуры с кнопкой "Отмена"
    InlineKeyboardMarkup keyboard = keyboardService.createAttachmentUploadKeyboard(eventId);
    
    // Попытка редактирования сообщения
    boolean edited = messageService.tryEditMessageText(chatId, messageId, instruction, keyboard);
    
    if (!edited) {
        // Fallback: отправка нового сообщения
        Message newMessage = messageService.sendMessageAndGet(chatId, instruction, keyboard);
        messageId = newMessage.getMessageId();
    }
    
    // Сохранение контекста и установка состояния
    conversationStateService.setAwaitingFile(user.getId(), eventId, chatId, messageId);
    messageService.answerCallbackQuery(callbackQueryId, "");
}

private String buildAttachmentUploadInstruction() {
    return "📎 *Отправьте файл для прикрепления к событию*\n\n" +
           "_Максимальный размер: 20 МБ_\n\n" +
           "Поддерживаемые типы файлов:\n" +
           "📄 Документы\n" +
           "🖼️ Фотографии\n" +
           "🎥 Видео\n" +
           "🎵 Аудио";
}
```

#### Новый метод handleCancelAddFile()

```java
/**
 * Обрабатывает отмену добавления файла.
 * 
 * <p>Очищает состояние ожидания файла и восстанавливает стандартный вид события.</p>
 * 
 * <p><b>Требования:</b> 2.2, 2.3, 2.4, 7.1-7.5</p>
 * 
 * @param eventId идентификатор события
 * @param user пользователь
 * @param chatId идентификатор чата
 * @param messageId идентификатор сообщения
 * @param callbackQueryId идентификатор callback query
 */
private void handleCancelAddFile(Long eventId, User user, Long chatId, 
                                Integer messageId, String callbackQueryId) throws Exception {
    log.debug("Отмена добавления файла для события ID={}, пользователь ID={}", 
            eventId, user.getId());
    
    try {
        // Очистка состояния ожидания файла
        conversationStateService.clearAwaitingFile(user.getId());
        
        // Получение события для восстановления карточки
        Event event = eventService.getEventById(eventId);
        
        // Формирование стандартного сообщения события
        String eventMessage = botMessageBuilder.buildEventMessage(event);
        
        // Создание стандартной клавиатуры события
        InlineKeyboardMarkup keyboard = keyboardService.createEventActionsKeyboard(event, user.getId());
        
        // Попытка редактирования сообщения
        boolean edited = messageService.tryEditMessageText(chatId, messageId, eventMessage, keyboard);
        
        if (!edited) {
            // Fallback: отправка нового сообщения
            messageService.sendMessage(chatId, eventMessage, keyboard);
        }
        
        messageService.answerCallbackQuery(callbackQueryId, "Отменено");
        
        log.info("Добавление файла отменено для события ID={}, пользователь ID={}", 
                eventId, user.getId());
        
    } catch (Exception e) {
        log.error("Ошибка при отмене добавления файла: eventId={}, userId={}, error={}", 
                eventId, user.getId(), e.getMessage(), e);
        messageService.answerCallbackQuery(callbackQueryId, "❌ Произошла ошибка");
        throw e;
    }
}
```

#### Изменения в методе handle()

Добавление обработки нового callback action "cancel" с subAction "add":

```java
@Override
public void handle(CallbackQuery callbackQuery, User user) throws Exception {
    // ... существующий код парсинга ...
    
    switch (action) {
        // ... существующие case ...
        
        case "cancel" -> {
            // Составное действие: cancel_add_{eventId}
            if (parts.length < 3) {
                log.warn("Недостаточно частей для действия 'cancel': callbackData='{}', parts={}, userId={}", 
                        callbackData, java.util.Arrays.toString(parts), user.getId());
                messageService.answerCallbackQuery(callbackQueryId, "❌ Ошибка: некорректный формат данных");
                return;
            }
            if (!parts[1].equals("add")) {
                log.warn("Некорректный subAction для 'cancel': ожидается 'add', получено '{}', callbackData='{}', userId={}", 
                        parts[1], callbackData, user.getId());
                messageService.answerCallbackQuery(callbackQueryId, "❌ Ошибка: неподдерживаемое действие");
                return;
            }
            Long eventId = Long.parseLong(parts[2]);
            log.debug("Обработка составного действия 'cancel_add': eventId={}", eventId);
            handleCancelAddFile(eventId, user, chatId, messageId, callbackQueryId);
        }
        
        // ... остальные case ...
    }
}
```

### KeyboardService

#### Новый метод createAttachmentUploadKeyboard()

```java
/**
 * Создает inline клавиатуру для режима загрузки вложения.
 * 
 * <p>Клавиатура содержит единственную кнопку "Отмена", которая позволяет
 * пользователю прервать процесс загрузки файла и вернуться к стандартному
 * виду карточки события.</p>
 * 
 * <p>Callback data формируется в формате "attach_file_cancel_add_{eventId}".</p>
 * 
 * <p><b>Требования:</b> 2.1, 6.1</p>
 * 
 * @param eventId идентификатор события
 * @return настроенная InlineKeyboardMarkup с кнопкой "Отмена"
 * @throws IllegalArgumentException если eventId равен null или не является положительным числом
 */
public InlineKeyboardMarkup createAttachmentUploadKeyboard(Long eventId) {
    // Валидация eventId
    if (eventId == null) {
        log.error("Попытка создать клавиатуру с null eventId");
        throw new IllegalArgumentException("EventId не может быть null");
    }
    
    if (eventId <= 0) {
        log.error("Попытка создать клавиатуру с некорректным eventId: {}", eventId);
        throw new IllegalArgumentException("EventId должен быть положительным числом, получено: " + eventId);
    }
    
    log.debug("Создание inline клавиатуры для загрузки вложения к событию ID={}", eventId);
    
    InlineKeyboardMarkup keyboard = new InlineKeyboardMarkup();
    List<List<InlineKeyboardButton>> rows = new ArrayList<>();
    
    // Единственная кнопка "Отмена"
    List<InlineKeyboardButton> row = new ArrayList<>();
    
    InlineKeyboardButton cancelBtn = new InlineKeyboardButton("❌ Отмена");
    String cancelCallbackData = "attach_file_cancel_add_" + eventId;
    cancelBtn.setCallbackData(cancelCallbackData);
    row.add(cancelBtn);
    
    rows.add(row);
    keyboard.setKeyboard(rows);
    
    log.debug("Inline клавиатура для загрузки вложения создана: eventId={}, cancelCallback='{}'", 
            eventId, cancelCallbackData);
    
    return keyboard;
}
```

### TelegramMessageService

Методы `editMessageText()` и `tryEditMessageText()` уже существуют в сервисе и не требуют изменений. Они уже поддерживают:
- Редактирование сообщений с MarkdownV2
- Обработку ошибок (сообщение удалено, слишком старое)
- Retry механизм с экспоненциальной задержкой

### ConversationStateService

Методы `setAwaitingFile()` и `clearAwaitingFile()` уже существуют и не требуют изменений. Они уже поддерживают:
- Сохранение контекста (eventId, chatId, messageId)
- Проверку состояния ожидания файла
- Очистку состояния

### UpdateProcessor

#### Изменения в handleAttachmentFileUpload()

**Текущая реализация:**
```java
private void handleAttachmentFileUpload(Message message, User user) {
    // ... извлечение информации о файле ...
    
    // Сохранение вложения
    Attachment attachment = attachmentService.saveAttachment(eventId, fileId, fileName, fileType, fileSize);
    
    // Обновление списка вложений
    editOrSendMessage(chatId, messageId, messageBuilder.toString(), keyboard, userId, eventId);
    
    // Очистка состояния
    conversationStateService.clearAwaitingFile(userId);
}
```

**Новая реализация:**
```java
private void handleAttachmentFileUpload(Message message, User user) {
    // ... извлечение информации о файле ...
    
    // Сохранение вложения
    Attachment attachment = attachmentService.saveAttachment(eventId, fileId, fileName, fileType, fileSize);
    
    log.info("Вложение успешно сохранено: attachmentId={}, eventId={}, userId={}", 
            attachment.getId(), eventId, userId);
    
    // НОВОЕ: Удаление сообщения пользователя с файлом
    try {
        messageService.deleteMessage(chatId, message.getMessageId());
        log.debug("Сообщение пользователя с файлом удалено: chatId={}, messageId={}, userId={}", 
                chatId, message.getMessageId(), userId);
    } catch (TelegramApiException e) {
        log.warn("Не удалось удалить сообщение пользователя с файлом: chatId={}, messageId={}, error={}", 
                chatId, message.getMessageId(), e.getMessage());
        // Продолжаем выполнение - удаление не критично
    }
    
    // Обновление списка вложений (сообщение с инструкцией обновляется на список вложений)
    try {
        Event event = eventService.getEventById(eventId);
        List<Attachment> attachments = attachmentService.getEventAttachments(eventId);
        
        boolean isCreator = event.belongsToUser(userId);
        
        // Формирование сообщения со списком вложений
        String attachmentsMessage = buildAttachmentsListMessage(event, attachments);
        InlineKeyboardMarkup keyboard = keyboardService.createAttachmentsListKeyboard(
            eventId, attachments, isCreator);
        
        // Редактирование сообщения с инструкцией на список вложений
        Integer resultMessageId = editOrSendMessage(chatId, messageId, attachmentsMessage, 
                keyboard, userId, eventId);
        
        log.debug("Список вложений обновлен: eventId={}, messageId={}", eventId, resultMessageId);
        
    } catch (TelegramApiException e) {
        log.warn("Не удалось обновить список вложений: eventId={}, messageId={}, error={}", 
                eventId, messageId, e.getMessage());
        // Продолжаем выполнение
    }
    
    // Очистка состояния ожидания файла
    conversationStateService.clearAwaitingFile(userId);
    log.debug("Состояние ожидания файла очищено: userId={}", userId);
}
```

### TelegramMessageService

#### Новый метод deleteMessage()

Метод уже существует в `TelegramMessageService` и не требует изменений. Он поддерживает:
- Удаление сообщений по chatId и messageId
- Обработку ошибок (сообщение уже удалено, нет прав)
- Логирование операций

## Модели данных

### AwaitingFileContext

Существующая модель в `ConversationStateService`:

```java
@Data
@AllArgsConstructor
public static class AwaitingFileContext {
    /**
     * Идентификатор события, к которому добавляется вложение
     */
    private Long eventId;
    
    /**
     * Идентификатор чата
     */
    private Long chatId;
    
    /**
     * Идентификатор сообщения для редактирования
     */
    private Integer messageId;
}
```

Эта модель уже содержит все необходимые поля для реализации требований.

### Callback Data Format

Формат callback data для новой кнопки "Отмена":

```
attach_file_cancel_add_{eventId}
```

Где:
- `attach_file` - префикс для всех callback'ов вложений
- `cancel` - действие (action)
- `add` - подействие (subAction), указывающее что отменяется добавление
- `{eventId}` - идентификатор события

Примеры:
- `attach_file_cancel_add_123` - отмена добавления вложения к событию с ID=123
- `attach_file_cancel_add_456` - отмена добавления вложения к событию с ID=456

## Свойства корректности

Свойство - это характеристика или поведение, которое должно выполняться для всех допустимых выполнений системы - по сути, формальное утверждение о том, что система должна делать. Свойства служат мостом между человекочитаемыми спецификациями и машинопроверяемыми гарантиями корректности.


### Свойства

**Property 1: Редактирование вместо создания нового сообщения**

*For any* события и пользователя-создателя, при нажатии кнопки "Добавить вложение" система должна вызвать метод редактирования сообщения (tryEditMessageText), а не отправку нового сообщения (sendMessage), если редактирование успешно.

**Validates: Requirements 1.1**

---

**Property 2: Инструкция содержит все необходимые элементы**

*For any* события, текст инструкции по загрузке файла должен содержать:
- Заголовок "Отправьте файл для прикрепления к событию"
- Максимальный размер файла "20 МБ"
- Список поддерживаемых типов: "Документы", "Фотографии", "Видео", "Аудио"
- MarkdownV2 форматирование (символы * и _)

**Validates: Requirements 1.2, 5.1, 5.2, 5.3, 5.4**

---

**Property 3: Полное сохранение контекста**

*For any* события, пользователя и сообщения, после вызова handleAddFile состояние ConversationState должно содержать корректные значения eventId, chatId и messageId.

**Validates: Requirements 1.3, 3.1, 3.2, 3.3**

---

**Property 4: Клавиатура содержит кнопку отмены**

*For any* события, клавиатура для режима загрузки вложения должна содержать кнопку с текстом "❌ Отмена" и callback data в формате "attach_file_cancel_add_{eventId}".

**Validates: Requirements 2.1, 6.1**

---

**Property 5: Полное восстановление карточки события**

*For any* события, после нажатия кнопки "Отмена" отредактированное сообщение должно содержать:
- Название события
- Дату и время события
- Описание события (если присутствует)
- Использовать тот же формат, что и BotMessageBuilder.buildEventMessage()

**Validates: Requirements 2.2, 7.1, 7.2, 7.3**

---

**Property 6: Очистка состояния при отмене**

*For any* пользователя, после вызова handleCancelAddFile состояние ожидания файла (isAwaitingFile) должно быть очищено.

**Validates: Requirements 2.3**

---

**Property 7: Восстановление стандартной клавиатуры**

*For any* события и пользователя, после нажатия кнопки "Отмена" клавиатура должна содержать стандартные кнопки управления событием: "✏️ Редактировать", "🗑️ Удалить", "📎 Вложения".

**Validates: Requirements 2.4, 7.4**

---

**Property 8: Использование сохраненного контекста при восстановлении**

*For any* события, при вызове handleCancelAddFile система должна использовать те же значения messageId, chatId и eventId, которые были сохранены в ConversationState при вызове handleAddFile.

**Validates: Requirements 3.4**

---

**Property 9: Fallback при неудачном редактировании**

*For any* события, если tryEditMessageText возвращает false (сообщение удалено или слишком старое), система должна отправить новое сообщение через sendMessageAndGet и обновить messageId в состоянии.

**Validates: Requirements 4.1, 4.2, 4.4**

---

**Property 10: Корректный парсинг callback data для отмены**

*For any* валидного callback data в формате "attach_file_cancel_add_{eventId}", система должна корректно извлечь eventId и вызвать handleCancelAddFile.

**Validates: Requirements 6.2, 6.3**

---

**Property 11: Отказ в доступе не создателю**

*For any* пользователя, не являющегося создателем события, при попытке добавить вложение система должна:
- Отправить callback ответ "❌ Нет прав доступа"
- Не вызывать tryEditMessageText
- Не вызывать setAwaitingFile
- Не изменять состояние ConversationState

**Validates: Requirements 8.1, 8.2, 8.3, 8.4**

---

**Property 12: Удаление сообщения пользователя с файлом**

*For any* файла, загруженного пользователем в режиме ожидания вложения, после успешного сохранения вложения система должна:
- Вызвать метод deleteMessage для удаления сообщения пользователя
- Залогировать успешное удаление на уровне DEBUG
- Продолжить выполнение даже если удаление не удалось
- Обновить сообщение с инструкцией, показав актуальный список вложений

**Validates: Requirements 9.1, 9.2, 9.3, 9.4, 9.5**

## Обработка ошибок

### Ошибки редактирования сообщения

**Сценарий 1: Сообщение удалено пользователем**
- `tryEditMessageText()` возвращает `false`
- Система отправляет новое сообщение через `sendMessageAndGet()`
- Обновляет `messageId` в `ConversationState`
- Логирует событие на уровне INFO

**Сценарий 2: Сообщение слишком старое (>48 часов)**
- `tryEditMessageText()` возвращает `false`
- Система отправляет новое сообщение через `sendMessageAndGet()`
- Обновляет `messageId` в `ConversationState`
- Логирует событие на уровне INFO

**Сценарий 3: Ошибка парсинга MarkdownV2**
- `TelegramMessageService` автоматически делает fallback на plain text
- Сообщение отправляется без форматирования
- Логирует событие на уровне WARN
- Метрика "markdown_parse_error_fallback" инкрементируется

### Ошибки прав доступа

**Сценарий: Пользователь не является создателем события**
- Проверка выполняется в начале `handleAddFile()`
- Отправляется callback ответ "❌ Нет прав доступа"
- Метод завершается без изменения состояния
- Логирует попытку на уровне WARN

### Ошибки состояния

**Сценарий: Событие не найдено**
- `EventService.getEventById()` выбрасывает исключение
- Исключение обрабатывается глобальным обработчиком
- Пользователю отправляется сообщение об ошибке
- Логирует ошибку на уровне ERROR

## Стратегия тестирования

### Dual Testing Approach

Для обеспечения комплексного покрытия используется двойной подход к тестированию:

**Unit Tests:**
- Проверка конкретных примеров и граничных случаев
- Тестирование обработки ошибок
- Проверка интеграции между компонентами
- Мокирование зависимостей для изоляции

**Property-Based Tests:**
- Проверка универсальных свойств на множестве входных данных
- Генерация случайных событий, пользователей, messageId
- Минимум 100 итераций на тест
- Тег формата: **Feature: attachment-upload-message-improvement, Property {N}: {property_text}**

### Unit Testing Focus

**Примеры для unit-тестов:**
1. Проверка формата инструкции с конкретным текстом
2. Проверка callback data для конкретного eventId
3. Проверка логирования при ошибках (через mock logger)
4. Проверка вызова методов сервисов (через Mockito)

**Граничные случаи:**
1. eventId = 1 (минимальное значение)
2. eventId = Long.MAX_VALUE (максимальное значение)
3. Событие без описания
4. Событие с очень длинным описанием

**Обработка ошибок:**
1. Сообщение удалено пользователем
2. Сообщение старше 48 часов
3. Событие не найдено
4. Пользователь не является создателем

### Property-Based Testing Configuration

**Библиотека:** jqwik (для Java)

**Конфигурация тестов:**
```java
@Property(tries = 100)
@Label("Feature: attachment-upload-message-improvement, Property 1: Редактирование вместо создания нового сообщения")
void shouldEditMessageInsteadOfSendingNew(@ForAll Event event, @ForAll User creator) {
    // Arrange
    assume(event.getUser().getId().equals(creator.getId()));
    
    // Act
    handler.handleAddFile(event.getId(), creator, chatId, messageId, callbackQueryId);
    
    // Assert
    verify(messageService).tryEditMessageText(eq(chatId), eq(messageId), anyString(), any());
    verify(messageService, never()).sendMessage(eq(chatId), anyString());
}
```

**Генераторы данных:**
- `EventArbitrary` - генерация случайных событий
- `UserArbitrary` - генерация случайных пользователей
- `MessageIdArbitrary` - генерация случайных messageId (1 до Integer.MAX_VALUE)
- `ChatIdArbitrary` - генерация случайных chatId

### Integration Testing

**Тестирование с реальными компонентами:**
1. Интеграция AttachmentCallbackHandler + ConversationStateService
2. Интеграция с реальной БД через Testcontainers
3. Проверка транзакционности операций
4. Проверка корректности сохранения и извлечения состояния

### Test Coverage Goals

- **Unit tests:** 80%+ покрытие строк кода
- **Property tests:** Покрытие всех 11 свойств корректности
- **Integration tests:** Покрытие основных сценариев использования
- **Edge cases:** Покрытие всех граничных случаев и ошибок

### Testing Strategy Summary

Комбинация unit-тестов и property-based тестов обеспечивает:
- **Конкретность:** Unit-тесты проверяют специфические примеры
- **Общность:** Property-тесты проверяют универсальные правила
- **Надежность:** Оба подхода дополняют друг друга
- **Уверенность:** Высокое покрытие критических путей выполнения
