# Документ проектирования - Семейный Календарь Бот

## Обзор

Проект представляет собой Spring Boot 3.4.x приложение для создания Telegram бота семейного календаря. Бот позволяет членам семьи создавать, просматривать и управлять событиями, получать уведомления о предстоящих мероприятиях. Система поддерживает персональные и семейные события, повторяющиеся события, вложения файлов, чек-листы, комментарии, гибкие напоминания и корзину удаленных событий. Система использует Webhook для получения обновлений от Telegram, PostgreSQL для хранения данных и Docker для развертывания.

Основные технологии:
- **Spring Boot 3.5.3** - основной фреймворк приложения (декабрь 2025, проверено через Context7)
- **Java 21 LTS** - целевая версия Java (стабильная LTS версия)
- **Spring Data JPA** - работа с базой данных
- **PostgreSQL 18.1** - реляционная база данных (декабрь 2025)
- **telegrambots-spring-boot-starter 8.2.0** - Spring Boot интеграция с Telegram Bot API (декабрь 2025)
- **Flyway 11.1.0** - миграции базы данных (декабрь 2025)
- **Testcontainers 1.21.2** - тестирование с контейнерами (декабрь 2025, проверено через Context7)
- **Maven 3.9+** - система сборки и управления зависимостями
- **Docker & Docker Compose** - контейнеризация
- **SLF4J + Logback** - логирование

## Архитектура

Приложение следует многоуровневой архитектуре с четким разделением ответственности:

```
┌─────────────────────────────────────────┐
│         Telegram Bot API                │
│         (External Service)              │
└──────────────┬──────────────────────────┘
               │ Webhook (HTTPS)
               ↓
┌─────────────────────────────────────────┐
│      Webhook Controller                 │
│  (@RestController)                      │
│  - Прием Updates от Telegram           │
│  - Валидация запросов                   │
└──────────────┬──────────────────────────┘
               │
               ↓
┌─────────────────────────────────────────┐
│      Command Handler Layer              │
│  - StartCommandHandler                  │
│  - AddEventCommandHandler               │
│  - UpcomingEventsCommandHandler         │
│  - MyEventsCommandHandler               │
│  - HelpCommandHandler                   │
│  - TodayCommandHandler                  │
│  - WeekCommandHandler                   │
│  - SearchCommandHandler                 │
│  - FilterCommandHandler                 │
│  - TrashCommandHandler                  │
│  - StatsCommandHandler                  │
└──────────────┬──────────────────────────┘
               │
               ↓
┌─────────────────────────────────────────┐
│         Service Layer                   │
│  - EventService                         │
│  - UserService                          │
│  - NotificationService                  │
│  - TelegramMessageService               │
│  - AttachmentService                    │
│  - CommentService                       │
│  - ChecklistService                     │
│  - RecurrenceService                    │
│  - EventHistoryService                  │
│  - ReminderService                      │
│  - TrashService                         │
│  - SearchService                        │
│  - StatisticsService                    │
└──────────────┬──────────────────────────┘
               │
               ↓
┌─────────────────────────────────────────┐
│      Repository Layer                   │
│  - EventRepository (JPA)                │
│  - UserRepository (JPA)                 │
│  - FamilyRepository (JPA)               │
│  - AttachmentRepository (JPA)           │
│  - CommentRepository (JPA)              │
│  - ChecklistItemRepository (JPA)        │
│  - RecurrenceRuleRepository (JPA)       │
│  - EventHistoryRepository (JPA)         │
│  - ReminderRepository (JPA)             │
└──────────────┬──────────────────────────┘
               │
               ↓
┌─────────────────────────────────────────┐
│         PostgreSQL Database             │
│  - users, events, families              │
│  - attachments, comments                │
│  - checklist_items, recurrence_rules    │
│  - event_history, reminders             │
└─────────────────────────────────────────┘

┌─────────────────────────────────────────┐
│      Scheduled Tasks                    │
│  - NotificationScheduler                │
│  - EventCompletionScheduler             │
│  - TrashCleanupScheduler                │
│  - WeeklySummaryScheduler               │
└─────────────────────────────────────────┘
```

### Принципы архитектуры

1. **Разделение ответственности**: Каждый слой имеет четко определенную роль
2. **Dependency Injection**: Использование Spring DI для управления зависимостями
3. **Webhook вместо Long Polling**: Реальное время обработки без постоянного опроса
4. **Персистентность**: Все данные хранятся в PostgreSQL
5. **Контейнеризация**: Docker для изоляции и простоты развертывания
6. **Миграции БД**: Flyway для версионирования схемы
7. **Scheduled Tasks**: Spring @Scheduled для уведомлений
8. **Тестируемость**: Все компоненты легко тестируются с Testcontainers

## Компоненты и интерфейсы

### 1. Webhook Controller

REST контроллер для приема обновлений от Telegram.

```java
@RestController
@RequestMapping("/webhook")
@Slf4j
public class TelegramWebhookController {
    private final UpdateProcessor updateProcessor;
    
    @PostMapping("/{botToken}")
    public ResponseEntity<Void> onUpdateReceived(
            @PathVariable String botToken,
            @RequestBody Update update) {
        
        log.info("Received update: {}", update.getUpdateId());
        
        // Валидация токена
        if (!isValidToken(botToken)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        
        // Асинхронная обработка
        updateProcessor.processUpdate(update);
        
        return ResponseEntity.ok().build();
    }
}
```

### 2. Command Handler Interface

```java
public interface CommandHandler {
    /**
     * Обрабатывает команду от пользователя
     * @param message Входящее сообщение
     * @param user Пользователь из БД
     * @return Текст ответа пользователю
     */
    String handle(Message message, User user);
    
    /**
     * Возвращает команду, которую обрабатывает этот handler
     */
    String getCommand();
    
    /**
     * Возвращает описание команды для /help
     */
    String getDescription();
    
    /**
     * Требуется ли авторизация для этой команды
     */
    default boolean requiresAuth() {
        return true;
    }
}
```

### 2.1. Update Processor

Обработчик обновлений от Telegram с поддержкой кнопок и callback queries.

```java
@Service
@Slf4j
public class UpdateProcessor {
    private final CommandDispatcher commandDispatcher;
    private final CallbackQueryHandler callbackQueryHandler;
    private final UserService userService;
    private final KeyboardService keyboardService;
    private final TelegramMessageService messageService;
    
    @Async
    public void processUpdate(Update update) {
        // Обработка callback query (inline кнопки)
        if (update.hasCallbackQuery()) {
            processCallbackQuery(update.getCallbackQuery());
            return;
        }
        
        // Обработка обычных сообщений
        if (!update.hasMessage()) {
            return;
        }
        
        Message message = update.getMessage();
        Long telegramId = message.getFrom().getId();
        
        // Преобразуем текст кнопки в команду, если это кнопка
        String text = message.getText();
        String command = keyboardService.buttonTextToCommand(text);
        
        // Создаем новое сообщение с командой
        Message processedMessage = message;
        if (!text.equals(command)) {
            processedMessage = new Message();
            processedMessage.setText(command);
            processedMessage.setFrom(message.getFrom());
            processedMessage.setChat(message.getChat());
        }
        
        // Проверяем авторизацию
        Optional<User> userOpt = userService.findByTelegramId(telegramId);
        
        // Обрабатываем команду
        String response = commandDispatcher.dispatch(processedMessage, userOpt.orElse(null));
        
        // Отправляем ответ с соответствующей клавиатурой
        ReplyKeyboardMarkup keyboard = userOpt.isPresent() 
            ? keyboardService.createAuthorizedUserKeyboard()
            : keyboardService.createUnauthorizedUserKeyboard();
            
        messageService.sendMessage(telegramId, response, keyboard);
    }
    
    private void processCallbackQuery(CallbackQuery callbackQuery) {
        Long telegramId = callbackQuery.getFrom().getId();
        String callbackData = callbackQuery.getData();
        
        log.info("Processing callback query: {} from user {}", callbackData, telegramId);
        
        // Обрабатываем callback
        callbackQueryHandler.handle(callbackQuery);
        
        // Отправляем подтверждение (убирает "часики" на кнопке)
        messageService.answerCallbackQuery(callbackQuery.getId(), "Обработано");
    }
}
```

### 2.2. Telegram Message Service

Сервис для отправки сообщений пользователям и обработки callback queries.

```java
@Service
@Slf4j
public class TelegramMessageService {
    private final BotConfig botConfig;
    
    /**
     * Отправляет сообщение пользователю с клавиатурой
     */
    public void sendMessage(Long chatId, String text, ReplyKeyboardMarkup keyboard) {
        SendMessage message = new SendMessage();
        message.setChatId(chatId.toString());
        message.setText(text);
        message.setParseMode("Markdown");
        message.setReplyMarkup(keyboard);
        
        try {
            // Отправка через Telegram Bot API
            execute(message);
            log.info("Message sent to chat {}", chatId);
        } catch (TelegramApiException e) {
            log.error("Failed to send message to chat {}", chatId, e);
            throw new RuntimeException("Failed to send message", e);
        }
    }
    
    /**
     * Отправляет сообщение пользователю с inline клавиатурой
     */
    public void sendMessageWithInlineKeyboard(Long chatId, String text, 
                                             InlineKeyboardMarkup keyboard) {
        SendMessage message = new SendMessage();
        message.setChatId(chatId.toString());
        message.setText(text);
        message.setParseMode("Markdown");
        message.setReplyMarkup(keyboard);
        
        try {
            execute(message);
            log.info("Message with inline keyboard sent to chat {}", chatId);
        } catch (TelegramApiException e) {
            log.error("Failed to send message to chat {}", chatId, e);
            throw new RuntimeException("Failed to send message", e);
        }
    }
    
    /**
     * Отправляет сообщение пользователю без клавиатуры
     */
    public void sendMessage(Long chatId, String text) {
        sendMessage(chatId, text, null);
    }
    
    /**
     * Отвечает на callback query (убирает "часики" на inline кнопке)
     */
    public void answerCallbackQuery(String callbackQueryId, String text) {
        AnswerCallbackQuery answer = new AnswerCallbackQuery();
        answer.setCallbackQueryId(callbackQueryId);
        answer.setText(text);
        answer.setShowAlert(false);
        
        try {
            execute(answer);
            log.debug("Answered callback query {}", callbackQueryId);
        } catch (TelegramApiException e) {
            log.error("Failed to answer callback query {}", callbackQueryId, e);
        }
    }
    
    /**
     * Редактирует существующее сообщение (для обновления inline кнопок)
     */
    public void editMessageText(Long chatId, Integer messageId, String newText,
                               InlineKeyboardMarkup keyboard) {
        EditMessageText editMessage = new EditMessageText();
        editMessage.setChatId(chatId.toString());
        editMessage.setMessageId(messageId);
        editMessage.setText(newText);
        editMessage.setParseMode("Markdown");
        if (keyboard != null) {
            editMessage.setReplyMarkup(keyboard);
        }
        
        try {
            execute(editMessage);
            log.info("Message {} edited in chat {}", messageId, chatId);
        } catch (TelegramApiException e) {
            log.error("Failed to edit message {} in chat {}", messageId, chatId, e);
        }
    }
}
```

### 2.3. Callback Query Handler

Обработчик callback queries от inline кнопок.

```java
@Service
@Slf4j
public class CallbackQueryHandler {
    private final EventService eventService;
    private final KeyboardService keyboardService;
    private final TelegramMessageService messageService;
    
    public void handle(CallbackQuery callbackQuery) {
        String data = callbackQuery.getData();
        Long chatId = callbackQuery.getMessage().getChatId();
        Integer messageId = callbackQuery.getMessage().getMessageId();
        Long userId = callbackQuery.getFrom().getId();
        
        log.info("Handling callback: {} from user {}", data, userId);
        
        if (data.startsWith("edit_")) {
            handleEdit(data, chatId, userId);
        } else if (data.startsWith("delete_")) {
            handleDeleteRequest(data, chatId, messageId);
        } else if (data.startsWith("confirm_delete_")) {
            handleDeleteConfirmation(data, chatId, messageId, userId);
        } else if (data.startsWith("cancel_delete_")) {
            handleDeleteCancellation(chatId, messageId);
        }
    }
    
    private void handleEdit(String data, Long chatId, Long userId) {
        Long eventId = extractEventId(data);
        // Логика редактирования события
        messageService.sendMessage(chatId, 
            "Редактирование события " + eventId + ". Отправьте новые данные.");
    }
    
    private void handleDeleteRequest(String data, Long chatId, Integer messageId) {
        Long eventId = extractEventId(data);
        
        // Показываем подтверждение
        InlineKeyboardMarkup keyboard = 
            keyboardService.createDeleteConfirmationKeyboard(eventId);
        
        messageService.editMessageText(chatId, messageId,
            "⚠️ Вы уверены, что хотите удалить это событие?", keyboard);
    }
    
    private void handleDeleteConfirmation(String data, Long chatId, 
                                         Integer messageId, Long userId) {
        Long eventId = extractEventId(data);
        
        try {
            eventService.deleteEvent(eventId, userId);
            messageService.editMessageText(chatId, messageId,
                "✅ Событие успешно удалено", null);
        } catch (Exception e) {
            log.error("Failed to delete event {}", eventId, e);
            messageService.editMessageText(chatId, messageId,
                "❌ Ошибка при удалении события", null);
        }
    }
    
    private void handleDeleteCancellation(Long chatId, Integer messageId) {
        messageService.editMessageText(chatId, messageId,
            "Удаление отменено", null);
    }
    
    private Long extractEventId(String callbackData) {
        String[] parts = callbackData.split("_");
        return Long.parseLong(parts[parts.length - 1]);
    }
}
```

### 3. Event Service

Сервис для управления событиями календаря.

```java
@Service
@Transactional
@Slf4j
public class EventService {
    private final EventRepository eventRepository;
    private final UserRepository userRepository;
    
    /**
     * Создает новое событие в календаре
     */
    public Event createEvent(Long userId, String title, String description,
                            LocalDateTime eventDateTime) {
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new UserNotFoundException(userId));
        
        Event event = Event.builder()
            .user(user)
            .family(user.getFamily())
            .title(title)
            .description(description)
            .eventDate(eventDateTime.toLocalDate())
            .eventTime(eventDateTime.toLocalTime())
            .notified(false)
            .build();
        
        return eventRepository.save(event);
    }
    
    /**
     * Получает предстоящие события семьи
     */
    public List<Event> getUpcomingEvents(Long familyId, int days) {
        LocalDate startDate = LocalDate.now();
        LocalDate endDate = startDate.plusDays(days);
        
        return eventRepository.findByFamilyIdAndEventDateBetween(
            familyId, startDate, endDate);
    }
    
    /**
     * Получает события пользователя
     */
    public List<Event> getUserEvents(Long userId) {
        return eventRepository.findByUserIdOrderByEventDateAsc(userId);
    }
    
    /**
     * Обновляет событие
     */
    public Event updateEvent(Long eventId, Long userId, 
                            String title, String description,
                            LocalDateTime eventDateTime) {
        Event event = eventRepository.findById(eventId)
            .orElseThrow(() -> new EventNotFoundException(eventId));
        
        // Проверка прав доступа
        if (!event.getUser().getId().equals(userId)) {
            throw new UnauthorizedAccessException(
                "User cannot edit this event");
        }
        
        event.setTitle(title);
        event.setDescription(description);
        event.setEventDate(eventDateTime.toLocalDate());
        event.setEventTime(eventDateTime.toLocalTime());
        
        return eventRepository.save(event);
    }
    
    /**
     * Удаляет событие
     */
    public void deleteEvent(Long eventId, Long userId) {
        Event event = eventRepository.findById(eventId)
            .orElseThrow(() -> new EventNotFoundException(eventId));
        
        if (!event.getUser().getId().equals(userId)) {
            throw new UnauthorizedAccessException(
                "User cannot delete this event");
        }
        
        eventRepository.delete(event);
    }
}
```

### 4. Keyboard Service

Сервис для создания клавиатур с кнопками команд и inline кнопок для интерактивных действий.

```java
@Service
@Slf4j
public class KeyboardService {
    
    /**
     * Создает клавиатуру для авторизованного пользователя
     */
    public ReplyKeyboardMarkup createAuthorizedUserKeyboard() {
        ReplyKeyboardMarkup keyboard = new ReplyKeyboardMarkup();
        keyboard.setResizeKeyboard(true);
        
        List<KeyboardRow> rows = new ArrayList<>();
        
        // Первая строка
        KeyboardRow row1 = new KeyboardRow();
        row1.add(new KeyboardButton("📅 Предстоящие события"));
        row1.add(new KeyboardButton("➕ Добавить событие"));
        rows.add(row1);
        
        // Вторая строка
        KeyboardRow row2 = new KeyboardRow();
        row2.add(new KeyboardButton("📋 Мои события"));
        row2.add(new KeyboardButton("❓ Помощь"));
        rows.add(row2);
        
        keyboard.setKeyboard(rows);
        return keyboard;
    }
    
    /**
     * Создает клавиатуру для неавторизованного пользователя
     */
    public ReplyKeyboardMarkup createUnauthorizedUserKeyboard() {
        ReplyKeyboardMarkup keyboard = new ReplyKeyboardMarkup();
        keyboard.setResizeKeyboard(true);
        
        List<KeyboardRow> rows = new ArrayList<>();
        
        KeyboardRow row = new KeyboardRow();
        row.add(new KeyboardButton("🚀 Начать"));
        row.add(new KeyboardButton("❓ Помощь"));
        rows.add(row);
        
        keyboard.setKeyboard(rows);
        return keyboard;
    }
    
    /**
     * Создает inline клавиатуру для управления событием
     */
    public InlineKeyboardMarkup createEventActionsKeyboard(Long eventId) {
        InlineKeyboardMarkup keyboard = new InlineKeyboardMarkup();
        
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();
        
        // Кнопки редактирования и удаления
        List<InlineKeyboardButton> row1 = new ArrayList<>();
        
        InlineKeyboardButton editBtn = new InlineKeyboardButton("✏️ Редактировать");
        editBtn.setCallbackData("edit_" + eventId);
        row1.add(editBtn);
        
        InlineKeyboardButton deleteBtn = new InlineKeyboardButton("🗑️ Удалить");
        deleteBtn.setCallbackData("delete_" + eventId);
        row1.add(deleteBtn);
        
        rows.add(row1);
        keyboard.setKeyboard(rows);
        
        return keyboard;
    }
    
    /**
     * Создает inline клавиатуру для подтверждения удаления
     */
    public InlineKeyboardMarkup createDeleteConfirmationKeyboard(Long eventId) {
        InlineKeyboardMarkup keyboard = new InlineKeyboardMarkup();
        
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();
        
        List<InlineKeyboardButton> row1 = new ArrayList<>();
        
        InlineKeyboardButton confirmBtn = new InlineKeyboardButton("✅ Да, удалить");
        confirmBtn.setCallbackData("confirm_delete_" + eventId);
        row1.add(confirmBtn);
        
        InlineKeyboardButton cancelBtn = new InlineKeyboardButton("❌ Отмена");
        cancelBtn.setCallbackData("cancel_delete_" + eventId);
        row1.add(cancelBtn);
        
        rows.add(row1);
        keyboard.setKeyboard(rows);
        
        return keyboard;
    }
    
    /**
     * Преобразует текст кнопки в команду
     */
    public String buttonTextToCommand(String buttonText) {
        return switch (buttonText) {
            case "🚀 Начать" -> "/start";
            case "📅 Предстоящие события" -> "/upcoming_events";
            case "➕ Добавить событие" -> "/add_event";
            case "📋 Мои события" -> "/my_events";
            case "❓ Помощь" -> "/help";
            default -> buttonText;
        };
    }
    
    /**
     * Создает inline-календарь для выбора даты события.
     * 
     * Календарь отображает указанный месяц с кнопками для каждого дня.
     * Даты в прошлом отображаются как пустые ячейки без текста.
     * Дни с существующими событиями выделяются визуальным индикатором с инициалом создателя.
     * Навигация в прошлое блокируется, если предыдущий месяц в прошлом.
     * 
     * @param year Год для отображения
     * @param month Месяц для отображения (1-12)
     * @param familyId ID семьи для проверки существующих событий
     * @return InlineKeyboardMarkup с календарем
     */
    public InlineKeyboardMarkup createCalendarKeyboard(int year, int month, Long familyId) {
        InlineKeyboardMarkup keyboard = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();
        
        YearMonth yearMonth = YearMonth.of(year, month);
        YearMonth currentYearMonth = YearMonth.now();
        LocalDate firstDay = yearMonth.atDay(1);
        int daysInMonth = yearMonth.lengthOfMonth();
        int firstDayOfWeek = firstDay.getDayOfWeek().getValue(); // 1=Monday, 7=Sunday
        
        // Получаем события семьи за этот месяц для визуального выделения
        LocalDate monthStart = yearMonth.atDay(1);
        LocalDate monthEnd = yearMonth.atEndOfMonth();
        List<Event> monthEvents = eventRepository
            .findByFamilyIdAndEventDateBetweenAndStatus(
                familyId, monthStart, monthEnd, Event.EventStatus.ACTIVE);
        
        // Группируем события по датам и берем первое событие (по времени) для каждой даты
        Map<LocalDate, Event> firstEventByDate = monthEvents.stream()
            .sorted(Comparator.comparing(Event::getEventTime))
            .collect(Collectors.toMap(
                Event::getEventDate,
                Function.identity(),
                (existing, replacement) -> existing, // Оставляем первое (самое раннее)
                LinkedHashMap::new
            ));
        
        log.debug("Найдено {} событий для календаря {}-{:02d}", 
            firstEventByDate.size(), year, month);
        
        // Заголовок с месяцем и годом
        List<InlineKeyboardButton> headerRow = new ArrayList<>();
        InlineKeyboardButton headerBtn = new InlineKeyboardButton(
            yearMonth.format(DateTimeFormatter.ofPattern("MMMM yyyy", new Locale("ru"))));
        headerBtn.setCallbackData("calendar_ignore");
        headerRow.add(headerBtn);
        rows.add(headerRow);
        
        // Дни недели
        List<InlineKeyboardButton> weekDaysRow = new ArrayList<>();
        String[] weekDays = {"Пн", "Вт", "Ср", "Чт", "Пт", "Сб", "Вс"};
        for (String day : weekDays) {
            InlineKeyboardButton dayBtn = new InlineKeyboardButton(day);
            dayBtn.setCallbackData("calendar_ignore");
            weekDaysRow.add(dayBtn);
        }
        rows.add(weekDaysRow);
        
        // Дни месяца
        List<InlineKeyboardButton> currentRow = new ArrayList<>();
        
        // Пустые ячейки до первого дня месяца
        for (int i = 1; i < firstDayOfWeek; i++) {
            InlineKeyboardButton emptyBtn = new InlineKeyboardButton(" ");
            emptyBtn.setCallbackData("calendar_ignore");
            currentRow.add(emptyBtn);
        }
        
        // Дни месяца
        LocalDate today = LocalDate.now();
        for (int day = 1; day <= daysInMonth; day++) {
            LocalDate date = LocalDate.of(year, month, day);
            InlineKeyboardButton dayBtn;
            
            // Даты в прошлом отображаются как пустые ячейки
            if (date.isBefore(today)) {
                dayBtn = new InlineKeyboardButton(" ");
                dayBtn.setCallbackData("calendar_ignore");
            } else {
                // Добавляем визуальный индикатор для дней с событиями
                String dayText = String.valueOf(day);
                
                // Если на этот день есть событие, добавляем эмодзи и инициал создателя
                if (firstEventByDate.containsKey(date)) {
                    Event event = firstEventByDate.get(date);
                    String creatorInitial = event.getUser().getFirstName()
                        .substring(0, 1).toUpperCase();
                    dayText = day + "📌" + creatorInitial;
                }
                
                dayBtn = new InlineKeyboardButton(dayText);
                dayBtn.setCallbackData(String.format("date_%d-%02d-%02d", year, month, day));
            }
            
            currentRow.add(dayBtn);
            
            // Переход на новую строку после воскресенья
            if ((firstDayOfWeek + day - 1) % 7 == 0) {
                rows.add(new ArrayList<>(currentRow));
                currentRow.clear();
            }
        }
        
        // Добавляем последнюю строку, если она не пустая
        if (!currentRow.isEmpty()) {
            // Заполняем пустыми ячейками до конца недели
            while (currentRow.size() < 7) {
                InlineKeyboardButton emptyBtn = new InlineKeyboardButton(" ");
                emptyBtn.setCallbackData("calendar_ignore");
                currentRow.add(emptyBtn);
            }
            rows.add(currentRow);
        }
        
        // Кнопки навигации
        List<InlineKeyboardButton> navigationRow = new ArrayList<>();
        
        // Кнопка "Предыдущий месяц" - блокируем если предыдущий месяц в прошлом
        YearMonth prevMonth = yearMonth.minusMonths(1);
        if (prevMonth.isBefore(currentYearMonth)) {
            // Добавляем пустую кнопку вместо навигации
            InlineKeyboardButton disabledBtn = new InlineKeyboardButton("   ");
            disabledBtn.setCallbackData("calendar_ignore");
            navigationRow.add(disabledBtn);
        } else {
            InlineKeyboardButton prevBtn = new InlineKeyboardButton("◀️ Пред");
            prevBtn.setCallbackData(String.format("calendar_%d-%02d", 
                prevMonth.getYear(), prevMonth.getMonthValue()));
            navigationRow.add(prevBtn);
        }
        
        InlineKeyboardButton cancelBtn = new InlineKeyboardButton("❌ Отмена");
        cancelBtn.setCallbackData("calendar_cancel");
        navigationRow.add(cancelBtn);
        
        InlineKeyboardButton nextBtn = new InlineKeyboardButton("След ▶️");
        YearMonth nextMonth = yearMonth.plusMonths(1);
        nextBtn.setCallbackData(String.format("calendar_%d-%02d", 
            nextMonth.getYear(), nextMonth.getMonthValue()));
        navigationRow.add(nextBtn);
        
        rows.add(navigationRow);
        
        keyboard.setKeyboard(rows);
        return keyboard;
    }
    
    /**
     * Создает inline-клавиатуру для выбора часа
     * @return InlineKeyboardMarkup с кнопками часов (0-23)
     */
    public InlineKeyboardMarkup createHourSelectionKeyboard() {
        InlineKeyboardMarkup keyboard = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();
        
        // Заголовок
        List<InlineKeyboardButton> headerRow = new ArrayList<>();
        InlineKeyboardButton headerBtn = new InlineKeyboardButton("Выберите час:");
        headerBtn.setCallbackData("time_ignore");
        headerRow.add(headerBtn);
        rows.add(headerRow);
        
        // Кнопки часов (по 4 в ряд)
        List<InlineKeyboardButton> currentRow = new ArrayList<>();
        for (int hour = 0; hour < 24; hour++) {
            InlineKeyboardButton hourBtn = new InlineKeyboardButton(
                String.format("%02d:00", hour));
            hourBtn.setCallbackData(String.format("hour_%02d", hour));
            currentRow.add(hourBtn);
            
            if ((hour + 1) % 4 == 0) {
                rows.add(new ArrayList<>(currentRow));
                currentRow.clear();
            }
        }
        
        // Кнопка отмены
        List<InlineKeyboardButton> cancelRow = new ArrayList<>();
        InlineKeyboardButton cancelBtn = new InlineKeyboardButton("❌ Отмена");
        cancelBtn.setCallbackData("time_cancel");
        cancelRow.add(cancelBtn);
        rows.add(cancelRow);
        
        keyboard.setKeyboard(rows);
        return keyboard;
    }
    
    /**
     * Создает inline-клавиатуру для выбора минут
     * @param selectedHour Выбранный час
     * @return InlineKeyboardMarkup с кнопками минут (0, 15, 30, 45)
     */
    public InlineKeyboardMarkup createMinuteSelectionKeyboard(int selectedHour) {
        InlineKeyboardMarkup keyboard = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();
        
        // Заголовок
        List<InlineKeyboardButton> headerRow = new ArrayList<>();
        InlineKeyboardButton headerBtn = new InlineKeyboardButton(
            String.format("Выберите минуты (час: %02d):", selectedHour));
        headerBtn.setCallbackData("time_ignore");
        headerRow.add(headerBtn);
        rows.add(headerRow);
        
        // Кнопки минут
        List<InlineKeyboardButton> minutesRow = new ArrayList<>();
        int[] minutes = {0, 15, 30, 45};
        for (int minute : minutes) {
            InlineKeyboardButton minuteBtn = new InlineKeyboardButton(
                String.format("%02d:%02d", selectedHour, minute));
            minuteBtn.setCallbackData(String.format("time_%02d:%02d", selectedHour, minute));
            minutesRow.add(minuteBtn);
        }
        rows.add(minutesRow);
        
        // Кнопки навигации
        List<InlineKeyboardButton> navigationRow = new ArrayList<>();
        
        InlineKeyboardButton backBtn = new InlineKeyboardButton("◀️ Назад");
        backBtn.setCallbackData("time_back");
        navigationRow.add(backBtn);
        
        InlineKeyboardButton cancelBtn = new InlineKeyboardButton("❌ Отмена");
        cancelBtn.setCallbackData("time_cancel");
        navigationRow.add(cancelBtn);
        
        rows.add(navigationRow);
        
        keyboard.setKeyboard(rows);
        return keyboard;
    }
}
```

### 5. Notification Service

Сервис для отправки уведомлений о предстоящих событиях.

```java
@Service
@Slf4j
public class NotificationService {
    private final EventRepository eventRepository;
    private final TelegramMessageService messageService;
    
    /**
     * Отправляет уведомления о событиях, которые начнутся через 1 час
     */
    @Scheduled(fixedDelay = 300000) // каждые 5 минут
    public void sendUpcomingEventNotifications() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime oneHourLater = now.plusHours(1);
        
        // Находим события, которые начнутся в ближайший час
        List<Event> upcomingEvents = eventRepository
            .findEventsForNotification(now, oneHourLater);
        
        for (Event event : upcomingEvents) {
            sendNotificationToFamily(event);
            markAsNotified(event);
        }
    }
    
    private void sendNotificationToFamily(Event event) {
        Family family = event.getFamily();
        String message = formatNotificationMessage(event);
        
        for (User user : family.getMembers()) {
            try {
                messageService.sendMessage(user.getTelegramId(), message);
            } catch (Exception e) {
                log.error("Failed to send notification to user {}", 
                         user.getId(), e);
            }
        }
    }
    
    private String formatNotificationMessage(Event event) {
        return String.format(
            "🔔 *Напоминание о событии*\n\n" +
            "📅 Дата: %s\n" +
            "🕐 Время: %s\n" +
            "📝 Описание: %s\n" +
            "👤 Создал: %s",
            event.getEventDate().format(DateTimeFormatter.ofPattern("dd.MM.yyyy")),
            event.getEventTime().format(DateTimeFormatter.ofPattern("HH:mm")),
            event.getDescription(),
            event.getUser().getFirstName()
        );
    }
}
```

### 6. Conversation Service

Сервис для управления состоянием многошагового диалога создания события через черновики в БД.

```java
@Service
@Transactional
@Slf4j
public class ConversationService {
    private final EventRepository eventRepository;
    private final UserRepository userRepository;
    
    /**
     * Начинает новый диалог создания события
     * Создает черновик события в БД
     */
    public Event startEventCreation(Long userId) {
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new UserNotFoundException(userId));
        
        // Удаляем предыдущие незавершенные черновики пользователя
        cancelPendingDrafts(userId);
        
        // Создаем новый черновик
        Event draft = Event.builder()
            .user(user)
            .family(user.getFamily())
            .status(Event.EventStatus.DRAFT)
            .notified(false)
            .build();
        
        Event savedDraft = eventRepository.save(draft);
        log.info("Created draft event {} for user {}", savedDraft.getId(), userId);
        
        return savedDraft;
    }
    
    /**
     * Обновляет дату в черновике события
     */
    public Event updateEventDate(Long userId, LocalDate date) {
        Event draft = getActiveDraft(userId);
        draft.setEventDate(date);
        
        Event updated = eventRepository.save(draft);
        log.info("Updated draft {} with date {}", draft.getId(), date);
        
        return updated;
    }
    
    /**
     * Обновляет время в черновике события
     */
    public Event updateEventTime(Long userId, LocalTime time) {
        Event draft = getActiveDraft(userId);
        draft.setEventTime(time);
        
        Event updated = eventRepository.save(draft);
        log.info("Updated draft {} with time {}", draft.getId(), time);
        
        return updated;
    }
    
    /**
     * Обновляет название в черновике события
     */
    public Event updateEventTitle(Long userId, String title) {
        Event draft = getActiveDraft(userId);
        draft.setTitle(title);
        
        Event updated = eventRepository.save(draft);
        log.info("Updated draft {} with title", draft.getId());
        
        return updated;
    }
    
    /**
     * Завершает создание события, обновляя описание и меняя статус на ACTIVE
     */
    public Event completeEventCreation(Long userId, String description) {
        Event draft = getActiveDraft(userId);
        draft.setDescription(description);
        draft.setStatus(Event.EventStatus.ACTIVE);
        
        Event completed = eventRepository.save(draft);
        log.info("Completed event creation: {}", completed.getId());
        
        return completed;
    }
    
    /**
     * Отменяет создание события, удаляя черновик
     */
    public void cancelEventCreation(Long userId) {
        cancelPendingDrafts(userId);
        log.info("Cancelled event creation for user {}", userId);
    }
    
    /**
     * Получает активный черновик пользователя
     */
    public Event getActiveDraft(Long userId) {
        return eventRepository.findByUserIdAndStatus(userId, Event.EventStatus.DRAFT)
            .orElseThrow(() -> new IllegalStateException(
                "No active draft found for user " + userId));
    }
    
    /**
     * Проверяет, есть ли у пользователя активный черновик
     */
    public boolean hasActiveDraft(Long userId) {
        return eventRepository.findByUserIdAndStatus(userId, Event.EventStatus.DRAFT)
            .isPresent();
    }
    
    /**
     * Получает текущий шаг диалога на основе заполненности полей черновика
     */
    public ConversationStep getCurrentStep(Event draft) {
        if (draft.getEventDate() == null) {
            return ConversationStep.WAITING_FOR_DATE;
        }
        if (draft.getEventTime() == null) {
            return ConversationStep.WAITING_FOR_TIME;
        }
        if (draft.getTitle() == null || draft.getTitle().isBlank()) {
            return ConversationStep.WAITING_FOR_TITLE;
        }
        return ConversationStep.WAITING_FOR_DESCRIPTION;
    }
    
    /**
     * Удаляет все незавершенные черновики пользователя
     */
    private void cancelPendingDrafts(Long userId) {
        List<Event> drafts = eventRepository.findAllByUserIdAndStatus(
            userId, Event.EventStatus.DRAFT);
        
        if (!drafts.isEmpty()) {
            eventRepository.deleteAll(drafts);
            log.info("Deleted {} pending drafts for user {}", drafts.size(), userId);
        }
    }
    
    /**
     * Шаги диалога создания события
     */
    public enum ConversationStep {
        WAITING_FOR_DATE,
        WAITING_FOR_TIME,
        WAITING_FOR_TITLE,
        WAITING_FOR_DESCRIPTION
    }
}
```

### 7. Repository Layer

Репозитории для доступа к данным с использованием Spring Data JPA.

#### EventRepository

```java
@Repository
public interface EventRepository extends JpaRepository<Event, Long> {
    
    /**
     * Находит события семьи в указанном диапазоне дат
     */
    List<Event> findByFamilyIdAndEventDateBetween(
        Long familyId, LocalDate startDate, LocalDate endDate);
    
    /**
     * Находит события семьи в указанном диапазоне дат с определенным статусом
     */
    List<Event> findByFamilyIdAndEventDateBetweenAndStatus(
        Long familyId, LocalDate startDate, LocalDate endDate, Event.EventStatus status);
    
    /**
     * Находит все события пользователя, отсортированные по дате
     */
    List<Event> findByUserIdOrderByEventDateAsc(Long userId);
    
    /**
     * Находит события для отправки уведомлений
     */
    @Query("SELECT e FROM Event e WHERE e.notified = false " +
           "AND e.status = 'ACTIVE' " +
           "AND FUNCTION('TIMESTAMP', e.eventDate, e.eventTime) " +
           "BETWEEN :start AND :end")
    List<Event> findEventsForNotification(
        @Param("start") LocalDateTime start,
        @Param("end") LocalDateTime end);
    
    /**
     * Находит черновик события пользователя по статусу
     */
    Optional<Event> findByUserIdAndStatus(Long userId, Event.EventStatus status);
    
    /**
     * Находит все черновики пользователя по статусу
     */
    List<Event> findAllByUserIdAndStatus(Long userId, Event.EventStatus status);
}
```

#### UserRepository

```java
@Repository
public interface UserRepository extends JpaRepository<User, Long> {
    
    /**
     * Находит пользователя по Telegram ID
     */
    Optional<User> findByTelegramId(Long telegramId);
}
```

#### FamilyRepository

```java
@Repository
public interface FamilyRepository extends JpaRepository<Family, Long> {
    // Стандартные CRUD операции от JpaRepository
}
```

## Модели данных

### Entity: User

```java
@Entity
@Table(name = "users")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(name = "telegram_id", unique = true, nullable = false)
    private Long telegramId;
    
    @Column(name = "username")
    private String username;
    
    @Column(name = "first_name", nullable = false)
    private String firstName;
    
    @Column(name = "last_name")
    private String lastName;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "family_id")
    private Family family;
    
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
    
    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
```

### Entity: Event

```java
@Entity
@Table(name = "events")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Event {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "family_id", nullable = false)
    private Family family;
    
    @Column(name = "title")
    private String title;
    
    @Column(name = "description", columnDefinition = "TEXT")
    private String description;
    
    @Column(name = "event_date")
    private LocalDate eventDate;
    
    @Column(name = "event_time")
    private LocalTime eventTime;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private EventStatus status = EventStatus.ACTIVE;
    
    @Column(name = "notified", nullable = false)
    private Boolean notified = false;
    
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
    
    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
    
    public enum EventStatus {
        DRAFT,   // Черновик события (в процессе создания)
        ACTIVE   // Активное событие
    }
}
```

### Entity: Family

```java
@Entity
@Table(name = "families")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Family {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(name = "name", nullable = false)
    private String name;
    
    @OneToMany(mappedBy = "family", cascade = CascadeType.ALL)
    private List<User> members = new ArrayList<>();
    
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
    
    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
```

## Схема базы данных

```sql
-- Миграция V1__Initial_schema.sql

CREATE TABLE families (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE users (
    id BIGSERIAL PRIMARY KEY,
    telegram_id BIGINT UNIQUE NOT NULL,
    username VARCHAR(255),
    first_name VARCHAR(255) NOT NULL,
    last_name VARCHAR(255),
    family_id BIGINT REFERENCES families(id),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_users_telegram_id ON users(telegram_id);
CREATE INDEX idx_users_family_id ON users(family_id);

CREATE TYPE event_status AS ENUM ('draft', 'active');

CREATE TABLE events (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    family_id BIGINT NOT NULL REFERENCES families(id) ON DELETE CASCADE,
    title VARCHAR(255),
    description TEXT,
    event_date DATE,
    event_time TIME,
    status event_status NOT NULL DEFAULT 'active',
    notified BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_events_family_date ON events(family_id, event_date);
CREATE INDEX idx_events_user_id ON events(user_id);
CREATE INDEX idx_events_notified ON events(notified, event_date, event_time);
CREATE INDEX idx_events_status ON events(status);
CREATE INDEX idx_events_user_status ON events(user_id, status);
```

## Свойства корректности

*Свойство - это характеристика или поведение, которое должно выполняться во всех допустимых выполнениях системы.*

### Свойство 1: Загрузка конфигурации
*Для любого* источника конфигурации (переменные окружения, application.properties), Система должна корректно загружать параметры бота, БД и webhook URL.
**Validates: Requirements 1.4, 2.1**

### Свойство 2: Валидация обязательных параметров
*Для любой* конфигурации с отсутствующими обязательными параметрами, Система должна выбрасывать исключение при старте.
**Validates: Requirements 2.2, 2.5**

### Свойство 3: Авторизация пользователей
*Для любого* Telegram ID, Система должна проверять наличие пользователя в БД перед выполнением команд, требующих авторизации.
**Validates: Requirements 3.1, 3.2**

### Свойство 4: Создание событий
*Для любого* валидного события (дата в будущем, непустое описание), Система должна сохранить его в БД с привязкой к пользователю и семье.
**Validates: Requirements 4.1, 4.2, 4.3**

### Свойство 5: Валидация даты события
*Для любой* даты события в прошлом, Система должна отклонить создание события с понятным сообщением об ошибке.
**Validates: Requirements 4.2**

### Свойство 6: Получение предстоящих событий
*Для любой* семьи, запрос предстоящих событий должен возвращать только события в указанном диапазоне дат, отсортированные по дате и времени.
**Validates: Requirements 5.1, 5.4**

### Свойство 7: Права доступа к событиям
*Для любого* события, только создатель события может редактировать или удалять его.
**Validates: Requirements 7.5**

### Свойство 8: Отправка уведомлений
*Для любого* события, которое начнется через 1 час, Система должна отправить уведомление всем членам семьи один раз.
**Validates: Requirements 6.1, 6.3**

### Свойство 9: Webhook регистрация
*При любом* запуске приложения, Система должна успешно зарегистрировать Webhook URL в Telegram API.
**Validates: Requirements 8.1, 8.5**

### Свойство 10: Обработка ошибок БД
*Для любой* ошибки базы данных, Система должна залогировать детали и отправить пользователю дружественное сообщение.
**Validates: Requirements 9.3**

### Свойство 11: Применение миграций
*При любом* запуске приложения, Система должна автоматически применить все pending миграции Flyway.
**Validates: Requirements 11.1**

### Свойство 12: Docker Compose запуск
*При выполнении* docker-compose up, Система должна запустить приложение и PostgreSQL, применить миграции и быть готовой к работе.
**Validates: Requirements 10.1, 10.2**

### Свойство 13: Отображение клавиатуры для авторизованных пользователей
*Для любого* авторизованного пользователя, Система должна отправлять сообщения с клавиатурой, содержащей кнопки всех доступных команд.
**Validates: Requirements 13.1, 13.3**

### Свойство 14: Отображение клавиатуры для неавторизованных пользователей
*Для любого* неавторизованного пользователя, Система должна отправлять сообщения с клавиатурой, содержащей только кнопки "Начать" и "Помощь".
**Validates: Requirements 13.1, 13.4**

### Свойство 15: Обработка нажатий кнопок
*Для любого* текста кнопки, Система должна преобразовать его в соответствующую команду и обработать идентично текстовой команде.
**Validates: Requirements 13.2**

### Свойство 16: Отображение inline кнопок для управления событиями
*Для любого* события пользователя, Система должна отображать inline кнопки для редактирования и удаления.
**Validates: Requirements 14.1**

### Свойство 17: Обработка callback queries
*Для любого* callback query от inline кнопки, Система должна обработать его и отправить answerCallbackQuery для визуальной обратной связи.
**Validates: Requirements 14.2, 14.4**

### Свойство 18: Подтверждение удаления события
*Для любого* запроса на удаление события через inline кнопку, Система должна показать кнопки подтверждения перед выполнением удаления.
**Validates: Requirements 14.3**

### Свойство 19: Создание черновика события
*Для любого* пользователя, начинающего создание события, Система должна создать черновик со статусом "draft" в базе данных.
**Validates: Requirements 15.1**

### Свойство 20: Обновление черновика при выборе даты
*Для любого* черновика события, при выборе даты через inline-календарь, Система должна обновить поле event_date в базе данных.
**Validates: Requirements 15.2, 4.1**

### Свойство 21: Обновление черновика при выборе времени
*Для любого* черновика события, при выборе времени через inline-кнопки, Система должна обновить поле event_time в базе данных.
**Validates: Requirements 15.3, 4.2**

### Свойство 22: Завершение создания события
*Для любого* черновика события с заполненными датой, временем и названием, при добавлении описания Система должна изменить статус на "active".
**Validates: Requirements 15.5, 4.5, 4.6**

### Свойство 23: Отмена создания события
*Для любого* незавершенного черновика, при отмене диалога Система должна удалить черновик из базы данных.
**Validates: Requirements 15.6**

### Свойство 24: Отображение inline-календаря
*Для любого* запроса на создание события, Система должна отобразить inline-календарь с текущим месяцем и кнопками навигации.
**Validates: Requirements 4.1, 4.7**

### Свойство 25: Блокировка дат в прошлом
*Для любой* даты в прошлом в inline-календаре, Система должна отключить возможность выбора этой даты.
**Validates: Requirements 4.8**

### Свойство 26: Отображение выбора времени
*Для любого* выбора времени, Система должна отобразить inline-кнопки для выбора часа, затем минут с интервалом 15 минут.
**Validates: Requirements 4.2**

### Свойство 27: Определение текущего шага диалога
*Для любого* черновика события, Система должна корректно определять текущий шаг диалога на основе заполненности полей (дата, время, название).
**Validates: Requirements 15.2, 15.3, 15.4**

### Свойство 28: Отображение пустых ячеек для прошлых дат
*Для любой* даты в прошлом в inline-календаре, Система должна отображать пустую ячейку без текста вместо точки или числа.
**Validates: Requirements 16.1**

### Свойство 29: Блокировка навигации в прошлое
*Для любого* месяца, если предыдущий месяц находится в прошлом относительно текущего месяца, Система должна блокировать или скрывать кнопку "Предыдущий месяц".
**Validates: Requirements 16.2**

### Свойство 30: Визуальное выделение дней с событиями
*Для любого* дня в inline-календаре, если на этот день есть хотя бы одно активное событие семьи пользователя, Система должна добавить визуальный индикатор к номеру дня в формате "день📌инициал" (например, "5📌А" для события Алексея).
**Validates: Requirements 16.3, 16.5**

### Свойство 31: Загрузка событий для календаря
*Для любого* отображаемого месяца в inline-календаре, Система должна запросить из базы данных все активные события семьи пользователя за этот месяц.
**Validates: Requirements 16.4**

### Свойство 32: Отображение инициала создателя события
*Для любого* дня с несколькими событиями от разных пользователей, Система должна отображать инициал создателя первого события по времени.
**Validates: Requirements 16.6**

## Обработка ошибок

### 1. Глобальный обработчик исключений

```java
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {
    
    @ExceptionHandler(UserNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleUserNotFound(
            UserNotFoundException ex) {
        log.error("User not found: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
            .body(new ErrorResponse("Пользователь не найден"));
    }
    
    @ExceptionHandler(UnauthorizedAccessException.class)
    public ResponseEntity<ErrorResponse> handleUnauthorizedAccess(
            UnauthorizedAccessException ex) {
        log.error("Unauthorized access: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
            .body(new ErrorResponse("Нет доступа к этому ресурсу"));
    }
    
    @ExceptionHandler(DataAccessException.class)
    public ResponseEntity<ErrorResponse> handleDatabaseError(
            DataAccessException ex) {
        log.error("Database error", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(new ErrorResponse(
                "Временная ошибка базы данных. Попробуйте позже."));
    }
}
```

## Стратегия тестирования

### Unit тесты

- Тестирование сервисов с моками репозиториев
- Тестирование обработчиков команд
- Тестирование валидации данных

### Integration тесты с Testcontainers

```java
@SpringBootTest
@Testcontainers
class EventServiceIntegrationTest {
    
    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(
        "postgres:18.1-alpine")
        .withDatabaseName("testdb")
        .withUsername("test")
        .withPassword("test");
    
    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }
    
    @Autowired
    private EventService eventService;
    
    @Test
    void shouldCreateAndRetrieveEvent() {
        // Test implementation
    }
}
```

## Зависимости (pom.xml)

```xml
<parent>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-parent</artifactId>
    <version>3.5.3</version>
    <relativePath/>
</parent>

<properties>
    <java.version>21</java.version>
    <maven.compiler.source>21</maven.compiler.source>
    <maven.compiler.target>21</maven.compiler.target>
    <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
    
    <!-- Версии библиотек (декабрь 2025, проверено через Context7) -->
    <telegram.version>8.2.0</telegram.version>
    <flyway.version>11.1.0</flyway.version>
    <testcontainers.version>1.21.2</testcontainers.version>
</properties>

<dependencies>
    <!-- Spring Boot -->
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-web</artifactId>
    </dependency>
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-data-jpa</artifactId>
    </dependency>
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-validation</artifactId>
    </dependency>
    
    <!-- PostgreSQL -->
    <dependency>
        <groupId>org.postgresql</groupId>
        <artifactId>postgresql</artifactId>
        <scope>runtime</scope>
    </dependency>
    
    <!-- Flyway -->
    <dependency>
        <groupId>org.flywaydb</groupId>
        <artifactId>flyway-core</artifactId>
        <version>${flyway.version}</version>
    </dependency>
    <dependency>
        <groupId>org.flywaydb</groupId>
        <artifactId>flyway-database-postgresql</artifactId>
        <version>${flyway.version}</version>
    </dependency>
    
    <!-- Telegram Bot -->
    <dependency>
        <groupId>org.telegram</groupId>
        <artifactId>telegrambots-spring-boot-starter</artifactId>
        <version>${telegram.version}</version>
    </dependency>
    
    <!-- Lombok -->
    <dependency>
        <groupId>org.projectlombok</groupId>
        <artifactId>lombok</artifactId>
        <optional>true</optional>
    </dependency>
    
    <!-- Testing -->
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-test</artifactId>
        <scope>test</scope>
    </dependency>
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-testcontainers</artifactId>
        <scope>test</scope>
    </dependency>
    <dependency>
        <groupId>org.testcontainers</groupId>
        <artifactId>postgresql</artifactId>
        <version>${testcontainers.version}</version>
        <scope>test</scope>
    </dependency>
    <dependency>
        <groupId>org.testcontainers</groupId>
        <artifactId>junit-jupiter</artifactId>
        <version>${testcontainers.version}</version>
        <scope>test</scope>
    </dependency>
</dependencies>

<build>
    <plugins>
        <plugin>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-maven-plugin</artifactId>
            <configuration>
                <excludes>
                    <exclude>
                        <groupId>org.projectlombok</groupId>
                        <artifactId>lombok</artifactId>
                    </exclude>
                </excludes>
            </configuration>
        </plugin>
    </plugins>
</build>
```

## Docker Configuration

### Dockerfile

```dockerfile
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY target/family-calendar-bot.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
```

### docker-compose.yml

```yaml
version: '3.8'

services:
  postgres:
    image: postgres:18.1-alpine
    environment:
      POSTGRES_DB: family_calendar
      POSTGRES_USER: botuser
      POSTGRES_PASSWORD: ${DB_PASSWORD}
    volumes:
      - postgres_data:/var/lib/postgresql/data
    ports:
      - "5432:5432"
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U botuser"]
      interval: 10s
      timeout: 5s
      retries: 5

  app:
    build: .
    depends_on:
      postgres:
        condition: service_healthy
    environment:
      SPRING_DATASOURCE_URL: jdbc:postgresql://postgres:5432/family_calendar
      SPRING_DATASOURCE_USERNAME: botuser
      SPRING_DATASOURCE_PASSWORD: ${DB_PASSWORD}
      TELEGRAM_BOT_TOKEN: ${BOT_TOKEN}
      TELEGRAM_BOT_WEBHOOK_URL: ${WEBHOOK_URL}
    ports:
      - "8080:8080"

volumes:
  postgres_data:
```

## Конфигурация (application.yml)

```yaml
spring:
  application:
    name: family-calendar-bot
  
  datasource:
    url: ${SPRING_DATASOURCE_URL:jdbc:postgresql://localhost:5432/family_calendar}
    username: ${SPRING_DATASOURCE_USERNAME:botuser}
    password: ${SPRING_DATASOURCE_PASSWORD}
    driver-class-name: org.postgresql.Driver
  
  jpa:
    hibernate:
      ddl-auto: validate
    show-sql: false
    properties:
      hibernate:
        format_sql: true
        dialect: org.hibernate.dialect.PostgreSQLDialect
  
  flyway:
    enabled: true
    locations: classpath:db/migration
    baseline-on-migrate: true

telegram:
  bot:
    token: ${TELEGRAM_BOT_TOKEN}
    username: ${TELEGRAM_BOT_USERNAME:FamilyCalendarBot}
    webhook-url: ${TELEGRAM_BOT_WEBHOOK_URL}

logging:
  level:
    root: INFO
    com.example.familycalendar: DEBUG
  pattern:
    console: "%d{yyyy-MM-dd HH:mm:ss} - %msg%n"
```

## Заключение

Данный дизайн обеспечивает:
- ✅ Webhook интеграцию для реального времени
- ✅ PostgreSQL для надежного хранения данных
- ✅ Docker для простого развертывания
- ✅ Flyway для версионирования БД
- ✅ Scheduled tasks для уведомлений
- ✅ Авторизацию по Telegram ID
- ✅ Полный функционал семейного календаря
- ✅ Comprehensive тестирование с Testcontainers
