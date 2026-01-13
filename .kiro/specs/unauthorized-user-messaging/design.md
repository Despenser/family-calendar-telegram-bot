# Документ проектирования

## Обзор

Данный документ описывает проектирование системы информативных сообщений для неавторизованных пользователей в семейном календарь-боте. Текущая реализация молча отклоняет запросы от незарегистрированных пользователей, что создает плохой пользовательский опыт. Новая система будет предоставлять понятные, дружелюбные сообщения, объясняющие причину ограничения доступа и способы получения доступа к функционалу бота.

Решение включает:
- Централизованный механизм проверки авторизации
- Категоризированные сообщения для разных типов команд
- Конфигурируемые тексты сообщений
- Логирование попыток доступа
- Обновление всех существующих обработчиков команд

## Архитектура

### Текущая архитектура

В настоящее время проверка авторизации выполняется в `UpdateProcessor`:

```java
// UpdateProcessor.java
private void handleCommand(Message message) {
    String commandText = message.getText().split(" ")[0];
    CommandHandler handler = commandDispatcher.getHandler(commandText);
    
    if (handler != null) {
        if (handler.requiresAuth()) {
            Optional<User> userOpt = userService.findByTelegramId(message.getFrom().getId());
            if (userOpt.isEmpty()) {
                // Молча игнорируется - нет сообщения пользователю
                return;
            }
            String response = handler.handle(message, userOpt.get());
        }
    }
}
```

### Новая архитектура

Новая архитектура добавляет:

1. **AuthorizationService** - централизованный сервис для проверки авторизации
2. **UnauthorizedMessageService** - сервис для формирования сообщений об ограничении доступа
3. **MessageCategory** - enum для категоризации типов сообщений
4. **Обновленный UpdateProcessor** - отправляет информативные сообщения

```
┌─────────────────────┐
│  UpdateProcessor    │
│                     │
│  - handleCommand()  │
└──────────┬──────────┘
           │
           ├──────────────────────────────────┐
           │                                  │
           ▼                                  ▼
┌──────────────────────┐          ┌─────────────────────────┐
│ AuthorizationService │          │ CommandDispatcher       │
│                      │          │                         │
│ - checkAuth()        │          │ - getHandler()          │
│ - logUnauthorized()  │          └─────────────────────────┘
└──────────┬───────────┘
           │
           ▼
┌────────────────────────────┐
│ UnauthorizedMessageService │
│                            │
│ - getMessage()             │
│ - formatMessage()          │
└────────────────────────────┘
           │
           ▼
┌────────────────────────────┐
│ TelegramMessageService     │
│                            │
│ - sendMessage()            │
└────────────────────────────┘
```

## Компоненты и интерфейсы

### 1. MessageCategory (Enum)

Категории сообщений для разных типов команд:

```java
public enum MessageCategory {
    EVENT_CREATION,      // Создание событий
    EVENT_VIEWING,       // Просмотр событий
    EVENT_MANAGEMENT,    // Редактирование/удаление
    SEARCH_FILTER,       // Поиск и фильтрация
    TRASH_MANAGEMENT,    // Управление корзиной
    STATISTICS,          // Статистика
    GENERAL              // Общее сообщение
}
```

### 2. AuthorizationService

Централизованный сервис для проверки авторизации:

```java
@Service
@Slf4j
public class AuthorizationService {
    
    private final UserService userService;
    private final UnauthorizedMessageService messageService;
    private final TelegramMessageService telegramMessageService;
    
    /**
     * Проверяет авторизацию пользователя и отправляет сообщение при отсутствии доступа.
     * 
     * @param telegramId Telegram ID пользователя
     * @param chatId ID чата для отправки сообщения
     * @param category категория команды
     * @param commandName имя команды для логирования
     * @return Optional с пользователем или пустой, если не авторизован
     */
    public Optional<User> checkAuthorizationAndNotify(
            Long telegramId, 
            Long chatId, 
            MessageCategory category,
            String commandName) {
        
        Optional<User> userOpt = userService.findByTelegramId(telegramId);
        
        if (userOpt.isEmpty()) {
            logUnauthorizedAccess(telegramId, commandName);
            String message = messageService.getMessage(category);
            telegramMessageService.sendMessage(chatId, message);
        }
        
        return userOpt;
    }
    
    /**
     * Логирует попытку доступа неавторизованного пользователя.
     */
    private void logUnauthorizedAccess(Long telegramId, String commandName) {
        log.info("Unauthorized access attempt: telegramId={}, command={}, timestamp={}", 
                telegramId, commandName, Instant.now());
    }
}
```

### 3. UnauthorizedMessageService

Сервис для формирования сообщений об ограничении доступа:

```java
@Service
@Slf4j
public class UnauthorizedMessageService {
    
    private final Map<MessageCategory, String> messages;
    
    @Autowired
    public UnauthorizedMessageService(@Value("${bot.messages.unauthorized.prefix}") String prefix) {
        this.messages = initializeMessages(prefix);
    }
    
    /**
     * Получает сообщение для указанной категории.
     */
    public String getMessage(MessageCategory category) {
        return messages.getOrDefault(category, messages.get(MessageCategory.GENERAL));
    }
    
    /**
     * Инициализирует сообщения для всех категорий.
     */
    private Map<MessageCategory, String> initializeMessages(String prefix) {
        Map<MessageCategory, String> msgs = new EnumMap<>(MessageCategory.class);
        
        msgs.put(MessageCategory.EVENT_CREATION, 
            formatMessage(prefix, 
                "Создание событий доступно только зарегистрированным пользователям семейного календаря."));
        
        msgs.put(MessageCategory.EVENT_VIEWING,
            formatMessage(prefix,
                "Просмотр событий доступен только членам семейного календаря."));
        
        msgs.put(MessageCategory.EVENT_MANAGEMENT,
            formatMessage(prefix,
                "Управление событиями доступно только зарегистрированным пользователям."));
        
        msgs.put(MessageCategory.SEARCH_FILTER,
            formatMessage(prefix,
                "Поиск и фильтрация событий доступны только членам семейного календаря."));
        
        msgs.put(MessageCategory.TRASH_MANAGEMENT,
            formatMessage(prefix,
                "Управление корзиной доступно только зарегистрированным пользователям."));
        
        msgs.put(MessageCategory.STATISTICS,
            formatMessage(prefix,
                "Просмотр статистики доступен только членам семейного календаря."));
        
        msgs.put(MessageCategory.GENERAL,
            formatMessage(prefix,
                "Эта функция доступна только зарегистрированным пользователям семейного календаря."));
        
        return msgs;
    }
    
    /**
     * Форматирует сообщение с префиксом и инструкциями.
     */
    private String formatMessage(String prefix, String mainText) {
        return String.format("%s %s\n\n%s",
            prefix,
            MarkdownFormatter.escape(mainText),
            MarkdownFormatter.escape("Для получения доступа обратитесь к администратору вашей семьи."));
    }
}
```

### 4. Обновленный UpdateProcessor

Модификация метода `handleCommand`:

```java
private void handleCommand(Message message) {
    String commandText = message.getText().split(" ")[0];
    CommandHandler handler = commandDispatcher.getHandler(commandText);
    
    if (handler != null) {
        if (handler.requiresAuth()) {
            Long telegramId = message.getFrom().getId();
            Long chatId = message.getChatId();
            String username = message.getFrom().getUserName();
            
            // Определяем категорию сообщения на основе команды
            MessageCategory category = determineMessageCategory(commandText);
            
            // Проверяем авторизацию и отправляем сообщение при необходимости
            Optional<User> userOpt = authorizationService.checkAuthorizationAndNotify(
                telegramId, chatId, category, commandText);
            
            if (userOpt.isEmpty()) {
                log.info("Command rejected due to unauthorized access: command={}, telegramId={}, username={}",
                        commandText, telegramId, username);
                return;
            }
            
            String response = handler.handle(message, userOpt.get());
            if (response != null && !response.isBlank()) {
                telegramMessageService.sendMessage(chatId, response);
            }
        } else {
            // Команды без авторизации
            String response = handler.handle(message, null);
            if (response != null && !response.isBlank()) {
                telegramMessageService.sendMessage(message.getChatId(), response);
            }
        }
    }
}

/**
 * Определяет категорию сообщения на основе команды.
 */
private MessageCategory determineMessageCategory(String command) {
    return switch (command) {
        case "/add_event" -> MessageCategory.EVENT_CREATION;
        case "/my_events", "/upcoming_events", "/today", "/week" -> MessageCategory.EVENT_VIEWING;
        case "/search", "/filter" -> MessageCategory.SEARCH_FILTER;
        case "/trash" -> MessageCategory.TRASH_MANAGEMENT;
        case "/stats" -> MessageCategory.STATISTICS;
        default -> MessageCategory.GENERAL;
    };
}
```

### 5. Обновление HelpCommandHandler

Модификация для отображения команд с пометками об авторизации:

```java
@Override
public String handle(Message message, User user) {
    boolean isAuthorized = (user != null);
    
    StringBuilder help = new StringBuilder();
    help.append(String.format("❓ %s\n\n", bold("Справка по командам")));
    
    if (!isAuthorized) {
        help.append(escape("Вы не зарегистрированы в семейном календаре.\n"));
        help.append(escape("Некоторые команды требуют регистрации (отмечены 🔒).\n\n"));
    }
    
    // Получаем все команды
    Map<String, CommandHandler> handlers = commandDispatcher.getAllHandlers();
    
    for (CommandHandler handler : handlers.values()) {
        String lockIcon = handler.requiresAuth() && !isAuthorized ? "🔒 " : "";
        help.append(String.format("%s%s - %s\n", 
            lockIcon,
            escape(handler.getCommand()),
            escape(handler.getDescription())));
    }
    
    if (!isAuthorized) {
        help.append(String.format("\n\n%s",
            escape("Для получения доступа к полному функционалу обратитесь к администратору вашей семьи.")));
    }
    
    return help.toString();
}
```

## Модели данных

### Конфигурация сообщений (application.yml)

```yaml
bot:
  messages:
    unauthorized:
      prefix: "🔒"
      contact-admin: "Для получения доступа обратитесь к администратору вашей семьи."
```

### Структура логирования

Логи попыток доступа неавторизованных пользователей:

```json
{
  "timestamp": "2026-01-12T10:30:00Z",
  "level": "INFO",
  "logger": "AuthorizationService",
  "message": "Unauthorized access attempt",
  "telegramId": 123456789,
  "username": "john_doe",
  "command": "/add_event",
  "category": "EVENT_CREATION"
}
```

## Свойства корректности

*Свойство - это характеристика или поведение, которое должно выполняться для всех допустимых выполнений системы - по сути, формальное утверждение о том, что система должна делать. Свойства служат мостом между человекочитаемыми спецификациями и машинно-проверяемыми гарантиями корректности.*


### Рефлексия свойств

После анализа критериев приемки выявлены следующие избыточности:

**Группа 1: Проверка и отправка сообщений**
- Свойство 1.1 (проверка в БД) + Свойство 1.2 (отправка сообщения) можно объединить в одно свойство: "Для любого незарегистрированного пользователя система проверяет БД и отправляет сообщение"
- Свойство 2.3 дублирует 1.2

**Группа 2: Содержание сообщений**
- Свойства 1.3, 1.4, 1.5 и 3.5 все проверяют содержание сообщений и могут быть объединены в одно комплексное свойство: "Все сообщения содержат эмодзи, объяснение и инструкции"

**Группа 3: Логирование**
- Свойства 2.5, 6.1, 6.2 проверяют разные аспекты логирования и могут быть объединены в одно свойство: "Каждая попытка доступа логируется с полной информацией"

**Группа 4: Негативные формулировки**
- Свойства 7.3 и 7.4 проверяют тон сообщений и могут быть объединены

После устранения избыточности остаются следующие уникальные свойства:

### Свойства корректности

**Property 1: Проверка авторизации и уведомление**
*Для любого* незарегистрированного пользователя и любой команды, требующей авторизации, система должна проверить наличие пользователя в БД и отправить информативное сообщение об ограничении доступа
**Validates: Requirements 1.1, 1.2, 2.3**

**Property 2: Структура сообщений об ограничении**
*Для любого* сообщения об ограничении доступа, оно должно содержать эмодзи "🔒", объяснение о необходимости регистрации и инструкции по получению доступа
**Validates: Requirements 1.3, 1.4, 1.5, 3.5**

**Property 3: Возвращаемое значение метода проверки**
*Для любого* вызова метода проверки авторизации, он должен возвращать Optional<User> - пустой для незарегистрированных и с пользователем для зарегистрированных
**Validates: Requirements 2.2**

**Property 4: Прерывание выполнения команды**
*Для любого* обработчика команды с требованием авторизации, метод handle не должен вызываться для незарегистрированных пользователей
**Validates: Requirements 2.4**

**Property 5: Полное логирование попыток доступа**
*Для любой* попытки доступа незарегистрированного пользователя, система должна создать лог-запись уровня INFO, содержащую telegram_id, username, команду и timestamp
**Validates: Requirements 2.5, 6.1, 6.2**

**Property 6: Отдельное логирование каждой попытки**
*Для любого* количества N попыток доступа, система должна создать ровно N отдельных лог-записей
**Validates: Requirements 6.3**

**Property 7: Маркировка команд в справке**
*Для любой* команды с requiresAuth=true, при отображении справки незарегистрированному пользователю, команда должна быть помечена эмодзи "🔒"
**Validates: Requirements 4.3**

**Property 8: Позитивный тон сообщений**
*Для любого* сообщения об ограничении доступа, оно не должно содержать негативные формулировки ("доступ запрещен", "вы не можете") и должно содержать конструктивные фразы ("станет доступно", "будет доступен")
**Validates: Requirements 7.3, 7.4**

**Property 9: Подстановка параметров в шаблоны**
*Для любого* сообщения, формируемого из шаблона с параметрами, все параметры должны быть корректно подставлены в итоговый текст
**Validates: Requirements 8.2**

## Обработка ошибок

### Сценарии ошибок

1. **Ошибка доступа к БД при проверке авторизации**
   - Логирование ошибки с уровнем ERROR
   - Отправка пользователю сообщения о временной недоступности
   - Не блокировать работу бота

2. **Ошибка отправки сообщения через Telegram API**
   - Логирование ошибки с деталями
   - Повторная попытка отправки (до 3 раз)
   - Если все попытки неудачны - логировать и продолжить работу

3. **Отсутствие шаблона сообщения для категории**
   - Использование дефолтного сообщения (MessageCategory.GENERAL)
   - Логирование предупреждения о missing template
   - Продолжение работы с дефолтным сообщением

4. **Ошибка при логировании попытки доступа**
   - Не должна прерывать основной flow
   - Логирование ошибки логирования (meta-logging)
   - Продолжение обработки команды

### Обработка edge cases

1. **Пользователь с null username**
   - Логировать только telegram_id
   - В сообщениях использовать обращение без имени

2. **Команда без категории в switch**
   - Использовать MessageCategory.GENERAL
   - Логировать предупреждение о неизвестной команде

3. **Одновременные попытки доступа от одного пользователя**
   - Каждая попытка обрабатывается независимо
   - Каждая попытка логируется отдельно

## Стратегия тестирования

### Unit-тесты

**AuthorizationServiceTest:**
- `testCheckAuthorizationForRegisteredUser()` - проверка возврата Optional с пользователем
- `testCheckAuthorizationForUnregisteredUser()` - проверка возврата пустого Optional и отправки сообщения
- `testLoggingUnauthorizedAccess()` - проверка логирования попыток доступа
- `testMultipleUnauthorizedAttempts()` - проверка логирования нескольких попыток

**UnauthorizedMessageServiceTest:**
- `testGetMessageForEachCategory()` - проверка получения сообщения для каждой категории
- `testMessageContainsLockEmoji()` - проверка наличия эмодзи во всех сообщениях
- `testMessageContainsContactAdmin()` - проверка наличия инструкций во всех сообщениях
- `testFallbackToGeneralMessage()` - проверка fallback на дефолтное сообщение
- `testNoNegativeFormulations()` - проверка отсутствия негативных формулировок

**UpdateProcessorTest:**
- `testCommandRejectedForUnauthorizedUser()` - проверка отклонения команды
- `testCommandExecutedForAuthorizedUser()` - проверка выполнения команды
- `testCorrectCategoryDetermination()` - проверка определения категории для каждой команды
- `testHandlerNotCalledForUnauthorized()` - проверка, что handle не вызывается

**HelpCommandHandlerTest:**
- `testHelpForUnauthorizedUser()` - проверка отображения справки с пометками
- `testHelpForAuthorizedUser()` - проверка отображения справки без пометок
- `testLockIconForAuthCommands()` - проверка наличия 🔒 для команд с авторизацией
- `testRegistrationInfoIncluded()` - проверка наличия информации о регистрации

**StartCommandHandlerTest:**
- `testStartCommandWithoutAuth()` - проверка выполнения без авторизации
- `testWelcomeMessageContent()` - проверка содержания приветственного сообщения

### Property-Based тесты

Для реализации property-based тестов в Java будем использовать библиотеку **jqwik** (версия 1.8.2).

**Генераторы данных:**

```java
@Provide
Arbitrary<Long> telegramIds() {
    return Arbitraries.longs().between(1L, Long.MAX_VALUE);
}

@Provide
Arbitrary<String> usernames() {
    return Arbitraries.strings()
        .alpha()
        .ofMinLength(3)
        .ofMaxLength(32);
}

@Provide
Arbitrary<String> commands() {
    return Arbitraries.of(
        "/add_event", "/my_events", "/upcoming_events",
        "/today", "/week", "/search", "/filter",
        "/trash", "/stats"
    );
}

@Provide
Arbitrary<MessageCategory> categories() {
    return Arbitraries.of(MessageCategory.class);
}
```

**Property тесты:**

```java
@Property
void unauthorizedUserAlwaysReceivesMessage(
    @ForAll("telegramIds") Long telegramId,
    @ForAll("commands") String command) {
    
    // Given: незарегистрированный пользователь
    when(userService.findByTelegramId(telegramId))
        .thenReturn(Optional.empty());
    
    // When: пользователь отправляет команду
    authorizationService.checkAuthorizationAndNotify(
        telegramId, chatId, category, command);
    
    // Then: сообщение должно быть отправлено
    verify(telegramMessageService, times(1))
        .sendMessage(eq(chatId), anyString());
}

@Property
void allMessagesContainRequiredElements(
    @ForAll("categories") MessageCategory category) {
    
    // When: получаем сообщение для категории
    String message = messageService.getMessage(category);
    
    // Then: сообщение содержит все обязательные элементы
    assertThat(message).contains("🔒");
    assertThat(message).containsIgnoringCase("регистр");
    assertThat(message).containsIgnoringCase("администратор");
}

@Property
void eachAttemptCreatesOneLogEntry(
    @ForAll("telegramIds") Long telegramId,
    @ForAll("commands") String command,
    @ForAll @IntRange(min = 1, max = 10) int attempts) {
    
    // Given: незарегистрированный пользователь
    when(userService.findByTelegramId(telegramId))
        .thenReturn(Optional.empty());
    
    // When: пользователь делает N попыток
    for (int i = 0; i < attempts; i++) {
        authorizationService.checkAuthorizationAndNotify(
            telegramId, chatId, category, command);
    }
    
    // Then: создано ровно N лог-записей
    verify(logger, times(attempts))
        .info(contains("Unauthorized access attempt"));
}

@Property
void noNegativeFormulationsInMessages(
    @ForAll("categories") MessageCategory category) {
    
    // When: получаем сообщение
    String message = messageService.getMessage(category);
    
    // Then: нет негативных формулировок
    assertThat(message.toLowerCase())
        .doesNotContain("запрещен", "не можете", "нельзя");
    
    // And: есть конструктивные формулировки
    assertThat(message.toLowerCase())
        .containsAnyOf("доступно", "будет доступен", "станет доступно");
}

@Property
void authCheckReturnsCorrectOptional(
    @ForAll("telegramIds") Long telegramId,
    @ForAll boolean isRegistered) {
    
    // Given: пользователь зарегистрирован или нет
    if (isRegistered) {
        User user = createTestUser(telegramId);
        when(userService.findByTelegramId(telegramId))
            .thenReturn(Optional.of(user));
    } else {
        when(userService.findByTelegramId(telegramId))
            .thenReturn(Optional.empty());
    }
    
    // When: проверяем авторизацию
    Optional<User> result = authorizationService
        .checkAuthorizationAndNotify(telegramId, chatId, category, command);
    
    // Then: результат соответствует статусу регистрации
    assertThat(result.isPresent()).isEqualTo(isRegistered);
}
```

### Integration тесты

**AuthorizationIntegrationTest:**
- Тестирование полного flow от получения команды до отправки сообщения
- Использование TestContainers для PostgreSQL
- Проверка взаимодействия всех компонентов

**MessageDeliveryIntegrationTest:**
- Тестирование отправки сообщений через mock Telegram API
- Проверка retry логики при ошибках
- Проверка форматирования сообщений

### Тестовое покрытие

Целевое покрытие:
- **Line coverage**: минимум 85%
- **Branch coverage**: минимум 80%
- **Method coverage**: 100% для публичных методов

Критические компоненты для 100% покрытия:
- AuthorizationService
- UnauthorizedMessageService
- UpdateProcessor (метод handleCommand)

## Миграция и развертывание

### План миграции

**Фаза 1: Создание новых компонентов**
1. Создать MessageCategory enum
2. Создать UnauthorizedMessageService
3. Создать AuthorizationService
4. Добавить конфигурацию в application.yml

**Фаза 2: Обновление UpdateProcessor**
1. Добавить зависимость на AuthorizationService
2. Обновить метод handleCommand
3. Добавить метод determineMessageCategory

**Фаза 3: Обновление HelpCommandHandler**
1. Обновить метод handle для отображения пометок
2. Добавить информацию о регистрации

**Фаза 4: Тестирование**
1. Написать unit-тесты для новых компонентов
2. Написать property-based тесты
3. Обновить существующие тесты

**Фаза 5: Развертывание**
1. Развернуть в тестовом окружении
2. Провести ручное тестирование
3. Развернуть в production

### Обратная совместимость

Изменения полностью обратно совместимы:
- Не изменяется API обработчиков команд
- Не изменяется структура БД
- Добавляется только новая функциональность

### Rollback план

В случае проблем:
1. Откатить изменения в UpdateProcessor
2. Удалить вызовы AuthorizationService
3. Вернуть старую логику молчаливого отклонения
4. Удалить новые сервисы можно позже

## Мониторинг и метрики

### Метрики для отслеживания

1. **Количество попыток доступа неавторизованных пользователей**
   - Метрика: `unauthorized_access_attempts_total`
   - Теги: command, telegram_id
   - Тип: Counter

2. **Количество отправленных сообщений об ограничении**
   - Метрика: `unauthorized_messages_sent_total`
   - Теги: category
   - Тип: Counter

3. **Время обработки проверки авторизации**
   - Метрика: `authorization_check_duration_seconds`
   - Тип: Histogram

4. **Ошибки при отправке сообщений**
   - Метрика: `message_send_errors_total`
   - Теги: error_type
   - Тип: Counter

### Алерты

1. **Высокая частота попыток доступа от одного пользователя**
   - Условие: > 10 попыток за 1 минуту
   - Действие: Логировать предупреждение, возможная атака

2. **Ошибки отправки сообщений**
   - Условие: > 5% ошибок за 5 минут
   - Действие: Уведомить администратора

3. **Рост количества неавторизованных попыток**
   - Условие: Рост > 50% за час
   - Действие: Проверить, не произошла ли утечка ссылки на бота

## Безопасность

### Защита от злоупотреблений

1. **Rate limiting для неавторизованных пользователей**
   - Ограничение: максимум 5 команд в минуту
   - Реализация: через in-memory cache с TTL

2. **Защита от спама**
   - Блокировка пользователя после 20 попыток за 5 минут
   - Временная блокировка на 1 час

3. **Логирование подозрительной активности**
   - Множественные попытки от разных telegram_id с одного IP
   - Автоматические попытки (слишком быстрые)

### Конфиденциальность

1. **Логирование персональных данных**
   - Не логировать username в production (только telegram_id)
   - Использовать хеширование для аналитики

2. **Сообщения об ошибках**
   - Не раскрывать внутреннюю структуру системы
   - Не указывать, существует ли пользователь в системе

## Производительность

### Оптимизации

1. **Кеширование сообщений**
   - Сообщения для категорий кешируются при старте
   - Не требуется повторное форматирование

2. **Асинхронная отправка сообщений**
   - Отправка через ExecutorService
   - Не блокирует обработку команды

3. **Batch логирование**
   - Группировка лог-записей для production
   - Снижение нагрузки на систему логирования

### Ожидаемая производительность

- Проверка авторизации: < 10ms
- Формирование сообщения: < 1ms
- Отправка сообщения: < 100ms (зависит от Telegram API)
- Общее время обработки: < 150ms

## Документация

### Обновления документации

1. **README.md**
   - Добавить раздел об обработке неавторизованных пользователей
   - Описать процесс регистрации

2. **API документация**
   - Документировать новые сервисы
   - Добавить примеры использования

3. **Руководство администратора**
   - Инструкции по добавлению пользователей
   - Мониторинг попыток доступа

4. **Changelog**
   - Описать новую функциональность
   - Указать breaking changes (если есть)
