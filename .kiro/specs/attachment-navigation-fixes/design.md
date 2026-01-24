# Design Document: Attachment Navigation Fixes

## Overview

Данный документ описывает проектное решение для исправления двух критических проблем с навигацией и редактированием сообщений при работе с вложениями событий в Telegram боте семейного календаря.


### Проблемы

1. **Потеря шапки сообщения при возврате к событию**: При нажатии кнопки "Назад к событию" теряется шапка "📋 Мои события" для первого события в списке
2. **Отдельное подтверждающее сообщение при загрузке файла**: После загрузки файла система отправляет два сообщения вместо редактирования одного

### Цели решения

- Сохранять контекст шапки сообщения при навигации между экранами
- Редактировать существующее сообщение вместо отправки нового при загрузке файла
- Добавить кнопку "Назад к вложениям" при просмотре файла
- Обеспечить плавную навигацию без загромождения чата

## Architecture

### Текущая архитектура

Система использует следующие компоненты для работы с вложениями:

1. **AttachmentCallbackHandler** - обработчик callback для операций с вложениями
2. **UpdateProcessor** - обработчик входящих сообщений, включая загрузку файлов
3. **ConversationStateService** - сервис для хранения состояния диалога
4. **BotMessageBuilder** - билдер для формирования сообщений о событиях
5. **TelegramMessageService** - сервис для отправки и редактирования сообщений
6. **KeyboardService** - сервис для создания inline клавиатур

### Механизм редактирования сообщений

Существующий механизм `editOrSendMessage`:
- Пытается отредактировать существующее сообщение через `tryEditMessageText`
- При неудаче отправляет новое сообщение
- Сохраняет новый messageId в ConversationState

### Хранение контекста

ConversationState уже содержит:
- `attachmentEvent` - событие, для которого открыт список вложений
- `attachmentChatId` - идентификатор чата
- `attachmentMessageId` - идентификатор сообщения со списком вложений
- `attachmentContextCreatedAt` - время создания контекста

## Components and Interfaces

### 1. Расширение ConversationState

Добавим новые поля для хранения контекста шапки сообщения в entity ConversationState:

- `eventHasMyEventsHeader` (Boolean) - флаг, указывающий что событие было первым в списке "Мои события"
- `eventCountForHeader` (Integer) - количество событий пользователя на момент открытия события

### 2. Расширение ConversationStateService

Добавим методы:

- `saveEventHeaderContext(userId, hasMyEventsHeader, eventCount)` - сохраняет контекст шапки
- `getEventHeaderContext(userId)` - получает контекст шапки (возвращает EventHeaderContext или null)
- `clearEventHeaderContext(userId)` - очищает контекст шапки

Новый класс `EventHeaderContext` с полями `hasMyEventsHeader` и `eventCount`.

### 3. Модификация AttachmentCallbackHandler

#### handleBackToEvent

**Текущая проблема**: Метод использует только `buildEventMessage(event)`, который не включает шапку.

**Решение**: 
1. Получить контекст шапки через `getEventHeaderContext(userId)`
2. Если контекст существует и `hasMyEventsHeader = true`, использовать `buildEventMessageWithHeader(event, eventCount)`
3. Иначе использовать `buildEventMessage(event)` без шапки
4. Очистить attachment context через `clearAttachmentMessageContext(userId)`

#### handleViewFile

**Изменение**: Добавить клавиатуру с кнопкой "Назад к вложениям" при отправке файла.

Использовать новый метод `sendFileWithKeyboard()` вместо `sendFile()`.

#### handleBackToAttachments (новый метод)

Обрабатывает callback "attach_file_list_{eventId}" при возврате из просмотра файла:
1. Получить событие и список вложений
2. Сформировать сообщение со списком вложений
3. Использовать `editOrSendMessage()` для редактирования или отправки нового сообщения

### 4. Модификация UpdateProcessor.handleAttachmentFileUpload

**Текущая проблема**: 
```java
// Отправляет отдельное подтверждающее сообщение
messageService.sendMessage(chatId, confirmationMessage);

// Затем обновляет список вложений
editOrSendMessage(...);
```

**Решение**: Убрать строку с `sendMessage(chatId, confirmationMessage)` и сразу вызывать `editOrSendMessage()` для обновления списка вложений.

### 5. Расширение KeyboardService

Добавить метод `createFileViewKeyboard(eventId)` - создает клавиатуру с кнопкой "⬅️ Назад к вложениям" (callback: `attach_file_list_{eventId}`).

### 6. Расширение TelegramMessageService

Добавить метод `sendFileWithKeyboard(chatId, fileId, fileType, caption, keyboard)` - отправляет файл с inline клавиатурой. Поддерживает типы: photo, video, audio, document.

### 7. Модификация обработчиков событий

При открытии первого события из списка "Мои события" необходимо сохранять контекст шапки:

```java
conversationStateService.saveEventHeaderContext(userId, true, events.size());
```

## Data Models

### ConversationState (изменения)

Добавляются поля:
- `event_has_my_events_header` BOOLEAN - флаг наличия шапки "Мои события"
- `event_count_for_header` INTEGER - количество событий для формирования шапки

Добавляются методы:
- `hasEventHeaderContext()` - проверяет наличие контекста шапки
- `clearEventHeaderContext()` - очищает контекст шапки

### Flyway миграция

```sql
ALTER TABLE conversation_states
ADD COLUMN event_has_my_events_header BOOLEAN DEFAULT NULL,
ADD COLUMN event_count_for_header INTEGER DEFAULT NULL;
```

## Correctness Properties

*Свойство (property) - это характеристика или поведение, которое должно выполняться для всех допустимых выполнений системы. Свойства служат мостом между человекочитаемыми спецификациями и машинно-проверяемыми гарантиями корректности.*

### Property 1: Сохранение шапки при возврате к событию

*Для любого* события и пользователя, если контекст шапки сохранен с флагом `hasMyEventsHeader = true`, то при возврате к событию через кнопку "Назад к событию" сообщение должно содержать шапку "📋 Мои события" с корректным количеством событий.

**Validates: Requirements 1.1, 1.2**

### Property 2: Механизм editOrSendMessage

*Для любой* операции обновления сообщения (загрузка файла, возврат к вложениям), система должна сначала попытаться отредактировать существующее сообщение через `tryEditMessageText`, и только при неудаче отправить новое сообщение.

**Validates: Requirements 2.1, 2.3, 3.3, 3.4**

### Property 3: Отсутствие дублирующих сообщений при загрузке файла

*Для любого* файла, загружаемого пользователем в качестве вложения, система не должна отправлять отдельное подтверждающее сообщение - вместо этого должно редактироваться существующее сообщение со списком вложений.

**Validates: Requirements 2.2**

### Property 4: Актуальность списка вложений после загрузки

*Для любого* файла, успешно загруженного и сохраненного в БД, обновленное сообщение со списком вложений должно содержать этот файл с корректными метаданными (имя, размер, дата загрузки).

**Validates: Requirements 2.5**

### Property 5: Наличие кнопки "Назад к вложениям"

*Для любого* файла вложения, при его просмотре пользователем клавиатура должна содержать кнопку "⬅️ Назад к вложениям" с корректным callback для возврата к списку вложений события.

**Validates: Requirements 3.1**

### Property 6: Полнота списка вложений при возврате

*Для любого* события с вложениями, при возврате к списку вложений через кнопку "Назад к вложениям" сообщение должно содержать все вложения события без потерь.

**Validates: Requirements 3.2**

### Property 7: Сохранение messageId после операций

*Для любой* успешной операции редактирования или отправки нового сообщения (при fallback), система должна сохранить актуальный messageId в ConversationState для последующих операций редактирования.

**Validates: Requirements 2.4, 5.3**

### Property 8: Сохранение контекста при отображении

*Для любого* сообщения о событии с шапкой "Мои события", система должна сохранить контекст шапки (флаг и количество событий) в ConversationState для корректного восстановления при возврате.

**Validates: Requirements 4.1**

### Property 9: Сохранение attachment context

*Для любого* отображаемого списка вложений, система должна сохранить messageId и chatId в ConversationState для последующего редактирования при загрузке файлов.

**Validates: Requirements 4.2**

### Property 10: Приоритет редактирования над отправкой

*Для любой* операции обновления сообщения, если в ConversationState сохранен messageId, система должна сначала попытаться отредактировать это сообщение перед отправкой нового.

**Validates: Requirements 4.3, 4.4**

### Property 11: Fallback при ошибках редактирования

*Для любой* ошибки редактирования сообщения (устаревший messageId, TelegramApiException), система должна отправить новое сообщение с тем же содержимым и обновить messageId в ConversationState.

**Validates: Requirements 5.1, 5.2**

### Property 12: Логирование ошибок редактирования

*Для любой* ошибки при попытке редактирования сообщения, система должна залогировать ошибку с уровнем INFO или WARN для последующего анализа.

**Validates: Requirements 5.4**

### Property 13: Целостность данных при fallback

*Для любого* нового сообщения, отправленного после ошибки редактирования, содержимое должно быть идентично тому, что должно было быть в отредактированном сообщении (без потери данных).

**Validates: Requirements 5.5**

## Error Handling

### 1. Ошибки редактирования сообщений

**Сценарий**: Telegram API возвращает ошибку при попытке редактирования сообщения (сообщение удалено, слишком старое, идентичный текст).

**Обработка**:
- Логирование ошибки с уровнем INFO (не ERROR, так как это ожидаемая ситуация)
- Автоматический fallback на отправку нового сообщения
- Сохранение нового messageId в ConversationState
- Пользователь получает корректную информацию без прерывания работы

**Код**:
```java
try {
    boolean edited = messageService.tryEditMessageText(chatId, messageId, text, keyboard);
    if (!edited) {
        log.info("Редактирование не удалось, отправка нового сообщения: chatId={}, messageId={}", 
                chatId, messageId);
        Message newMessage = messageService.sendMessageAndGet(chatId, text, keyboard);
        conversationStateService.saveAttachmentMessageId(userId, eventId, chatId, newMessage.getMessageId());
    }
} catch (TelegramApiException e) {
    log.error("Критическая ошибка при редактировании/отправке: {}", e.getMessage(), e);
    throw e;
}
```

### 2. Отсутствие контекста шапки

**Сценарий**: Пользователь нажимает "Назад к событию", но контекст шапки не сохранен в ConversationState.

**Обработка**:
- Отображение сообщения о событии без шапки
- Логирование с уровнем DEBUG (это нормальная ситуация для событий, открытых не из списка "Мои события")
- Продолжение работы без ошибок

**Код**:
```java
EventHeaderContext headerContext = conversationStateService.getEventHeaderContext(userId);
if (headerContext != null && headerContext.isHasMyEventsHeader()) {
    message = botMessageBuilder.buildEventMessageWithHeader(event, headerContext.getEventCount());
} else {
    log.debug("Контекст шапки не найден для пользователя ID={}, отображение без шапки", userId);
    message = botMessageBuilder.buildEventMessage(event);
}
```

### 3. Отсутствие attachment context при загрузке файла

**Сценарий**: Пользователь загружает файл, но контекст ожидания файла не найден в ConversationState.

**Обработка**:
- Логирование с уровнем WARN
- Очистка состояния ожидания файла
- Отправка сообщения об ошибке пользователю
- Прерывание обработки загрузки файла

**Код**:
```java
AwaitingFileContext context = conversationStateService.getAwaitingFileContext(userId);
if (context == null) {
    log.warn("Контекст ожидания файла не найден для пользователя: userId={}", userId);
    conversationStateService.clearAwaitingFile(userId);
    messageService.sendMessage(chatId, "❌ Произошла ошибка. Попробуйте добавить файл заново.");
    return;
}
```

### 4. Ошибки при сохранении контекста

**Сценарий**: Ошибка при сохранении контекста в БД (например, нарушение ограничений, проблемы с транзакцией).

**Обработка**:
- Логирование с уровнем ERROR
- Откат транзакции (автоматически через @Transactional)
- Продолжение работы без сохранения контекста
- Пользователь может потерять шапку при возврате, но основная функциональность работает

**Код**:
```java
@Transactional
public void saveEventHeaderContext(Long userId, boolean hasMyEventsHeader, int eventCount) {
    try {
        ConversationState state = getOrCreateState(userId);
        state.setEventHasMyEventsHeader(hasMyEventsHeader);
        state.setEventCountForHeader(eventCount);
        conversationStateRepository.save(state);
    } catch (Exception e) {
        log.error("Ошибка при сохранении контекста шапки: userId={}, error={}", 
                userId, e.getMessage(), e);
        // Транзакция откатится автоматически
        throw e;
    }
}
```

## Testing Strategy

### Dual Testing Approach

Используем комбинацию unit-тестов и property-based тестов для комплексного покрытия:

- **Unit-тесты**: Проверяют конкретные сценарии, граничные случаи и интеграцию компонентов
- **Property-тесты**: Проверяют универсальные свойства на большом количестве сгенерированных входных данных

### Unit Testing

#### 1. ConversationStateService

**Тесты**:
- `saveEventHeaderContext_shouldSaveContext()` - проверка сохранения контекста
- `getEventHeaderContext_shouldReturnContext()` - проверка получения контекста
- `getEventHeaderContext_shouldReturnNull_whenNoContext()` - проверка отсутствия контекста
- `clearEventHeaderContext_shouldClearContext()` - проверка очистки контекста

**Подход**: Использовать @DataJpaTest с Testcontainers для тестирования с реальной БД.

#### 2. AttachmentCallbackHandler

**Тесты**:
- `handleBackToEvent_shouldIncludeHeader_whenContextExists()` - проверка включения шапки
- `handleBackToEvent_shouldNotIncludeHeader_whenNoContext()` - проверка отсутствия шапки
- `handleViewFile_shouldIncludeBackButton()` - проверка наличия кнопки "Назад к вложениям"
- `handleBackToAttachments_shouldDisplayAllAttachments()` - проверка полноты списка вложений

**Подход**: Использовать Mockito для мокирования зависимостей (services, repositories).

#### 3. UpdateProcessor

**Тесты**:
- `handleAttachmentFileUpload_shouldNotSendConfirmationMessage()` - проверка отсутствия отдельного сообщения
- `handleAttachmentFileUpload_shouldEditExistingMessage()` - проверка редактирования сообщения
- `handleAttachmentFileUpload_shouldIncludeNewFileInList()` - проверка наличия файла в списке

**Подход**: Использовать Mockito для мокирования TelegramMessageService и проверки вызовов методов.

#### 4. KeyboardService

**Тесты**:
- `createFileViewKeyboard_shouldIncludeBackButton()` - проверка структуры клавиатуры
- `createFileViewKeyboard_shouldHaveCorrectCallback()` - проверка корректности callback data

**Подход**: Простые unit-тесты без моков.

#### 5. TelegramMessageService

**Тесты**:
- `sendFileWithKeyboard_shouldSendPhoto_whenTypeIsPhoto()` - проверка отправки фото
- `sendFileWithKeyboard_shouldSendDocument_whenTypeIsDocument()` - проверка отправки документа
- `sendFileWithKeyboard_shouldIncludeKeyboard()` - проверка наличия клавиатуры

**Подход**: Мокирование Telegram Bot API.

### Property-Based Testing

Используем библиотеку **jqwik** для property-based тестирования в Java.

**Конфигурация**: Минимум 100 итераций на каждый property-тест.

#### Property 1: Сохранение шапки при возврате

```java
@Property(tries = 100)
@Tag("Feature: attachment-navigation-fixes, Property 1: Сохранение шапки при возврате к событию")
void backToEvent_shouldPreserveHeader_whenContextSaved(
        @ForAll @LongRange(min = 1) Long userId,
        @ForAll @LongRange(min = 1) Long eventId,
        @ForAll @IntRange(min = 1, max = 100) int eventCount) {
    
    // Сохраняем контекст с флагом hasMyEventsHeader = true
    conversationStateService.saveEventHeaderContext(userId, true, eventCount);
    
    // Вызываем handleBackToEvent
    // ...
    
    // Проверяем, что сообщение содержит шапку "📋 Мои события"
    assertThat(message).contains("📋 Мои события");
    assertThat(message).contains("Всего событий: " + eventCount);
}
```

#### Property 2: Механизм editOrSendMessage

```java
@Property(tries = 100)
@Tag("Feature: attachment-navigation-fixes, Property 2: Механизм editOrSendMessage")
void editOrSendMessage_shouldTryEditFirst_beforeSendingNew(
        @ForAll @LongRange(min = 1) Long chatId,
        @ForAll @IntRange(min = 1) Integer messageId,
        @ForAll String text) {
    
    // Вызываем editOrSendMessage
    // ...
    
    // Проверяем, что tryEditMessageText был вызван первым
    verify(messageService).tryEditMessageText(eq(chatId), eq(messageId), anyString(), any());
}
```

#### Property 3: Отсутствие дублирующих сообщений

```java
@Property(tries = 100)
@Tag("Feature: attachment-navigation-fixes, Property 3: Отсутствие дублирующих сообщений при загрузке файла")
void fileUpload_shouldNotSendConfirmationMessage(
        @ForAll @LongRange(min = 1) Long userId,
        @ForAll @LongRange(min = 1) Long eventId,
        @ForAll String fileName) {
    
    // Загружаем файл
    // ...
    
    // Проверяем, что sendMessage НЕ был вызван с подтверждающим текстом
    verify(messageService, never()).sendMessage(anyLong(), contains("успешно прикреплен"));
}
```

#### Property 4: Актуальность списка вложений

```java
@Property(tries = 100)
@Tag("Feature: attachment-navigation-fixes, Property 4: Актуальность списка вложений после загрузки")
void fileUpload_shouldIncludeFileInList(
        @ForAll @LongRange(min = 1) Long eventId,
        @ForAll String fileName,
        @ForAll @LongRange(min = 1, max = 20_000_000) Long fileSize) {
    
    // Загружаем файл
    // ...
    
    // Проверяем, что обновленное сообщение содержит файл
    assertThat(updatedMessage).contains(fileName);
}
```

#### Property 7: Сохранение messageId

```java
@Property(tries = 100)
@Tag("Feature: attachment-navigation-fixes, Property 7: Сохранение messageId после операций")
void operation_shouldSaveMessageId_afterSuccess(
        @ForAll @LongRange(min = 1) Long userId,
        @ForAll @LongRange(min = 1) Long eventId,
        @ForAll @IntRange(min = 1) Integer messageId) {
    
    // Выполняем операцию редактирования
    // ...
    
    // Проверяем, что messageId сохранен в ConversationState
    AttachmentMessageContext context = conversationStateService.getAttachmentMessageContext(userId);
    assertThat(context).isNotNull();
    assertThat(context.getMessageId()).isEqualTo(messageId);
}
```

### Integration Testing

**Тесты**:
- Полный flow: открытие события → переход к вложениям → загрузка файла → возврат к событию
- Проверка сохранения и восстановления контекста через БД
- Проверка fallback механизма при ошибках редактирования

**Подход**: Использовать @SpringBootTest с Testcontainers для PostgreSQL.

### Test Coverage Goals

- **Unit-тесты**: Покрытие кода минимум 80%
- **Property-тесты**: Минимум 100 итераций на каждое свойство
- **Integration-тесты**: Покрытие всех критических user flows

