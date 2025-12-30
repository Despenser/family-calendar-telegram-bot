# Документ проектирования - Семейный Календарь Бот

## Обзор

Проект представляет собой Spring Boot 3.4.x приложение для создания Telegram бота семейного календаря. Бот позволяет членам семьи создавать, просматривать и управлять событиями, получать уведомления о предстоящих мероприятиях. Система использует Webhook для получения обновлений от Telegram, PostgreSQL для хранения данных и Docker для развертывания.

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
└──────────────┬──────────────────────────┘
               │
               ↓
┌─────────────────────────────────────────┐
│         Service Layer                   │
│  - EventService                         │
│  - UserService                          │
│  - NotificationService                  │
│  - TelegramMessageService               │
└──────────────┬──────────────────────────┘
               │
               ↓
┌─────────────────────────────────────────┐
│      Repository Layer                   │
│  - EventRepository (JPA)                │
│  - UserRepository (JPA)                 │
│  - FamilyRepository (JPA)               │
└──────────────┬──────────────────────────┘
               │
               ↓
┌─────────────────────────────────────────┐
│         PostgreSQL Database             │
│  - users, events, families tables       │
└─────────────────────────────────────────┘

┌─────────────────────────────────────────┐
│      Scheduled Tasks                    │
│  - NotificationScheduler                │
│  (проверка каждые 5 минут)             │
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

### 4. Notification Service

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
    
    @Column(name = "title", nullable = false)
    private String title;
    
    @Column(name = "description", columnDefinition = "TEXT")
    private String description;
    
    @Column(name = "event_date", nullable = false)
    private LocalDate eventDate;
    
    @Column(name = "event_time", nullable = false)
    private LocalTime eventTime;
    
    @Column(name = "notified", nullable = false)
    private Boolean notified = false;
    
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
    
    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
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

CREATE TABLE events (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    family_id BIGINT NOT NULL REFERENCES families(id) ON DELETE CASCADE,
    title VARCHAR(255) NOT NULL,
    description TEXT,
    event_date DATE NOT NULL,
    event_time TIME NOT NULL,
    notified BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_events_family_date ON events(family_id, event_date);
CREATE INDEX idx_events_user_id ON events(user_id);
CREATE INDEX idx_events_notified ON events(notified, event_date, event_time);
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
