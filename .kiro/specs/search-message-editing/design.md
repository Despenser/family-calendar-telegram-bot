# Дизайн: Редактирование сообщения при поиске событий

## Обзор

Данный дизайн описывает улучшение пользовательского опыта при поиске событий путем редактирования одного сообщения вместо отправки множества новых сообщений. Текущая реализация команды `/search` отправляет новое сообщение для каждого этапа поиска (запрос текста, результаты), что загромождает чат. Новая реализация будет редактировать одно сообщение на протяжении всего процесса поиска и предоставит кнопку для повторного поиска.

Основные улучшения:
- Редактирование одного сообщения вместо отправки новых
- Сохранение message_id в ConversationState для последующего редактирования
- Кнопка "🔍 Найти заново" для инициации нового поиска
- Автоматическое удаление пользовательских сообщений с поисковыми запросами
- Обработка ошибок редактирования с fallback на отправку нового сообщения

## Архитектура

### Компоненты системы

```
┌─────────────────────────────────────────────────────────────────┐
│                         UpdateProcessor                          │
│  (обрабатывает входящие обновления от Telegram)                 │
└────────────────┬────────────────────────────────────────────────┘
                 │
                 ├─ Команда /search
                 │  └──> SearchCommandHandler.handle()
                 │
                 ├─ Текстовое сообщение (поисковый запрос)
                 │  └──> SearchCommandHandler.performSearch()
                 │
                 └─ Callback "search_again:"
                    └──> SearchCommandHandler.handleSearchAgainCallback()

┌─────────────────────────────────────────────────────────────────┐
│                     SearchCommandHandler                         │
│  - handle(): обработка команды /search                          │
│  - performSearch(): выполнение поиска                           │
│  - handleSearchAgainCallback(): обработка кнопки "Найти заново"│
└────────────────┬────────────────────────────────────────────────┘
                 │
                 ├──> ConversationStateService
                 │    (сохранение/получение message_id)
                 │
                 ├──> SearchService
                 │    (выполнение поиска событий)
                 │
                 ├──> TelegramMessageService
                 │    (отправка/редактирование сообщений)
                 │
                 └──> EventFormatter
                      (форматирование результатов)
```

### Поток данных

#### 1. Инициация поиска (/search)
```
Пользователь → /search → SearchCommandHandler.handle()
                              ↓
                    TelegramMessageService.sendMessageWithInlineKeyboardAndGet()
                              ↓
                    ConversationStateService.setAwaitingSearchQuery()
                    ConversationStateService.saveSearchMessageId()
                              ↓
                    Сообщение с запросом текста отправлено
```

#### 2. Ввод поискового запроса
```
Пользователь → текст → UpdateProcessor
                              ↓
                    ConversationStateService.isAwaitingSearchQuery()
                              ↓
                    SearchCommandHandler.performSearch()
                              ↓
                    TelegramMessageService.deleteMessage() (удаление сообщения пользователя)
                              ↓
                    SearchService.searchEvents()
                              ↓
                    TelegramMessageService.tryEditMessageText()
                              ↓
                    Сообщение отредактировано с результатами
```

#### 3. Повторный поиск (кнопка "Найти заново")
```
Пользователь → кнопка → CallbackQueryDispatcher
                              ↓
                    SearchCommandHandler.handleSearchAgainCallback()
                              ↓
                    TelegramMessageService.editMessageText()
                              ↓
                    ConversationStateService.setAwaitingSearchQuery()
                              ↓
                    Сообщение отредактировано с запросом текста
```

## Компоненты и интерфейсы

### SearchCommandHandler

Основной обработчик команды `/search` и связанных callback.

**Методы:**

```java
public String handle(Message message, User user)
```
- Обрабатывает команду `/search`
- Отправляет сообщение с запросом ввести текст для поиска
- Сохраняет message_id в ConversationState
- Устанавливает состояние ожидания поискового запроса

```java
public void performSearch(Long chatId, User user, String query)
```
- Выполняет поиск событий по запросу пользователя
- Удаляет сообщение пользователя с поисковым запросом
- Редактирует исходное сообщение с результатами поиска
- Добавляет кнопку "🔍 Найти заново"
- Обрабатывает ошибки редактирования с fallback на отправку нового сообщения

```java
public void handleSearchAgainCallback(CallbackQuery callbackQuery, User user)
```
- Обрабатывает нажатие кнопки "🔍 Найти заново"
- Редактирует сообщение, возвращая его к состоянию запроса текста
- Устанавливает состояние ожидания нового поискового запроса
- Отвечает на callback query

### ConversationStateService

Сервис для управления состоянием диалогов пользователей.

**Новые методы:**

```java
public void setAwaitingSearchQuery(Long userId, Long chatId, Integer messageId)
```
- Устанавливает состояние ожидания поискового запроса
- Сохраняет message_id для последующего редактирования

```java
public boolean isAwaitingSearchQuery(Long userId)
```
- Проверяет, ожидает ли пользователь ввода поискового запроса

```java
public SearchQueryContext getSearchQueryContext(Long userId)
```
- Получает контекст поиска (chatId, messageId)

```java
public void clearAwaitingSearchQuery(Long userId)
```
- Очищает состояние ожидания поискового запроса

**Новый класс:**

```java
@Data
@AllArgsConstructor
public static class SearchQueryContext {
    private Long chatId;
    private Integer messageId;
}
```

### TelegramMessageService

Сервис для отправки и редактирования сообщений через Telegram Bot API.

**Используемые методы:**

```java
public Message sendMessageWithInlineKeyboardAndGet(Long chatId, String text, InlineKeyboardMarkup keyboard)
```
- Отправляет сообщение с inline кнопками и возвращает объект Message
- Используется для отправки первого сообщения с запросом текста

```java
public boolean tryEditMessageText(Long chatId, Integer messageId, String newText, InlineKeyboardMarkup replyMarkup)
```
- Пытается отредактировать существующее сообщение
- Возвращает true при успехе, false если сообщение не найдено/удалено
- Не выбрасывает исключение при ошибках "сообщение не найдено"

```java
public void editMessageText(Long chatId, Integer messageId, String newText, InlineKeyboardMarkup replyMarkup)
```
- Редактирует текст существующего сообщения
- Выбрасывает исключение при ошибках

```java
public boolean deleteMessage(Long chatId, Integer messageId)
```
- Удаляет сообщение из чата
- Возвращает true при успехе, false если сообщение не найдено

### CallbackQueryDispatcher

Диспетчер для маршрутизации callback queries к соответствующим обработчикам.

**Изменения:**
- Добавление маршрутизации callback с префиксом "search_again:" к SearchCommandHandler

### CallbackPrefix

Enum для префиксов callback data.

**Новое значение:**

```java
SEARCH_AGAIN("search_again:")
```

## Модели данных

### ConversationState (изменения)

Добавление полей для хранения контекста поиска:

```java
@Entity
@Table(name = "conversation_states")
public class ConversationState {
    // ... существующие поля ...
    
    /**
     * ID чата для поиска событий
     */
    @Column(name = "search_chat_id")
    private Long searchChatId;
    
    /**
     * ID сообщения для редактирования при поиске
     */
    @Column(name = "search_message_id")
    private Integer searchMessageId;
    
    /**
     * Проверяет, есть ли контекст поиска
     */
    public boolean hasSearchContext() {
        return searchChatId != null && searchMessageId != null;
    }
    
    /**
     * Очищает контекст поиска
     */
    public void clearSearchContext() {
        this.searchChatId = null;
        this.searchMessageId = null;
    }
}
```

### Миграция базы данных

```sql
-- V18__Add_search_context_to_conversation_states.sql
ALTER TABLE conversation_states
ADD COLUMN search_chat_id BIGINT,
ADD COLUMN search_message_id INTEGER;

COMMENT ON COLUMN conversation_states.search_chat_id IS 'ID чата для поиска событий';
COMMENT ON COLUMN conversation_states.search_message_id IS 'ID сообщения для редактирования при поиске';
```


## Correctness Properties

*Свойство корректности (property) - это характеристика или поведение, которое должно выполняться для всех допустимых выполнений системы - по сути, формальное утверждение о том, что система должна делать. Свойства служат мостом между человекочитаемыми спецификациями и машинопроверяемыми гарантиями корректности.*

### Property 1: Редактирование сообщения при вводе запроса

*Для любого* пользователя, который ввел команду /search и получил сообщение с запросом текста, при вводе поискового запроса система должна отредактировать исходное сообщение, а не отправить новое.

**Validates: Requirements 1.3, 1.4**

### Property 2: Сохранение message_id

*Для любого* пользователя, который ввел команду /search, система должна сохранить message_id отправленного сообщения в ConversationState.

**Validates: Requirements 1.2**

### Property 3: Отображение кнопки повторного поиска

*Для любого* результата поиска (найдены события или нет), отредактированное сообщение должно содержать кнопку "🔍 Найти заново".

**Validates: Requirements 2.6, 3.1**

### Property 4: Возврат к запросу текста при нажатии кнопки

*Для любого* пользователя, который нажал кнопку "🔍 Найти заново", система должна отредактировать сообщение, вернув его к состоянию запроса текста для поиска.

**Validates: Requirements 3.2**

### Property 5: Удаление пользовательских сообщений

*Для любого* пользователя, который ввел поисковый запрос, система должна удалить сообщение пользователя с текстом запроса из чата.

**Validates: Requirements 7.1, 7.4**

### Property 6: Fallback при ошибке редактирования

*Для любого* случая, когда редактирование сообщения не удается (сообщение удалено, не найдено), система должна отправить новое сообщение с результатами поиска.

**Validates: Requirements 6.1, 6.2**

### Property 7: Валидация поискового запроса

*Для любого* поискового запроса длиной менее 2 символов, система должна отредактировать сообщение, показав ошибку валидации.

**Validates: Requirements 6.4**

### Property 8: Форматирование результатов поиска

*Для любого* результата поиска с найденными событиями, отредактированное сообщение должно содержать заголовок "🔍 **Результаты поиска**", поисковый запрос в формате "_Запрос: \"текст\"_", список событий и счетчик "_Найдено событий: N_".

**Validates: Requirements 2.1, 2.2, 2.3, 2.4**

### Property 9: Сообщение об отсутствии результатов

*Для любого* результата поиска без найденных событий, отредактированное сообщение должно содержать текст "По запросу \"текст\" ничего не найдено."

**Validates: Requirements 2.5**

### Property 10: Очистка состояния при других операциях

*Для любого* пользователя, который начал другую операцию (не связанную с поиском), система должна очистить состояние поиска.

**Validates: Requirements 4.4**

## Обработка ошибок

### Ошибки редактирования сообщения

**Сценарий:** Сообщение не найдено или удалено пользователем

**Обработка:**
1. `TelegramMessageService.tryEditMessageText()` возвращает `false`
2. `SearchCommandHandler.performSearch()` отправляет новое сообщение с результатами
3. Логирование: INFO уровень - "Не удалось отредактировать сообщение поиска, отправка нового"
4. Сохранение нового message_id в ConversationState

**Требования:** 6.1, 6.2

### Ошибки валидации поискового запроса

**Сценарий:** Поисковый запрос слишком короткий (< 2 символов)

**Обработка:**
1. Валидация в `SearchCommandHandler.performSearch()`
2. Редактирование сообщения с текстом ошибки
3. Сохранение состояния ожидания нового запроса
4. Логирование: DEBUG уровень - "Поисковый запрос слишком короткий"

**Требования:** 6.4

### Ошибки удаления сообщения пользователя

**Сценарий:** Не удается удалить сообщение пользователя с поисковым запросом

**Обработка:**
1. `TelegramMessageService.deleteMessage()` возвращает `false` или выбрасывает исключение
2. Логирование: WARN уровень - "Не удалось удалить сообщение пользователя"
3. Продолжение обработки поискового запроса
4. Результаты поиска все равно отображаются

**Требования:** 7.3

### Ошибки при обработке callback

**Сценарий:** Ошибка при обработке нажатия кнопки "Найти заново"

**Обработка:**
1. Обработка исключения в `SearchCommandHandler.handleSearchAgainCallback()`
2. Ответ на callback query с сообщением об ошибке
3. Логирование: ERROR уровень - "Ошибка при обработке callback search_again"
4. Пользователь может повторить попытку или использовать команду /search заново

**Требования:** 3.3, 3.4

### Ошибки поиска событий

**Сценарий:** Ошибка при выполнении поиска в базе данных

**Обработка:**
1. Обработка исключения в `SearchCommandHandler.performSearch()`
2. Редактирование сообщения с текстом "❌ Произошла ошибка при поиске событий"
3. Логирование: ERROR уровень - "Ошибка при выполнении поиска"
4. Сохранение состояния для возможности повторного поиска

**Требования:** 6.3

## Стратегия тестирования

### Unit тесты

#### SearchCommandHandlerTest

**Тесты для handle():**
- `testHandleSendsMessageWithSearchPrompt()` - проверка отправки сообщения с запросом текста
- `testHandleSavesMessageIdInConversationState()` - проверка сохранения message_id
- `testHandleSetsAwaitingSearchQueryState()` - проверка установки состояния ожидания

**Тесты для performSearch():**
- `testPerformSearchWithValidQuery()` - проверка успешного поиска с валидным запросом
- `testPerformSearchEditsOriginalMessage()` - проверка редактирования исходного сообщения
- `testPerformSearchDeletesUserMessage()` - проверка удаления сообщения пользователя
- `testPerformSearchWithShortQuery()` - проверка валидации короткого запроса
- `testPerformSearchWithNoResults()` - проверка отображения сообщения об отсутствии результатов
- `testPerformSearchFallbackOnEditError()` - проверка fallback при ошибке редактирования
- `testPerformSearchAddsSearchAgainButton()` - проверка добавления кнопки "Найти заново"
- `testPerformSearchFormatsResultsCorrectly()` - проверка форматирования результатов

**Тесты для handleSearchAgainCallback():**
- `testHandleSearchAgainCallbackEditsMessage()` - проверка редактирования сообщения
- `testHandleSearchAgainCallbackSetsAwaitingState()` - проверка установки состояния ожидания
- `testHandleSearchAgainCallbackAnswersQuery()` - проверка ответа на callback query

#### ConversationStateServiceTest

**Тесты для новых методов:**
- `testSetAwaitingSearchQuery()` - проверка сохранения контекста поиска
- `testIsAwaitingSearchQuery()` - проверка определения состояния ожидания
- `testGetSearchQueryContext()` - проверка получения контекста поиска
- `testClearAwaitingSearchQuery()` - проверка очистки состояния
- `testSearchContextPersistence()` - проверка сохранения в базе данных

#### TelegramMessageServiceTest

**Тесты для tryEditMessageText():**
- `testTryEditMessageTextSuccess()` - проверка успешного редактирования
- `testTryEditMessageTextMessageNotFound()` - проверка обработки "сообщение не найдено"
- `testTryEditMessageTextMessageDeleted()` - проверка обработки удаленного сообщения

### Property-Based тесты

#### SearchCommandHandlerPropertyTest

**Property 1: Редактирование сообщения при вводе запроса**
```java
@Property
void searchAlwaysEditsOriginalMessage(
    @ForAll @StringLength(min = 2, max = 100) String query,
    @ForAll @LongRange(min = 1) Long userId,
    @ForAll @LongRange(min = 1) Long chatId
) {
    // Arrange: создаем пользователя и отправляем команду /search
    User user = createUser(userId);
    Message initialMessage = sendSearchCommand(user, chatId);
    Integer messageId = initialMessage.getMessageId();
    
    // Act: вводим поисковый запрос
    performSearch(chatId, user, query);
    
    // Assert: проверяем, что сообщение было отредактировано
    verify(messageService).tryEditMessageText(eq(chatId), eq(messageId), anyString(), any());
    verify(messageService, never()).sendMessage(eq(chatId), anyString(), any(InlineKeyboardMarkup.class));
}
```
**Feature: search-message-editing, Property 1: Редактирование сообщения при вводе запроса**

**Property 2: Сохранение message_id**
```java
@Property
void searchAlwaysSavesMessageId(
    @ForAll @LongRange(min = 1) Long userId,
    @ForAll @LongRange(min = 1) Long chatId
) {
    // Arrange: создаем пользователя
    User user = createUser(userId);
    
    // Act: отправляем команду /search
    Message sentMessage = sendSearchCommand(user, chatId);
    
    // Assert: проверяем, что message_id сохранен
    SearchQueryContext context = conversationStateService.getSearchQueryContext(userId);
    assertThat(context).isNotNull();
    assertThat(context.getMessageId()).isEqualTo(sentMessage.getMessageId());
}
```
**Feature: search-message-editing, Property 2: Сохранение message_id**

**Property 3: Отображение кнопки повторного поиска**
```java
@Property
void searchResultsAlwaysHaveSearchAgainButton(
    @ForAll @StringLength(min = 2, max = 100) String query,
    @ForAll @LongRange(min = 1) Long userId,
    @ForAll List<Event> events // может быть пустым
) {
    // Arrange: создаем пользователя и настраиваем mock для поиска
    User user = createUser(userId);
    when(searchService.searchEvents(any(), any(), eq(query))).thenReturn(events);
    
    // Act: выполняем поиск
    performSearch(123L, user, query);
    
    // Assert: проверяем наличие кнопки "Найти заново"
    ArgumentCaptor<InlineKeyboardMarkup> keyboardCaptor = ArgumentCaptor.forClass(InlineKeyboardMarkup.class);
    verify(messageService).tryEditMessageText(any(), any(), anyString(), keyboardCaptor.capture());
    
    InlineKeyboardMarkup keyboard = keyboardCaptor.getValue();
    boolean hasSearchAgainButton = keyboard.getKeyboard().stream()
        .flatMap(List::stream)
        .anyMatch(button -> button.getCallbackData().startsWith("search_again:"));
    
    assertThat(hasSearchAgainButton).isTrue();
}
```
**Feature: search-message-editing, Property 3: Отображение кнопки повторного поиска**

**Property 5: Удаление пользовательских сообщений**
```java
@Property
void searchAlwaysDeletesUserMessage(
    @ForAll @StringLength(min = 2, max = 100) String query,
    @ForAll @LongRange(min = 1) Long userId,
    @ForAll @LongRange(min = 1) Long chatId,
    @ForAll @IntRange(min = 1) Integer userMessageId
) {
    // Arrange: создаем пользователя и сообщение
    User user = createUser(userId);
    Message userMessage = createMessage(userMessageId, query);
    
    // Act: выполняем поиск
    performSearch(chatId, user, query, userMessage);
    
    // Assert: проверяем, что сообщение пользователя удалено
    verify(messageService).deleteMessage(eq(chatId), eq(userMessageId));
}
```
**Feature: search-message-editing, Property 5: Удаление пользовательских сообщений**

### Integration тесты

#### SearchMessageEditingIntegrationTest

**Тест полного цикла поиска:**
```java
@Test
void testCompleteSearchCycle() {
    // 1. Отправка команды /search
    // 2. Проверка сохранения message_id
    // 3. Ввод поискового запроса
    // 4. Проверка редактирования сообщения
    // 5. Проверка удаления сообщения пользователя
    // 6. Нажатие кнопки "Найти заново"
    // 7. Проверка возврата к запросу текста
}
```

**Тест fallback при ошибке редактирования:**
```java
@Test
void testFallbackWhenEditFails() {
    // 1. Отправка команды /search
    // 2. Удаление сообщения (симуляция)
    // 3. Ввод поискового запроса
    // 4. Проверка отправки нового сообщения
}
```

### Конфигурация Property-Based тестов

- Минимум 100 итераций на каждый property тест
- Использование библиотеки jqwik для генерации случайных данных
- Генераторы для:
  - Поисковых запросов (строки длиной 2-100 символов)
  - ID пользователей и чатов (положительные Long)
  - Списков событий (включая пустые списки)
  - Message ID (положительные Integer)

