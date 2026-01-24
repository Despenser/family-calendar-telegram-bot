# Документ дизайна: Завершение реализации функционала вложений к событиям

## Обзор

Данный документ описывает дизайн завершения реализации функционала работы с вложениями к событиям в Telegram-боте семейного календаря. Функционал позволяет пользователям прикреплять файлы различных типов (документы, фотографии, видео, аудио) к событиям календаря.

Частичная реализация уже существует:
- Модель `Attachment` с полями: id, event, fileId, fileName, fileType, fileSize, uploadedAt
- Сервис `AttachmentService` с методами: saveAttachment, getEventAttachments, deleteAttachment
- Репозиторий `AttachmentRepository`
- Заглушка `AttachmentCallbackHandler`
- Префикс `ATTACH_FILE` в `CallbackPrefix`

Необходимо реализовать:
- Пользовательский интерфейс для работы с вложениями
- Обработку загрузки файлов через `UpdateProcessor`
- Интеграцию с `ConversationStateService` для управления состоянием диалога
- Полную реализацию `AttachmentCallbackHandler`

## Архитектура

### Компоненты системы

```
┌─────────────────────────────────────────────────────────────┐
│                     Telegram Bot API                         │
└────────────────────┬────────────────────────────────────────┘
                     │
                     ▼
┌─────────────────────────────────────────────────────────────┐
│                   UpdateProcessor                            │
│  - processUpdate()                                           │
│  - handleFileMessage()                                       │
└────────────┬────────────────────────────────┬────────────────┘
             │                                │
             ▼                                ▼
┌────────────────────────┐      ┌────────────────────────────┐
│ CallbackQueryDispatcher│      │  ConversationStateService  │
└────────────┬───────────┘      │  - setAwaitingFile()       │
             │                  │  - isAwaitingFile()        │
             ▼                  │  - clearAwaitingFile()     │
┌────────────────────────┐      └────────────────────────────┘
│AttachmentCallbackHandler│
│  - handleAttachmentList()│
│  - handleAddFile()       │
│  - handleViewFile()      │
│  - handleDeleteFile()    │
└────────────┬────────────┘
             │
             ▼
┌─────────────────────────────────────────────────────────────┐
│                   AttachmentService                          │
│  - saveAttachment()                                          │
│  - getEventAttachments()                                     │
│  - deleteAttachment()                                        │
│  - getAttachment()                                           │
└────────────┬────────────────────────────────────────────────┘
             │
             ▼
┌─────────────────────────────────────────────────────────────┐
│                 AttachmentRepository                         │
│  - findByEventIdOrderByUploadedAtAsc()                       │
│  - findById()                                                │
└─────────────────────────────────────────────────────────────┘
```

### Поток данных

#### 1. Просмотр списка вложений
```
User → Нажимает "📎 Вложения" → AttachmentCallbackHandler.handleAttachmentList()
→ AttachmentService.getEventAttachments() → Отображение списка
```

#### 2. Добавление файла
```
User → Нажимает "➕ Добавить файл" → AttachmentCallbackHandler.handleAddFile()
→ ConversationStateService.setAwaitingFile() → Сообщение "Отправьте файл"
→ User отправляет файл → UpdateProcessor.handleFileMessage()
→ AttachmentService.saveAttachment() → Подтверждение
```

#### 3. Просмотр файла
```
User → Нажимает на вложение → AttachmentCallbackHandler.handleViewFile()
→ AttachmentService.getAttachment() → TelegramMessageService.sendFile()
```

#### 4. Удаление файла
```
User → Нажимает "🗑️" → AttachmentCallbackHandler.handleDeleteFile()
→ Запрос подтверждения → User подтверждает
→ AttachmentService.deleteAttachment() → Обновление списка
```

## Компоненты и интерфейсы

### 1. ConversationStateService (расширение)

Добавление нового состояния для ожидания файла:

```java
/**
 * Map для отслеживания пользователей, ожидающих загрузки файла.
 * Key: userId, Value: AwaitingFileContext (eventId, chatId, messageId)
 */
private final Map<Long, AwaitingFileContext> usersAwaitingFile = new ConcurrentHashMap<>();

/**
 * Устанавливает состояние ожидания файла для пользователя.
 * 
 * @param userId идентификатор пользователя
 * @param eventId идентификатор события
 * @param chatId идентификатор чата
 * @param messageId идентификатор сообщения со списком вложений
 */
public void setAwaitingFile(Long userId, Long eventId, Long chatId, Integer messageId);

/**
 * Проверяет, ожидает ли пользователь загрузки файла.
 * 
 * @param userId идентификатор пользователя
 * @return true, если пользователь ожидает загрузки файла
 */
public boolean isAwaitingFile(Long userId);

/**
 * Получает контекст ожидания файла для пользователя.
 * 
 * @param userId идентификатор пользователя
 * @return контекст ожидания файла или null
 */
public AwaitingFileContext getAwaitingFileContext(Long userId);

/**
 * Очищает состояние ожидания файла для пользователя.
 * 
 * @param userId идентификатор пользователя
 */
public void clearAwaitingFile(Long userId);

/**
 * Контекст ожидания файла.
 */
@Data
@AllArgsConstructor
public static class AwaitingFileContext {
    private Long eventId;
    private Long chatId;
    private Integer messageId; // ID сообщения со списком вложений для обновления
}
```

### 2. AttachmentCallbackHandler (полная реализация)

```java
@Component
@RequiredArgsConstructor
@Slf4j
public class AttachmentCallbackHandler implements CallbackHandler {
    
    private final AttachmentService attachmentService;
    private final EventService eventService;
    private final TelegramMessageService messageService;
    private final KeyboardService keyboardService;
    private final ConversationStateService conversationStateService;
    private final AuthorizationService authorizationService;
    
    @Override
    public CallbackPrefix getPrefix() {
        return CallbackPrefix.ATTACH_FILE;
    }
    
    @Override
    @HandleCallbackErrors
    public void handle(CallbackQuery callbackQuery, User user) throws Exception {
        String callbackData = callbackQuery.getData();
        Long chatId = callbackQuery.getMessage().getChatId();
        Integer messageId = callbackQuery.getMessage().getMessageId();
        String callbackQueryId = callbackQuery.getId();
        
        // Формат: attach_file_{action}_{eventId}[_{attachmentId}]
        String payload = CallbackPrefix.ATTACH_FILE.extractPayload(callbackData);
        String[] parts = payload.split("_");
        
        if (parts.length < 2) {
            log.warn("Некорректный формат callback data: {}", callbackData);
            messageService.answerCallbackQuery(callbackQueryId, "❌ Ошибка обработки запроса");
            return;
        }
        
        String action = parts[0];
        Long eventId = Long.parseLong(parts[1]);
        
        switch (action) {
            case "list" -> handleAttachmentList(eventId, user, chatId, messageId, callbackQueryId);
            case "add" -> handleAddFile(eventId, user, chatId, messageId, callbackQueryId);
            case "view" -> {
                if (parts.length < 3) {
                    messageService.answerCallbackQuery(callbackQueryId, "❌ Ошибка: не указан ID вложения");
                    return;
                }
                Long attachmentId = Long.parseLong(parts[2]);
                handleViewFile(attachmentId, eventId, user, chatId, callbackQueryId);
            }
            case "delete" -> {
                if (parts.length < 3) {
                    messageService.answerCallbackQuery(callbackQueryId, "❌ Ошибка: не указан ID вложения");
                    return;
                }
                Long attachmentId = Long.parseLong(parts[2]);
                handleDeleteFile(attachmentId, eventId, user, chatId, messageId, callbackQueryId);
            }
            case "confirm_delete" -> {
                if (parts.length < 3) {
                    messageService.answerCallbackQuery(callbackQueryId, "❌ Ошибка: не указан ID вложения");
                    return;
                }
                Long attachmentId = Long.parseLong(parts[2]);
                handleConfirmDelete(attachmentId, eventId, user, chatId, messageId, callbackQueryId);
            }
            case "cancel_delete" -> handleCancelDelete(eventId, user, chatId, messageId, callbackQueryId);
            case "back" -> handleBackToEvent(eventId, user, chatId, messageId, callbackQueryId);
            default -> {
                log.warn("Неизвестное действие: {}", action);
                messageService.answerCallbackQuery(callbackQueryId, "❌ Неизвестное действие");
            }
        }
    }
    
    // Методы обработки действий...
}
```

### 3. UpdateProcessor (расширение)

Модификация метода `handleFileMessage()` для обработки вложений:

```java
private void handleFileMessage(Message message, Optional<User> userOptional) {
    try {
        Long chatId = message.getChatId();
        Long telegramId = message.getFrom().getId();
        
        if (userOptional.isEmpty()) {
            log.warn("Неавторизованный пользователь пытается отправить файл: telegramId={}", telegramId);
            messageService.sendMessage(chatId, 
                "❌ Для отправки файлов необходимо авторизоваться. Используйте /start");
            return;
        }
        
        User user = userOptional.get();
        
        // Проверяем, ожидает ли пользователь загрузки файла для вложения
        if (conversationStateService.isAwaitingFile(user.getId())) {
            handleAttachmentFileUpload(message, user);
            return;
        }
        
        // Существующая логика для черновиков...
    }
}

private void handleAttachmentFileUpload(Message message, User user) {
    // Обработка загрузки файла для вложения
}
```

### 4. KeyboardService (расширение)

Добавление методов для создания клавиатур вложений:

```java
/**
 * Создает inline-клавиатуру для списка вложений события.
 * 
 * @param eventId идентификатор события
 * @param attachments список вложений
 * @param isCreator является ли пользователь создателем события
 * @return inline-клавиатура
 */
public InlineKeyboardMarkup createAttachmentsListKeyboard(
    Long eventId, 
    List<Attachment> attachments, 
    boolean isCreator
);

/**
 * Создает inline-клавиатуру для подтверждения удаления вложения.
 * 
 * @param eventId идентификатор события
 * @param attachmentId идентификатор вложения
 * @return inline-клавиатура
 */
public InlineKeyboardMarkup createDeleteAttachmentConfirmationKeyboard(
    Long eventId, 
    Long attachmentId
);
```

### 5. AttachmentService (расширение)

Добавление метода для получения одного вложения:

```java
/**
 * Получает вложение по идентификатору.
 * 
 * @param attachmentId идентификатор вложения
 * @return вложение
 * @throws AttachmentNotFoundException если вложение не найдено
 */
@Transactional(readOnly = true)
public Attachment getAttachment(Long attachmentId) {
    log.debug("Получение вложения ID {}", attachmentId);
    
    return attachmentRepository.findById(attachmentId)
        .orElseThrow(() -> new AttachmentNotFoundException(attachmentId));
}
```

### 6. TelegramMessageService (расширение)

Добавление методов для отправки файлов:

```java
/**
 * Отправляет файл пользователю по Telegram file_id.
 * 
 * @param chatId идентификатор чата
 * @param fileId Telegram file_id
 * @param fileType тип файла (document, photo, video, audio)
 * @param caption подпись к файлу
 * @throws TelegramApiException если произошла ошибка при отправке
 */
public void sendFile(Long chatId, String fileId, String fileType, String caption) 
    throws TelegramApiException;
```

## Модели данных

### Существующая модель Attachment

```java
@Entity
@Table(name = "attachments")
public class Attachment {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "event_id", nullable = false)
    private Event event;
    
    @Column(name = "file_id", nullable = false)
    private String fileId; // Telegram file_id
    
    @Column(name = "file_name")
    private String fileName;
    
    @Column(name = "file_type")
    private String fileType; // document, photo, video, audio
    
    @Column(name = "file_size")
    private Long fileSize; // в байтах
    
    @Column(name = "uploaded_at", nullable = false)
    private LocalDateTime uploadedAt;
}
```

### Расширение CallbackPrefix

Уже существует:
```java
ATTACH_FILE("attach_file_")
```

Формат callback data:
- `attach_file_list_{eventId}` - просмотр списка вложений
- `attach_file_add_{eventId}` - добавление файла
- `attach_file_view_{eventId}_{attachmentId}` - просмотр файла
- `attach_file_delete_{eventId}_{attachmentId}` - удаление файла
- `attach_file_confirm_delete_{eventId}_{attachmentId}` - подтверждение удаления
- `attach_file_cancel_delete_{eventId}` - отмена удаления
- `attach_file_back_{eventId}` - возврат к карточке события

## Обработка ошибок

### Типы ошибок

1. **FileSizeExceededException** (уже существует)
   - Выбрасывается при превышении размера файла 20 МБ
   - Обрабатывается в `AttachmentService.saveAttachment()`

2. **AttachmentNotFoundException** (уже существует)
   - Выбрасывается при попытке получить несуществующее вложение
   - Обрабатывается в `AttachmentService.getAttachment()` и `deleteAttachment()`

3. **UnauthorizedAccessException** (уже существует)
   - Выбрасывается при попытке удалить вложение не создателем события
   - Обрабатывается в `AttachmentService.deleteAttachment()`

4. **EventNotFoundException** (уже существует)
   - Выбрасывается при попытке добавить вложение к несуществующему событию
   - Обрабатывается в `AttachmentService.saveAttachment()`

5. **TelegramApiException**
   - Выбрасывается при ошибках Telegram API
   - Обрабатывается в `TelegramMessageService.sendFile()`

### Стратегия обработки

```java
try {
    // Операция с вложением
} catch (FileSizeExceededException e) {
    messageService.answerCallbackQuery(callbackQueryId, "❌ Файл слишком большой");
    messageService.sendMessage(chatId, 
        String.format("❌ Размер файла %.2f МБ превышает лимит 20 МБ", 
                     fileSize / (1024.0 * 1024.0)));
} catch (AttachmentNotFoundException e) {
    messageService.answerCallbackQuery(callbackQueryId, "❌ Вложение не найдено");
    log.error("Вложение не найдено: attachmentId={}", attachmentId);
} catch (UnauthorizedAccessException e) {
    messageService.answerCallbackQuery(callbackQueryId, "❌ Нет прав доступа");
    messageService.sendMessage(chatId, "❌ Только создатель события может удалять вложения");
} catch (TelegramApiException e) {
    messageService.answerCallbackQuery(callbackQueryId, "❌ Ошибка отправки файла");
    log.error("Ошибка Telegram API: {}", e.getMessage(), e);
} catch (Exception e) {
    messageService.answerCallbackQuery(callbackQueryId, "❌ Произошла ошибка");
    log.error("Неожиданная ошибка: {}", e.getMessage(), e);
}
```

## Стратегия тестирования

### Unit-тесты

1. **ConversationStateServiceTest**
   - Тест установки состояния ожидания файла
   - Тест проверки состояния ожидания файла
   - Тест очистки состояния ожидания файла
   - Тест получения контекста ожидания файла

2. **AttachmentCallbackHandlerTest**
   - Тест обработки просмотра списка вложений
   - Тест обработки добавления файла
   - Тест обработки просмотра файла
   - Тест обработки удаления файла
   - Тест обработки подтверждения удаления
   - Тест обработки отмены удаления
   - Тест обработки возврата к событию

3. **UpdateProcessorTest**
   - Тест обработки файла для вложения
   - Тест обработки файла без состояния ожидания
   - Тест обработки файла неавторизованным пользователем

4. **KeyboardServiceTest**
   - Тест создания клавиатуры списка вложений для создателя
   - Тест создания клавиатуры списка вложений для не-создателя
   - Тест создания клавиатуры подтверждения удаления

5. **TelegramMessageServiceTest**
   - Тест отправки документа
   - Тест отправки фотографии
   - Тест отправки видео
   - Тест отправки аудио
   - Тест обработки ошибки Telegram API

### Integration-тесты

1. **AttachmentFlowIntegrationTest**
   - Тест полного потока: добавление → просмотр → удаление
   - Тест добавления нескольких файлов
   - Тест попытки удаления вложения не создателем
   - Тест попытки добавления файла превышающего лимит


## Correctness Properties

*Свойство (property) - это характеристика или поведение, которое должно выполняться для всех допустимых выполнений системы. По сути, это формальное утверждение о том, что система должна делать. Свойства служат мостом между человекочитаемыми спецификациями и машинно-проверяемыми гарантиями корректности.*

### Property Reflection

После анализа всех acceptance criteria выявлены следующие возможности для объединения и устранения избыточности:

**Объединения:**
- Свойства 2.2-2.5 (сохранение файлов разных типов) → объединены в Property 2
- Свойства 4.2 и 4.3 (отображение информации о вложении) → объединены в Property 5
- Свойства 5.2-5.5 (отправка файлов разных типов) → объединены в Property 7
- Свойства 6.3 и 6.4 (обновление списка и подтверждение) → объединены в Property 10
- Свойства 7.1, 7.2 и 7.3 (проверка прав доступа) → объединены в Property 12
- Свойства 9.1 и 9.2 (обработка ошибок API) → объединены в Property 16

**Устранение избыточности:**
- Property 8.2 логически включает Property 2.7 (очистка состояния после сохранения)
- Property 9.4 логически включает очистку состояния при любых ошибках

### Свойства

**Property 1: Кнопка вложений в карточке события**
*For any* события, карточка события должна содержать inline-кнопку "📎 Вложения" с правильным callback data
**Validates: Requirements 1.1**

**Property 2: Сохранение файлов всех типов**
*For any* поддерживаемого типа файла (document, photo, video, audio), когда пользователь в режиме ожидания файла отправляет файл этого типа, система должна сохранить вложение с соответствующим типом
**Validates: Requirements 2.2, 2.3, 2.4, 2.5**

**Property 3: Отображение количества вложений**
*For any* события с N вложениями (N > 0), текст кнопки вложений должен содержать формат "📎 Вложения (N)" с правильным числом
**Validates: Requirements 1.3**

**Property 4: Установка состояния ожидания файла**
*For any* создателя события, когда он нажимает кнопку "➕ Добавить файл", система должна установить состояние ожидания файла в ConversationStateService
**Validates: Requirements 2.1, 8.1**

**Property 5: Отображение информации о вложении**
*For any* вложения, отображаемое сообщение должно содержать имя файла, тип файла, размер файла и дату/время загрузки
**Validates: Requirements 4.2, 4.3**

**Property 6: Сортировка вложений по дате**
*For any* события с несколькими вложениями, список вложений должен быть отсортирован по дате загрузки (от старых к новым)
**Validates: Requirements 4.1**

**Property 7: Отправка файлов по типу**
*For any* вложения, при просмотре система должна отправить файл через Telegram API используя правильный метод для типа файла (sendDocument, sendPhoto, sendVideo, sendAudio)
**Validates: Requirements 5.1, 5.2, 5.3, 5.4, 5.5**

**Property 8: Подтверждение удаления**
*For any* создателя события, когда он нажимает кнопку удаления вложения, система должна отобразить клавиатуру подтверждения удаления
**Validates: Requirements 6.1**

**Property 9: Удаление вложения из БД**
*For any* вложения, когда создатель события подтверждает удаление, вложение должно быть удалено из базы данных
**Validates: Requirements 6.2**

**Property 10: Обновление списка после удаления**
*For any* вложения, после успешного удаления система должна обновить сообщение со списком вложений
**Validates: Requirements 6.3, 6.4**

**Property 11: Видимость кнопок для создателя**
*For any* события, кнопка "➕ Добавить файл" должна отображаться только для создателя события, а кнопки удаления вложений должны отображаться только для создателя
**Validates: Requirements 4.5, 6.5**

**Property 12: Проверка прав доступа**
*For any* пользователя, не являющегося создателем события, попытка добавить или удалить вложение должна быть отклонена с выбросом UnauthorizedAccessException
**Validates: Requirements 7.1, 7.2, 7.3**

**Property 13: Доступ к просмотру для всех**
*For any* члена семьи, просмотр вложений события должен быть разрешен без выброса исключения
**Validates: Requirements 7.4**

**Property 14: Очистка состояния после обработки**
*For any* пользователя в режиме ожидания файла, после успешной обработки файла состояние ожидания должно быть очищено в ConversationStateService
**Validates: Requirements 2.7, 8.2**

**Property 15: Очистка состояния при отмене**
*For any* пользователя в режиме ожидания файла, при отмене добавления файла состояние должно быть очищено
**Validates: Requirements 8.3**

**Property 16: Обработка ошибок Telegram API**
*For any* операции с файлами, если Telegram API выбрасывает исключение, система должна отобразить сообщение об ошибке пользователю и залогировать детали ошибки
**Validates: Requirements 9.1, 9.2, 9.3**

**Property 17: Очистка состояния при ошибке**
*For any* ошибки при обработке файла, система должна очистить состояние диалога для предотвращения зависания
**Validates: Requirements 9.4**

**Property 18: Форматирование размера файла**
*For any* вложения, размер файла должен быть отформатирован в удобочитаемом формате (КБ для размеров < 1 МБ, МБ для размеров >= 1 МБ)
**Validates: Requirements 10.2**

**Property 19: Форматирование даты загрузки**
*For any* вложения, дата загрузки должна быть отформатирована в формате "dd.MM.yyyy HH:mm"
**Validates: Requirements 10.3**

**Property 20: Использование эмодзи для типов файлов**
*For any* типа файла, при отображении вложения должен использоваться соответствующий эмодзи (📄 для document, 🖼️ для photo, 🎥 для video, 🎵 для audio)
**Validates: Requirements 10.1**

**Property 21: Использование inline-клавиатур**
*For any* взаимодействия с вложениями, система должна использовать InlineKeyboardMarkup для отображения действий
**Validates: Requirements 10.4**

**Property 22: Использование Markdown-форматирования**
*For any* сообщения о вложениях, текст должен использовать Markdown-форматирование для улучшения читаемости
**Validates: Requirements 10.5**

**Property 23: Подтверждение после сохранения**
*For any* успешно сохраненного файла, система должна отправить подтверждающее сообщение с информацией о файле (имя, размер)
**Validates: Requirements 2.6**

**Property 24: Сообщение об ошибке размера файла**
*For any* файла размером более 20 МБ, сообщение об ошибке должно содержать фактический размер файла и лимит
**Validates: Requirements 3.2**

**Property 25: Подсказка при текстовом сообщении**
*For any* пользователя в режиме ожидания файла, если он отправляет текстовое сообщение, система должна отобразить подсказку о необходимости отправить файл
**Validates: Requirements 8.4**

**Property 26: Отображение списка или сообщения об отсутствии**
*For any* события, при нажатии кнопки "📎 Вложения" система должна отобразить либо список вложений (если они есть), либо сообщение "У этого события пока нет вложений" (если их нет)
**Validates: Requirements 1.2, 4.4**

**Property 27: Обработка ошибки отправки файла**
*For any* вложения, если отправка файла через Telegram API не удалась, система должна отобразить сообщение об ошибке
**Validates: Requirements 5.6**

