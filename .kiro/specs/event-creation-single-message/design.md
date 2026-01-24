# Документ дизайна

## Обзор

Данный документ описывает дизайн улучшения процесса создания события через команду `/add_event`. Цель - сделать процесс создания события более чистым и удобным, аналогично процессу редактирования события, где весь диалог происходит в одном сообщении бота, а сообщения пользователя удаляются из чата.

### Текущее поведение

В текущей реализации при создании события:
1. Пользователь вызывает `/add_event`
2. Бот отправляет сообщение с выбором типа события
3. Пользователь выбирает дату и время через inline-кнопки (сообщение обновляется)
4. Бот отправляет **новое сообщение** с запросом названия
5. Пользователь отправляет название - **сообщение остается в чате**
6. Бот отправляет **новое сообщение** с запросом описания
7. Пользователь отправляет описание - **сообщение остается в чате**
8. Бот отправляет **новое сообщение** с подтверждением создания

Результат: в чате остается 5+ сообщений (бот + пользователь).

### Желаемое поведение

После улучшения:
1. Пользователь вызывает `/add_event`
2. Бот отправляет сообщение с выбором типа события
3. Пользователь выбирает дату и время через inline-кнопки (сообщение обновляется)
4. Бот **обновляет то же сообщение** с запросом названия
5. Пользователь отправляет название - **сообщение удаляется**, бот **обновляет свое сообщение**
6. Бот **обновляет то же сообщение** с запросом описания
7. Пользователь отправляет описание - **сообщение удаляется**, бот **обновляет свое сообщение**
8. Бот **обновляет то же сообщение** с финальной карточкой события

Результат: в чате остается только 1 сообщение бота с финальной карточкой события.

## Архитектура

### Компоненты, требующие изменений

1. **UpdateProcessor** - обработка текстовых сообщений при создании события
2. **ConversationService** - управление состоянием диалога создания события
3. **TelegramMessageService** - методы для удаления сообщений пользователя
4. **Event** (модель) - использование существующего поля `messageId` для хранения идентификатора сообщения создания

### Поток данных

```
Пользователь отправляет название
         ↓
UpdateProcessor.handleConversationMessage()
         ↓
TelegramMessageService.deleteMessageSilently(userMessageId) ← Удаление сообщения пользователя
         ↓
ConversationService.updateEventTitle(userId, text)
         ↓
TelegramMessageService.editMessageText(creationMessageId, ...) ← Обновление сообщения бота
         ↓
Чат остается чистым
```

## Компоненты и интерфейсы

### 1. UpdateProcessor

**Изменяемый метод**: `handleConversationMessage(Message message, User user)`

**Текущая реализация** (псевдокод):
```java
private void handleConversationMessage(Message message, User user) {
    Event draft = conversationService.getActiveDraft(user.getId());
    ConversationStep step = conversationService.getCurrentStep(draft);
    String text = message.getText();
    Long chatId = message.getChatId();
    
    switch (step) {
        case WAITING_FOR_TITLE -> {
            conversationService.updateEventTitle(user.getId(), text);
            
            // Отправляется НОВОЕ сообщение
            String response = "✅ Название сохранено: " + text + "\n\n" +
                "Теперь отправьте описание события или нажмите кнопку 'Пропустить':";
            InlineKeyboardMarkup skipKeyboard = keyboardService.createSkipDescriptionKeyboard();
            messageService.sendMessageWithInlineKeyboard(chatId, response, skipKeyboard);
        }
        
        case WAITING_FOR_DESCRIPTION -> {
            String description = text.equalsIgnoreCase("пропустить") ? null : text;
            Event completedEvent = conversationService.completeEventCreation(user.getId(), description);
            
            // Отправляется НОВОЕ сообщение
            eventService.sendOrUpdateEventMessage(completedEvent, chatId);
        }
    }
}
```

**Новая реализация** (псевдокод):
```java
private void handleConversationMessage(Message message, User user) {
    Event draft = conversationService.getActiveDraft(user.getId());
    ConversationStep step = conversationService.getCurrentStep(draft);
    String text = message.getText();
    Long chatId = message.getChatId();
    Integer userMessageId = message.getMessageId();
    Long creationMessageId = draft.getMessageId(); // Получаем сохраненный messageId
    
    switch (step) {
        case WAITING_FOR_TITLE -> {
            // 1. Удаляем сообщение пользователя
            messageService.deleteMessageSilently(chatId, userMessageId);
            
            // 2. Обновляем название в черновике
            conversationService.updateEventTitle(user.getId(), text);
            
            // 3. Обновляем ТО ЖЕ сообщение бота
            String response = formatMessage(
                "📅 *Создание нового события*\n\n" +
                "✅ Название: %s\n\n" +
                "Теперь отправьте описание события или нажмите кнопку 'Пропустить':",
                text
            );
            InlineKeyboardMarkup skipKeyboard = keyboardService.createSkipDescriptionKeyboard();
            messageService.editMessageText(chatId, creationMessageId.intValue(), response, skipKeyboard);
        }
        
        case WAITING_FOR_DESCRIPTION -> {
            // 1. Удаляем сообщение пользователя
            messageService.deleteMessageSilently(chatId, userMessageId);
            
            // 2. Обновляем описание и завершаем создание
            String description = text.equalsIgnoreCase("пропустить") ? null : text;
            Event completedEvent = conversationService.completeEventCreation(user.getId(), description);
            
            // 3. Обновляем ТО ЖЕ сообщение бота с финальной карточкой события
            String eventMessage = botMessageBuilder.buildEventMessageWithHeader(completedEvent, 
                eventService.getActiveEventsCount(completedEvent.getUser().getId()));
            InlineKeyboardMarkup keyboard = keyboardService.createEventActionsKeyboard(completedEvent, user.getId());
            messageService.editMessageText(chatId, creationMessageId.intValue(), eventMessage, keyboard);
        }
    }
}
```

### 2. ConversationService

**Изменяемый метод**: `startEventCreation(Long userId)`

**Текущая реализация** (псевдокод):
```java
@Transactional
public Event startEventCreation(Long userId) {
    User user = userRepository.findById(userId)
        .orElseThrow(() -> new IllegalArgumentException("Пользователь не найден"));
    
    Event draft = Event.builder()
        .user(user)
        .family(user.getFamily())
        .status(Event.EventStatus.DRAFT)
        .build();
    
    return eventRepository.save(draft);
}
```

**Новая реализация** (псевдокод):
```java
@Transactional
public Event startEventCreation(Long userId) {
    User user = userRepository.findById(userId)
        .orElseThrow(() -> new IllegalArgumentException("Пользователь не найден"));
    
    Event draft = Event.builder()
        .user(user)
        .family(user.getFamily())
        .status(Event.EventStatus.DRAFT)
        // messageId будет установлен позже через setCreationMessageId()
        .build();
    
    return eventRepository.save(draft);
}

// Новый метод для сохранения messageId сообщения создания
@Transactional
public void setCreationMessageId(Long userId, Long messageId) {
    Event draft = getActiveDraft(userId);
    draft.setMessageId(messageId);
    eventRepository.save(draft);
    
    log.debug("MessageId сообщения создания сохранен: userId={}, messageId={}", userId, messageId);
}
```

### 3. AddEventCommandHandler

**Изменяемый метод**: `handle(Message message, User user)`

**Текущая реализация** (псевдокод):
```java
@Override
@Transactional
public String handle(Message message, User user) {
    Long chatId = message.getChatId();
    
    // Создаем черновик события
    conversationService.startEventCreation(user.getId());
    
    // Показываем выбор типа события
    InlineKeyboardMarkup typeKeyboard = keyboardService.createEventTypeSelectionKeyboard();
    messageService.sendMessageWithInlineKeyboard(chatId, 
        formatMessage("📅 %s\n\nВыберите тип события:", "Создание нового события"), 
        typeKeyboard);
    
    return null;
}
```

**Новая реализация** (псевдокод):
```java
@Override
@Transactional
public String handle(Message message, User user) {
    Long chatId = message.getChatId();
    
    // Создаем черновик события
    conversationService.startEventCreation(user.getId());
    
    // Показываем выбор типа события и СОХРАНЯЕМ messageId
    InlineKeyboardMarkup typeKeyboard = keyboardService.createEventTypeSelectionKeyboard();
    Message sentMessage = messageService.sendMessageWithInlineKeyboardAndGet(chatId, 
        formatMessage("📅 %s\n\nВыберите тип события:", "Создание нового события"), 
        typeKeyboard);
    
    // Сохраняем messageId в черновике для последующих обновлений
    conversationService.setCreationMessageId(user.getId(), sentMessage.getMessageId().longValue());
    
    return null;
}
```

### 4. TelegramMessageService

**Существующий метод**: `deleteMessageSilently(Long chatId, Integer messageId)`

Этот метод уже существует и используется в процессе редактирования события. Он корректно обрабатывает ошибки удаления и логирует их.

**Новый метод**: `sendMessageWithInlineKeyboardAndGet(Long chatId, String text, InlineKeyboardMarkup keyboard)`

```java
/**
 * Отправляет сообщение с inline-клавиатурой и возвращает объект отправленного сообщения.
 * 
 * @param chatId идентификатор чата
 * @param text текст сообщения
 * @param keyboard inline-клавиатура
 * @return объект отправленного сообщения с messageId
 * @throws TelegramApiException если отправка не удалась
 */
public Message sendMessageWithInlineKeyboardAndGet(Long chatId, String text, InlineKeyboardMarkup keyboard) 
        throws TelegramApiException {
    SendMessage sendMessage = SendMessage.builder()
        .chatId(chatId.toString())
        .text(text)
        .parseMode("MarkdownV2")
        .replyMarkup(keyboard)
        .build();
    
    Message sentMessage = bot.execute(sendMessage);
    
    log.debug("Сообщение с inline-клавиатурой отправлено: chatId={}, messageId={}", 
            chatId, sentMessage.getMessageId());
    
    return sentMessage;
}
```

## Модели данных

### Event (существующая модель)

Используется существующее поле `messageId`:

```java
/**
 * Идентификатор сообщения Telegram, связанного с этим событием.
 * Используется для обновления существующего сообщения при редактировании события
 * вместо отправки нового сообщения, что предотвращает засорение чата.
 * 
 * Для черновиков (DRAFT) это поле хранит messageId сообщения создания,
 * которое обновляется на каждом шаге диалога.
 * 
 * Значение NULL возможно в следующих случаях:
 * - Событие создано до внедрения функции редактирования сообщений
 * - Сообщение о событии было удалено пользователем
 * - Событие находится в статусе DRAFT и сообщение еще не отправлено
 */
@Column(name = "message_id")
private Long messageId;
```

**Изменения**: Обновление JavaDoc для указания использования поля в процессе создания события.

## Свойства корректности

*Свойство - это характеристика или поведение, которое должно выполняться во всех допустимых выполнениях системы - по сути, формальное утверждение о том, что должна делать система. Свойства служат мостом между человекочитаемыми спецификациями и машинно-проверяемыми гарантиями корректности.*


### Свойство 1: Удаление сообщений пользователя при вводе данных

*Для любого* текстового сообщения пользователя с названием или описанием события, система должна удалить это сообщение из чата после обработки.

**Валидирует: Требования 1.1, 2.1**

### Свойство 2: Сохранение данных после удаления сообщения

*Для любого* текстового сообщения пользователя с названием или описанием события, после удаления сообщения данные должны быть сохранены в черновике события.

**Валидирует: Требования 1.2, 2.2**

### Свойство 3: Обновление сообщения создания при вводе данных

*Для любого* названия или описания события, после сохранения данных система должна обновить сообщение создания через editMessageText с актуальной информацией.

**Валидирует: Требования 1.3, 3.5**

### Свойство 4: Завершение создания события после ввода описания

*Для любого* описания события (включая пропуск), после сохранения система должна завершить создание события и изменить статус с DRAFT на ACTIVE.

**Валидирует: Требования 2.3**

### Свойство 5: Создание сообщения при начале процесса

*Для любого* пользователя, при вызове команды /add_event система должна создать сообщение создания и сохранить его messageId в черновике.

**Валидирует: Требования 3.1, 4.1**

### Свойство 6: Обновление сообщения при выборе даты и времени

*Для любой* даты и времени, выбранных через inline-кнопки, система должна обновить сообщение создания через editMessageText.

**Валидирует: Требования 3.2, 3.3**

### Свойство 7: Инвариант messageId на протяжении процесса создания

*Для любого* черновика события, messageId сообщения создания должен оставаться неизменным на всех шагах диалога до завершения создания.

**Валидирует: Требования 3.6**

### Свойство 8: Использование сохраненного messageId для обновлений

*Для любого* обновления сообщения создания (при вводе названия, описания, выборе даты/времени), система должна использовать messageId, сохраненный в черновике события.

**Валидирует: Требования 4.2, 4.3**

### Свойство 9: Устойчивость к ошибкам удаления сообщений

*Для любой* ошибки при удалении сообщения пользователя (отсутствие прав, сообщение уже удалено, сетевая ошибка), система должна продолжить обработку и не прерывать процесс создания события.

**Валидирует: Требования 5.4**

## Обработка ошибок

### Ошибки удаления сообщений

Система должна корректно обрабатывать следующие типы ошибок при удалении сообщений пользователя:

1. **Отсутствие прав на удаление** (`TelegramApiRequestException` с кодом 403)
   - Логирование: WARN уровень
   - Действие: Продолжить обработку, сохранить данные, обновить сообщение бота
   - Сообщение лога: "Нет прав для удаления сообщения пользователя: chatId={}, messageId={}, userId={}"

2. **Сообщение уже удалено** (`TelegramApiRequestException` с кодом 400, "message to delete not found")
   - Логирование: DEBUG уровень
   - Действие: Продолжить обработку, сохранить данные, обновить сообщение бота
   - Сообщение лога: "Сообщение пользователя уже удалено: chatId={}, messageId={}, userId={}"

3. **Сетевая ошибка** (`TelegramApiException`)
   - Логирование: ERROR уровень
   - Действие: Продолжить обработку, сохранить данные, обновить сообщение бота
   - Сообщение лога: "Ошибка при удалении сообщения пользователя: chatId={}, messageId={}, userId={}, error={}"

### Ошибки обновления сообщения создания

Система должна корректно обрабатывать ошибки при обновлении сообщения создания:

1. **Сообщение не найдено** (`TelegramApiRequestException` с кодом 400, "message to edit not found")
   - Логирование: WARN уровень
   - Действие: Отправить новое сообщение вместо обновления
   - Сообщение лога: "Сообщение создания не найдено, отправка нового: chatId={}, messageId={}, userId={}"

2. **Сетевая ошибка** (`TelegramApiException`)
   - Логирование: ERROR уровень
   - Действие: Повторить попытку обновления один раз, при неудаче - отправить новое сообщение
   - Сообщение лога: "Ошибка при обновлении сообщения создания: chatId={}, messageId={}, userId={}, error={}"

## Стратегия тестирования

### Двойной подход к тестированию

Для обеспечения корректности реализации будут использоваться:

1. **Unit-тесты** - для проверки конкретных примеров, граничных случаев и обработки ошибок
2. **Property-based тесты** - для проверки универсальных свойств на множестве случайных входных данных

Оба типа тестов дополняют друг друга и необходимы для комплексного покрытия.

### Unit-тестирование

**Фокус unit-тестов:**
- Конкретные примеры корректного поведения
- Граничные случаи (пустые строки, очень длинные названия, специальные символы)
- Обработка ошибок (отсутствие прав, сообщение не найдено, сетевые ошибки)
- Интеграционные точки между компонентами

**Примеры unit-тестов:**

1. `shouldDeleteUserMessageWhenTitleProvided()` - проверка удаления сообщения с названием
2. `shouldDeleteUserMessageWhenDescriptionProvided()` - проверка удаления сообщения с описанием
3. `shouldUpdateCreationMessageWhenTitleSaved()` - проверка обновления сообщения после сохранения названия
4. `shouldCompleteEventCreationWhenDescriptionProvided()` - проверка завершения создания
5. `shouldContinueProcessingWhenDeleteMessageFails()` - проверка устойчивости к ошибкам удаления
6. `shouldHandleVeryLongEventTitle()` - проверка обработки длинных названий
7. `shouldHandleSpecialCharactersInTitle()` - проверка обработки специальных символов
8. `shouldHandleEmptyDescriptionAsSkip()` - проверка обработки пустого описания

### Property-based тестирование

**Библиотека:** jqwik (рекомендуемая библиотека для property-based testing в Java)

**Конфигурация:** Минимум 100 итераций на каждый property-тест

**Формат тега:** `@Tag("Feature: event-creation-single-message, Property {number}: {property_text}")`

**Property-тесты:**

1. **Property 1: Удаление сообщений пользователя**
   ```java
   @Property
   @Tag("Feature: event-creation-single-message, Property 1: Удаление сообщений пользователя при вводе данных")
   void shouldDeleteUserMessageForAnyInput(@ForAll String eventTitle) {
       // Для любого названия события, сообщение пользователя должно быть удалено
   }
   ```

2. **Property 2: Сохранение данных после удаления**
   ```java
   @Property
   @Tag("Feature: event-creation-single-message, Property 2: Сохранение данных после удаления сообщения")
   void shouldSaveDataAfterDeletingMessage(@ForAll String eventTitle) {
       // Для любого названия, данные должны быть сохранены в черновике
   }
   ```

3. **Property 3: Обновление сообщения создания**
   ```java
   @Property
   @Tag("Feature: event-creation-single-message, Property 3: Обновление сообщения создания при вводе данных")
   void shouldUpdateCreationMessageForAnyInput(@ForAll String eventTitle) {
       // Для любого названия, сообщение создания должно быть обновлено
   }
   ```

4. **Property 7: Инвариант messageId**
   ```java
   @Property
   @Tag("Feature: event-creation-single-message, Property 7: Инвариант messageId на протяжении процесса создания")
   void shouldPreserveMessageIdThroughoutCreation(@ForAll String title, @ForAll String description) {
       // messageId должен оставаться неизменным на всех шагах
   }
   ```

5. **Property 9: Устойчивость к ошибкам**
   ```java
   @Property
   @Tag("Feature: event-creation-single-message, Property 9: Устойчивость к ошибкам удаления сообщений")
   void shouldContinueProcessingOnDeleteError(@ForAll String eventTitle) {
       // При любой ошибке удаления, процесс должен продолжаться
   }
   ```

### Интеграционное тестирование

Для проверки взаимодействия компонентов будут использоваться интеграционные тесты с использованием:
- **Testcontainers** - для тестирования с реальной базой данных PostgreSQL
- **MockBot** - для симуляции Telegram Bot API

**Примеры интеграционных тестов:**

1. `shouldCompleteFullEventCreationFlow()` - проверка полного потока создания события от команды до финальной карточки
2. `shouldPreserveMessageIdAcrossSteps()` - проверка сохранения messageId на всех шагах
3. `shouldHandleMultipleUsersCreatingEventsSimultaneously()` - проверка параллельного создания событий разными пользователями

### Покрытие требований тестами

Каждое свойство корректности должно быть покрыто как минимум одним property-based тестом и несколькими unit-тестами для конкретных случаев.

| Свойство | Property-тест | Unit-тесты | Интеграционный тест |
|----------|---------------|------------|---------------------|
| 1. Удаление сообщений | ✓ | ✓ | ✓ |
| 2. Сохранение данных | ✓ | ✓ | ✓ |
| 3. Обновление сообщения | ✓ | ✓ | ✓ |
| 4. Завершение создания | - | ✓ | ✓ |
| 5. Создание сообщения | - | ✓ | ✓ |
| 6. Обновление при выборе даты/времени | - | ✓ | ✓ |
| 7. Инвариант messageId | ✓ | ✓ | ✓ |
| 8. Использование messageId | - | ✓ | ✓ |
| 9. Устойчивость к ошибкам | ✓ | ✓ | - |
