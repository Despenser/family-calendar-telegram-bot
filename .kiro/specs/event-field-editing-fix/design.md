# Документ проектирования

## Обзор

Данный документ описывает проектное решение для улучшения процесса редактирования событий в Telegram боте семейного календаря. В настоящее время при редактировании события (название, описание, дата, время) бот создаёт новые сообщения для каждого шага редактирования, что засоряет чат и ухудшает пользовательский опыт.

Требуется изменить поведение так, чтобы все операции редактирования происходили в рамках одного сообщения - того, которое отображает редактируемое событие. При нажатии кнопки "Редактировать" сообщение о событии должно обновляться, показывая интерфейс редактирования (календарь, выбор времени, инструкцию для ввода текста). После завершения редактирования или отмены сообщение должно вернуться к отображению полной информации о событии.

Решение включает:
1. Обновление сообщения о событии при входе в режим редактирования
2. Сохранение messageId в контексте редактирования для последующего обновления
3. Обновление того же сообщения при выборе даты/времени или вводе текста
4. Кнопка "Отменить" для возврата к просмотру события
5. Автоматическое удаление промежуточных текстовых сообщений пользователя

## Архитектура

### Текущая реализация

```
Пользователь нажимает "Редактировать дату"
    ↓
EventCallbackHandler.handleEditField()
    ↓
Отправляется НОВОЕ сообщение с календарем
    ↓
Пользователь выбирает дату
    ↓
DateTimeCallbackHandler.handleDateSelection()
    ↓
Обновляется событие в БД
    ↓
Отправляется ЕЩЁ ОДНО новое сообщение с обновлённым событием
```

### Новая реализация

```
Пользователь нажимает "Редактировать дату"
    ↓
EventCallbackHandler.handleEditField()
    ↓
ОБНОВЛЯЕТСЯ текущее сообщение, показывая календарь
    ↓
Сохраняется messageId в EditingContext
    ↓
Пользователь выбирает дату
    ↓
DateTimeCallbackHandler.handleDateSelection()
    ↓
Обновляется событие в БД
    ↓
ОБНОВЛЯЕТСЯ то же сообщение, показывая полную информацию о событии
```


## Компоненты и интерфейсы

### 1. ConversationStateService.EditingContext

**Изменения в классе:**

```java
@Data
@AllArgsConstructor
public static class EditingContext {
    /**
     * Идентификатор редактируемого события
     */
    private Long eventId;
    
    /**
     * Идентификатор чата
     */
    private Long chatId;
    
    /**
     * Текущее редактируемое поле
     */
    private EditField currentField;
    
    /**
     * Идентификатор сообщения, в котором происходит редактирование.
     * Используется для обновления того же сообщения при изменениях.
     * НОВОЕ ПОЛЕ
     */
    private Integer messageId;
}
```

**Новые методы в ConversationStateService:**

```java
/**
 * Начинает процесс редактирования события для пользователя с сохранением messageId.
 * 
 * @param userId идентификатор пользователя
 * @param eventId идентификатор редактируемого события
 * @param chatId идентификатор чата
 * @param messageId идентификатор сообщения для редактирования
 */
public void startEventEditing(Long userId, Long eventId, Long chatId, Integer messageId) {
    EditingContext context = new EditingContext(eventId, chatId, null, messageId);
    usersEditingEvents.put(userId, context);
    log.info("Пользователь ID={} начал редактирование события ID={} в сообщении ID={}", 
            userId, eventId, messageId);
}

/**
 * Получает messageId для текущего редактирования.
 * 
 * @param userId идентификатор пользователя
 * @return messageId или null, если пользователь не редактирует событие
 */
public Integer getEditingMessageId(Long userId) {
    EditingContext context = usersEditingEvents.get(userId);
    return context != null ? context.getMessageId() : null;
}
```


### 2. EventCallbackHandler

**Изменения в методе handleEditEvent:**

Этот метод вызывается при нажатии кнопки "Редактировать" на сообщении с событием. Текущая реализация вызывает `myEventsCommandHandler.handleEditCallback()`, который отправляет **новое** сообщение с меню выбора поля для редактирования.

**Проблема:** При нажатии кнопки "Редактировать" создаётся новое сообщение "Редактирование события", вместо обновления текущего сообщения.

**Решение:** Изменить метод `handleEditEvent` так, чтобы он:
1. Сохранял messageId в контексте редактирования
2. Обновлял текущее сообщение через `editMessageText` вместо отправки нового
3. Показывал меню выбора поля для редактирования в том же сообщении

```java
private void handleEditEvent(String callbackData, User user, Long chatId, 
                             Integer messageId, String callbackQueryId) {
    Long userId = user.getId();
    Long eventId = extractEventId(callbackData, CallbackPrefix.EDIT_EVENT);
    
    log.info("Редактирование события ID={} пользователем ID={}", eventId, userId);
    
    try {
        // Получаем событие и проверяем права доступа
        Event event = eventService.getEventById(eventId);
        
        // Проверяем права доступа
        if (!event.getCreatedBy().getId().equals(userId)) {
            messageService.answerCallbackQuery(callbackQueryId, 
                "У вас нет прав для редактирования этого события");
            return;
        }
        
        // ИЗМЕНЕНИЕ: Сохраняем messageId в контексте редактирования
        conversationStateService.startEventEditing(userId, eventId, chatId, messageId);
        
        // Формируем сообщение с текущими данными события и клавиатурой выбора поля
        String message = buildEditFieldSelectionMessage(event);
        InlineKeyboardMarkup keyboard = keyboardService.createEditFieldSelectionKeyboard(eventId);
        
        // ИЗМЕНЕНИЕ: Обновляем ТЕКУЩЕЕ сообщение вместо отправки нового
        messageService.editMessageText(chatId, messageId, message, keyboard);
        messageService.answerCallbackQuery(callbackQueryId, "");
        
    } catch (TelegramApiException e) {
        log.error("Ошибка при редактировании события: eventId={}, userId={}, error={}", 
                 eventId, userId, e.getMessage(), e);
        throw new RuntimeException("Ошибка при редактировании события", e);
    }
}

/**
 * Формирует сообщение с текущими данными события для выбора поля редактирования.
 * 
 * @param event событие для редактирования
 * @return отформатированное сообщение
 */
private String buildEditFieldSelectionMessage(Event event) {
    StringBuilder message = new StringBuilder();
    message.append("📝 Редактирование события\n\n");
    message.append(BotMessageBuilder.buildEventMessage(event));
    message.append("\n\nВыберите поле для редактирования:");
    return message.toString();
}
```

**Изменения в методе handleEditField:**

```java
private void handleEditField(String callbackData, User user, Long chatId, 
                             Integer messageId, String callbackQueryId) {
    Long userId = user.getId();
    try {
        // Извлекаем payload и парсим field и eventId
        String payload = CallbackPrefix.EDIT_FIELD.extractPayload(callbackData);
        String[] parts = payload.split("_", 2);
        
        if (parts.length != 2) {
            log.error("Некорректный формат callback data: {}", callbackData);
            messageService.answerCallbackQuery(callbackQueryId, "Ошибка");
            return;
        }
        
        String field = parts[0];
        Long eventId = Long.parseLong(parts[1]);
        
        log.info("Редактирование поля '{}' события ID={} пользователем ID={}", 
                field, eventId, userId);
        
        // ИЗМЕНЕНИЕ: Сохраняем messageId в контексте редактирования
        conversationStateService.startEventEditing(userId, eventId, chatId, messageId);
        
        // Устанавливаем редактируемое поле
        EditField editField = mapToEditField(field);
        if (editField != null) {
            conversationStateService.setEditingField(userId, editField);
        }
        
        // Формируем сообщение и клавиатуру
        String message;
        InlineKeyboardMarkup keyboard = null;
        
        switch (field) {
            case "date" -> {
                message = "📅 Редактирование даты\n\nВыберите новую дату из календаря:";
                Long familyId = user.getFamily() != null ? user.getFamily().getId() : null;
                if (familyId != null) {
                    LocalDate now = LocalDate.now();
                    keyboard = keyboardService.createCalendarKeyboard(
                        now.getYear(), 
                        now.getMonthValue(), 
                        familyId
                    );
                }
                // Добавляем кнопку "Отменить"
                keyboard = addCancelButton(keyboard, eventId);
            }
            case "time" -> {
                message = "🕐 Редактирование времени\n\nВыберите новое время:";
                keyboard = keyboardService.createHourSelectionKeyboard();
                // Добавляем кнопку "Отменить"
                keyboard = addCancelButton(keyboard, eventId);
            }
            case "title" -> {
                message = "📝 Редактирование названия\n\nОтправьте новое название события:";
                // Создаем клавиатуру только с кнопкой "Отменить"
                keyboard = createCancelOnlyKeyboard(eventId);
            }
            case "description" -> {
                message = "📄 Редактирование описания\n\nОтправьте новое описание события:";
                // Создаем клавиатуру только с кнопкой "Отменить"
                keyboard = createCancelOnlyKeyboard(eventId);
            }
            default -> {
                message = "❌ Неизвестное поле для редактирования";
            }
        }
        
        // ИЗМЕНЕНИЕ: Обновляем ТЕКУЩЕЕ сообщение вместо отправки нового
        messageService.editMessageText(chatId, messageId, message, keyboard);
        messageService.answerCallbackQuery(callbackQueryId, "");
        
    } catch (TelegramApiException e) {
        log.error("Ошибка при редактировании поля: {}", e.getMessage(), e);
        throw new RuntimeException("Ошибка при редактировании поля", e);
    }
}

/**
 * Добавляет кнопку "Отменить" к существующей клавиатуре.
 */
private InlineKeyboardMarkup addCancelButton(InlineKeyboardMarkup keyboard, Long eventId) {
    if (keyboard == null) {
        return createCancelOnlyKeyboard(eventId);
    }
    
    List<List<InlineKeyboardButton>> rows = new ArrayList<>(keyboard.getKeyboard());
    
    // Добавляем кнопку "Отменить" в последнюю строку
    List<InlineKeyboardButton> cancelRow = new ArrayList<>();
    InlineKeyboardButton cancelButton = new InlineKeyboardButton();
    cancelButton.setText("❌ Отменить");
    cancelButton.setCallbackData(CallbackPrefix.EDIT_CANCEL.withPayload(eventId.toString()));
    cancelRow.add(cancelButton);
    rows.add(cancelRow);
    
    keyboard.setKeyboard(rows);
    return keyboard;
}

/**
 * Создает клавиатуру только с кнопкой "Отменить".
 */
private InlineKeyboardMarkup createCancelOnlyKeyboard(Long eventId) {
    InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
    List<List<InlineKeyboardButton>> keyboard = new ArrayList<>();
    
    List<InlineKeyboardButton> row = new ArrayList<>();
    InlineKeyboardButton cancelButton = new InlineKeyboardButton();
    cancelButton.setText("❌ Отменить");
    cancelButton.setCallbackData(CallbackPrefix.EDIT_CANCEL.withPayload(eventId.toString()));
    row.add(cancelButton);
    keyboard.add(row);
    
    markup.setKeyboard(keyboard);
    return markup;
}
```


**Изменения в методе handleEditCancel:**

```java
private void handleEditCancel(String callbackData, Long userId, Long chatId, String callbackQueryId) {
    String eventIdStr = CallbackPrefix.EDIT_CANCEL.extractPayload(callbackData);
    Long eventId = Long.parseLong(eventIdStr);
    
    log.info("Отмена редактирования события ID={} пользователем ID={}", eventId, userId);
    
    try {
        // Получаем messageId из контекста редактирования
        Integer messageId = conversationStateService.getEditingMessageId(userId);
        
        // Очищаем состояние редактирования
        conversationStateService.clearEventEditing(userId);
        
        // Получаем событие для отображения
        Event event = eventService.getEventById(eventId);
        
        if (messageId != null) {
            // ИЗМЕНЕНИЕ: Обновляем то же сообщение, возвращая его к отображению события
            String eventMessage = BotMessageBuilder.buildEventMessage(event);
            InlineKeyboardMarkup keyboard = keyboardService.createEventKeyboard(event);
            messageService.editMessageText(chatId, messageId, eventMessage, keyboard);
        } else {
            // Fallback: если messageId не найден, отправляем новое сообщение
            log.warn("MessageId не найден в контексте редактирования, отправляем новое сообщение");
            eventService.sendOrUpdateEventMessage(event, chatId);
        }
        
        messageService.answerCallbackQuery(callbackQueryId, "Редактирование отменено");
        
    } catch (TelegramApiException e) {
        log.error("Ошибка при отмене редактирования: {}", e.getMessage(), e);
        throw new RuntimeException("Ошибка при отмене редактирования", e);
    }
}
```


### 3. DateTimeCallbackHandler

**Изменения в методе handleDateSelection:**

```java
private void handleDateSelection(String callbackData, Long userId, Long chatId, 
                                 Integer messageId, String callbackQueryId) {
    String dateStr = CallbackPrefix.DATE.extractPayload(callbackData);
    LocalDate date = LocalDate.parse(dateStr);
    
    if (conversationStateService.isEditingEvent(userId)) {
        // Редактирование существующего события
        EditingContext context = conversationStateService.getEditingContext(userId);
        if (context != null && context.getEventId() != null) {
            try {
                // Обновляем дату события
                Event updatedEvent = eventService.updateEventDate(
                    context.getEventId(), userId, date
                );
                
                // ИЗМЕНЕНИЕ: Используем messageId из контекста, а не из callback
                Integer editingMessageId = context.getMessageId();
                
                if (editingMessageId != null) {
                    // Обновляем сообщение о событии в том же сообщении
                    String eventMessage = BotMessageBuilder.buildEventMessage(updatedEvent);
                    InlineKeyboardMarkup keyboard = keyboardService.createEventKeyboard(updatedEvent);
                    messageService.editMessageText(chatId, editingMessageId, eventMessage, keyboard);
                    
                    log.info("Дата события обновлена и сообщение обновлено: eventId={}, messageId={}", 
                            context.getEventId(), editingMessageId);
                } else {
                    // Fallback: если messageId не найден
                    log.warn("MessageId не найден в контексте, используем sendOrUpdateEventMessage");
                    eventService.sendOrUpdateEventMessage(updatedEvent, chatId);
                }
                
                // Очищаем состояние редактирования
                conversationStateService.clearEventEditing(userId);
                
                messageService.answerCallbackQuery(callbackQueryId, "Дата обновлена");
                
            } catch (Exception e) {
                log.error("Ошибка при обновлении даты: {}", e.getMessage(), e);
                throw new RuntimeException("Ошибка при обновлении даты", e);
            }
        }
    } else {
        // Создание нового события (существующая логика без изменений)
        conversationService.updateEventDate(userId, date);
        
        InlineKeyboardMarkup keyboard = keyboardService.createHourSelectionKeyboard();
        String formattedDate = date.format(DATE_FORMATTER);
        String message = messageBuilder.buildDateSelectedMessage(formattedDate);
        
        try {
            messageService.editMessageText(chatId, messageId, message, keyboard);
            messageService.answerCallbackQuery(callbackQueryId, "Дата выбрана");
        } catch (TelegramApiException e) {
            log.error("Ошибка при выборе даты: {}", e.getMessage(), e);
            throw new RuntimeException("Ошибка при выборе даты", e);
        }
    }
}
```

**Изменения в методе handleTimeSelection:**

```java
private void handleTimeSelection(String callbackData, Long userId, Long chatId, 
                                 Integer messageId, String callbackQueryId) {
    String timeStr = callbackData.substring(5); // Убираем "time_"
    LocalTime time = LocalTime.parse(timeStr);
    
    if (conversationStateService.isEditingEvent(userId)) {
        // Редактирование существующего события
        EditingContext context = conversationStateService.getEditingContext(userId);
        if (context != null && context.getEventId() != null) {
            try {
                // Обновляем время события
                Event updatedEvent = eventService.updateEventTime(
                    context.getEventId(), userId, time
                );
                
                // ИЗМЕНЕНИЕ: Используем messageId из контекста
                Integer editingMessageId = context.getMessageId();
                
                if (editingMessageId != null) {
                    // Обновляем сообщение о событии в том же сообщении
                    String eventMessage = BotMessageBuilder.buildEventMessage(updatedEvent);
                    InlineKeyboardMarkup keyboard = keyboardService.createEventKeyboard(updatedEvent);
                    messageService.editMessageText(chatId, editingMessageId, eventMessage, keyboard);
                    
                    log.info("Время события обновлено и сообщение обновлено: eventId={}, messageId={}", 
                            context.getEventId(), editingMessageId);
                } else {
                    // Fallback
                    log.warn("MessageId не найден в контексте, используем sendOrUpdateEventMessage");
                    eventService.sendOrUpdateEventMessage(updatedEvent, chatId);
                }
                
                // Очищаем состояние редактирования
                conversationStateService.clearEventEditing(userId);
                
                messageService.answerCallbackQuery(callbackQueryId, "Время обновлено");
                
            } catch (Exception e) {
                log.error("Ошибка при обновлении времени: {}", e.getMessage(), e);
                throw new RuntimeException("Ошибка при обновлении времени", e);
            }
        }
    } else {
        // Создание нового события (существующая логика без изменений)
        conversationService.updateEventTime(userId, time);
        
        String formattedTime = time.format(TIME_FORMATTER);
        String message = messageBuilder.buildTimeSelectedMessage(formattedTime);
        
        try {
            messageService.editMessageText(chatId, messageId, message, null);
            messageService.answerCallbackQuery(callbackQueryId, "Время выбрано");
        } catch (TelegramApiException e) {
            log.error("Ошибка при выборе времени: {}", e.getMessage(), e);
            throw new RuntimeException("Ошибка при выборе времени", e);
        }
    }
}
```


### 4. UpdateProcessor

**Новая логика обработки текстовых сообщений при редактировании:**

```java
/**
 * Обрабатывает текстовое сообщение от пользователя.
 * Проверяет различные состояния диалога и обрабатывает соответственно.
 */
private void handleTextMessage(Update update, User user) {
    Long userId = user.getId();
    Long chatId = update.getMessage().getChatId();
    Integer userMessageId = update.getMessage().getMessageId();
    String text = update.getMessage().getText();
    
    // Проверяем, редактирует ли пользователь событие
    if (conversationStateService.isEditingEvent(userId)) {
        handleEventFieldEdit(userId, chatId, userMessageId, text);
        return;
    }
    
    // Проверяем, ожидает ли пользователь ввода заметки к завершенному событию
    if (conversationStateService.isAwaitingCompletionNote(userId)) {
        handleCompletionNoteInput(userId, chatId, userMessageId, text);
        return;
    }
    
    // Проверяем, ожидает ли пользователь ввода поискового запроса
    if (conversationStateService.isAwaitingSearchQuery(userId)) {
        handleSearchQueryInput(userId, chatId, userMessageId, text);
        return;
    }
    
    // Остальная логика обработки текстовых сообщений...
}

/**
 * Обрабатывает ввод текста при редактировании поля события.
 * 
 * @param userId идентификатор пользователя
 * @param chatId идентификатор чата
 * @param userMessageId идентификатор сообщения пользователя
 * @param text введенный текст
 */
private void handleEventFieldEdit(Long userId, Long chatId, Integer userMessageId, String text) {
    EditingContext context = conversationStateService.getEditingContext(userId);
    
    if (context == null || context.getCurrentField() == null) {
        log.warn("Контекст редактирования не найден для пользователя ID={}", userId);
        return;
    }
    
    Long eventId = context.getEventId();
    EditField field = context.getCurrentField();
    Integer editingMessageId = context.getMessageId();
    
    log.info("Обработка ввода текста для поля '{}' события ID={} пользователем ID={}", 
            field, eventId, userId);
    
    try {
        Event updatedEvent = null;
        
        // Обновляем соответствующее поле события
        switch (field) {
            case TITLE -> {
                updatedEvent = eventService.updateEventTitle(eventId, userId, text);
                log.debug("Название события обновлено: eventId={}, newTitle='{}'", eventId, text);
            }
            case DESCRIPTION -> {
                updatedEvent = eventService.updateEventDescription(eventId, userId, text);
                log.debug("Описание события обновлено: eventId={}", eventId);
            }
            default -> {
                log.warn("Неподдерживаемое поле для текстового ввода: {}", field);
                return;
            }
        }
        
        if (updatedEvent != null && editingMessageId != null) {
            // Обновляем сообщение о событии
            String eventMessage = BotMessageBuilder.buildEventMessage(updatedEvent);
            InlineKeyboardMarkup keyboard = keyboardService.createEventKeyboard(updatedEvent);
            messageService.editMessageText(chatId, editingMessageId, eventMessage, keyboard);
            
            log.info("Поле '{}' события обновлено и сообщение обновлено: eventId={}, messageId={}", 
                    field, eventId, editingMessageId);
            
            // Удаляем сообщение пользователя с введенным текстом
            try {
                messageService.deleteMessage(chatId, userMessageId);
                log.debug("Сообщение пользователя удалено: messageId={}", userMessageId);
            } catch (TelegramApiException e) {
                log.warn("Не удалось удалить сообщение пользователя: messageId={}, error={}", 
                        userMessageId, e.getMessage());
                // Продолжаем выполнение, даже если удаление не удалось
            }
        }
        
        // Очищаем состояние редактирования
        conversationStateService.clearEventEditing(userId);
        
    } catch (Exception e) {
        log.error("Ошибка при обновлении поля события: userId={}, eventId={}, field={}, error={}", 
                userId, eventId, field, e.getMessage(), e);
        
        // Отправляем сообщение об ошибке
        try {
            String errorMessage = "❌ Произошла ошибка при обновлении " + 
                                (field == EditField.TITLE ? "названия" : "описания") + 
                                " события. Попробуйте еще раз.";
            messageService.sendMessage(chatId, errorMessage);
        } catch (TelegramApiException ex) {
            log.error("Не удалось отправить сообщение об ошибке: {}", ex.getMessage());
        }
    }
}
```


### 5. TelegramMessageService

**Новый метод для удаления сообщений:**

```java
/**
 * Удаляет сообщение из чата.
 * 
 * @param chatId идентификатор чата
 * @param messageId идентификатор сообщения для удаления
 * @throws TelegramApiException если удаление не удалось
 */
public void deleteMessage(Long chatId, Integer messageId) throws TelegramApiException {
    log.debug("Удаление сообщения: chatId={}, messageId={}", chatId, messageId);
    
    DeleteMessage deleteMessage = new DeleteMessage();
    deleteMessage.setChatId(chatId.toString());
    deleteMessage.setMessageId(messageId);
    
    try {
        execute(deleteMessage);
        log.debug("Сообщение успешно удалено: chatId={}, messageId={}", chatId, messageId);
    } catch (TelegramApiException e) {
        log.warn("Не удалось удалить сообщение: chatId={}, messageId={}, error={}", 
                chatId, messageId, e.getMessage());
        throw e;
    }
}
```

### 6. EventService

**Новые методы для обновления отдельных полей:**

```java
/**
 * Обновляет название события.
 * 
 * @param eventId идентификатор события
 * @param userId идентификатор пользователя
 * @param title новое название
 * @return обновленное событие
 */
public Event updateEventTitle(Long eventId, Long userId, String title) {
    Event event = getEventById(eventId);
    
    // Проверка прав доступа
    if (!event.getCreatedBy().getId().equals(userId)) {
        throw new UnauthorizedAccessException("Нет прав для редактирования этого события");
    }
    
    event.setTitle(title);
    Event savedEvent = eventRepository.save(event);
    
    log.info("Название события обновлено: eventId={}, userId={}, newTitle='{}'", 
            eventId, userId, title);
    
    return savedEvent;
}

/**
 * Обновляет описание события.
 * 
 * @param eventId идентификатор события
 * @param userId идентификатор пользователя
 * @param description новое описание
 * @return обновленное событие
 */
public Event updateEventDescription(Long eventId, Long userId, String description) {
    Event event = getEventById(eventId);
    
    // Проверка прав доступа
    if (!event.getCreatedBy().getId().equals(userId)) {
        throw new UnauthorizedAccessException("Нет прав для редактирования этого события");
    }
    
    event.setDescription(description);
    Event savedEvent = eventRepository.save(event);
    
    log.info("Описание события обновлено: eventId={}, userId={}", eventId, userId);
    
    return savedEvent;
}
```


## Модели данных

### ConversationStateService.EditingContext

```java
@Data
@AllArgsConstructor
public static class EditingContext {
    /**
     * Идентификатор редактируемого события
     */
    private Long eventId;
    
    /**
     * Идентификатор чата
     */
    private Long chatId;
    
    /**
     * Текущее редактируемое поле
     */
    private EditField currentField;
    
    /**
     * Идентификатор сообщения, в котором происходит редактирование.
     * Используется для обновления того же сообщения при изменениях.
     */
    private Integer messageId;
}
```

## Свойства корректности

*Свойство - это характеристика или поведение, которое должно выполняться во всех допустимых выполнениях системы - по сути, формальное утверждение о том, что система должна делать. Свойства служат мостом между человекочитаемыми спецификациями и машинопроверяемыми гарантиями корректности.*


### Свойство 1: Редактирование в одном сообщении

*Для любого* события и любого редактируемого поля (название, описание, дата, время), при начале редактирования messageId сообщения должен сохраниться в контексте редактирования и оставаться неизменным на протяжении всего процесса редактирования.

**Validates: Requirements 1.1, 1.2, 1.3, 1.4, 3.1, 4.1**

### Свойство 2: Возврат к просмотру после завершения

*Для любого* события, после завершения редактирования любого поля (успешного обновления или отмены), сообщение должно вернуться к отображению полной информации о событии с тем же messageId.

**Validates: Requirements 1.5, 2.2, 3.3, 4.3**

### Свойство 3: Наличие кнопки отмены в режиме редактирования

*Для любого* режима редактирования (дата, время, название, описание), клавиатура сообщения должна содержать кнопку "Отменить".

**Validates: Requirements 2.1, 7.2, 7.3, 7.4**

### Свойство 4: Сохранение изменений при отмене

*Для любого* события, при нажатии кнопки "Отменить" все ранее сохраненные изменения должны остаться в базе данных, а состояние редактирования должно быть очищено.

**Validates: Requirements 2.3, 2.4, 6.4**

### Свойство 5: Обновление данных в БД

*Для любого* редактируемого поля события, после ввода нового значения (выбора даты/времени или отправки текста), соответствующее поле в базе данных должно быть обновлено с новым значением.

**Validates: Requirements 3.2, 4.2, 5.2**

### Свойство 6: Сохранение контекста редактирования

*Для любого* пользователя, начинающего редактирование события, система должна сохранить eventId, chatId и messageId в EditingContext, и этот контекст должен использоваться для всех последующих операций редактирования.

**Validates: Requirements 6.1, 6.2, 6.3**

### Свойство 7: Удаление промежуточных сообщений

*Для любого* текстового поля (название, описание), после успешного обновления значения, текстовое сообщение пользователя с новым значением должно быть удалено из чата.

**Validates: Requirements 5.4, 8.1, 8.2**

### Свойство 8: Устойчивость к ошибкам удаления

*Для любого* случая, когда система не может удалить сообщение пользователя, система должна продолжить работу без выброса исключения и завершить обновление события.

**Validates: Requirements 8.4**

### Свойство 9: Навигация без создания новых сообщений

*Для любого* интерфейса с навигацией (календарь, выбор времени), при нажатии кнопок навигации messageId должен оставаться неизменным.

**Validates: Requirements 3.4, 4.4**

### Свойство 10: Корректность клавиатуры в зависимости от режима

*Для любого* состояния сообщения (просмотр события, редактирование даты, редактирование времени, ожидание текста), клавиатура должна содержать соответствующие кнопки для данного состояния.

**Validates: Requirements 7.1, 7.2, 7.3, 7.4, 7.5, 2.5**


## Обработка ошибок

### Типы ошибок

1. **Сообщение удалено пользователем во время редактирования**
   - Ошибка: "message to edit not found" при попытке обновить сообщение
   - Обработка: отправка нового сообщения с информацией о событии, обновление messageId в событии
   - Логирование: WARN уровень

2. **Не удалось удалить текстовое сообщение пользователя**
   - Ошибка: TelegramApiException при вызове deleteMessage
   - Обработка: продолжение работы без выброса исключения
   - Логирование: WARN уровень

3. **Контекст редактирования не найден**
   - Ошибка: EditingContext == null при обработке ввода текста
   - Обработка: игнорирование сообщения, логирование предупреждения
   - Логирование: WARN уровень

4. **Ошибка обновления поля события**
   - Ошибка: Exception при вызове eventService.updateEventTitle/Description
   - Обработка: отправка сообщения об ошибке пользователю, очистка состояния
   - Логирование: ERROR уровень

5. **Нет прав для редактирования события**
   - Ошибка: UnauthorizedAccessException
   - Обработка: отправка сообщения об ошибке пользователю
   - Логирование: WARN уровень

### Стратегия обработки

```java
try {
    // Попытка обновления сообщения
    Integer editingMessageId = context.getMessageId();
    
    if (editingMessageId != null) {
        messageService.editMessageText(chatId, editingMessageId, eventMessage, keyboard);
    } else {
        // Fallback: если messageId не найден
        log.warn("MessageId не найден в контексте, используем sendOrUpdateEventMessage");
        eventService.sendOrUpdateEventMessage(updatedEvent, chatId);
    }
    
} catch (TelegramApiRequestException e) {
    if (isMessageNotFoundError(e)) {
        // Сообщение удалено - отправляем новое
        log.info("Сообщение удалено пользователем, отправляем новое");
        eventService.sendOrUpdateEventMessage(updatedEvent, chatId);
    } else {
        throw e;
    }
}
```

## Стратегия тестирования

### Unit тесты

1. **Тест сохранения messageId в контексте**
   - Начало редактирования события
   - Проверка, что messageId сохранен в EditingContext
   - Проверка, что messageId доступен через getEditingMessageId()

2. **Тест обновления сообщения при редактировании даты**
   - Мок события с messageId
   - Выбор новой даты
   - Проверка вызова editMessageText с правильным messageId
   - Проверка, что sendMessage НЕ вызывается

3. **Тест обновления сообщения при редактировании времени**
   - Мок события с messageId
   - Выбор нового времени
   - Проверка вызова editMessageText с правильным messageId
   - Проверка, что sendMessage НЕ вызывается

4. **Тест обновления текстового поля**
   - Установка состояния редактирования названия
   - Отправка текстового сообщения
   - Проверка обновления поля в БД
   - Проверка вызова editMessageText
   - Проверка вызова deleteMessage для сообщения пользователя

5. **Тест отмены редактирования**
   - Начало редактирования
   - Нажатие кнопки "Отменить"
   - Проверка очистки состояния
   - Проверка вызова editMessageText с информацией о событии

6. **Тест наличия кнопки отмены**
   - Для каждого режима редактирования
   - Проверка наличия кнопки "Отменить" в клавиатуре

7. **Тест устойчивости к ошибкам удаления**
   - Мок ошибки при deleteMessage
   - Проверка, что обновление события завершается успешно
   - Проверка логирования предупреждения

### Property-Based тесты

1. **Property Test 1: Сохранение messageId при редактировании**
   - **Property 1: Редактирование в одном сообщении**
   - **Validates: Requirements 1.1, 1.2, 1.3, 1.4**
   - Генерация: случайное событие, случайное поле для редактирования
   - Операция: начало редактирования
   - Проверка: messageId сохранен в контексте и не изменяется
   - Минимум 100 итераций

2. **Property Test 2: Round-trip редактирования**
   - **Property 2: Возврат к просмотру после завершения**
   - **Validates: Requirements 1.5, 2.2**
   - Генерация: случайное событие, случайное новое значение поля
   - Операция: начало редактирования → обновление → завершение
   - Проверка: messageId остался тем же, отображается полная информация
   - Минимум 100 итераций

3. **Property Test 3: Обновление данных в БД**
   - **Property 5: Обновление данных в БД**
   - **Validates: Requirements 3.2, 4.2, 5.2**
   - Генерация: случайное событие, случайное новое значение
   - Операция: обновление поля
   - Проверка: значение в БД соответствует новому значению
   - Минимум 100 итераций

### Integration тесты

1. **Интеграционный тест полного цикла редактирования даты**
   - Создание события
   - Начало редактирования даты
   - Выбор новой даты
   - Проверка обновления в БД
   - Проверка, что messageId не изменился

2. **Интеграционный тест полного цикла редактирования названия**
   - Создание события
   - Начало редактирования названия
   - Отправка нового названия
   - Проверка обновления в БД
   - Проверка удаления текстового сообщения
   - Проверка, что messageId не изменился

3. **Интеграционный тест отмены редактирования**
   - Создание события
   - Начало редактирования
   - Нажатие кнопки "Отменить"
   - Проверка очистки состояния
   - Проверка, что messageId не изменился

### Testing Framework

- **Unit tests**: JUnit 5 + Mockito
- **Property tests**: jqwik (уже используется в проекте)
- **Integration tests**: Spring Boot Test + Testcontainers

Каждый property test будет помечен комментарием:
```java
// Feature: event-field-editing-fix, Property 1: Редактирование в одном сообщении
```


## Примечания по реализации

### 1. Обратная совместимость

- Изменения в EditingContext добавляют новое поле messageId
- Существующий код продолжит работать, но будет использовать старый метод startEventEditing без messageId
- Постепенная миграция на новый метод с messageId
- Fallback на sendOrUpdateEventMessage если messageId не найден

### 2. Производительность

- Операция editMessageText быстрее, чем sendMessage
- Уменьшение количества сообщений в чате улучшает UX
- Удаление промежуточных сообщений снижает нагрузку на чат

### 3. Безопасность

- Проверка прав доступа при обновлении полей события
- Валидация messageId перед использованием
- Обработка всех возможных ошибок Telegram API

### 4. Логирование

- Детальное логирование всех операций редактирования
- Разделение уровней логирования (INFO для нормальных операций, WARN для fallback, ERROR для критических ошибок)
- Логирование messageId для отслеживания обновлений

### 5. Пользовательский опыт

- Все редактирование происходит в одном сообщении
- Кнопка "Отменить" всегда доступна
- Промежуточные сообщения автоматически удаляются
- Чат остается чистым и организованным

### 6. Ограничения Telegram API

- Сообщения старше 48 часов нельзя редактировать
- Удалённые пользователем сообщения нельзя редактировать
- Максимальная длина сообщения: 4096 символов
- Все эти случаи обрабатываются с fallback на отправку нового сообщения

### 7. Состояние диалога

- EditingContext хранит всю необходимую информацию для редактирования
- Состояние автоматически очищается после завершения или отмены
- Поддержка одновременного редактирования только одного события на пользователя

### 8. Интеграция с существующим кодом

- Минимальные изменения в существующих handler'ах
- Использование существующих методов EventService
- Добавление новых методов для обновления отдельных полей
- Централизованное форматирование сообщений через BotMessageBuilder

## Диаграммы последовательности

### Редактирование даты

```
Пользователь                EventCallbackHandler    ConversationStateService    DateTimeCallbackHandler    EventService    TelegramMessageService
    |                              |                           |                           |                    |                    |
    |--"Редактировать дату"------->|                           |                           |                    |                    |
    |                              |--startEventEditing()----->|                           |                    |                    |
    |                              |  (eventId, chatId,        |                           |                    |                    |
    |                              |   messageId)              |                           |                    |                    |
    |                              |                           |                           |                    |                    |
    |                              |--editMessageText()--------|---------------------------|--------------------|------------------->|
    |                              |  (показать календарь)     |                           |                    |                    |
    |<-----------------------------|---------------------------|---------------------------|--------------------|--------------------|
    |  Календарь в том же          |                           |                           |                    |                    |
    |  сообщении                   |                           |                           |                    |                    |
    |                              |                           |                           |                    |                    |
    |--"Выбрать дату"--------------|---------------------------|-------------------------->|                    |                    |
    |                              |                           |                           |--updateEventDate()->|                    |
    |                              |                           |                           |                    |                    |
    |                              |                           |<--getEditingContext()-----|                    |                    |
    |                              |                           |   (получить messageId)    |                    |                    |
    |                              |                           |                           |                    |                    |
    |                              |                           |                           |--editMessageText()->|------------------->|
    |                              |                           |                           |  (показать событие)|                    |
    |<-----------------------------|---------------------------|---------------------------|--------------------|--------------------|
    |  Обновлённое событие         |                           |                           |                    |                    |
    |  в том же сообщении          |                           |                           |                    |                    |
    |                              |                           |<--clearEventEditing()-----|                    |                    |
```

### Редактирование названия

```
Пользователь                EventCallbackHandler    ConversationStateService    UpdateProcessor    EventService    TelegramMessageService
    |                              |                           |                      |                    |                    |
    |--"Редактировать название"--->|                           |                      |                    |                    |
    |                              |--startEventEditing()----->|                      |                    |                    |
    |                              |  (eventId, chatId,        |                      |                    |                    |
    |                              |   messageId)              |                      |                    |                    |
    |                              |                           |                      |                    |                    |
    |                              |--editMessageText()--------|----------------------|--------------------|------------------->|
    |                              |  (инструкция для ввода)   |                      |                    |                    |
    |<-----------------------------|---------------------------|----------------------|--------------------|--------------------|
    |  Инструкция в том же         |                           |                      |                    |                    |
    |  сообщении                   |                           |                      |                    |                    |
    |                              |                           |                      |                    |                    |
    |--"Новое название"------------|---------------------------|--------------------->|                    |                    |
    |                              |                           |<--getEditingContext()|                    |                    |
    |                              |                           |                      |--updateEventTitle()->|                    |
    |                              |                           |                      |                    |                    |
    |                              |                           |                      |--editMessageText()->|------------------->|
    |                              |                           |                      |  (показать событие)|                    |
    |<-----------------------------|---------------------------|----------------------|--------------------|--------------------|
    |  Обновлённое событие         |                           |                      |                    |                    |
    |  в том же сообщении          |                           |                      |                    |                    |
    |                              |                           |                      |--deleteMessage()--->|------------------->|
    |                              |                           |                      |  (удалить текст    |                    |
    |                              |                           |                      |   пользователя)    |                    |
    |                              |                           |<--clearEventEditing()|                    |                    |
```

