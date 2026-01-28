# Документ проектирования: Валидация дат с учетом таймзоны пользователя

## Overview

Система должна корректно валидировать даты и время событий с учетом таймзоны каждого пользователя. В настоящее время система использует серверное время (UTC) для всех операций, что приводит к некорректной валидации для пользователей в других часовых поясах.

Решение включает:
1. Добавление поля timezone в модель User
2. Автоматическое определение таймзоны при регистрации
3. Использование таймзоны пользователя для всех операций с датами и временем
4. Миграцию существующих данных

## Architecture

### Компоненты системы

```
┌─────────────────────────────────────────────────────────────┐
│                     Telegram Bot API                         │
└──────────────────────┬──────────────────────────────────────┘
                       │
                       ▼
┌─────────────────────────────────────────────────────────────┐
│                   UpdateProcessor                            │
│  - Получает timezone из Telegram Update                     │
└──────────────────────┬──────────────────────────────────────┘
                       │
                       ▼
┌─────────────────────────────────────────────────────────────┐
│                    UserService                               │
│  - Сохраняет timezone при регистрации                       │
│  - Предоставляет timezone для других сервисов               │
└──────────────────────┬──────────────────────────────────────┘
                       │
                       ▼
┌─────────────────────────────────────────────────────────────┐
│                  KeyboardService                             │
│  - Использует User timezone для валидации дат в календаре   │
│  - Отображает только валидные даты                          │
└──────────────────────┬──────────────────────────────────────┘
                       │
                       ▼
┌─────────────────────────────────────────────────────────────┐
│              DateTimeCallbackHandler                         │
│  - Валидирует выбранные даты с учетом timezone              │
│  - Проверяет время для сегодняшнего дня                     │
└──────────────────────┬──────────────────────────────────────┘
                       │
                       ▼
┌─────────────────────────────────────────────────────────────┐
│                   EventService                               │
│  - Форматирует даты/время в timezone пользователя           │
│  - Группирует события по датам в timezone пользователя      │
└─────────────────────────────────────────────────────────────┘
```

### Поток данных

1. **Регистрация пользователя**:
   - Telegram Update содержит информацию о пользователе
   - UpdateProcessor извлекает timezone (если доступна)
   - UserService сохраняет timezone в БД (или использует default)

2. **Отображение календаря**:
   - KeyboardService получает User с timezone
   - Вычисляет текущую дату в timezone пользователя
   - Отображает только валидные даты (сегодня и будущее)

3. **Выбор даты**:
   - DateTimeCallbackHandler получает выбранную дату
   - Валидирует дату относительно User timezone
   - Для сегодняшнего дня дополнительно валидирует время

4. **Отображение событий**:
   - EventService форматирует даты в timezone пользователя
   - Группирует события по датам в timezone пользователя

## Components and Interfaces

### 1. Database Schema Changes

#### Migration V19: Add timezone column

```sql
-- Добавление колонки timezone в таблицу users
ALTER TABLE users ADD COLUMN timezone VARCHAR(50) DEFAULT 'Europe/Moscow';

-- Комментарий для новой колонки
COMMENT ON COLUMN users.timezone IS 'Часовой пояс пользователя (IANA timezone ID, например Europe/Moscow)';

-- Индекс не требуется, так как timezone не используется для поиска
```

### 2. Model Changes

#### User.java

Добавить поле timezone:

```java
/**
 * Часовой пояс пользователя в формате IANA (например, "Europe/Moscow").
 * Используется для корректной валидации и отображения дат и времени.
 * По умолчанию установлен в "Europe/Moscow" (UTC+3).
 */
@Column(name = "timezone", length = 50)
@Builder.Default
private String timezone = "Europe/Moscow";

/**
 * Получает ZoneId пользователя для работы с датами и временем.
 * 
 * @return ZoneId пользователя
 */
public ZoneId getZoneId() {
    return ZoneId.of(timezone);
}

/**
 * Получает текущую дату в таймзоне пользователя.
 * 
 * @return текущая дата в таймзоне пользователя
 */
public LocalDate getCurrentDate() {
    return LocalDate.now(getZoneId());
}

/**
 * Получает текущее время в таймзоне пользователя.
 * 
 * @return текущее время в таймзоне пользователя
 */
public LocalDateTime getCurrentDateTime() {
    return LocalDateTime.now(getZoneId());
}
```

### 3. Service Changes

#### UserService.java

Обновить метод createUser для сохранения timezone:

```java
/**
 * Создает нового пользователя в системе с указанной таймзоной.
 *
 * @param telegramId уникальный идентификатор пользователя в Telegram
 * @param username username пользователя в Telegram (может быть null)
 * @param firstName имя пользователя (обязательное поле)
 * @param family семья, к которой принадлежит пользователь (может быть null)
 * @param timezone часовой пояс пользователя в формате IANA (может быть null, тогда используется default)
 *
 * @return созданный и сохраненный пользователь
 */
@Transactional
public User createUser(Long telegramId, String username, String firstName, 
                      Family family, String timezone) {
    log.info("Создание нового пользователя: telegramId={}, username={}, firstName={}, " +
            "familyId={}, timezone={}", 
            telegramId, username, firstName, 
            family != null ? family.getId() : null, timezone);
    
    // Валидация timezone
    String validatedTimezone = validateAndNormalizeTimezone(timezone);
    
    User user = User.builder()
            .telegramId(telegramId)
            .username(username)
            .firstName(firstName)
            .family(family)
            .timezone(validatedTimezone)
            .build();
    
    User savedUser = userRepository.save(user);
    
    log.info("Пользователь успешно создан: userId={}, telegramId={}, timezone={}", 
            savedUser.getId(), savedUser.getTelegramId(), savedUser.getTimezone());
    
    return savedUser;
}

/**
 * Валидирует и нормализует timezone.
 * Если timezone невалидна или null, возвращает default timezone.
 * 
 * @param timezone timezone для валидации
 * @return валидная timezone
 */
private String validateAndNormalizeTimezone(String timezone) {
    if (timezone == null || timezone.isBlank()) {
        log.debug("Timezone не указана, используется default: Europe/Moscow");
        return "Europe/Moscow";
    }
    
    try {
        ZoneId.of(timezone);
        log.debug("Timezone валидна: {}", timezone);
        return timezone;
    } catch (DateTimeException e) {
        log.warn("Невалидная timezone: {}, используется default: Europe/Moscow", timezone);
        return "Europe/Moscow";
    }
}

/**
 * Обновляет timezone пользователя.
 * 
 * @param userId ID пользователя
 * @param timezone новая timezone
 * @throws UserNotFoundException если пользователь не найден
 */
@Transactional
public void updateTimezone(Long userId, String timezone) {
    log.info("Обновление timezone: userId={}, timezone={}", userId, timezone);
    
    User user = userRepository.findById(userId)
            .orElseThrow(() -> new UserNotFoundException("Пользователь не найден: " + userId));
    
    String validatedTimezone = validateAndNormalizeTimezone(timezone);
    user.setTimezone(validatedTimezone);
    userRepository.save(user);
    
    log.info("Timezone успешно обновлена: userId={}, timezone={}", userId, validatedTimezone);
}
```

#### KeyboardService.java

Обновить метод createCalendarKeyboard для использования User timezone:

```java
/**
 * Создает inline-календарь для выбора даты события с учетом таймзоны пользователя.
 * 
 * @param year год для отображения
 * @param month месяц для отображения (1-12)
 * @param user пользователь для определения timezone и семьи
 * @return настроенная InlineKeyboardMarkup с календарем
 */
public InlineKeyboardMarkup createCalendarKeyboard(int year, int month, User user) {
    if (month < 1 || month > 12) {
        throw new IllegalArgumentException("Month must be between 1 and 12");
    }
    
    Long familyId = user.getFamily() != null ? user.getFamily().getId() : null;
    ZoneId userZone = user.getZoneId();
    
    log.debug("Создание inline-календаря для {}-{:02d}, userId={}, timezone={}, familyId={}", 
            year, month, user.getId(), user.getTimezone(), familyId);
    
    // ... существующий код ...
    
    // ИЗМЕНЕНИЕ: Используем текущую дату в timezone пользователя
    LocalDate today = user.getCurrentDate();
    
    for (int day = 1; day <= daysInMonth; day++) {
        LocalDate date = LocalDate.of(year, month, day);
        InlineKeyboardButton dayBtn;
        
        // ИЗМЕНЕНИЕ: Даты в прошлом относительно timezone пользователя
        if (date.isBefore(today)) {
            dayBtn = new InlineKeyboardButton(" ");
            dayBtn.setCallbackData("calendar_ignore");
        } else {
            // ... существующий код для отображения дат ...
        }
    }
    
    // ... остальной код ...
}
```

#### DateTimeCallbackHandler.java

Добавить валидацию времени для сегодняшнего дня:

```java
/**
 * Обрабатывает выбор времени (час и минуты) с валидацией относительно timezone пользователя.
 */
private void handleTimeSelection(String callbackData, Long userId, Long chatId, 
                                 Integer messageId, String callbackQueryId) {
    String timeStr = callbackData.substring(5); // Убираем "time_"
    LocalTime time = LocalTime.parse(timeStr);
    
    // Получаем пользователя для проверки timezone
    User user = userService.findById(userId)
            .orElseThrow(() -> new UserNotFoundException("Пользователь не найден: " + userId));
    
    // Проверяем, редактируется ли существующее событие
    if (conversationStateService.isEditingEvent(userId)) {
        // ... существующий код для редактирования ...
    } else {
        // Создание нового события (черновик)
        
        // Получаем дату из черновика
        Event draft = conversationService.getDraft(userId);
        LocalDate eventDate = draft.getEventDate();
        
        // НОВАЯ ВАЛИДАЦИЯ: Проверяем, что время не в прошлом для сегодняшнего дня
        LocalDate today = user.getCurrentDate();
        if (eventDate.equals(today)) {
            LocalTime currentTime = user.getCurrentDateTime().toLocalTime();
            if (time.isBefore(currentTime)) {
                String errorMessage = messageBuilder.buildPastTimeErrorMessage(time, currentTime);
                messageService.answerCallbackQuery(callbackQueryId, 
                        "Нельзя выбрать время в прошлом");
                
                // Показываем сообщение об ошибке
                messageService.editMessageText(chatId, messageId, errorMessage, 
                        keyboardService.createHourSelectionKeyboard());
                
                log.warn("Попытка выбрать время в прошлом: userId={}, eventDate={}, " +
                        "selectedTime={}, currentTime={}", 
                        userId, eventDate, time, currentTime);
                return;
            }
        }
        
        conversationService.updateEventTime(userId, time);
        
        // ... остальной существующий код ...
    }
}
```

#### EventService.java

Обновить методы форматирования для использования User timezone:

```java
/**
 * Группирует события по датам в timezone пользователя.
 * 
 * @param events список событий
 * @param user пользователь для определения timezone
 * @return Map с датами в timezone пользователя и списками событий
 */
public Map<LocalDate, List<Event>> groupEventsByDate(List<Event> events, User user) {
    ZoneId userZone = user.getZoneId();
    
    return events.stream()
            .collect(Collectors.groupingBy(
                    Event::getEventDate,
                    TreeMap::new,
                    Collectors.toList()
            ));
}

/**
 * Определяет, является ли дата события "сегодня" относительно timezone пользователя.
 * 
 * @param eventDate дата события
 * @param user пользователь для определения timezone
 * @return true если событие сегодня
 */
public boolean isToday(LocalDate eventDate, User user) {
    return eventDate.equals(user.getCurrentDate());
}

/**
 * Определяет, является ли дата события "завтра" относительно timezone пользователя.
 * 
 * @param eventDate дата события
 * @param user пользователь для определения timezone
 * @return true если событие завтра
 */
public boolean isTomorrow(LocalDate eventDate, User user) {
    return eventDate.equals(user.getCurrentDate().plusDays(1));
}
```

### 4. Handler Changes

#### StartCommandHandler.java

Обновить для извлечения timezone из Telegram Update:

```java
@Override
public void handle(Message message, User user) {
    Long telegramId = message.getFrom().getId();
    Long chatId = message.getChatId();
    
    // Попытка извлечь timezone из Telegram (может быть недоступна)
    String timezone = extractTimezoneFromMessage(message);
    
    if (user == null) {
        // Регистрация нового пользователя
        String username = message.getFrom().getUserName();
        String firstName = message.getFrom().getFirstName();
        
        // Создаем семью и пользователя с timezone
        Family family = familyService.createFamily(firstName);
        user = userService.createUser(telegramId, username, firstName, family, timezone);
        
        log.info("Новый пользователь зарегистрирован: userId={}, timezone={}", 
                user.getId(), user.getTimezone());
    }
    
    // ... остальной код ...
}

/**
 * Пытается извлечь timezone из Telegram Message.
 * Telegram API может предоставлять timezone через language_code.
 * 
 * @param message Telegram сообщение
 * @return timezone или null если недоступна
 */
private String extractTimezoneFromMessage(Message message) {
    // Telegram API не предоставляет прямой доступ к timezone
    // Можно использовать language_code как подсказку, но это ненадежно
    // Поэтому возвращаем null и используем default timezone
    
    String languageCode = message.getFrom().getLanguageCode();
    log.debug("Language code from Telegram: {}", languageCode);
    
    // Можно добавить маппинг language_code -> timezone, но это неточно
    // Например: "ru" -> "Europe/Moscow", "en" -> "Europe/London"
    // Но пользователь с "ru" может быть в любой timezone
    
    return null; // Используем default timezone
}
```

## Data Models

### User Entity

```java
@Entity
@Table(name = "users")
public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(name = "telegram_id", nullable = false, unique = true)
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
    
    @Enumerated(EnumType.STRING)
    @Column(name = "event_filter", length = 20)
    @Builder.Default
    private EventFilter eventFilter = EventFilter.ALL;
    
    // НОВОЕ ПОЛЕ
    @Column(name = "timezone", length = 50)
    @Builder.Default
    private String timezone = "Europe/Moscow";
    
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
    
    // Методы для работы с timezone
    public ZoneId getZoneId() {
        return ZoneId.of(timezone);
    }
    
    public LocalDate getCurrentDate() {
        return LocalDate.now(getZoneId());
    }
    
    public LocalDateTime getCurrentDateTime() {
        return LocalDateTime.now(getZoneId());
    }
}
```

## Correctness Properties

*Свойство корректности (correctness property) — это характеристика или поведение, которое должно выполняться во всех валидных сценариях работы системы. По сути, это формальное утверждение о том, что система должна делать. Свойства служат мостом между человекочитаемыми спецификациями и машинно-проверяемыми гарантиями корректности.*


### Property 1: Timezone persistence
*For any* User created with a valid timezone, retrieving that User from the database should return the same timezone value.
**Validates: Requirements 1.1**

### Property 2: Default timezone fallback
*For any* User created without a timezone or with an invalid timezone, the User should have "Europe/Moscow" as their timezone.
**Validates: Requirements 1.3**

### Property 3: Timezone update
*For any* existing User and any valid timezone string, updating the User's timezone should result in the User having the new timezone when retrieved from the database.
**Validates: Requirements 1.4**

### Property 4: Calendar date validation
*For any* User with any timezone, when displaying a calendar, all dates before the User's current date should be displayed as empty cells, and all dates from today onwards should be displayed as selectable buttons.
**Validates: Requirements 2.1, 2.2, 2.3**

### Property 5: Current date indicator
*For any* User with any timezone, the calendar should mark the User's current date with the 📍 indicator.
**Validates: Requirements 2.5**

### Property 6: Time validation for today
*For any* User selecting today's date, attempting to select a time in the past relative to the User's current time should be rejected, while any future time should be accepted.
**Validates: Requirements 3.1, 3.2**

### Property 7: Event date formatting
*For any* Event and any User, when formatting the Event's date and time, the formatted string should reflect the date and time in the User's timezone.
**Validates: Requirements 4.1**

### Property 8: Relative date labels
*For any* Event, when the Event's date equals the User's current date, it should be labeled as "Сегодня", and when it equals the User's current date plus one day, it should be labeled as "Завтра".
**Validates: Requirements 4.2**

### Property 9: Event grouping by date
*For any* list of Events and any User, grouping the Events by date should use the Event dates as they appear in the User's timezone, not server time.
**Validates: Requirements 4.3**

## Error Handling

### Timezone Validation Errors

**Invalid Timezone String**:
- When: User provides an invalid IANA timezone ID
- Action: Log warning and use default timezone "Europe/Moscow"
- User Impact: User sees default timezone, no error message
- Example: "Invalid/Timezone" → "Europe/Moscow"

**Null Timezone**:
- When: Timezone is not provided during user creation
- Action: Use default timezone "Europe/Moscow"
- User Impact: Transparent, user gets default timezone
- Example: null → "Europe/Moscow"

### Date Validation Errors

**Past Date Selection**:
- When: User attempts to select a date in the past
- Action: Display date as empty cell, prevent selection
- User Impact: Cannot select past dates
- Callback: "calendar_ignore" (no action)

**Past Time Selection**:
- When: User selects past time for today's date
- Action: Show error message, return to hour selection
- User Impact: Sees error "Нельзя выбрать время в прошлом"
- Recovery: User can select different time

### Database Errors

**Migration Failure**:
- When: V19 migration fails to add timezone column
- Action: Rollback migration, log error
- User Impact: System continues with old schema
- Recovery: Fix migration and retry

**Timezone Update Failure**:
- When: Database error during timezone update
- Action: Rollback transaction, throw exception
- User Impact: Timezone not updated, error message shown
- Recovery: User can retry update

## Testing Strategy

### Unit Tests

Unit tests will verify specific examples and edge cases:

1. **Timezone Validation**:
   - Valid IANA timezone IDs are accepted
   - Invalid timezone IDs fall back to default
   - Null timezone falls back to default
   - Empty string timezone falls back to default

2. **Date Calculations**:
   - Current date calculation in different timezones
   - Past date detection in different timezones
   - Future date detection in different timezones
   - Today/tomorrow label calculation

3. **Time Validation**:
   - Past time rejection for today
   - Future time acceptance for today
   - Any time acceptance for future dates

4. **Migration**:
   - V19 migration adds timezone column
   - Default value is set correctly
   - Migration is idempotent

### Property-Based Tests

Property-based tests will verify universal properties across all inputs. Each test should run a minimum of 100 iterations.

1. **Property 1: Timezone persistence** (100+ iterations)
   - Generate: Random valid IANA timezone IDs
   - Test: Create user, save, retrieve, verify timezone matches
   - Tag: **Feature: user-timezone-validation, Property 1: Timezone persistence**

2. **Property 2: Default timezone fallback** (100+ iterations)
   - Generate: Random invalid timezone strings, nulls, empty strings
   - Test: Create user, verify timezone is "Europe/Moscow"
   - Tag: **Feature: user-timezone-validation, Property 2: Default timezone fallback**

3. **Property 3: Timezone update** (100+ iterations)
   - Generate: Random users, random valid timezones
   - Test: Update timezone, retrieve, verify new timezone
   - Tag: **Feature: user-timezone-validation, Property 3: Timezone update**

4. **Property 4: Calendar date validation** (100+ iterations)
   - Generate: Random timezones, random year/month combinations
   - Test: Create calendar, verify past dates are empty, future dates are buttons
   - Tag: **Feature: user-timezone-validation, Property 4: Calendar date validation**

5. **Property 5: Current date indicator** (100+ iterations)
   - Generate: Random timezones, random year/month combinations
   - Test: Create calendar, verify current date has 📍 indicator
   - Tag: **Feature: user-timezone-validation, Property 5: Current date indicator**

6. **Property 6: Time validation for today** (100+ iterations)
   - Generate: Random timezones, random times
   - Test: Validate time for today, verify past times rejected, future accepted
   - Tag: **Feature: user-timezone-validation, Property 6: Time validation for today**

7. **Property 7: Event date formatting** (100+ iterations)
   - Generate: Random events, random user timezones
   - Test: Format event date, verify it reflects user timezone
   - Tag: **Feature: user-timezone-validation, Property 7: Event date formatting**

8. **Property 8: Relative date labels** (100+ iterations)
   - Generate: Random events with dates today/tomorrow/other
   - Test: Verify correct labels ("Сегодня", "Завтра", or date)
   - Tag: **Feature: user-timezone-validation, Property 8: Relative date labels**

9. **Property 9: Event grouping by date** (100+ iterations)
   - Generate: Random events, random user timezones
   - Test: Group events, verify grouping uses user timezone
   - Tag: **Feature: user-timezone-validation, Property 9: Event grouping by date**

### Integration Tests

Integration tests will verify component interactions:

1. **End-to-End User Registration**:
   - Register user with timezone
   - Verify timezone saved in database
   - Verify calendar shows correct dates

2. **End-to-End Event Creation**:
   - Create user with specific timezone
   - Create event for today
   - Verify time validation works correctly
   - Verify event displays in correct timezone

3. **Migration Testing**:
   - Run V19 migration on test database
   - Verify timezone column exists
   - Verify default values set
   - Run migration again, verify idempotency

### Test Configuration

- **Property-based testing library**: jqwik (already used in project)
- **Minimum iterations per property test**: 100
- **Test database**: H2 in-memory for unit tests, PostgreSQL testcontainer for integration tests
- **Timezone test data**: Use ZoneId.getAvailableZoneIds() for valid timezones
- **Date/time test data**: Generate dates within reasonable range (2020-2030)

### Test Data Generators

For property-based tests, we need custom generators:

```java
/**
 * Генератор валидных IANA timezone IDs
 */
@Provide
Arbitrary<String> validTimezones() {
    List<String> timezones = new ArrayList<>(ZoneId.getAvailableZoneIds());
    return Arbitraries.of(timezones);
}

/**
 * Генератор невалидных timezone строк
 */
@Provide
Arbitrary<String> invalidTimezones() {
    return Arbitraries.strings()
            .alpha()
            .ofMinLength(1)
            .ofMaxLength(50)
            .filter(s -> {
                try {
                    ZoneId.of(s);
                    return false; // Валидная timezone, пропускаем
                } catch (DateTimeException e) {
                    return true; // Невалидная timezone, используем
                }
            });
}

/**
 * Генератор пользователей с случайными timezone
 */
@Provide
Arbitrary<User> usersWithTimezone() {
    return Combinators.combine(
            Arbitraries.longs().greaterOrEqual(1L),
            Arbitraries.strings().alpha().ofMinLength(1).ofMaxLength(50),
            validTimezones()
    ).as((telegramId, firstName, timezone) -> 
            User.builder()
                    .telegramId(telegramId)
                    .firstName(firstName)
                    .timezone(timezone)
                    .build()
    );
}

/**
 * Генератор дат в разумном диапазоне
 */
@Provide
Arbitrary<LocalDate> reasonableDates() {
    return Arbitraries.dates()
            .between(LocalDate.of(2020, 1, 1), LocalDate.of(2030, 12, 31));
}

/**
 * Генератор времени
 */
@Provide
Arbitrary<LocalTime> times() {
    return Arbitraries.times();
}
```

## Implementation Notes

### Telegram API Limitations

Telegram Bot API не предоставляет прямой доступ к timezone пользователя. Доступные данные:
- `language_code`: Код языка (например, "ru", "en")
- Геолокация: Только если пользователь явно отправляет

Поэтому:
1. Мы не можем автоматически определить точную timezone
2. Используем разумный default: "Europe/Moscow" (UTC+3)
3. В будущем можно добавить команду для ручной настройки timezone

### Performance Considerations

1. **Timezone Conversion**: Операции с timezone быстрые, не требуют оптимизации
2. **Database Index**: Индекс на timezone не нужен (не используется для поиска)
3. **Caching**: User entity уже кэшируется Spring Data JPA

### Migration Strategy

1. **V19 Migration**: Добавляет колонку с default значением
2. **Backward Compatibility**: Старый код продолжит работать (default timezone)
3. **Rollback**: Можно откатить миграцию без потери данных

### Future Enhancements

1. **Timezone Settings Command**: Добавить команду `/settings` для изменения timezone
2. **Timezone Detection**: Использовать геолокацию для определения timezone
3. **Timezone Display**: Показывать текущую timezone в профиле пользователя
4. **Multiple Timezones**: Поддержка отображения событий в разных timezone для семей в разных часовых поясах
