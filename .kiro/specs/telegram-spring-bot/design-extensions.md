# Расширения дизайна - Новые функции

## Дополнительные модели данных

### Entity: Attachment (Вложение)

```java
@Entity
@Table(name = "attachments")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
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
    private String fileType; // document, photo, video
    
    @Column(name = "file_size")
    private Long fileSize;
    
    @Column(name = "uploaded_at", nullable = false)
    private LocalDateTime uploadedAt;
    
    @PrePersist
    protected void onCreate() {
        uploadedAt = LocalDateTime.now();
    }
}
```

### Entity: Comment (Комментарий)

```java
@Entity
@Table(name = "comments")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Comment {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "event_id", nullable = false)
    private Event event;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;
    
    @Column(name = "text", columnDefinition = "TEXT", nullable = false)
    private String text;
    
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
    
    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
```

### Entity: ChecklistItem (Пункт чек-листа)

```java
@Entity
@Table(name = "checklist_items")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChecklistItem {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "event_id", nullable = false)
    private Event event;
    
    @Column(name = "text", nullable = false)
    private String text;
    
    @Column(name = "completed", nullable = false)
    private Boolean completed = false;
    
    @Column(name = "position", nullable = false)
    private Integer position;
    
    @Column(name = "completed_at")
    private LocalDateTime completedAt;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "completed_by")
    private User completedBy;
}
```


### Entity: RecurrenceRule (Правило повторения)

```java
@Entity
@Table(name = "recurrence_rules")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RecurrenceRule {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(name = "series_id", nullable = false)
    private String seriesId; // UUID для связи событий серии
    
    @Enumerated(EnumType.STRING)
    @Column(name = "frequency", nullable = false)
    private Frequency frequency; // DAILY, WEEKLY, MONTHLY
    
    @Column(name = "interval", nullable = false)
    private Integer interval = 1; // каждые N дней/недель/месяцев
    
    @Column(name = "days_of_week")
    private String daysOfWeek; // "1,3,5" для Пн,Ср,Пт
    
    @Column(name = "end_date")
    private LocalDate endDate;
    
    @Column(name = "occurrences")
    private Integer occurrences; // количество повторений
    
    @Column(name = "exceptions")
    private String exceptions; // "2025-01-15,2025-02-20" - исключенные даты
    
    public enum Frequency {
        DAILY, WEEKLY, MONTHLY
    }
}
```

### Entity: EventHistory (История изменений)

```java
@Entity
@Table(name = "event_history")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EventHistory {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(name = "event_id", nullable = false)
    private Long eventId;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "action_type", nullable = false)
    private ActionType actionType;
    
    @Column(name = "field_name")
    private String fieldName;
    
    @Column(name = "old_value", columnDefinition = "TEXT")
    private String oldValue;
    
    @Column(name = "new_value", columnDefinition = "TEXT")
    private String newValue;
    
    @Column(name = "changed_at", nullable = false)
    private LocalDateTime changedAt;
    
    @PrePersist
    protected void onCreate() {
        changedAt = LocalDateTime.now();
    }
    
    public enum ActionType {
        CREATED, UPDATED, DELETED, RESTORED
    }
}
```


### Entity: Reminder (Напоминание)

```java
@Entity
@Table(name = "reminders")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Reminder {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "event_id", nullable = false)
    private Event event;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "reminder_type", nullable = false)
    private ReminderType reminderType;
    
    @Column(name = "custom_minutes")
    private Integer customMinutes; // для custom типа
    
    @Column(name = "reminder_time", nullable = false)
    private LocalDateTime reminderTime;
    
    @Column(name = "sent", nullable = false)
    private Boolean sent = false;
    
    @Column(name = "sent_at")
    private LocalDateTime sentAt;
    
    public enum ReminderType {
        MORNING_OF_DAY,      // утром в день события (9:00)
        EVENING_BEFORE,      // вечером накануне (20:00)
        ONE_HOUR_BEFORE,     // за 1 час
        TEN_MINUTES_BEFORE,  // за 10 минут
        CUSTOM               // свое время
    }
}
```

### Обновленная Entity: Event

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
    
    @Column(name = "end_time")
    private LocalTime endTime; // время окончания
    
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private EventStatus status = EventStatus.ACTIVE;
    
    @Column(name = "is_personal", nullable = false)
    private Boolean isPersonal = false;
    
    @Column(name = "series_id")
    private String seriesId; // UUID для повторяющихся событий
    
    @Column(name = "completion_note", columnDefinition = "TEXT")
    private String completionNote;
    
    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;
    
    @Column(name = "completed_at")
    private LocalDateTime completedAt;
    
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
    
    @OneToMany(mappedBy = "event", cascade = CascadeType.ALL)
    private List<Attachment> attachments = new ArrayList<>();
    
    @OneToMany(mappedBy = "event", cascade = CascadeType.ALL)
    private List<Comment> comments = new ArrayList<>();
    
    @OneToMany(mappedBy = "event", cascade = CascadeType.ALL)
    private List<ChecklistItem> checklistItems = new ArrayList<>();
    
    @OneToMany(mappedBy = "event", cascade = CascadeType.ALL)
    private List<Reminder> reminders = new ArrayList<>();
    
    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
    
    public enum EventStatus {
        DRAFT,      // Черновик события (в процессе создания)
        ACTIVE,     // Активное событие
        COMPLETED,  // Завершенное событие
        DELETED     // Удаленное событие (в корзине)
    }
}
```


## Новые сервисы

### AttachmentService

Сервис для управления вложениями событий.

```java
@Service
@Transactional
@Slf4j
public class AttachmentService {
    private final AttachmentRepository attachmentRepository;
    private final EventRepository eventRepository;
    
    /**
     * Сохраняет вложение к событию
     */
    public Attachment saveAttachment(Long eventId, String fileId, 
                                    String fileName, String fileType, Long fileSize) {
        Event event = eventRepository.findById(eventId)
            .orElseThrow(() -> new EventNotFoundException(eventId));
        
        // Проверка размера файла (макс 20 МБ)
        if (fileSize > 20 * 1024 * 1024) {
            throw new FileSizeExceededException("File size exceeds 20 MB limit");
        }
        
        Attachment attachment = Attachment.builder()
            .event(event)
            .fileId(fileId)
            .fileName(fileName)
            .fileType(fileType)
            .fileSize(fileSize)
            .build();
        
        return attachmentRepository.save(attachment);
    }
    
    /**
     * Получает все вложения события
     */
    public List<Attachment> getEventAttachments(Long eventId) {
        return attachmentRepository.findByEventIdOrderByUploadedAtAsc(eventId);
    }
    
    /**
     * Удаляет вложение
     */
    public void deleteAttachment(Long attachmentId, Long userId) {
        Attachment attachment = attachmentRepository.findById(attachmentId)
            .orElseThrow(() -> new AttachmentNotFoundException(attachmentId));
        
        // Проверка прав доступа
        if (!attachment.getEvent().getUser().getId().equals(userId)) {
            throw new UnauthorizedAccessException("User cannot delete this attachment");
        }
        
        attachmentRepository.delete(attachment);
    }
}
```

### CommentService

Сервис для управления комментариями к событиям.

```java
@Service
@Transactional
@Slf4j
public class CommentService {
    private final CommentRepository commentRepository;
    private final EventRepository eventRepository;
    private final UserRepository userRepository;
    private final TelegramMessageService messageService;
    
    /**
     * Добавляет комментарий к событию
     */
    public Comment addComment(Long eventId, Long userId, String text) {
        Event event = eventRepository.findById(eventId)
            .orElseThrow(() -> new EventNotFoundException(eventId));
        
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new UserNotFoundException(userId));
        
        Comment comment = Comment.builder()
            .event(event)
            .user(user)
            .text(text)
            .build();
        
        Comment saved = commentRepository.save(comment);
        
        // Отправка уведомлений членам семьи (если событие семейное)
        if (!event.getIsPersonal()) {
            notifyFamilyAboutComment(event, user, text);
        }
        
        return saved;
    }
    
    /**
     * Получает все комментарии события
     */
    public List<Comment> getEventComments(Long eventId) {
        return commentRepository.findByEventIdOrderByCreatedAtAsc(eventId);
    }
    
    private void notifyFamilyAboutComment(Event event, User author, String text) {
        Family family = event.getFamily();
        String message = String.format(
            "💬 Новый комментарий к событию \"%s\" от %s:\n%s",
            event.getTitle(), author.getFirstName(), text
        );
        
        for (User member : family.getMembers()) {
            if (!member.getId().equals(author.getId())) {
                messageService.sendMessage(member.getTelegramId(), message);
            }
        }
    }
}
```


### ChecklistService

Сервис для управления чек-листами событий.

```java
@Service
@Transactional
@Slf4j
public class ChecklistService {
    private final ChecklistItemRepository checklistItemRepository;
    private final EventRepository eventRepository;
    private final UserRepository userRepository;
    
    /**
     * Создает чек-лист для события
     */
    public List<ChecklistItem> createChecklist(Long eventId, List<String> items) {
        Event event = eventRepository.findById(eventId)
            .orElseThrow(() -> new EventNotFoundException(eventId));
        
        List<ChecklistItem> checklistItems = new ArrayList<>();
        for (int i = 0; i < items.size(); i++) {
            ChecklistItem item = ChecklistItem.builder()
                .event(event)
                .text(items.get(i))
                .position(i)
                .completed(false)
                .build();
            checklistItems.add(item);
        }
        
        return checklistItemRepository.saveAll(checklistItems);
    }
    
    /**
     * Отмечает пункт чек-листа выполненным
     */
    public ChecklistItem toggleItemCompletion(Long itemId, Long userId) {
        ChecklistItem item = checklistItemRepository.findById(itemId)
            .orElseThrow(() -> new ChecklistItemNotFoundException(itemId));
        
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new UserNotFoundException(userId));
        
        item.setCompleted(!item.getCompleted());
        if (item.getCompleted()) {
            item.setCompletedAt(LocalDateTime.now());
            item.setCompletedBy(user);
        } else {
            item.setCompletedAt(null);
            item.setCompletedBy(null);
        }
        
        return checklistItemRepository.save(item);
    }
    
    /**
     * Получает чек-лист события
     */
    public List<ChecklistItem> getEventChecklist(Long eventId) {
        return checklistItemRepository.findByEventIdOrderByPositionAsc(eventId);
    }
    
    /**
     * Проверяет, все ли пункты выполнены
     */
    public boolean isChecklistComplete(Long eventId) {
        List<ChecklistItem> items = getEventChecklist(eventId);
        return !items.isEmpty() && items.stream().allMatch(ChecklistItem::getCompleted);
    }
}
```

### RecurrenceService

Сервис для управления повторяющимися событиями.

```java
@Service
@Transactional
@Slf4j
public class RecurrenceService {
    private final RecurrenceRuleRepository recurrenceRuleRepository;
    private final EventRepository eventRepository;
    
    /**
     * Создает повторяющееся событие
     */
    public List<Event> createRecurringEvent(Event baseEvent, RecurrenceRule rule) {
        String seriesId = UUID.randomUUID().toString();
        rule.setSeriesId(seriesId);
        recurrenceRuleRepository.save(rule);
        
        List<Event> events = new ArrayList<>();
        LocalDate currentDate = baseEvent.getEventDate();
        int count = 0;
        
        while (shouldCreateOccurrence(currentDate, rule, count)) {
            if (!isExcludedDate(currentDate, rule)) {
                Event occurrence = createOccurrence(baseEvent, currentDate, seriesId);
                events.add(occurrence);
                count++;
            }
            currentDate = getNextOccurrenceDate(currentDate, rule);
        }
        
        return eventRepository.saveAll(events);
    }
    
    /**
     * Обновляет всю серию событий
     */
    public void updateSeries(String seriesId, Event updatedEvent) {
        List<Event> seriesEvents = eventRepository.findBySeriesIdAndStatus(
            seriesId, Event.EventStatus.ACTIVE);
        
        for (Event event : seriesEvents) {
            event.setTitle(updatedEvent.getTitle());
            event.setDescription(updatedEvent.getDescription());
            event.setEventTime(updatedEvent.getEventTime());
            event.setEndTime(updatedEvent.getEndTime());
        }
        
        eventRepository.saveAll(seriesEvents);
    }
    
    /**
     * Удаляет всю серию событий
     */
    public void deleteSeries(String seriesId, Long userId) {
        List<Event> seriesEvents = eventRepository.findBySeriesIdAndStatus(
            seriesId, Event.EventStatus.ACTIVE);
        
        for (Event event : seriesEvents) {
            if (!event.getUser().getId().equals(userId)) {
                throw new UnauthorizedAccessException("User cannot delete this series");
            }
            event.setStatus(Event.EventStatus.DELETED);
            event.setDeletedAt(LocalDateTime.now());
        }
        
        eventRepository.saveAll(seriesEvents);
    }
    
    private boolean shouldCreateOccurrence(LocalDate date, RecurrenceRule rule, int count) {
        if (rule.getEndDate() != null && date.isAfter(rule.getEndDate())) {
            return false;
        }
        if (rule.getOccurrences() != null && count >= rule.getOccurrences()) {
            return false;
        }
        return true;
    }
    
    private LocalDate getNextOccurrenceDate(LocalDate current, RecurrenceRule rule) {
        return switch (rule.getFrequency()) {
            case DAILY -> current.plusDays(rule.getInterval());
            case WEEKLY -> current.plusWeeks(rule.getInterval());
            case MONTHLY -> current.plusMonths(rule.getInterval());
        };
    }
    
    private boolean isExcludedDate(LocalDate date, RecurrenceRule rule) {
        if (rule.getExceptions() == null) return false;
        return Arrays.asList(rule.getExceptions().split(","))
            .contains(date.toString());
    }
    
    private Event createOccurrence(Event base, LocalDate date, String seriesId) {
        return Event.builder()
            .user(base.getUser())
            .family(base.getFamily())
            .title(base.getTitle())
            .description(base.getDescription())
            .eventDate(date)
            .eventTime(base.getEventTime())
            .endTime(base.getEndTime())
            .status(Event.EventStatus.ACTIVE)
            .isPersonal(base.getIsPersonal())
            .seriesId(seriesId)
            .build();
    }
}
```


### TrashService

Сервис для управления корзиной удаленных событий.

```java
@Service
@Transactional
@Slf4j
public class TrashService {
    private final EventRepository eventRepository;
    
    /**
     * Получает удаленные события пользователя
     */
    public List<Event> getUserTrash(Long userId) {
        return eventRepository.findByUserIdAndStatusOrderByDeletedAtDesc(
            userId, Event.EventStatus.DELETED);
    }
    
    /**
     * Восстанавливает событие из корзины
     */
    public Event restoreEvent(Long eventId, Long userId) {
        Event event = eventRepository.findById(eventId)
            .orElseThrow(() -> new EventNotFoundException(eventId));
        
        if (!event.getUser().getId().equals(userId)) {
            throw new UnauthorizedAccessException("User cannot restore this event");
        }
        
        if (event.getStatus() != Event.EventStatus.DELETED) {
            throw new IllegalStateException("Event is not in trash");
        }
        
        event.setStatus(Event.EventStatus.ACTIVE);
        event.setDeletedAt(null);
        
        return eventRepository.save(event);
    }
    
    /**
     * Удаляет событие окончательно
     */
    public void permanentlyDelete(Long eventId, Long userId) {
        Event event = eventRepository.findById(eventId)
            .orElseThrow(() -> new EventNotFoundException(eventId));
        
        if (!event.getUser().getId().equals(userId)) {
            throw new UnauthorizedAccessException("User cannot delete this event");
        }
        
        eventRepository.delete(event);
    }
    
    /**
     * Очищает старые события из корзины (старше 30 дней)
     */
    @Scheduled(cron = "0 0 2 * * *") // каждый день в 2:00
    public void cleanupOldTrash() {
        LocalDateTime thirtyDaysAgo = LocalDateTime.now().minusDays(30);
        List<Event> oldEvents = eventRepository
            .findByStatusAndDeletedAtBefore(Event.EventStatus.DELETED, thirtyDaysAgo);
        
        eventRepository.deleteAll(oldEvents);
        log.info("Cleaned up {} old events from trash", oldEvents.size());
    }
}
```

### EventHistoryService

Сервис для управления историей изменений событий.

```java
@Service
@Transactional
@Slf4j
public class EventHistoryService {
    private final EventHistoryRepository eventHistoryRepository;
    
    /**
     * Записывает изменение события
     */
    public void recordChange(Long eventId, User user, 
                            EventHistory.ActionType actionType,
                            String fieldName, String oldValue, String newValue) {
        EventHistory history = EventHistory.builder()
            .eventId(eventId)
            .user(user)
            .actionType(actionType)
            .fieldName(fieldName)
            .oldValue(oldValue)
            .newValue(newValue)
            .build();
        
        eventHistoryRepository.save(history);
    }
    
    /**
     * Получает историю изменений события
     */
    public List<EventHistory> getEventHistory(Long eventId) {
        return eventHistoryRepository.findByEventIdOrderByChangedAtDesc(eventId);
    }
}
```

### ReminderService

Сервис для управления напоминаниями.

```java
@Service
@Transactional
@Slf4j
public class ReminderService {
    private final ReminderRepository reminderRepository;
    private final TelegramMessageService messageService;
    
    /**
     * Создает напоминания для события
     */
    public List<Reminder> createReminders(Event event, List<Reminder.ReminderType> types) {
        List<Reminder> reminders = new ArrayList<>();
        
        for (Reminder.ReminderType type : types) {
            LocalDateTime reminderTime = calculateReminderTime(event, type, null);
            
            Reminder reminder = Reminder.builder()
                .event(event)
                .reminderType(type)
                .reminderTime(reminderTime)
                .sent(false)
                .build();
            
            reminders.add(reminder);
        }
        
        return reminderRepository.saveAll(reminders);
    }
    
    /**
     * Создает кастомное напоминание
     */
    public Reminder createCustomReminder(Event event, int minutesBefore) {
        LocalDateTime reminderTime = calculateReminderTime(
            event, Reminder.ReminderType.CUSTOM, minutesBefore);
        
        Reminder reminder = Reminder.builder()
            .event(event)
            .reminderType(Reminder.ReminderType.CUSTOM)
            .customMinutes(minutesBefore)
            .reminderTime(reminderTime)
            .sent(false)
            .build();
        
        return reminderRepository.save(reminder);
    }
    
    /**
     * Отправляет напоминания
     */
    @Scheduled(fixedDelay = 60000) // каждую минуту
    public void sendReminders() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime oneMinuteLater = now.plusMinutes(1);
        
        List<Reminder> dueReminders = reminderRepository
            .findBySentFalseAndReminderTimeBetween(now, oneMinuteLater);
        
        for (Reminder reminder : dueReminders) {
            sendReminder(reminder);
        }
    }
    
    private void sendReminder(Reminder reminder) {
        Event event = reminder.getEvent();
        String message = formatReminderMessage(event, reminder);
        
        // Отправка создателю или всей семье в зависимости от типа события
        if (event.getIsPersonal()) {
            messageService.sendMessage(event.getUser().getTelegramId(), message);
        } else {
            for (User member : event.getFamily().getMembers()) {
                messageService.sendMessage(member.getTelegramId(), message);
            }
        }
        
        reminder.setSent(true);
        reminder.setSentAt(LocalDateTime.now());
        reminderRepository.save(reminder);
    }
    
    private LocalDateTime calculateReminderTime(Event event, 
                                               Reminder.ReminderType type, 
                                               Integer customMinutes) {
        LocalDateTime eventDateTime = LocalDateTime.of(event.getEventDate(), event.getEventTime());
        
        return switch (type) {
            case MORNING_OF_DAY -> LocalDateTime.of(event.getEventDate(), LocalTime.of(9, 0));
            case EVENING_BEFORE -> LocalDateTime.of(event.getEventDate().minusDays(1), LocalTime.of(20, 0));
            case ONE_HOUR_BEFORE -> eventDateTime.minusHours(1);
            case TEN_MINUTES_BEFORE -> eventDateTime.minusMinutes(10);
            case CUSTOM -> eventDateTime.minusMinutes(customMinutes);
        };
    }
    
    private String formatReminderMessage(Event event, Reminder reminder) {
        String timeInfo = switch (reminder.getReminderType()) {
            case MORNING_OF_DAY -> "Сегодня";
            case EVENING_BEFORE -> "Завтра";
            case ONE_HOUR_BEFORE -> "Через 1 час";
            case TEN_MINUTES_BEFORE -> "Через 10 минут";
            case CUSTOM -> String.format("Через %d минут", reminder.getCustomMinutes());
        };
        
        return String.format(
            "🔔 *Напоминание*\n\n" +
            "%s: %s\n" +
            "📅 %s в %s\n" +
            "📝 %s",
            timeInfo,
            event.getTitle(),
            event.getEventDate().format(DateTimeFormatter.ofPattern("dd.MM.yyyy")),
            event.getEventTime().format(DateTimeFormatter.ofPattern("HH:mm")),
            event.getDescription() != null ? event.getDescription() : ""
        );
    }
}
```


### SearchService

Сервис для поиска и фильтрации событий.

```java
@Service
@Transactional(readOnly = true)
@Slf4j
public class SearchService {
    private final EventRepository eventRepository;
    
    /**
     * Поиск событий по тексту
     */
    public List<Event> searchEvents(Long familyId, Long userId, String query) {
        return eventRepository.searchByTitleOrDescription(familyId, userId, query);
    }
    
    /**
     * Фильтрация событий
     */
    public List<Event> filterEvents(Long familyId, Long userId, EventFilter filter) {
        return switch (filter) {
            case FAMILY -> eventRepository.findByFamilyIdAndIsPersonalFalseAndStatus(
                familyId, Event.EventStatus.ACTIVE);
            case PERSONAL -> eventRepository.findByUserIdAndIsPersonalTrueAndStatus(
                userId, Event.EventStatus.ACTIVE);
            case COMPLETED -> eventRepository.findByFamilyIdAndStatus(
                familyId, Event.EventStatus.COMPLETED);
            case UPCOMING -> eventRepository.findUpcomingEvents(familyId, userId, LocalDate.now());
        };
    }
    
    public enum EventFilter {
        FAMILY, PERSONAL, COMPLETED, UPCOMING
    }
}
```

### StatisticsService

Сервис для статистики событий.

```java
@Service
@Transactional(readOnly = true)
@Slf4j
public class StatisticsService {
    private final EventRepository eventRepository;
    
    /**
     * Получает статистику за месяц
     */
    public EventStatistics getMonthlyStatistics(Long familyId, Long userId, 
                                               YearMonth yearMonth) {
        LocalDate startDate = yearMonth.atDay(1);
        LocalDate endDate = yearMonth.atEndOfMonth();
        
        int totalEvents = eventRepository.countByFamilyIdAndEventDateBetween(
            familyId, startDate, endDate);
        
        int completedEvents = eventRepository.countByFamilyIdAndEventDateBetweenAndStatus(
            familyId, startDate, endDate, Event.EventStatus.COMPLETED);
        
        int upcomingEvents = eventRepository.countByFamilyIdAndEventDateBetweenAndStatus(
            familyId, startDate, endDate, Event.EventStatus.ACTIVE);
        
        int personalEvents = eventRepository.countByUserIdAndEventDateBetweenAndIsPersonal(
            userId, startDate, endDate, true);
        
        int familyEvents = totalEvents - personalEvents;
        
        return EventStatistics.builder()
            .totalEvents(totalEvents)
            .completedEvents(completedEvents)
            .upcomingEvents(upcomingEvents)
            .personalEvents(personalEvents)
            .familyEvents(familyEvents)
            .build();
    }
    
    @Data
    @Builder
    public static class EventStatistics {
        private int totalEvents;
        private int completedEvents;
        private int upcomingEvents;
        private int personalEvents;
        private int familyEvents;
    }
}
```

### EventCompletionScheduler

Планировщик для автоматического завершения событий.

```java
@Component
@Slf4j
public class EventCompletionScheduler {
    private final EventRepository eventRepository;
    private final TelegramMessageService messageService;
    
    /**
     * Проверяет и завершает события
     */
    @Scheduled(fixedDelay = 600000) // каждые 10 минут
    public void completeExpiredEvents() {
        LocalDateTime now = LocalDateTime.now();
        
        List<Event> expiredEvents = eventRepository.findExpiredActiveEvents(now);
        
        for (Event event : expiredEvents) {
            event.setStatus(Event.EventStatus.COMPLETED);
            event.setCompletedAt(now);
            eventRepository.save(event);
            
            // Отправка предложения добавить заметку
            sendCompletionNotification(event);
        }
        
        log.info("Completed {} expired events", expiredEvents.size());
    }
    
    private void sendCompletionNotification(Event event) {
        String message = String.format(
            "✅ Событие \"%s\" завершено.\n\n" +
            "Хотите добавить заметку о том, как прошло событие?",
            event.getTitle()
        );
        
        InlineKeyboardMarkup keyboard = createCompletionKeyboard(event.getId());
        messageService.sendMessageWithInlineKeyboard(
            event.getUser().getTelegramId(), message, keyboard);
    }
    
    private InlineKeyboardMarkup createCompletionKeyboard(Long eventId) {
        InlineKeyboardMarkup keyboard = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();
        
        List<InlineKeyboardButton> row = new ArrayList<>();
        InlineKeyboardButton addNoteBtn = new InlineKeyboardButton("📝 Добавить заметку");
        addNoteBtn.setCallbackData("add_completion_note_" + eventId);
        row.add(addNoteBtn);
        
        rows.add(row);
        keyboard.setKeyboard(rows);
        return keyboard;
    }
}
```


## Обновленная схема базы данных

```sql
-- Обновление таблицы events
ALTER TABLE events 
ADD COLUMN end_time TIME,
ADD COLUMN is_personal BOOLEAN NOT NULL DEFAULT FALSE,
ADD COLUMN series_id VARCHAR(255),
ADD COLUMN completion_note TEXT,
ADD COLUMN deleted_at TIMESTAMP,
ADD COLUMN completed_at TIMESTAMP;

-- Обновление ENUM event_status
ALTER TYPE event_status ADD VALUE 'completed';
ALTER TYPE event_status ADD VALUE 'deleted';

-- Создание индексов для events
CREATE INDEX idx_events_series_id ON events(series_id);
CREATE INDEX idx_events_is_personal ON events(is_personal);
CREATE INDEX idx_events_deleted_at ON events(deleted_at);

-- Таблица вложений
CREATE TABLE attachments (
    id BIGSERIAL PRIMARY KEY,
    event_id BIGINT NOT NULL REFERENCES events(id) ON DELETE CASCADE,
    file_id VARCHAR(255) NOT NULL,
    file_name VARCHAR(255),
    file_type VARCHAR(50),
    file_size BIGINT,
    uploaded_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_attachments_event_id ON attachments(event_id);

-- Таблица комментариев
CREATE TABLE comments (
    id BIGSERIAL PRIMARY KEY,
    event_id BIGINT NOT NULL REFERENCES events(id) ON DELETE CASCADE,
    user_id BIGINT NOT NULL REFERENCES users(id),
    text TEXT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_comments_event_id ON comments(event_id);
CREATE INDEX idx_comments_created_at ON comments(created_at);

-- Таблица чек-листов
CREATE TABLE checklist_items (
    id BIGSERIAL PRIMARY KEY,
    event_id BIGINT NOT NULL REFERENCES events(id) ON DELETE CASCADE,
    text VARCHAR(500) NOT NULL,
    completed BOOLEAN NOT NULL DEFAULT FALSE,
    position INTEGER NOT NULL,
    completed_at TIMESTAMP,
    completed_by BIGINT REFERENCES users(id)
);

CREATE INDEX idx_checklist_event_id ON checklist_items(event_id);
CREATE INDEX idx_checklist_position ON checklist_items(event_id, position);

-- Таблица правил повторения
CREATE TYPE frequency_type AS ENUM ('daily', 'weekly', 'monthly');

CREATE TABLE recurrence_rules (
    id BIGSERIAL PRIMARY KEY,
    series_id VARCHAR(255) NOT NULL UNIQUE,
    frequency frequency_type NOT NULL,
    interval INTEGER NOT NULL DEFAULT 1,
    days_of_week VARCHAR(50),
    end_date DATE,
    occurrences INTEGER,
    exceptions TEXT
);

CREATE INDEX idx_recurrence_series_id ON recurrence_rules(series_id);

-- Таблица истории изменений
CREATE TYPE action_type AS ENUM ('created', 'updated', 'deleted', 'restored');

CREATE TABLE event_history (
    id BIGSERIAL PRIMARY KEY,
    event_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL REFERENCES users(id),
    action_type action_type NOT NULL,
    field_name VARCHAR(100),
    old_value TEXT,
    new_value TEXT,
    changed_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_event_history_event_id ON event_history(event_id);
CREATE INDEX idx_event_history_changed_at ON event_history(changed_at);

-- Таблица напоминаний
CREATE TYPE reminder_type AS ENUM (
    'morning_of_day', 
    'evening_before', 
    'one_hour_before', 
    'ten_minutes_before', 
    'custom'
);

CREATE TABLE reminders (
    id BIGSERIAL PRIMARY KEY,
    event_id BIGINT NOT NULL REFERENCES events(id) ON DELETE CASCADE,
    reminder_type reminder_type NOT NULL,
    custom_minutes INTEGER,
    reminder_time TIMESTAMP NOT NULL,
    sent BOOLEAN NOT NULL DEFAULT FALSE,
    sent_at TIMESTAMP
);

CREATE INDEX idx_reminders_event_id ON reminders(event_id);
CREATE INDEX idx_reminders_time_sent ON reminders(reminder_time, sent);
```

## Новые свойства корректности

### Свойство 33: Управление вложениями
*Для любого* файла размером менее 20 МБ, Система должна успешно сохранить его как вложение к событию с сохранением file_id, имени и типа файла.
**Validates: Requirements 20.2, 20.3, 20.6**

### Свойство 34: Комментарии к событиям
*Для любого* комментария к семейному событию, Система должна отправить уведомление всем членам семьи, кроме автора комментария.
**Validates: Requirements 21.3, 21.5**

### Свойство 35: Чек-листы
*Для любого* чек-листа, когда все пункты отмечены выполненными, Система должна определить чек-лист как завершенный.
**Validates: Requirements 22.5, 22.6**

### Свойство 36: Повторяющиеся события
*Для любого* повторяющегося события, изменение одного события из серии не должно влиять на другие события серии, если пользователь выбрал "Изменить только это".
**Validates: Requirements 27.7**

### Свойство 37: Корзина событий
*Для любого* удаленного события, Система должна сохранить его в корзине со статусом "deleted" и возможностью восстановления в течение 30 дней.
**Validates: Requirements 19.1, 19.2, 19.6**

### Свойство 38: Персональные события
*Для любого* персонального события, Система должна показывать его только создателю и не отправлять уведомления другим членам семьи.
**Validates: Requirements 26.3, 26.4, 26.6**

### Свойство 39: Гибкие напоминания
*Для любого* настроенного напоминания, Система должна отправить уведомление в точное время согласно типу напоминания.
**Validates: Requirements 23.4**

### Свойство 40: История изменений
*Для любого* изменения события, Система должна создать запись в истории с указанием автора, времени и деталей изменения.
**Validates: Requirements 29.1, 29.2**

### Свойство 41: Автоматическое завершение
*Для любого* события, время окончания которого прошло, Система должна автоматически изменить статус на "completed".
**Validates: Requirements 25.1**

### Свойство 42: Поиск событий
*Для любого* поискового запроса, Система должна найти все события, содержащие текст запроса в названии или описании.
**Validates: Requirements 28.3, 28.4**

### Свойство 43: Временной интервал события
*Для любого* события с указанным временем окончания, время окончания должно быть позже времени начала.
**Validates: Requirements 32.3**

### Свойство 44: Быстрое создание из текста
*Для любого* текстового сообщения в формате "Событие: [название] Дата: [дата] Время: [время]", Система должна распознать параметры и предложить создать событие.
**Validates: Requirements 30.1, 30.2**

### Свойство 45: Статистика событий
*Для любого* месяца, Система должна корректно подсчитывать количество событий по категориям (всего, завершенные, предстоящие, персональные, семейные).
**Validates: Requirements 31.3, 31.4**

