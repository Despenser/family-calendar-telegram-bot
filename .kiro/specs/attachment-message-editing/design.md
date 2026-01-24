# Документ проектирования: Редактирование сообщений с вложениями

## Обзор

Данный документ описывает проектное решение для реализации функциональности редактирования сообщений при работе с вложениями в Telegram боте. Вместо создания новых сообщений при каждой операции с вложениями, система будет редактировать одно и то же сообщение, сохраняя чистоту чата и улучшая пользовательский опыт.

### Цели проектирования

1. Минимизировать количество сообщений в чате при работе с вложениями
2. Обеспечить плавный пользовательский опыт через редактирование сообщений
3. Корректно обрабатывать случаи, когда редактирование невозможно
4. Сохранять контекст диалога между операциями

### Ограничения

- Telegram API позволяет редактировать сообщения только в течение 48 часов
- Пользователь может удалить сообщение, что сделает редактирование невозможным
- Необходимо сохранять messageId между операциями для возможности редактирования

## Архитектура

### Компоненты системы

```
┌─────────────────────────────────────────────────────────────┐
│                    AttachmentCallbackHandler                 │
│  - handleAttachmentList()                                    │
│  - handleAddFile()                                           │
│  - handleDeleteFile()                                        │
│  - handleConfirmDelete()                                     │
│  - handleCancelDelete()                                      │
└────────────────┬────────────────────────────────────────────┘
                 │
                 ├──────────────────┐
                 │                  │
                 ▼                  ▼
┌────────────────────────┐  ┌──────────────────────────┐
│ ConversationStateService│  │ TelegramMessageService   │
│  - saveAttachmentMsg()  │  │  - tryEditMessageText()  │
│  - getAttachmentMsg()   │  │  - editMessageText()     │
│  - clearAttachmentMsg() │  │  - sendMessage()         │
└────────────────────────┘  └──────────────────────────┘
```

### Поток данных

1. **Открытие списка вложений:**
   - Пользователь нажимает кнопку "Вложения" в карточке события
   - AttachmentCallbackHandler получает messageId из CallbackQuery
   - Система редактирует сообщение события, отображая список вложений
   - messageId сохраняется в ConversationState

2. **Добавление вложения:**
   - Пользователь нажимает "Добавить файл"
   - Система сохраняет messageId в ConversationState
   - Пользователь отправляет файл
   - Система редактирует сохраненное сообщение с обновленным списком
   - При ошибке редактирования отправляется новое сообщение

3. **Удаление вложения:**
   - Пользователь нажимает "Удалить" на вложении
   - Система редактирует сообщение с запросом подтверждения
   - При подтверждении система редактирует сообщение с обновленным списком
   - При отмене система редактирует сообщение обратно к списку

## Компоненты и интерфейсы

### ConversationStateService

Расширение существующего сервиса для хранения messageId вложений.

```java
public class ConversationStateService {
    
    /**
     * Сохраняет messageId сообщения с вложениями для пользователя
     * 
     * @param userId идентификатор пользователя
     * @param eventId идентификатор события
     * @param chatId идентификатор чата
     * @param messageId идентификатор сообщения для редактирования
     */
    public void saveAttachmentMessageId(Long userId, Long eventId, 
                                       Long chatId, Integer messageId);
    
    /**
     * Получает сохраненный messageId сообщения с вложениями
     * 
     * @param userId идентификатор пользователя
     * @return AttachmentMessageContext с chatId и messageId, или null
     */
    public AttachmentMessageContext getAttachmentMessageContext(Long userId);
    
    /**
     * Очищает сохраненный messageId сообщения с вложениями
     * 
     * @param userId идентификатор пользователя
     */
    public void clearAttachmentMessageContext(Long userId);
}
```

### AttachmentMessageContext

Новый класс для хранения контекста сообщения с вложениями.

```java
public class AttachmentMessageContext {
    private Long eventId;
    private Long chatId;
    private Integer messageId;
    private Instant createdAt;
    
    // Конструкторы, геттеры, сеттеры
    
    /**
     * Проверяет, не устарел ли контекст (старше 47 часов)
     * Оставляем запас в 1 час до лимита Telegram в 48 часов
     */
    public boolean isExpired() {
        return Duration.between(createdAt, Instant.now())
                      .toHours() > 47;
    }
}
```

### AttachmentCallbackHandler

Модификация существующего обработчика для использования редактирования.

```java
public class AttachmentCallbackHandler {
    
    /**
     * Обрабатывает отображение списка вложений с редактированием сообщения
     */
    private void handleAttachmentList(Long eventId, User user, Long chatId, 
                                     Integer messageId, String callbackQueryId) {
        // 1. Получить список вложений
        // 2. Сформировать текст сообщения
        // 3. Создать клавиатуру
        // 4. Попытаться отредактировать сообщение
        // 5. При успехе - сохранить messageId в ConversationState
        // 6. При ошибке - отправить новое сообщение и сохранить новый messageId
    }
    
    /**
     * Обрабатывает начало добавления файла с сохранением контекста
     */
    private void handleAddFile(Long eventId, User user, Long chatId, 
                              Integer messageId, String callbackQueryId) {
        // 1. Проверить права доступа
        // 2. Сохранить messageId в ConversationState
        // 3. Установить состояние ожидания файла
        // 4. Отправить инструкцию (новое сообщение)
    }
    
    /**
     * Обрабатывает запрос подтверждения удаления с редактированием
     */
    private void handleDeleteFile(Long attachmentId, Long eventId, User user, 
                                 Long chatId, Integer messageId, String callbackQueryId) {
        // 1. Проверить права доступа
        // 2. Получить информацию о вложении
        // 3. Сформировать текст подтверждения
        // 4. Создать клавиатуру подтверждения
        // 5. Попытаться отредактировать сообщение
        // 6. При ошибке - отправить новое сообщение
    }
    
    /**
     * Обрабатывает подтверждение удаления с редактированием списка
     */
    private void handleConfirmDelete(Long attachmentId, Long eventId, User user, 
                                    Long chatId, Integer messageId, String callbackQueryId) {
        // 1. Удалить вложение
        // 2. Получить обновленный список вложений
        // 3. Попытаться отредактировать сообщение с новым списком
        // 4. При ошибке - отправить новое сообщение
    }
    
    /**
     * Обрабатывает отмену удаления с возвратом к списку
     */
    private void handleCancelDelete(Long eventId, User user, Long chatId, 
                                   Integer messageId, String callbackQueryId) {
        // 1. Получить список вложений
        // 2. Попытаться отредактировать сообщение обратно к списку
        // 3. При ошибке - отправить новое сообщение
    }
    
    /**
     * Вспомогательный метод для редактирования или отправки нового сообщения
     */
    private Integer editOrSendMessage(Long chatId, Integer messageId, 
                                     String text, InlineKeyboardMarkup keyboard,
                                     Long userId, Long eventId) {
        // 1. Попытаться отредактировать сообщение
        // 2. Если успешно - вернуть тот же messageId
        // 3. Если не удалось - отправить новое сообщение
        // 4. Сохранить новый messageId в ConversationState
        // 5. Вернуть новый messageId
    }
}
```

### FileMessageHandler

Модификация обработчика файлов для использования сохраненного messageId.

```java
public class FileMessageHandler {
    
    /**
     * Обрабатывает полученный файл с редактированием сохраненного сообщения
     */
    public void handleFileMessage(Message message, User user) {
        // 1. Получить контекст из ConversationState
        // 2. Сохранить файл в БД
        // 3. Получить обновленный список вложений
        // 4. Попытаться отредактировать сохраненное сообщение
        // 5. При ошибке - отправить новое сообщение
        // 6. Очистить состояние ожидания файла
    }
}
```

## Модели данных

### Расширение ConversationState

Добавление полей для хранения контекста сообщения с вложениями:

```java
@Entity
@Table(name = "conversation_states")
public class ConversationState {
    // Существующие поля...
    
    @Column(name = "attachment_event_id")
    private Long attachmentEventId;
    
    @Column(name = "attachment_chat_id")
    private Long attachmentChatId;
    
    @Column(name = "attachment_message_id")
    private Integer attachmentMessageId;
    
    @Column(name = "attachment_context_created_at")
    private Instant attachmentContextCreatedAt;
    
    // Геттеры и сеттеры...
}
```

### Миграция базы данных

```sql
-- Добавление полей для хранения контекста сообщения с вложениями
ALTER TABLE conversation_states 
ADD COLUMN attachment_event_id BIGINT,
ADD COLUMN attachment_chat_id BIGINT,
ADD COLUMN attachment_message_id INTEGER,
ADD COLUMN attachment_context_created_at TIMESTAMP;

-- Индекс для быстрого поиска по userId
CREATE INDEX idx_conversation_states_user_id 
ON conversation_states(user_id);
```

## Correctness Properties

*Свойство корректности (property) - это характеристика или поведение, которое должно выполняться для всех допустимых выполнений системы. По сути, это формальное утверждение о том, что система должна делать. Свойства служат мостом между человекочитаемыми спецификациями и машинно-проверяемыми гарантиями корректности.*

### Property 1: Сохранение messageId при операциях с вложениями

*Для любой* операции с вложениями (отображение списка, добавление файла, удаление), когда операция начинается с валидным messageId, система должна сохранить этот messageId в состоянии диалога пользователя.

**Validates: Requirements 1.1, 9.1**

### Property 2: Использование сохраненного messageId

*Для любого* действия пользователя с вложениями, если в состоянии диалога сохранен messageId, система должна использовать именно этот messageId для редактирования сообщения.

**Validates: Requirements 1.2, 9.2**

### Property 3: Fallback при отсутствии messageId

*Для любой* операции с вложениями, если messageId отсутствует в состоянии диалога или контекст истек, система должна переключиться в fallback режим и отправить новое сообщение.

**Validates: Requirements 1.3**

### Property 4: Редактирование после успешной операции

*Для любой* успешно завершенной операции с вложениями (добавление, удаление), система должна отредактировать существующее сообщение с обновленным списком вложений, а не создавать новое сообщение.

**Validates: Requirements 2.1, 3.1, 9.3**

### Property 5: Fallback при невозможности редактирования

*Для любой* попытки редактирования сообщения, если редактирование невозможно (сообщение удалено, слишком старое, или другая ошибка API), система должна отправить новое сообщение и сохранить новый messageId в состоянии диалога.

**Validates: Requirements 2.2, 3.2, 4.3, 5.1, 5.2**

### Property 6: Обновление клавиатуры при изменении списка

*Для любого* изменения списка вложений (добавление, удаление), отредактированное или новое сообщение должно содержать клавиатуру с актуальными кнопками, соответствующими текущему состоянию списка.

**Validates: Requirements 2.3**

### Property 7: Редактирование для запроса подтверждения

*Для любого* запроса удаления вложения, система должна отредактировать текущее сообщение, заменив список вложений на запрос подтверждения с кнопками "Да, удалить" и "Отмена".

**Validates: Requirements 4.1**

### Property 8: Round-trip при отмене удаления

*Для любого* запроса подтверждения удаления, если пользователь отменяет операцию, система должна отредактировать сообщение обратно к списку вложений, восстанавливая исходное состояние.

**Validates: Requirements 4.2**

### Property 9: Изоляция состояния между пользователями

*Для любых* двух разных пользователей, сохраненные messageId в состоянии диалога должны быть изолированы - изменение messageId одного пользователя не должно влиять на messageId другого пользователя.

**Validates: Requirements 6.1**

### Property 10: Обновление messageId при смене события

*Для любого* пользователя, при переходе от одного события к другому, система должна обновить сохраненный messageId в состоянии диалога на messageId нового события.

**Validates: Requirements 6.2**

### Property 11: Очистка messageId при возврате к карточке

*Для любого* пользователя, при возврате из списка вложений к карточке события, система должна очистить сохраненный messageId из состояния диалога.

**Validates: Requirements 6.3**

### Property 12: Редактирование при открытии списка вложений

*Для любого* события, при открытии списка вложений из карточки события, система должна отредактировать сообщение карточки события, а не создавать новое сообщение.

**Validates: Requirements 7.1**

### Property 13: Round-trip редактирования при навигации

*Для любого* события, последовательность действий "открыть список вложений → вернуться к карточке события" должна редактировать одно и то же сообщение дважды, не создавая новых сообщений.

**Validates: Requirements 7.2**

### Property 14: Использование messageId из CallbackQuery

*Для любого* callback query с вложениями, если в нем присутствует messageId, система должна использовать этот messageId для редактирования сообщения.

**Validates: Requirements 7.3**

### Property 15: Клавиатура соответствует списку вложений

*Для любого* списка вложений, отображаемая клавиатура должна содержать кнопку для каждого вложения (просмотр/удалить), кнопку "Добавить" (если пользователь - создатель) и кнопку "Назад".

**Validates: Requirements 8.1**

### Property 16: Клавиатура подтверждения удаления

*Для любого* запроса подтверждения удаления вложения, клавиатура должна содержать ровно две кнопки: "Да, удалить" и "Отмена".

**Validates: Requirements 8.2**

### Property 17: Права доступа в клавиатуре

*Для любого* пользователя, просматривающего вложения чужого события, клавиатура не должна содержать кнопки "Добавить" и "Удалить", только кнопки "Просмотр" и "Назад".

**Validates: Requirements 8.4**



## Обработка ошибок

### Ошибки редактирования сообщений

**Сообщение не найдено (Message not found)**
- **Причина:** Пользователь удалил сообщение
- **Обработка:** Переключение в fallback режим, отправка нового сообщения
- **Логирование:** INFO уровень с указанием chatId и messageId
- **Действие:** Сохранение нового messageId в состоянии диалога

**Сообщение слишком старое (Message too old)**
- **Причина:** Прошло более 48 часов с момента отправки
- **Обработка:** Переключение в fallback режим, отправка нового сообщения
- **Логирование:** INFO уровень с указанием возраста сообщения
- **Действие:** Сохранение нового messageId в состоянии диалога

**Сообщение не изменилось (Message not modified)**
- **Причина:** Новое содержимое идентично текущему
- **Обработка:** Считать операцию успешной, не предпринимать дополнительных действий
- **Логирование:** DEBUG уровень
- **Действие:** Сохранить текущий messageId

**Ошибка парсинга MarkdownV2**
- **Причина:** Некорректное экранирование специальных символов
- **Обработка:** Использовать метод `tryEditMessageText` с fallback на plain text
- **Логирование:** WARN уровень с деталями ошибки
- **Действие:** Повторная попытка без форматирования

**Сетевые ошибки**
- **Причина:** Проблемы с подключением к Telegram API
- **Обработка:** Retry механизм (3 попытки с экспоненциальной задержкой)
- **Логирование:** ERROR уровень при исчерпании попыток
- **Действие:** Выброс исключения для обработки на верхнем уровне

### Ошибки состояния диалога

**Контекст истек**
- **Причина:** Прошло более 47 часов с момента создания контекста
- **Обработка:** Очистка контекста, переключение в fallback режим
- **Логирование:** INFO уровень
- **Действие:** Отправка нового сообщения

**Контекст не найден**
- **Причина:** Состояние диалога было очищено или не создавалось
- **Обработка:** Использование messageId из CallbackQuery или fallback режим
- **Логирование:** DEBUG уровень
- **Действие:** Попытка использовать messageId из callback

**Несоответствие события**
- **Причина:** Сохраненный eventId не совпадает с текущим
- **Обработка:** Обновление контекста с новым eventId и messageId
- **Логирование:** DEBUG уровень
- **Действие:** Сохранение нового контекста

### Ошибки прав доступа

**Пользователь не является создателем**
- **Причина:** Попытка добавить/удалить вложение к чужому событию
- **Обработка:** Отклонение операции с сообщением об ошибке
- **Логирование:** WARN уровень с указанием userId и eventId
- **Действие:** Отправка callback answer с текстом "❌ Нет прав доступа"

## Стратегия тестирования

### Двойной подход к тестированию

Система требует комплексного тестирования с использованием как unit-тестов, так и property-based тестов для обеспечения корректности работы редактирования сообщений.

### Unit-тесты

**Фокус на конкретных примерах и edge cases:**

1. **Тесты сохранения и извлечения messageId**
   - Сохранение messageId для пользователя
   - Извлечение сохраненного messageId
   - Очистка messageId
   - Изоляция между пользователями

2. **Тесты истечения контекста**
   - Контекст не истек (< 47 часов)
   - Контекст истек (> 47 часов)
   - Граничное значение (ровно 47 часов)

3. **Тесты обработки ошибок редактирования**
   - Сообщение не найдено
   - Сообщение слишком старое
   - Сообщение не изменилось
   - Ошибка парсинга MarkdownV2

4. **Тесты клавиатуры**
   - Клавиатура для создателя события
   - Клавиатура для не-создателя события
   - Клавиатура для пустого списка вложений
   - Клавиатура подтверждения удаления

5. **Интеграционные тесты**
   - Полный цикл: открыть список → добавить файл → вернуться к списку
   - Полный цикл: открыть список → удалить файл → подтвердить → вернуться к списку
   - Полный цикл: открыть список → удалить файл → отменить → вернуться к списку

### Property-based тесты

**Конфигурация:** Минимум 100 итераций на тест

**Библиотека:** jqwik для Java (property-based testing framework)

**Property тесты:**

1. **Property 1-3: Управление messageId**
   ```java
   @Property
   @Tag("Feature: attachment-message-editing, Property 1: Сохранение messageId")
   void shouldSaveMessageIdForAnyOperation(@ForAll Long userId, 
                                          @ForAll Long eventId,
                                          @ForAll Integer messageId) {
       // Для любой операции с валидным messageId
       // система должна сохранить его в состоянии диалога
   }
   ```

2. **Property 4-5: Редактирование и fallback**
   ```java
   @Property
   @Tag("Feature: attachment-message-editing, Property 4: Редактирование после операции")
   void shouldEditMessageAfterSuccessfulOperation(@ForAll Event event,
                                                  @ForAll Attachment attachment) {
       // Для любой успешной операции
       // система должна отредактировать сообщение, а не создать новое
   }
   ```

3. **Property 8: Round-trip при отмене**
   ```java
   @Property
   @Tag("Feature: attachment-message-editing, Property 8: Round-trip при отмене")
   void shouldRestoreOriginalStateOnCancel(@ForAll Event event,
                                          @ForAll List<Attachment> attachments) {
       // Для любого запроса удаления с последующей отменой
       // система должна вернуться к исходному списку
   }
   ```

4. **Property 9: Изоляция между пользователями**
   ```java
   @Property
   @Tag("Feature: attachment-message-editing, Property 9: Изоляция состояния")
   void shouldIsolateMessageIdBetweenUsers(@ForAll Long userId1,
                                          @ForAll Long userId2,
                                          @ForAll Integer messageId1,
                                          @ForAll Integer messageId2) {
       // Для любых двух пользователей
       // изменение messageId одного не должно влиять на другого
   }
   ```

5. **Property 15-17: Клавиатура**
   ```java
   @Property
   @Tag("Feature: attachment-message-editing, Property 15: Клавиатура списка")
   void shouldCreateCorrectKeyboardForAttachmentList(@ForAll Event event,
                                                     @ForAll List<Attachment> attachments,
                                                     @ForAll boolean isCreator) {
       // Для любого списка вложений
       // клавиатура должна содержать правильные кнопки
   }
   ```

### Тестирование с моками

**Моки Telegram API:**
- Использовать Mockito для мокирования `TelegramMessageService`
- Проверять вызовы `editMessageText` vs `sendMessage`
- Симулировать различные ошибки API (404, 400, timeout)

**Моки базы данных:**
- Использовать H2 in-memory database для интеграционных тестов
- Использовать @DataJpaTest для тестирования репозиториев
- Проверять корректность сохранения и извлечения состояния диалога

### Покрытие тестами

**Целевые метрики:**
- Line coverage: > 80%
- Branch coverage: > 75%
- Все correctness properties покрыты property-based тестами
- Все edge cases покрыты unit-тестами

### Тестовые данные

**Генераторы для property-based тестов:**
- Генератор валидных userId (положительные Long)
- Генератор валидных eventId (положительные Long)
- Генератор валидных messageId (положительные Integer)
- Генератор списков вложений (0-10 элементов)
- Генератор событий с различными статусами и владельцами

**Фикстуры для unit-тестов:**
- Пользователь-создатель события
- Пользователь-не-создатель события
- События с вложениями и без
- Истекшие и актуальные контексты

## Диаграммы последовательности

### Успешное редактирование при добавлении вложения

```
Пользователь -> AttachmentHandler: Нажать "Добавить файл"
AttachmentHandler -> ConversationState: saveAttachmentMessageId(userId, eventId, chatId, messageId)
AttachmentHandler -> Пользователь: Отправить инструкцию

Пользователь -> FileHandler: Отправить файл
FileHandler -> ConversationState: getAttachmentMessageContext(userId)
ConversationState -> FileHandler: AttachmentMessageContext
FileHandler -> AttachmentService: saveAttachment(file)
FileHandler -> TelegramService: tryEditMessageText(chatId, messageId, newText, keyboard)
TelegramService -> Telegram API: editMessageText
Telegram API -> TelegramService: Success
TelegramService -> FileHandler: true
FileHandler -> ConversationState: saveAttachmentMessageId(userId, eventId, chatId, messageId)
```

### Fallback при невозможности редактирования

```
Пользователь -> AttachmentHandler: Подтвердить удаление
AttachmentHandler -> AttachmentService: deleteAttachment(attachmentId)
AttachmentHandler -> ConversationState: getAttachmentMessageContext(userId)
ConversationState -> AttachmentHandler: AttachmentMessageContext
AttachmentHandler -> TelegramService: tryEditMessageText(chatId, messageId, newText, keyboard)
TelegramService -> Telegram API: editMessageText
Telegram API -> TelegramService: Error 400 (Message not found)
TelegramService -> AttachmentHandler: false
AttachmentHandler -> TelegramService: sendMessage(chatId, newText, keyboard)
TelegramService -> Telegram API: sendMessage
Telegram API -> TelegramService: Message (newMessageId)
TelegramService -> AttachmentHandler: Message
AttachmentHandler -> ConversationState: saveAttachmentMessageId(userId, eventId, chatId, newMessageId)
```

## Миграция существующего кода

### Этапы миграции

1. **Добавление полей в ConversationState**
   - Создать миграцию БД для новых полей
   - Обновить entity класс ConversationState

2. **Расширение ConversationStateService**
   - Добавить методы для работы с attachment message context
   - Добавить проверку истечения контекста

3. **Модификация AttachmentCallbackHandler**
   - Заменить вызовы `sendMessage` на `editOrSendMessage`
   - Добавить сохранение messageId после каждой операции
   - Добавить извлечение messageId из состояния

4. **Модификация FileMessageHandler**
   - Добавить извлечение сохраненного messageId
   - Использовать редактирование вместо отправки нового сообщения

5. **Тестирование**
   - Написать unit-тесты для новых методов
   - Написать property-based тесты для correctness properties
   - Провести интеграционное тестирование

### Обратная совместимость

- Если messageId не сохранен, система работает в fallback режиме (как сейчас)
- Существующие сообщения продолжают работать без изменений
- Новое поведение активируется автоматически при первой операции с вложениями

### Риски и митигация

**Риск:** Потеря messageId при перезапуске приложения
- **Митигация:** Хранение в БД, а не в памяти

**Риск:** Истечение контекста (> 47 часов)
- **Митигация:** Автоматическая проверка и fallback на новое сообщение

**Риск:** Пользователь удаляет сообщение
- **Митигация:** Обработка ошибки "Message not found" и fallback

**Риск:** Конкурентные операции от одного пользователя
- **Митигация:** Использование транзакций при обновлении состояния
