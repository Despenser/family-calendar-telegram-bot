# Документ дизайна: Переиспользование сообщения при просмотре вложения

## Обзор

Данный дизайн описывает изменения в механизме просмотра вложений в Telegram боте семейного календаря. Вместо отправки нового сообщения при просмотре вложения, система будет удалять текущее сообщение со списком вложений, отправлять файл новым сообщением и сохранять его Message_Id для последующего редактирования при возврате к списку.

Основная идея: использовать паттерн "удалить-отправить-сохранить" вместо простой отправки нового сообщения, что позволит поддерживать чистоту чата и улучшить пользовательский опыт.

## Архитектура

### Текущая архитектура

```
Пользователь нажимает "Просмотр вложения"
    ↓
AttachmentCallbackHandler.handleViewFile()
    ↓
TelegramMessageService.sendFileWithKeyboard()
    ↓
Новое сообщение с файлом отправлено
    ↓
Старое сообщение со списком остается в чате
```

### Новая архитектура

```
Пользователь нажимает "Просмотр вложения"
    ↓
AttachmentCallbackHandler.handleViewFile()
    ↓
TelegramMessageService.deleteMessage() - удаляем старое сообщение
    ↓
TelegramMessageService.sendFileWithKeyboard() - отправляем файл
    ↓
ConversationStateService.saveAttachmentMessageId() - сохраняем новый messageId
    ↓
Пользователь видит только сообщение с файлом
```

## Компоненты и интерфейсы

### 1. TelegramMessageService

Добавляется новый метод для удаления сообщений:

```java
/**
 * Удаляет сообщение из чата.
 * 
 * <p>Этот метод пытается удалить сообщение и возвращает результат операции.
 * Не выбрасывает исключение, если сообщение не найдено или уже удалено.</p>
 * 
 * <p><b>Обработка ошибок:</b></p>
 * <ul>
 *   <li>Сообщение удалено пользователем - возвращает false</li>
 *   <li>Сообщение не найдено - возвращает false</li>
 *   <li>Успешное удаление - возвращает true</li>
 *   <li>Другие ошибки - выбрасывает TelegramApiException</li>
 * </ul>
 * 
 * <p><b>Требования:</b> 4.1, 4.2, 4.3, 4.4, 4.5</p>
 * 
 * @param chatId ID чата, где находится сообщение
 * @param messageId ID сообщения для удаления
 * @return true если удаление успешно, false если сообщение не найдено/удалено
 * @throws TelegramApiException при других ошибках (сетевые и т.д.)
 * @throws IllegalArgumentException если chatId или messageId null
 */
public boolean deleteMessage(Long chatId, Integer messageId) throws TelegramApiException
```

Вспомогательный метод для проверки ошибки "сообщение не найдено":

```java
/**
 * Проверяет, является ли ошибка "сообщение не найдено для удаления".
 * 
 * <p>Эта ошибка возникает когда:</p>
 * <ul>
 *   <li>Пользователь удалил сообщение</li>
 *   <li>Сообщение не существует</li>
 *   <li>Бот не имеет доступа к сообщению</li>
 * </ul>
 * 
 * <p><b>Требования:</b> 4.2</p>
 * 
 * @param e исключение от Telegram API
 * @return true если это ошибка "сообщение не найдено", false иначе
 */
private boolean isMessageDeleteNotFoundError(TelegramApiRequestException e)
```

### 2. AttachmentCallbackHandler

Модифицируется метод `handleViewFile`:

```java
/**
 * Обрабатывает просмотр файла.
 * 
 * <p>Алгоритм работы:</p>
 * <ol>
 *   <li>Получает вложение из БД</li>
 *   <li>Удаляет текущее сообщение со списком вложений</li>
 *   <li>Отправляет файл новым сообщением с клавиатурой</li>
 *   <li>Сохраняет новый messageId в ConversationState</li>
 * </ol>
 * 
 * <p>Если удаление не удалось (сообщение уже удалено пользователем),
 * продолжает отправку файла без ошибки.</p>
 * 
 * <p><b>Требования:</b> 1.1, 1.2, 1.3, 2.1, 2.2, 2.3, 5.1, 5.2, 5.3, 5.4, 5.5</p>
 * 
 * @param attachmentId идентификатор вложения
 * @param eventId идентификатор события
 * @param user пользователь
 * @param chatId идентификатор чата
 * @param messageId идентификатор текущего сообщения для удаления
 * @param callbackQueryId идентификатор callback query
 */
private void handleViewFile(Long attachmentId, Long eventId, User user, 
                           Long chatId, Integer messageId, String callbackQueryId) throws Exception
```

### 3. ConversationStateService

Используются существующие методы:

```java
/**
 * Сохраняет идентификатор сообщения с вложениями для последующего редактирования.
 * 
 * <p>Этот метод сохраняет контекст сообщения, чтобы при возврате к списку вложений
 * система могла отредактировать то же сообщение вместо создания нового.</p>
 * 
 * <p><b>Требования:</b> 3.1</p>
 * 
 * @param userId идентификатор пользователя
 * @param eventId идентификатор события
 * @param chatId идентификатор чата
 * @param messageId идентификатор сообщения для сохранения
 */
public void saveAttachmentMessageId(Long userId, Long eventId, Long chatId, Integer messageId)
```

## Модели данных

### ConversationState

Используются существующие поля (изменений не требуется):

```java
/**
 * Идентификатор события для контекста вложений.
 */
@ManyToOne(fetch = FetchType.LAZY)
@JoinColumn(name = "attachment_event_id")
private Event attachmentEvent;

/**
 * Идентификатор чата для контекста вложений.
 */
@Column(name = "attachment_chat_id")
private Long attachmentChatId;

/**
 * Идентификатор сообщения для редактирования при работе с вложениями.
 */
@Column(name = "attachment_message_id")
private Integer attachmentMessageId;

/**
 * Время создания контекста вложений.
 */
@Column(name = "attachment_context_created_at")
private Instant attachmentContextCreatedAt;
```

## Свойства корректности

*Свойство - это характеристика или поведение, которое должно выполняться для всех валидных выполнений системы - по сути, формальное утверждение о том, что система должна делать. Свойства служат мостом между человекочитаемыми спецификациями и машинно-проверяемыми гарантиями корректности.*


### Prework анализ выполнен

После анализа acceptance criteria и property reflection, определены следующие свойства корректности:

**Property 1: Последовательность операций при просмотре вложения**
*For any* вложение и пользователь, при вызове handleViewFile система должна сначала удалить текущее сообщение (вызвать deleteMessage), затем отправить файл (вызвать sendFileWithKeyboard), и затем сохранить новый messageId (вызвать saveAttachmentMessageId) - в строго этом порядке.
**Validates: Requirements 1.1, 1.2, 1.3**

**Property 2: Сохранение messageId после отправки файла**
*For any* отправленный файл вложения, система должна сохранить его messageId в ConversationState через метод saveAttachmentMessageId с правильными параметрами (userId, eventId, chatId, messageId).
**Validates: Requirements 1.3, 2.3, 3.1**

**Property 3: Resilience при неудачном удалении**
*For any* ситуация, когда deleteMessage возвращает false (сообщение не найдено или уже удалено), система должна продолжить выполнение и отправить файл через sendFileWithKeyboard без выброса исключения.
**Validates: Requirements 2.1**

**Property 4: Поведение метода deleteMessage**
*For any* вызов deleteMessage с валидными chatId и messageId:
- Должен выполнить запрос DeleteMessage к Telegram API
- Должен вернуть true при успешном удалении
- Должен вернуть false при ошибке "сообщение не найдено"
- Должен выбросить TelegramApiException при других ошибках
**Validates: Requirements 4.2, 4.3, 4.4, 4.5**

**Property 5: Комплексное логирование операций**
*For any* выполнение handleViewFile, система должна логировать:
- DEBUG лог перед удалением сообщения (с chatId, messageId, userId)
- INFO лог при успешном удалении
- WARN лог при неудачном удалении (с причиной)
- INFO лог после отправки файла (с новым messageId)
- DEBUG лог при сохранении messageId в состоянии
**Validates: Requirements 5.1, 5.2, 5.3, 5.4, 5.5**

## Обработка ошибок

### 1. Ошибки удаления сообщения

**Сценарий:** Сообщение не может быть удалено (уже удалено пользователем, не найдено)

**Обработка:**
- `TelegramMessageService.deleteMessage()` возвращает `false`
- `AttachmentCallbackHandler.handleViewFile()` логирует WARN сообщение
- Продолжает выполнение и отправляет файл
- Сохраняет новый messageId в ConversationState

**Код:**
```java
boolean deleted = messageService.deleteMessage(chatId, messageId);
if (!deleted) {
    log.warn("Не удалось удалить сообщение (возможно, уже удалено пользователем): " +
            "chatId={}, messageId={}, userId={}", chatId, messageId, user.getId());
}
// Продолжаем отправку файла независимо от результата удаления
```

### 2. Ошибки отправки файла

**Сценарий:** Файл не может быть отправлен (сетевая ошибка, файл не найден в Telegram)

**Обработка:**
- `TelegramMessageService.sendFileWithKeyboard()` выбрасывает `TelegramApiException`
- `AttachmentCallbackHandler.handleViewFile()` ловит исключение
- Логирует ERROR с деталями
- Отправляет callback answer с сообщением об ошибке
- Отправляет текстовое сообщение пользователю об ошибке

**Код:**
```java
try {
    Message sentMessage = messageService.sendFileWithKeyboard(chatId, attachment.getFileId(), 
            attachment.getFileType(), caption, keyboard);
    // Сохраняем messageId
} catch (TelegramApiException e) {
    log.error("Ошибка Telegram API при отправке файла ID={}: {}", 
            attachmentId, e.getMessage(), e);
    messageService.answerCallbackQuery(callbackQueryId, "❌ Ошибка отправки файла");
    messageService.sendMessage(chatId, 
            "❌ Не удалось отправить файл\\. Попробуйте позже\\.");
}
```

### 3. Ошибки сохранения состояния

**Сценарий:** Не удается сохранить messageId в ConversationState (ошибка БД)

**Обработка:**
- `ConversationStateService.saveAttachmentMessageId()` выбрасывает исключение
- `AttachmentCallbackHandler.handleViewFile()` ловит исключение
- Логирует ERROR с деталями
- Файл уже отправлен пользователю, поэтому просмотр работает
- При возврате к списку будет отправлено новое сообщение (fallback в editOrSendMessage)

**Код:**
```java
try {
    conversationStateService.saveAttachmentMessageId(user.getId(), eventId, 
            chatId, newMessageId);
    log.debug("Message_Id сохранен в ConversationState: userId={}, eventId={}, messageId={}", 
            user.getId(), eventId, newMessageId);
} catch (Exception e) {
    log.error("Ошибка при сохранении messageId в ConversationState: " +
            "userId={}, eventId={}, messageId={}, error={}", 
            user.getId(), eventId, newMessageId, e.getMessage(), e);
    // Не пробрасываем исключение - файл уже отправлен пользователю
}
```

### 4. Ошибки получения вложения

**Сценарий:** Вложение не найдено в БД

**Обработка:**
- `AttachmentService.getAttachment()` выбрасывает `AttachmentNotFoundException`
- `AttachmentCallbackHandler.handleViewFile()` ловит исключение
- Логирует ERROR
- Отправляет callback answer с сообщением об ошибке

**Код:**
```java
try {
    Attachment attachment = attachmentService.getAttachment(attachmentId);
    // Продолжаем обработку
} catch (AttachmentNotFoundException e) {
    log.error("Вложение ID={} не найдено", attachmentId);
    messageService.answerCallbackQuery(callbackQueryId, "❌ Вложение не найдено");
    return;
}
```

## Стратегия тестирования

### Dual Testing Approach

Используется комбинация unit-тестов и property-based тестов для комплексного покрытия:

**Unit-тесты** проверяют:
- Конкретные примеры успешного удаления и отправки
- Обработку специфических ошибок (сообщение не найдено, файл не найден)
- Интеграцию между компонентами
- Edge cases (null параметры, пустые строки)

**Property-тесты** проверяют:
- Универсальные свойства, которые должны выполняться для всех входных данных
- Последовательность операций при различных комбинациях параметров
- Корректность логирования при различных сценариях
- Resilience при различных типах ошибок

### Property-Based Testing Configuration

**Библиотека:** jqwik (рекомендуемая библиотека для property-based testing в Java)

**Конфигурация:**
- Минимум 100 итераций на каждый property-тест
- Каждый тест помечен комментарием с ссылкой на свойство из дизайна
- Формат тега: `// Feature: attachment-view-message-reuse, Property N: <текст свойства>`

**Пример конфигурации теста:**
```java
// Feature: attachment-view-message-reuse, Property 1: Последовательность операций при просмотре вложения
@Property(tries = 100)
void viewAttachment_shouldFollowCorrectOperationSequence(
        @ForAll @LongRange(min = 1) Long attachmentId,
        @ForAll @LongRange(min = 1) Long eventId,
        @ForAll @LongRange(min = 1) Long userId,
        @ForAll @LongRange(min = 1) Long chatId,
        @ForAll @IntRange(min = 1) Integer messageId) {
    // Тест проверяет последовательность вызовов:
    // 1. deleteMessage
    // 2. sendFileWithKeyboard
    // 3. saveAttachmentMessageId
}
```

### Unit Testing Balance

**Unit-тесты фокусируются на:**
1. **Конкретные примеры:**
   - Успешное удаление и отправка файла
   - Неудачное удаление, но успешная отправка
   - Ошибка при отправке файла

2. **Edge cases:**
   - Вложение не найдено
   - Null параметры
   - Некорректный fileType

3. **Интеграционные точки:**
   - Взаимодействие AttachmentCallbackHandler с TelegramMessageService
   - Взаимодействие с ConversationStateService
   - Взаимодействие с AttachmentService

**Property-тесты фокусируются на:**
1. **Универсальные свойства:**
   - Последовательность операций всегда соблюдается
   - MessageId всегда сохраняется после успешной отправки
   - Система всегда resilient к ошибкам удаления

2. **Комплексное покрытие входных данных:**
   - Различные комбинации ID (attachment, event, user, chat, message)
   - Различные типы файлов
   - Различные сценарии ошибок

### Тестовые сценарии

#### Unit-тесты

1. **AttachmentCallbackHandlerTest**
   - `testHandleViewFile_Success()` - успешный просмотр вложения
   - `testHandleViewFile_MessageAlreadyDeleted()` - сообщение уже удалено
   - `testHandleViewFile_AttachmentNotFound()` - вложение не найдено
   - `testHandleViewFile_FileUploadError()` - ошибка отправки файла
   - `testHandleViewFile_StateServiceError()` - ошибка сохранения состояния

2. **TelegramMessageServiceTest**
   - `testDeleteMessage_Success()` - успешное удаление
   - `testDeleteMessage_NotFound()` - сообщение не найдено
   - `testDeleteMessage_NetworkError()` - сетевая ошибка
   - `testDeleteMessage_NullParameters()` - null параметры

#### Property-тесты

1. **AttachmentCallbackHandlerPropertyTest**
   - `testProperty1_OperationSequence()` - Property 1
   - `testProperty2_MessageIdSaving()` - Property 2
   - `testProperty3_ResilienceOnDeleteFailure()` - Property 3
   - `testProperty5_ComprehensiveLogging()` - Property 5

2. **TelegramMessageServicePropertyTest**
   - `testProperty4_DeleteMessageBehavior()` - Property 4

### Метрики покрытия

**Целевые показатели:**
- Line coverage: > 85%
- Branch coverage: > 80%
- Property tests: минимум 100 итераций на тест
- Unit tests: покрытие всех edge cases и error paths
