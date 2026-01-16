# Дизайн: Исправление ошибок в продакшене

## Обзор

Данный документ описывает технический дизайн для исправления критических ошибок, обнаруженных в логах Docker приложения. Основные проблемы связаны с:
1. Некорректным экранированием специальных символов в MarkdownV2
2. Отсутствием обработчиков для callback-запросов фильтрации
3. Неполной реализацией команды /filter
4. Неэффективной обработкой устаревших callback-запросов
5. Предупреждениями о версиях зависимостей

## Архитектура

### Компоненты для модификации

1. **MarkdownFormatter** - утилита для форматирования текста в MarkdownV2
2. **FilterCommandHandler** - обработчик команды /filter
3. **CallbackQueryDispatcher** - диспетчер callback-запросов
4. **FilterCallbackHandler** (новый) - обработчик callback-запросов фильтрации
5. **TelegramMessageService** - сервис отправки сообщений
6. **application.yml** - конфигурация приложения
7. **pom.xml** - зависимости Maven

## Компоненты и интерфейсы

### 1. MarkdownFormatter

**Текущая проблема:** Метод `escapeMarkdownV2` не экранирует все зарезервированные символы.

**Решение:**
```java
public class MarkdownFormatter {
    // Все зарезервированные символы MarkdownV2
    private static final String[] MARKDOWN_SPECIAL_CHARS = {
        "_", "*", "[", "]", "(", ")", "~", "`", ">", "#", "+", "-", "=", "|", "{", "}", ".", "!"
    };
    
    /**
     * Экранирует все специальные символы MarkdownV2
     */
    public static String escapeMarkdownV2(String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }
        
        String escaped = text;
        for (String specialChar : MARKDOWN_SPECIAL_CHARS) {
            escaped = escaped.replace(specialChar, "\\" + specialChar);
        }
        return escaped;
    }
    
    /**
     * Экранирует текст, сохраняя уже существующую разметку
     */
    public static String escapeMarkdownV2PreservingFormatting(String text) {
        // Логика для сохранения *bold* и _italic_
        // но экранирования остальных символов
    }
}
```

### 2. FilterCommandHandler

**Текущая проблема:** Обработчик возвращает пустой ответ.

**Решение:**
```java
@Component
public class FilterCommandHandler implements CommandHandler {
    
    private final KeyboardService keyboardService;
    private final UserService userService;
    
    @Override
    public SendMessage handle(Update update, User user) {
        Long chatId = update.getMessage().getChatId();
        
        // Создаем inline-клавиатуру с опциями фильтрации
        InlineKeyboardMarkup keyboard = keyboardService.createFilterKeyboard();
        
        String messageText = MarkdownFormatter.escapeMarkdownV2(
            "🔍 Выберите тип событий для отображения:"
        );
        
        return SendMessage.builder()
            .chatId(chatId.toString())
            .text(messageText)
            .parseMode("MarkdownV2")
            .replyMarkup(keyboard)
            .build();
    }
    
    @Override
    public String getCommand() {
        return "/filter";
    }
}
```

### 3. KeyboardService (расширение)

**Новый метод:**
```java
public InlineKeyboardMarkup createFilterKeyboard() {
    List<List<InlineKeyboardButton>> keyboard = new ArrayList<>();
    
    // Первая строка: Все события
    keyboard.add(Collections.singletonList(
        InlineKeyboardButton.builder()
            .text("📋 Все события")
            .callbackData("filter_all")
            .build()
    ));
    
    // Вторая строка: Семейные и Личные
    keyboard.add(Arrays.asList(
        InlineKeyboardButton.builder()
            .text("👨‍👩‍👧‍👦 Семейные")
            .callbackData("filter_family")
            .build(),
        InlineKeyboardButton.builder()
            .text("👤 Личные")
            .callbackData("filter_personal")
            .build()
    ));
    
    return InlineKeyboardMarkup.builder()
        .keyboard(keyboard)
        .build();
}
```

### 4. FilterCallbackHandler (новый компонент)

```java
@Component
public class FilterCallbackHandler implements CallbackHandler {
    
    private final UserService userService;
    private final EventService eventService;
    private final TelegramMessageService telegramMessageService;
    private final KeyboardService keyboardService;
    
    @Override
    public void handle(CallbackQuery callbackQuery, User user) {
        String callbackData = callbackQuery.getData();
        Long chatId = callbackQuery.getMessage().getChatId();
        
        // Определяем тип фильтра
        EventFilter filter = parseFilter(callbackData);
        
        // Сохраняем выбор пользователя
        userService.setEventFilter(user.getId(), filter);
        
        // Получаем отфильтрованные события
        List<Event> events = eventService.getFilteredEvents(user, filter);
        
        // Формируем и отправляем ответ
        String messageText = formatFilteredEvents(events, filter);
        EditMessageText editMessage = EditMessageText.builder()
            .chatId(chatId.toString())
            .messageId(callbackQuery.getMessage().getMessageId())
            .text(messageText)
            .parseMode("MarkdownV2")
            .replyMarkup(keyboardService.createFilterKeyboard())
            .build();
            
        telegramMessageService.editMessage(editMessage);
        
        // Отвечаем на callback query
        telegramMessageService.answerCallbackQuery(
            callbackQuery.getId(),
            "Фильтр применен: " + filter.getDisplayName()
        );
    }
    
    @Override
    public boolean canHandle(String callbackData) {
        return callbackData != null && callbackData.startsWith("filter_");
    }
    
    private EventFilter parseFilter(String callbackData) {
        switch (callbackData) {
            case "filter_all":
                return EventFilter.ALL;
            case "filter_family":
                return EventFilter.FAMILY;
            case "filter_personal":
                return EventFilter.PERSONAL;
            default:
                return EventFilter.ALL;
        }
    }
    
    private String formatFilteredEvents(List<Event> events, EventFilter filter) {
        StringBuilder sb = new StringBuilder();
        sb.append(MarkdownFormatter.escapeMarkdownV2("🔍 Фильтр: "))
          .append("*").append(MarkdownFormatter.escapeMarkdownV2(filter.getDisplayName())).append("*\n\n");
        
        if (events.isEmpty()) {
            sb.append(MarkdownFormatter.escapeMarkdownV2("Нет событий для отображения"));
        } else {
            for (Event event : events) {
                sb.append(formatEvent(event)).append("\n\n");
            }
        }
        
        return sb.toString();
    }
}
```

### 5. EventFilter (новый enum)

```java
public enum EventFilter {
    ALL("Все события"),
    FAMILY("Семейные"),
    PERSONAL("Личные");
    
    private final String displayName;
    
    EventFilter(String displayName) {
        this.displayName = displayName;
    }
    
    public String getDisplayName() {
        return displayName;
    }
}
```

### 6. CallbackQueryDispatcher (модификация)

**Добавление обработчика фильтрации:**
```java
@Service
public class CallbackQueryDispatcher {
    
    private final List<CallbackHandler> handlers;
    private final FilterCallbackHandler filterCallbackHandler;
    
    public void dispatch(CallbackQuery callbackQuery, User user) {
        String callbackData = callbackQuery.getData();
        
        // Проверяем FilterCallbackHandler первым
        if (filterCallbackHandler.canHandle(callbackData)) {
            filterCallbackHandler.handle(callbackQuery, user);
            return;
        }
        
        // Остальные обработчики
        for (CallbackHandler handler : handlers) {
            if (handler.canHandle(callbackData)) {
                handler.handle(callbackQuery, user);
                return;
            }
        }
        
        // Логируем неизвестный callback
        log.warn("Неизвестный callback data: '{}', telegramId={}", 
                 callbackData, user.getTelegramId());
    }
}
```

### 7. TelegramMessageService (улучшение обработки ошибок)

**Модификация метода answerCallbackQuery:**
```java
public void answerCallbackQuery(String callbackQueryId, String text) {
    try {
        AnswerCallbackQuery answer = AnswerCallbackQuery.builder()
            .callbackQueryId(callbackQueryId)
            .text(text)
            .build();
            
        bot.execute(answer);
    } catch (TelegramApiException e) {
        // Проверяем, не устарел ли запрос
        if (e.getMessage().contains("query is too old")) {
            log.info("Callback query устарел: callbackQueryId={}", callbackQueryId);
            return; // Не повторяем попытки
        }
        
        log.error("Ошибка ответа на callback query: callbackQueryId={}, error={}", 
                  callbackQueryId, e.getMessage());
    }
}
```

**Модификация метода sendMessage с retry:**
```java
private void sendMessageWithRetry(SendMessage message, int maxAttempts) {
    int attempt = 0;
    TelegramApiException lastException = null;
    
    while (attempt < maxAttempts) {
        try {
            bot.execute(message);
            return; // Успешно отправлено
        } catch (TelegramApiException e) {
            lastException = e;
            attempt++;
            
            // Логируем каждую попытку
            log.error("Bad Request (400): Ошибка парсинга MarkdownV2. " +
                     "telegramId={}, textPreview='{}', attempt={}/{}", 
                     extractChatId(message), 
                     truncateText(message.getText(), 50),
                     attempt,
                     maxAttempts);
            
            // Если это ошибка парсинга, не имеет смысла повторять
            if (e.getMessage().contains("can't parse entities")) {
                log.error("Критическая ошибка парсинга, прекращаем попытки");
                break;
            }
        }
    }
    
    // Все попытки исчерпаны
    log.error("Все попытки отправки сообщения исчерпаны: " +
             "telegramId={}, textLength={}, error={}", 
             extractChatId(message),
             message.getText().length(),
             lastException.getMessage());
}
```

## Модели данных

### User (расширение)

Добавляем поле для хранения выбранного фильтра:

```java
@Entity
@Table(name = "users")
public class User {
    // ... существующие поля
    
    @Enumerated(EnumType.STRING)
    @Column(name = "event_filter")
    private EventFilter eventFilter = EventFilter.ALL;
    
    // getters and setters
}
```

### Миграция базы данных

```sql
-- V12__Add_event_filter_to_users.sql
ALTER TABLE users 
ADD COLUMN event_filter VARCHAR(20) DEFAULT 'ALL';
```

## Свойства корректности

*Свойство - это характеристика или поведение, которое должно выполняться во всех допустимых выполнениях системы.*

### Свойство 1: Экранирование специальных символов

*Для любого* текста, содержащего зарезервированные символы MarkdownV2, после применения `escapeMarkdownV2` все специальные символы должны быть экранированы обратным слешем.

**Validates: Requirements 1.1, 1.2, 1.3**

### Свойство 2: Идемпотентность экранирования

*Для любого* текста, применение `escapeMarkdownV2` дважды должно давать тот же результат, что и однократное применение (уже экранированные символы не должны экранироваться повторно).

**Validates: Requirements 1.4**

### Свойство 3: Обработка callback-запросов фильтрации

*Для любого* callback data с префиксом 'filter_', система должна найти соответствующий обработчик и успешно обработать запрос.

**Validates: Requirements 2.1, 2.2, 2.3, 2.5**

### Свойство 4: Непустой ответ команды /filter

*Для любого* пользователя, выполнение команды /filter должно возвращать SendMessage с непустым текстом и inline-клавиатурой.

**Validates: Requirements 3.1, 3.2, 3.3**

### Свойство 5: Прекращение повторов для устаревших запросов

*Для любого* callback query, если получена ошибка "query is too old", система не должна делать повторные попытки ответа.

**Validates: Requirements 4.1, 4.2, 4.5**

## Обработка ошибок

### Стратегия обработки ошибок парсинга MarkdownV2

1. **Первая попытка**: Отправка с MarkdownV2
2. **При ошибке парсинга**: Логирование детальной информации
3. **Fallback**: Отправка без форматирования (plain text)
4. **Критическая ошибка**: Уведомление разработчика

### Стратегия обработки устаревших callback-запросов

1. **Проверка возраста**: Если запрос старше 30 секунд - пропустить
2. **Обработка ошибки**: Логировать как INFO, не как ERROR
3. **Без повторов**: Не делать retry для устаревших запросов

### Стратегия обработки неизвестных callback

1. **Логирование**: WARN уровень с callback data и telegramId
2. **Ответ пользователю**: "Действие недоступно, попробуйте обновить"
3. **Метрики**: Счетчик неизвестных callback для мониторинга

## Стратегия тестирования

### Unit-тесты

1. **MarkdownFormatterTest**
   - Тест экранирования каждого специального символа
   - Тест идемпотентности экранирования
   - Тест с пустыми и null значениями
   - Тест сохранения форматирования

2. **FilterCommandHandlerTest**
   - Тест генерации непустого ответа
   - Тест создания inline-клавиатуры
   - Тест корректного экранирования текста

3. **FilterCallbackHandlerTest**
   - Тест обработки каждого типа фильтра
   - Тест сохранения выбора пользователя
   - Тест форматирования отфильтрованных событий

4. **CallbackQueryDispatcherTest**
   - Тест маршрутизации filter_ callback
   - Тест логирования неизвестных callback

5. **TelegramMessageServiceTest**
   - Тест обработки устаревших callback
   - Тест прекращения retry при ошибках парсинга
   - Тест fallback на plain text

### Property-Based тесты

1. **Property: Экранирование всех специальных символов**
   - Генерация случайных строк со специальными символами
   - Проверка, что все символы экранированы

2. **Property: Идемпотентность экранирования**
   - Генерация случайных строк
   - Проверка: escape(escape(s)) == escape(s)

3. **Property: Обработка всех filter_ callback**
   - Генерация случайных filter_ callback data
   - Проверка, что все обрабатываются без исключений

### Integration тесты

1. **Тест полного flow фильтрации**
   - Отправка команды /filter
   - Нажатие на кнопку фильтра
   - Проверка отображения отфильтрованных событий

2. **Тест обработки ошибок MarkdownV2**
   - Отправка сообщения с проблемными символами
   - Проверка fallback на plain text

## Конфигурация

### application.yml

```yaml
spring:
  jpa:
    open-in-view: false  # Явно отключаем
    properties:
      hibernate:
        # Удаляем явное указание dialect
        # dialect: org.hibernate.dialect.PostgreSQLDialect
```

### pom.xml

```xml
<!-- Обновление Flyway для поддержки PostgreSQL 18 -->
<dependency>
    <groupId>org.flywaydb</groupId>
    <artifactId>flyway-core</artifactId>
    <version>10.21.0</version>
</dependency>

<dependency>
    <groupId>org.flywaydb</groupId>
    <artifactId>flyway-database-postgresql</artifactId>
    <version>10.21.0</version>
</dependency>
```

## Метрики и мониторинг

### Новые метрики

1. **markdown_parse_errors_total** - Счетчик ошибок парсинга MarkdownV2
2. **unknown_callbacks_total** - Счетчик неизвестных callback
3. **filter_usage_total** - Счетчик использования каждого типа фильтра
4. **stale_callback_queries_total** - Счетчик устаревших callback queries

### Алерты

1. **Высокий процент ошибок парсинга** - > 5% от всех сообщений
2. **Много неизвестных callback** - > 10 в час
3. **Рост устаревших callback** - индикатор проблем с производительностью
