package ru.golubyatnikov.family.calendar.bot.service;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;
import ru.golubyatnikov.family.calendar.bot.exception.EventNotFoundException;
import ru.golubyatnikov.family.calendar.bot.exception.InvalidDateException;
import ru.golubyatnikov.family.calendar.bot.exception.UnauthorizedAccessException;
import ru.golubyatnikov.family.calendar.bot.exception.UserNotFoundException;
import ru.golubyatnikov.family.calendar.bot.handler.MyEventsCommandHandler;
import ru.golubyatnikov.family.calendar.bot.model.Event;
import ru.golubyatnikov.family.calendar.bot.model.EventFilter;
import ru.golubyatnikov.family.calendar.bot.model.EventHistory;
import ru.golubyatnikov.family.calendar.bot.model.User;
import ru.golubyatnikov.family.calendar.bot.repository.EventRepository;
import ru.golubyatnikov.family.calendar.bot.repository.UserRepository;
import ru.golubyatnikov.family.calendar.bot.util.BotMessageBuilder;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

import static ru.golubyatnikov.family.calendar.bot.util.MarkdownFormatter.bold;
import static ru.golubyatnikov.family.calendar.bot.util.MarkdownFormatter.escape;

/**
 * Сервис для управления событиями в семейном календаре.
 * 
 * <p>Этот сервис предоставляет бизнес-логику для работы с событиями, включая:</p>
 * <ul>
 *   <li>Создание новых событий с валидацией даты</li>
 *   <li>Получение предстоящих событий семьи</li>
 *   <li>Получение событий конкретного пользователя</li>
 *   <li>Обновление существующих событий с проверкой прав доступа</li>
 *   <li>Удаление событий с проверкой прав доступа</li>
 * </ul>
 * 
 * <p>Все операции изменения данных выполняются в транзакциях для обеспечения
 * целостности данных. Сервис выполняет валидацию входных данных через Bean Validation
 * и проверку прав доступа перед выполнением операций.</p>
 * 
 * <p><b>Требования:</b> 4.1, 4.2, 4.3, 5.1, 5.4, 7.1, 7.2, 7.3, 7.4, 7.5, 8.1, 8.4, 8.5</p>
 * 
 * @see Event
 * @see EventRepository
 * @see UserRepository
 * @author Family Calendar Bot Team
 * @version 1.0.0
 * @since 2025-12-30
 */
@Service
@Transactional
@Validated
@Slf4j
public class EventService {
    
    private final EventRepository eventRepository;
    private final UserRepository userRepository;
    private final EventHistoryService eventHistoryService;
    private final ReminderService reminderService;
    private final TelegramMessageService telegramMessageService;
    private final KeyboardService keyboardService;
    private final BotMessageBuilder botMessageBuilder;
    private final MyEventsCommandHandler myEventsCommandHandler;
    
    /**
     * Конструктор с инъекцией зависимостей.
     * MyEventsCommandHandler инжектируется с @Lazy для разрыва циклической зависимости.
     */
    public EventService(
            EventRepository eventRepository,
            UserRepository userRepository,
            EventHistoryService eventHistoryService,
            ReminderService reminderService,
            TelegramMessageService telegramMessageService,
            KeyboardService keyboardService,
            BotMessageBuilder botMessageBuilder,
            @Lazy MyEventsCommandHandler myEventsCommandHandler) {
        this.eventRepository = eventRepository;
        this.userRepository = userRepository;
        this.eventHistoryService = eventHistoryService;
        this.reminderService = reminderService;
        this.telegramMessageService = telegramMessageService;
        this.keyboardService = keyboardService;
        this.botMessageBuilder = botMessageBuilder;
        this.myEventsCommandHandler = myEventsCommandHandler;
    }
    
    /**
     * Создает новое событие в календаре.
     * 
     * <p>Метод выполняет следующие действия:</p>
     * <ol>
     *   <li>Проверяет существование пользователя в базе данных</li>
     *   <li>Валидирует дату и время события (не должно быть в прошлом)</li>
     *   <li>Создает событие с привязкой к пользователю и его семье</li>
     *   <li>Сохраняет событие в базе данных</li>
     *   <li>Записывает действие в историю изменений</li>
     * </ol>
     * 
     * <p><b>Требования:</b> 4.1, 4.2, 4.3, 26.2, 32.1, 18.5</p>
     * 
     * @param userId идентификатор пользователя, создающего событие
     * @param title название события (обязательное, не более 255 символов)
     * @param description описание события (может быть null, не более 2000 символов)
     * @param eventDateTime дата и время события
     * @return созданное и сохраненное событие
     * @throws UserNotFoundException если пользователь с указанным ID не найден
     * @throws InvalidDateException если дата события находится в прошлом
     * @throws jakarta.validation.ConstraintViolationException если параметры не прошли валидацию
     */
    public Event createEvent(
            @NotNull(message = "userId не может быть null") Long userId, 
            @NotBlank(message = "Название события не может быть пустым") 
            @Size(max = 255, message = "Название события не может превышать 255 символов") String title, 
            @Size(max = 2000, message = "Описание события не может превышать 2000 символов") String description, 
            @NotNull(message = "Дата и время события не могут быть null") LocalDateTime eventDateTime) {
        return createEvent(userId, title, description, eventDateTime, null, false);
    }
    
    /**
     * Создает новое событие в календаре с расширенными параметрами.
     * 
     * <p>Метод выполняет следующие действия:</p>
     * <ol>
     *   <li>Проверяет существование пользователя в базе данных</li>
     *   <li>Валидирует дату и время события (не должно быть в прошлом)</li>
     *   <li>Валидирует временной интервал (endTime должно быть после eventTime)</li>
     *   <li>Создает событие с привязкой к пользователю и его семье</li>
     *   <li>Сохраняет событие в базе данных</li>
     *   <li>Записывает действие в историю изменений</li>
     * </ol>
     * 
     * <p><b>Требования:</b> 4.1, 4.2, 4.3, 26.2, 32.1, 18.5</p>
     * 
     * @param userId идентификатор пользователя, создающего событие
     * @param title название события (обязательное, не более 255 символов)
     * @param description описание события (может быть null, не более 2000 символов)
     * @param eventDateTime дата и время начала события
     * @param endTime время окончания события (может быть null)
     * @param isPersonal флаг персонального события (true - видно только создателю)
     * @return созданное и сохраненное событие
     * @throws UserNotFoundException если пользователь с указанным ID не найден
     * @throws InvalidDateException если дата события находится в прошлом или endTime раньше eventTime
     * @throws jakarta.validation.ConstraintViolationException если параметры не прошли валидацию
     */
    public Event createEvent(
            @NotNull(message = "userId не может быть null") Long userId, 
            @NotBlank(message = "Название события не может быть пустым") 
            @Size(max = 255, message = "Название события не может превышать 255 символов") String title, 
            @Size(max = 2000, message = "Описание события не может превышать 2000 символов") String description, 
            @NotNull(message = "Дата и время события не могут быть null") LocalDateTime eventDateTime, 
            LocalTime endTime, 
            Boolean isPersonal) {
        log.debug("Создание события для пользователя ID={}: title='{}', dateTime={}, endTime={}, isPersonal={}", 
                  userId, title, eventDateTime, endTime, isPersonal);
        
        // Валидация даты - событие не должно быть в прошлом (сравниваем только даты без времени)
        if (eventDateTime.toLocalDate().isBefore(LocalDate.now())) {
            log.warn("Попытка создать событие с датой в прошлом: {} для пользователя ID={}", 
                     eventDateTime, userId);
            throw new InvalidDateException("Дата события не может быть в прошлом");
        }
        
        // Валидация временного интервала
        if (endTime != null && endTime.isBefore(eventDateTime.toLocalTime())) {
            log.warn("Попытка создать событие с временем окончания раньше времени начала: start={}, end={}", 
                     eventDateTime.toLocalTime(), endTime);
            throw new InvalidDateException("Время окончания не может быть раньше времени начала");
        }
        
        // Поиск пользователя
        User user = userRepository.findById(userId)
            .orElseThrow(() -> {
                log.error("Пользователь с ID={} не найден при создании события", userId);
                return new UserNotFoundException(userId);
            });
        
        // Проверка наличия семьи у пользователя
        if (user.getFamily() == null) {
            log.error("Пользователь ID={} не принадлежит ни одной семье", userId);
            throw new IllegalStateException("Пользователь должен принадлежать семье для создания событий");
        }
        
        // Создание события
        Event event = Event.builder()
            .user(user)
            .family(user.getFamily())
            .title(title)
            .description(description)
            .eventDate(eventDateTime.toLocalDate())
            .eventTime(eventDateTime.toLocalTime())
            .endTime(endTime)
            .isPersonal(isPersonal != null ? isPersonal : false)
            .notified(false)
            .build();
        
        Event savedEvent = eventRepository.save(event);
        log.info("Событие ID={} успешно создано пользователем ID={} для семьи ID={} (персональное: {})", 
                 savedEvent.getId(), userId, user.getFamily().getId(), savedEvent.getIsPersonal());
        
        // Запись в историю изменений
        eventHistoryService.recordChange(
            savedEvent.getId(),
            userId,
            EventHistory.ActionType.CREATED,
            null,
            null,
            String.format("Событие '%s' создано", title)
        );
        
        return savedEvent;
    }
    
    /**
     * Получает предстоящие активные события семьи на указанное количество дней.
     * 
     * <p>Метод возвращает только активные события семьи (со статусом ACTIVE), 
     * которые запланированы в диапазоне от текущей даты до указанного количества 
     * дней в будущем. События автоматически сортируются по дате и времени.</p>
     * 
     * <p>Исключаются события со статусами:</p>
     * <ul>
     *   <li>COMPLETED - завершенные события</li>
     *   <li>DELETED - удаленные события (в корзине)</li>
     *   <li>DRAFT - черновики событий</li>
     * </ul>
     * 
     * <p><b>Требования:</b> 1.1, 1.2, 1.3, 1.4, 2.1, 2.2, 2.3, 2.4, 3.1, 3.2, 3.3, 3.4, 4.1, 4.2, 5.1, 5.4</p>
     * 
     * @param familyId идентификатор семьи
     * @param days количество дней для поиска событий (от текущей даты)
     * @return список активных предстоящих событий, отсортированный по дате и времени
     * @throws IllegalArgumentException если days меньше или равно 0
     */
    @Transactional(readOnly = true)
    public List<Event> getUpcomingEvents(Long familyId, int days) {
        log.debug("Получение активных предстоящих событий для семьи ID={} на {} дней", familyId, days);
        
        if (days <= 0) {
            throw new IllegalArgumentException("Количество дней должно быть больше 0");
        }
        
        LocalDate startDate = LocalDate.now();
        LocalDate endDate = startDate.plusDays(days);
        
        List<Event> events = eventRepository.findByFamilyIdAndEventDateBetweenAndStatus(
            familyId, startDate, endDate, Event.EventStatus.ACTIVE);
        
        log.debug("Найдено {} активных предстоящих событий для семьи ID={}", events.size(), familyId);
        return events;
    }
    
    /**
     * Получает активные события пользователя.
     * 
     * <p>Возвращает только события со статусом ACTIVE, исключая:</p>
     * <ul>
     *   <li>Удаленные события (DELETED) - доступны через /trash</li>
     *   <li>Черновики (DRAFT) - незавершенные события</li>
     *   <li>Завершенные события (COMPLETED) - прошедшие события</li>
     * </ul>
     * 
     * <p><b>Требования:</b> 1.1, 1.2, 1.3, 1.4, 3.1, 3.2, 3.3</p>
     * 
     * @param userId идентификатор пользователя
     * @return список активных событий пользователя, отсортированный по дате
     */
    @Transactional(readOnly = true)
    public List<Event> getUserEvents(Long userId) {
        log.debug("Получение активных событий пользователя ID={}", userId);
        
        List<Event> events = eventRepository.findByUserIdAndStatusOrderByEventDateAscEventTimeAsc(
            userId, 
            Event.EventStatus.ACTIVE
        );
        
        log.debug("Найдено {} активных событий для пользователя ID={}", events.size(), userId);
        return events;
    }
    
    /**
     * Получает событие по его идентификатору.
     * 
     * <p>Метод используется для получения полной информации о событии
     * для отображения деталей, редактирования или удаления.</p>
     * 
     * <p><b>Требования:</b> 8.1</p>
     * 
     * @param eventId идентификатор события
     * @return событие с указанным ID
     * @throws EventNotFoundException если событие с указанным ID не найдено
     */
    @Transactional(readOnly = true)
    public Event getEventById(Long eventId) {
        log.debug("Получение события по ID={}", eventId);
        
        Event event = eventRepository.findById(eventId)
            .orElseThrow(() -> {
                log.error("Событие с ID={} не найдено", eventId);
                return new EventNotFoundException(eventId);
            });
        
        log.debug("Событие ID={} успешно получено: title='{}'", eventId, event.getTitle());
        return event;
    }
    
    /**
     * Сохраняет событие в базе данных.
     * 
     * <p>Этот метод используется для сохранения изменений в существующем событии
     * без дополнительных проверок прав доступа. Используйте его только когда
     * проверки уже выполнены или не требуются (например, при обновлении служебных полей).</p>
     * 
     * <p><b>Требования:</b> 2.1, 2.4, 3.1</p>
     * 
     * @param event событие для сохранения
     * @return сохраненное событие
     */
    public Event saveEvent(@NotNull(message = "event не может быть null") Event event) {
        log.debug("Сохранение события ID={}", event.getId());
        Event savedEvent = eventRepository.save(event);
        log.debug("Событие ID={} успешно сохранено", savedEvent.getId());
        return savedEvent;
    }
    
    /**
     * Обновляет существующее событие.
     * 
     * <p>Метод выполняет следующие проверки перед обновлением:</p>
     * <ol>
     *   <li>Проверяет существование события</li>
     *   <li>Проверяет права доступа (только создатель может редактировать)</li>
     *   <li>Валидирует новую дату (не должна быть в прошлом)</li>
     *   <li>Обновляет поля события</li>
     *   <li>Записывает изменения в историю</li>
     * </ol>
     * 
     * <p><b>Требования:</b> 7.2, 7.4, 7.5, 18.5</p>
     * 
     * @param eventId идентификатор события для обновления
     * @param userId идентификатор пользователя, выполняющего обновление
     * @param title новое название события (обязательное, не более 255 символов)
     * @param description новое описание события (может быть null, не более 2000 символов)
     * @param eventDateTime новая дата и время события
     * @return обновленное событие
     * @throws EventNotFoundException если событие с указанным ID не найдено
     * @throws UnauthorizedAccessException если пользователь не является создателем события
     * @throws InvalidDateException если новая дата находится в прошлом
     * @throws jakarta.validation.ConstraintViolationException если параметры не прошли валидацию
     */
    public Event updateEvent(
            @NotNull(message = "eventId не может быть null") Long eventId, 
            @NotNull(message = "userId не может быть null") Long userId, 
            @NotBlank(message = "Название события не может быть пустым") 
            @Size(max = 255, message = "Название события не может превышать 255 символов") String title, 
            @Size(max = 2000, message = "Описание события не может превышать 2000 символов") String description, 
            @NotNull(message = "Дата и время события не могут быть null") LocalDateTime eventDateTime) {
        return updateEvent(eventId, userId, title, description, eventDateTime, null);
    }
    
    /**
     * Обновляет существующее событие с расширенными параметрами.
     * 
     * <p>Метод выполняет следующие проверки перед обновлением:</p>
     * <ol>
     *   <li>Проверяет существование события</li>
     *   <li>Проверяет права доступа (только создатель может редактировать)</li>
     *   <li>Валидирует новую дату (не должна быть в прошлом)</li>
     *   <li>Валидирует временной интервал (endTime должно быть после eventTime)</li>
     *   <li>Обновляет поля события</li>
     *   <li>Записывает изменения в историю</li>
     * </ol>
     * 
     * <p><b>Требования:</b> 7.2, 7.4, 7.5, 26.2, 18.5</p>
     * 
     * @param eventId идентификатор события для обновления
     * @param userId идентификатор пользователя, выполняющего обновление
     * @param title новое название события (обязательное, не более 255 символов)
     * @param description новое описание события (может быть null, не более 2000 символов)
     * @param eventDateTime новая дата и время начала события
     * @param endTime новое время окончания события (может быть null)
     * @return обновленное событие
     * @throws EventNotFoundException если событие с указанным ID не найдено
     * @throws UnauthorizedAccessException если пользователь не является создателем события
     * @throws InvalidDateException если новая дата находится в прошлом или endTime раньше eventTime
     * @throws jakarta.validation.ConstraintViolationException если параметры не прошли валидацию
     */
    public Event updateEvent(
            @NotNull(message = "eventId не может быть null") Long eventId, 
            @NotNull(message = "userId не может быть null") Long userId, 
            @NotBlank(message = "Название события не может быть пустым") 
            @Size(max = 255, message = "Название события не может превышать 255 символов") String title, 
            @Size(max = 2000, message = "Описание события не может превышать 2000 символов") String description, 
            @NotNull(message = "Дата и время события не могут быть null") LocalDateTime eventDateTime, 
            LocalTime endTime) {
        log.debug("Обновление события ID={} пользователем ID={}, endTime={}", eventId, userId, endTime);
        
        // Валидация даты (сравниваем только даты без времени)
        if (eventDateTime.toLocalDate().isBefore(LocalDate.now())) {
            log.warn("Попытка обновить событие ID={} с датой в прошлом: {}", eventId, eventDateTime);
            throw new InvalidDateException("Дата события не может быть в прошлом");
        }
        
        // Валидация временного интервала
        if (endTime != null && endTime.isBefore(eventDateTime.toLocalTime())) {
            log.warn("Попытка обновить событие ID={} с временем окончания раньше времени начала: start={}, end={}", 
                     eventId, eventDateTime.toLocalTime(), endTime);
            throw new InvalidDateException("Время окончания не может быть раньше времени начала");
        }
        
        // Поиск события
        Event event = eventRepository.findById(eventId)
            .orElseThrow(() -> {
                log.error("Событие с ID={} не найдено при попытке обновления", eventId);
                return new EventNotFoundException(eventId);
            });
        
        // Проверка прав доступа - только создатель может редактировать
        if (!event.belongsToUser(userId)) {
            log.warn("Пользователь ID={} попытался обновить чужое событие ID={} (владелец: ID={})", 
                     userId, eventId, event.getUser().getId());
            throw new UnauthorizedAccessException(
                "Только создатель события может его редактировать");
        }
        
        // Сохранение старых значений для истории
        String oldTitle = event.getTitle();
        String oldDescription = event.getDescription();
        LocalDate oldDate = event.getEventDate();
        LocalTime oldTime = event.getEventTime();
        LocalTime oldEndTime = event.getEndTime();
        
        // Обновление полей
        event.setTitle(title);
        event.setDescription(description);
        event.setEventDate(eventDateTime.toLocalDate());
        event.setEventTime(eventDateTime.toLocalTime());
        event.setEndTime(endTime);
        
        Event updatedEvent = eventRepository.save(event);
        log.info("Событие ID={} успешно обновлено пользователем ID={}", eventId, userId);
        
        // Запись изменений в историю
        if (!oldTitle.equals(title)) {
            eventHistoryService.recordChange(eventId, userId, EventHistory.ActionType.UPDATED, 
                "title", oldTitle, title);
        }
        if ((oldDescription == null && description != null) || 
            (oldDescription != null && !oldDescription.equals(description))) {
            eventHistoryService.recordChange(eventId, userId, EventHistory.ActionType.UPDATED, 
                "description", oldDescription, description);
        }
        if (!oldDate.equals(eventDateTime.toLocalDate()) || !oldTime.equals(eventDateTime.toLocalTime())) {
            eventHistoryService.recordChange(eventId, userId, EventHistory.ActionType.UPDATED, 
                "datetime", 
                String.format("%s %s", oldDate, oldTime),
                String.format("%s %s", eventDateTime.toLocalDate(), eventDateTime.toLocalTime()));
        }
        if ((oldEndTime == null && endTime != null) || 
            (oldEndTime != null && !oldEndTime.equals(endTime))) {
            eventHistoryService.recordChange(eventId, userId, EventHistory.ActionType.UPDATED, 
                "end_time", 
                oldEndTime != null ? oldEndTime.toString() : null, 
                endTime != null ? endTime.toString() : null);
        }
        
        return updatedEvent;
    }
    
    /**
     * Перемещает событие в корзину (мягкое удаление).
     * 
     * <p>Метод выполняет следующие действия:</p>
     * <ol>
     *   <li>Проверяет существование события</li>
     *   <li>Проверяет права доступа (только создатель может удалять)</li>
     *   <li>Удаляет сообщение события из чата</li>
     *   <li>Изменяет статус события на DELETED</li>
     *   <li>Сбрасывает messageId и isMyEventsHeader</li>
     *   <li>Устанавливает дату удаления</li>
     *   <li>Записывает действие в историю</li>
     *   <li>Обновляет шапку /my_events</li>
     * </ol>
     * 
     * <p>Событие хранится в корзине 30 дней, после чего автоматически удаляется.</p>
     * 
     * <p><b>Требования:</b> 1.1, 1.2, 1.3, 1.4, 7.3, 7.5, 19.1, 18.5, 2.3, 4.1, 4.2, 4.3</p>
     * 
     * @param eventId идентификатор события для удаления
     * @param userId идентификатор пользователя, выполняющего удаление
     * @return удаленное событие
     * @throws EventNotFoundException если событие с указанным ID не найдено
     * @throws UnauthorizedAccessException если пользователь не является создателем события
     */
    public Event deleteEvent(Long eventId, Long userId) {
        log.debug("Перемещение события ID={} в корзину пользователем ID={}", eventId, userId);
        
        // Поиск события
        Event event = eventRepository.findById(eventId)
            .orElseThrow(() -> {
                log.error("Событие с ID={} не найдено при попытке удаления", eventId);
                return new EventNotFoundException(eventId);
            });
        
        // Проверка прав доступа - только создатель может удалять
        if (!event.belongsToUser(userId)) {
            log.warn("Пользователь ID={} попытался удалить чужое событие ID={} (владелец: ID={})", 
                     userId, eventId, event.getUser().getId());
            throw new UnauthorizedAccessException(
                "Только создатель события может его удалить");
        }
        
        // Удаляем сообщение события из чата
        if (event.getMessageId() != null) {
            Long chatId = event.getUser().getTelegramId();
            if (chatId != null) {
                try {
                    telegramMessageService.deleteMessage(chatId, event.getMessageId().intValue());
                    log.debug("Сообщение события удалено при удалении: eventId={}, messageId={}", 
                             eventId, event.getMessageId());
                } catch (Exception e) {
                    log.warn("Не удалось удалить сообщение события при удалении: eventId={}, messageId={}, error={}", 
                             eventId, event.getMessageId(), e.getMessage());
                }
            } else {
                log.warn("Не удалось получить chatId для удаления сообщения события ID={}", eventId);
            }
        }
        
        // Перемещение в корзину
        event.setStatus(Event.EventStatus.DELETED);
        event.setDeletedAt(LocalDateTime.now());
        event.setMessageId(null);
        event.setIsMyEventsHeader(false);
        
        Event deletedEvent = eventRepository.save(event);
        
        log.info("Событие ID={} успешно перемещено в корзину пользователем ID={}", eventId, userId);
        
        // Запись в историю изменений
        eventHistoryService.recordDeletion(eventId, userId);
        
        // Обновляем шапку /my_events после удаления события
        updateMyEventsHeaderAfterRemoval(userId);
        
        return deletedEvent;
    }
    
    /**
     * Завершает событие вручную.
     * 
     * <p>Метод выполняет следующие действия:</p>
     * <ol>
     *   <li>Проверяет существование события</li>
     *   <li>Проверяет права доступа (только создатель может завершить)</li>
     *   <li>Проверяет статус события (должно быть ACTIVE)</li>
     *   <li>Удаляет сообщение события из чата</li>
     *   <li>Изменяет статус на COMPLETED</li>
     *   <li>Сбрасывает messageId и isMyEventsHeader</li>
     *   <li>Устанавливает completedAt в текущее время</li>
     *   <li>Записывает действие в историю изменений</li>
     *   <li>Отмечает все неотправленные напоминания как отправленные</li>
     *   <li>Обновляет шапку /my_events</li>
     * </ol>
     * 
     * <p><b>Требования:</b> 1.1, 1.2, 1.3, 1.4, 1.5, 3.1, 3.2, 3.3, 5.1</p>
     * 
     * @param eventId идентификатор события
     * @param userId идентификатор пользователя, завершающего событие
     * @return завершенное событие
     * @throws EventNotFoundException если событие не найдено
     * @throws UnauthorizedAccessException если пользователь не является создателем
     * @throws IllegalStateException если событие не в статусе ACTIVE
     */
    public Event completeEvent(
            @NotNull(message = "eventId не может быть null") Long eventId,
            @NotNull(message = "userId не может быть null") Long userId) {
        log.debug("Завершение события ID={} пользователем ID={}", eventId, userId);
        
        // Поиск события
        Event event = eventRepository.findById(eventId)
            .orElseThrow(() -> {
                log.error("Событие с ID={} не найдено при попытке завершения", eventId);
                return new EventNotFoundException(eventId);
            });
        
        // Проверка прав доступа - только создатель может завершить
        if (!event.belongsToUser(userId)) {
            log.warn("Пользователь ID={} попытался завершить чужое событие ID={} (владелец: ID={})", 
                     userId, eventId, event.getUser().getId());
            throw new UnauthorizedAccessException(
                "Только создатель события может его завершить");
        }
        
        // Проверка статуса события - должно быть ACTIVE
        if (event.getStatus() != Event.EventStatus.ACTIVE) {
            log.warn("Попытка завершить неактивное событие ID={} (статус: {})", 
                     eventId, event.getStatus());
            throw new IllegalStateException(
                String.format("Можно завершить только активное событие (текущий статус: %s)", 
                             event.getStatus()));
        }
        
        // Удаляем сообщение события из чата
        if (event.getMessageId() != null) {
            Long chatId = event.getUser().getTelegramId();
            if (chatId != null) {
                try {
                    telegramMessageService.deleteMessage(chatId, event.getMessageId().intValue());
                    log.debug("Сообщение события удалено при завершении: eventId={}, messageId={}", 
                             eventId, event.getMessageId());
                } catch (Exception e) {
                    log.warn("Не удалось удалить сообщение события при завершении: eventId={}, messageId={}, error={}", 
                             eventId, event.getMessageId(), e.getMessage());
                }
            } else {
                log.warn("Не удалось получить chatId для удаления сообщения события ID={}", eventId);
            }
        }
        
        // Установка статуса COMPLETED и completedAt
        event.setStatus(Event.EventStatus.COMPLETED);
        event.setCompletedAt(LocalDateTime.now());
        event.setMessageId(null);
        event.setIsMyEventsHeader(false);
        
        Event completedEvent = eventRepository.save(event);
        log.info("Событие ID={} успешно завершено вручную пользователем ID={}", eventId, userId);
        
        // Запись в историю изменений
        eventHistoryService.recordChange(
            eventId,
            userId,
            EventHistory.ActionType.UPDATED,
            "status",
            "ACTIVE",
            "COMPLETED"
        );
        
        // Обработка напоминаний
        handleEventCompletion(eventId);
        
        // Обновляем шапку /my_events после удаления события
        updateMyEventsHeaderAfterRemoval(userId);
        
        return completedEvent;
    }
    
    /**
     * Добавляет заметку к завершенному событию.
     * 
     * <p>Метод позволяет пользователю добавить заметку после завершения события,
     * например, описать как прошло событие или что было сделано.</p>
     * 
     * <p><b>Требования:</b> 25.3, 18.5</p>
     * 
     * @param eventId идентификатор события
     * @param userId идентификатор пользователя, добавляющего заметку
     * @param note текст заметки
     * @return обновленное событие с заметкой
     * @throws EventNotFoundException если событие с указанным ID не найдено
     * @throws UnauthorizedAccessException если пользователь не является создателем события
     * @throws IllegalStateException если событие не завершено
     * @throws IllegalArgumentException если заметка пустая
     */
    public Event addCompletionNote(Long eventId, Long userId, String note) {
        log.debug("Добавление заметки к завершенному событию ID={} пользователем ID={}", eventId, userId);
        
        // Валидация заметки
        if (note == null || note.isBlank()) {
            log.warn("Попытка добавить пустую заметку к событию ID={}", eventId);
            throw new IllegalArgumentException("Заметка не может быть пустой");
        }
        
        // Поиск события
        Event event = eventRepository.findById(eventId)
            .orElseThrow(() -> {
                log.error("Событие с ID={} не найдено при попытке добавления заметки", eventId);
                return new EventNotFoundException(eventId);
            });
        
        // Проверка прав доступа
        if (!event.belongsToUser(userId)) {
            log.warn("Пользователь ID={} попытался добавить заметку к чужому событию ID={}", userId, eventId);
            throw new UnauthorizedAccessException(
                "Только создатель события может добавить заметку");
        }
        
        // Проверка статуса события
        if (!event.isCompleted()) {
            log.warn("Попытка добавить заметку к незавершенному событию ID={}", eventId);
            throw new IllegalStateException("Заметку можно добавить только к завершенному событию");
        }
        
        // Сохранение старой заметки для истории
        String oldNote = event.getCompletionNote();
        
        // Добавление заметки
        event.setCompletionNote(note);
        Event updatedEvent = eventRepository.save(event);
        
        log.info("Заметка успешно добавлена к событию ID={} пользователем ID={}", eventId, userId);
        
        // Запись в историю изменений
        eventHistoryService.recordChange(
            eventId,
            userId,
            EventHistory.ActionType.UPDATED,
            "completion_note",
            oldNote,
            note
        );
        
        return updatedEvent;
    }
    
    /**
     * Обновляет название события.
     * 
     * <p>Метод выполняет следующие действия:</p>
     * <ol>
     *   <li>Проверяет существование события</li>
     *   <li>Проверяет права доступа на редактирование</li>
     *   <li>Валидирует новое название</li>
     *   <li>Обновляет название события</li>
     *   <li>Записывает изменение в историю</li>
     * </ol>
     * 
     * <p><b>Требования:</b> 2.4, 2.5</p>
     * 
     * @param eventId идентификатор события
     * @param userId идентификатор пользователя
     * @param newTitle новое название события (не более 255 символов)
     * @return обновленное событие
     * @throws EventNotFoundException если событие не найдено
     * @throws UnauthorizedAccessException если нет прав на редактирование
     * @throws jakarta.validation.ConstraintViolationException если название пустое или слишком длинное
     */
    public Event updateEventTitle(
            @NotNull(message = "eventId не может быть null") Long eventId, 
            @NotNull(message = "userId не может быть null") Long userId, 
            @NotBlank(message = "Название события не может быть пустым") 
            @Size(max = 255, message = "Название события не может превышать 255 символов") String newTitle) {
        log.debug("Обновление названия события ID={} пользователем ID={}", eventId, userId);
        
        // Получение события и проверка прав
        Event event = getEventById(eventId);
        checkEditPermission(event, userId);
        
        // Сохранение старого значения
        String oldTitle = event.getTitle();
        
        // Обновление названия
        event.setTitle(newTitle);
        Event updated = eventRepository.save(event);
        
        log.info("Название события ID={} обновлено пользователем ID={}: '{}' → '{}'", 
                 eventId, userId, oldTitle, newTitle);
        
        // Запись в историю
        eventHistoryService.recordChange(
            eventId,
            userId,
            EventHistory.ActionType.UPDATED,
            "title",
            oldTitle,
            newTitle
        );
        
        return updated;
    }
    
    /**
     * Обновляет дату события.
     * 
     * <p>Метод выполняет следующие действия:</p>
     * <ol>
     *   <li>Проверяет существование события</li>
     *   <li>Проверяет права доступа на редактирование</li>
     *   <li>Валидирует новую дату (не должна быть в прошлом)</li>
     *   <li>Обновляет дату события</li>
     *   <li>Записывает изменение в историю</li>
     *   <li>Пересчитывает напоминания</li>
     * </ol>
     * 
     * <p><b>Требования:</b> 2.4, 2.5</p>
     * 
     * @param eventId идентификатор события
     * @param userId идентификатор пользователя
     * @param newDate новая дата события
     * @return обновленное событие
     * @throws EventNotFoundException если событие не найдено
     * @throws UnauthorizedAccessException если нет прав на редактирование
     * @throws InvalidDateException если дата в прошлом
     * @throws jakarta.validation.ConstraintViolationException если параметры null
     */
    public Event updateEventDate(
            @NotNull(message = "eventId не может быть null") Long eventId, 
            @NotNull(message = "userId не может быть null") Long userId, 
            @NotNull(message = "Дата события не может быть null") LocalDate newDate) {
        log.debug("Обновление даты события ID={} пользователем ID={}", eventId, userId);
        
        // Валидация даты - не должна быть в прошлом (сравниваем только даты без времени)
        if (newDate.isBefore(LocalDate.now())) {
            log.warn("Попытка установить дату в прошлом для события ID={}: {}", eventId, newDate);
            throw new InvalidDateException("Дата события не может быть в прошлом");
        }
        
        // Получение события и проверка прав
        Event event = getEventById(eventId);
        checkEditPermission(event, userId);
        
        // Сохранение старого значения
        LocalDate oldDate = event.getEventDate();
        
        // Обновление даты
        event.setEventDate(newDate);
        Event updated = eventRepository.save(event);
        
        log.info("Дата события ID={} обновлена пользователем ID={}: {} → {}", 
                 eventId, userId, oldDate, newDate);
        
        // Запись в историю
        eventHistoryService.recordChange(
            eventId,
            userId,
            EventHistory.ActionType.UPDATED,
            "event_date",
            oldDate.toString(),
            newDate.toString()
        );
        
        // Пересчет напоминаний
        handleEventDateTimeChange(eventId);
        
        return updated;
    }
    
    /**
     * Обновляет время события.
     * 
     * <p>Метод выполняет следующие действия:</p>
     * <ol>
     *   <li>Проверяет существование события</li>
     *   <li>Проверяет права доступа на редактирование</li>
     *   <li>Валидирует новое время</li>
     *   <li>Обновляет время события</li>
     *   <li>Записывает изменение в историю</li>
     *   <li>Пересчитывает напоминания</li>
     * </ol>
     * 
     * <p><b>Требования:</b> 2.4, 2.5</p>
     * 
     * @param eventId идентификатор события
     * @param userId идентификатор пользователя
     * @param newTime новое время события
     * @return обновленное событие
     * @throws EventNotFoundException если событие не найдено
     * @throws UnauthorizedAccessException если нет прав на редактирование
     * @throws jakarta.validation.ConstraintViolationException если параметры null
     */
    public Event updateEventTime(
            @NotNull(message = "eventId не может быть null") Long eventId, 
            @NotNull(message = "userId не может быть null") Long userId, 
            @NotNull(message = "Время события не может быть null") LocalTime newTime) {
        log.debug("Обновление времени события ID={} пользователем ID={}", eventId, userId);
        
        // Получение события и проверка прав
        Event event = getEventById(eventId);
        checkEditPermission(event, userId);
        
        // Сохранение старого значения
        LocalTime oldTime = event.getEventTime();
        
        // Обновление времени
        event.setEventTime(newTime);
        Event updated = eventRepository.save(event);
        
        log.info("Время события ID={} обновлено пользователем ID={}: {} → {}", 
                 eventId, userId, oldTime, newTime);
        
        // Запись в историю
        eventHistoryService.recordChange(
            eventId,
            userId,
            EventHistory.ActionType.UPDATED,
            "event_time",
            oldTime != null ? oldTime.toString() : null,
            newTime.toString()
        );
        
        // Пересчет напоминаний
        handleEventDateTimeChange(eventId);
        
        return updated;
    }
    
    /**
     * Обновляет описание события.
     * 
     * <p>Метод выполняет следующие действия:</p>
     * <ol>
     *   <li>Проверяет существование события</li>
     *   <li>Проверяет права доступа на редактирование</li>
     *   <li>Обновляет описание события</li>
     *   <li>Записывает изменение в историю</li>
     * </ol>
     * 
     * <p><b>Требования:</b> 2.4, 2.5</p>
     * 
     * @param eventId идентификатор события
     * @param userId идентификатор пользователя
     * @param newDescription новое описание события (может быть null или пустым, не более 2000 символов)
     * @return обновленное событие
     * @throws EventNotFoundException если событие не найдено
     * @throws UnauthorizedAccessException если нет прав на редактирование
     * @throws jakarta.validation.ConstraintViolationException если описание слишком длинное
     */
    public Event updateEventDescription(
            @NotNull(message = "eventId не может быть null") Long eventId, 
            @NotNull(message = "userId не может быть null") Long userId, 
            @Size(max = 2000, message = "Описание события не может превышать 2000 символов") String newDescription) {
        log.debug("Обновление описания события ID={} пользователем ID={}", eventId, userId);
        
        // Получение события и проверка прав
        Event event = getEventById(eventId);
        checkEditPermission(event, userId);
        
        // Сохранение старого значения
        String oldDescription = event.getDescription();
        
        // Обновление описания
        event.setDescription(newDescription);
        Event updated = eventRepository.save(event);
        
        log.info("Описание события ID={} обновлено пользователем ID={}", eventId, userId);
        
        // Запись в историю
        eventHistoryService.recordChange(
            eventId,
            userId,
            EventHistory.ActionType.UPDATED,
            "description",
            oldDescription,
            newDescription
        );
        
        return updated;
    }
    
    /**
     * Проверяет права пользователя на редактирование события.
     * 
     * <p>Пользователь может редактировать событие, если:</p>
     * <ul>
     *   <li>Он является создателем события</li>
     *   <li>Событие семейное (не персональное) и пользователь из той же семьи</li>
     * </ul>
     * 
     * @param event событие для проверки
     * @param userId идентификатор пользователя
     * @throws UnauthorizedAccessException если у пользователя нет прав на редактирование
     */
    private void checkEditPermission(Event event, Long userId) {
        // Проверка: пользователь - создатель события
        if (event.getUser().getId().equals(userId)) {
            return;
        }
        
        // Проверка: событие семейное и пользователь из той же семьи
        if (!event.getIsPersonal() && event.getFamily() != null) {
            boolean isFromSameFamily = event.getFamily().getMembers().stream()
                .anyMatch(u -> u.getId().equals(userId));
            
            if (isFromSameFamily) {
                return;
            }
        }
        
        // Нет прав на редактирование
        log.warn("Пользователь ID={} попытался отредактировать событие ID={} без прав доступа", 
                 userId, event.getId());
        throw new UnauthorizedAccessException(
            "У вас нет прав для редактирования этого события");
    }
    
    /**
     * Обрабатывает изменение даты или времени события.
     * Пересчитывает все неотправленные напоминания для нового времени.
     * 
     * <p><b>Требования:</b> 9.5</p>
     * 
     * @param eventId идентификатор события
     */
    public void handleEventDateTimeChange(Long eventId) {
        log.info("Обработка изменения даты/времени события ID={}", eventId);
        
        try {
            reminderService.recalculateReminders(eventId);
            log.info("Напоминания пересчитаны для события ID={}", eventId);
        } catch (Exception e) {
            log.error("Ошибка при пересчете напоминаний для события ID={}: {}", 
                     eventId, e.getMessage(), e);
        }
    }
    
    /**
     * Обрабатывает завершение события.
     * Отмечает все неотправленные напоминания как отправленные.
     * 
     * <p><b>Требования:</b> 11.2</p>
     * 
     * @param eventId идентификатор события
     */
    public void handleEventCompletion(Long eventId) {
        log.info("Обработка завершения события ID={}", eventId);
        
        try {
            reminderService.markRemindersAsSent(eventId);
            log.info("Напоминания отмечены как отправленные для события ID={}", eventId);
        } catch (Exception e) {
            log.error("Ошибка при отметке напоминаний для события ID={}: {}", 
                     eventId, e.getMessage(), e);
        }
    }
    
    /**
     * Обновляет шапку /my_events после удаления или завершения события.
     * 
     * <p>Метод выполняет следующие действия:</p>
     * <ol>
     *   <li>Получает актуальный список активных событий пользователя</li>
     *   <li>Если список пуст - отправляет сообщение о пустом состоянии</li>
     *   <li>Если есть события - устанавливает isMyEventsHeader для первого события</li>
     *   <li>Обновляет счетчик в шапке через updateMyEventsHeaderCount</li>
     * </ol>
     * 
     * <p><b>Требования:</b> 2.1, 2.2, 2.3, 2.4, 3.1, 3.2, 3.3</p>
     * 
     * @param userId идентификатор пользователя
     */
    @Transactional
    public void updateMyEventsHeaderAfterRemoval(Long userId) {
        log.debug("Обновление шапки /my_events после удаления события для пользователя ID={}", userId);
        
        // Получаем актуальный список активных событий
        List<Event> activeEvents = getUserEvents(userId);
        
        // Получаем пользователя для доступа к chatId
        User user = userRepository.findById(userId)
            .orElseThrow(() -> {
                log.error("Пользователь с ID={} не найден при обновлении шапки /my_events", userId);
                return new UserNotFoundException(userId);
            });
        
        Long chatId = user.getTelegramId();
        
        if (chatId == null) {
            log.warn("Не удалось получить chatId для пользователя ID={}", userId);
            return;
        }
        
        if (activeEvents.isEmpty()) {
            // Отправляем сообщение о пустом состоянии
            String emptyMessage = buildEmptyStateMessage();
            try {
                telegramMessageService.sendMessage(chatId, emptyMessage);
                log.info("Сообщение о пустом состоянии отправлено пользователю ID={}", userId);
            } catch (Exception e) {
                log.error("Ошибка при отправке сообщения о пустом состоянии пользователю ID={}: {}", 
                         userId, e.getMessage(), e);
            }
            return;
        }
        
        // Находим новое первое событие
        Event newFirstEvent = activeEvents.get(0);
        
        // Устанавливаем флаг isMyEventsHeader для нового первого события
        if (!Boolean.TRUE.equals(newFirstEvent.getIsMyEventsHeader())) {
            newFirstEvent.setIsMyEventsHeader(true);
            eventRepository.save(newFirstEvent);
            log.debug("Флаг isMyEventsHeader установлен для события ID={}", newFirstEvent.getId());
        }
        
        // Сбрасываем флаг isMyEventsHeader для остальных событий (если он был установлен)
        // Это обеспечивает уникальность флага (Требование 2.2)
        for (int i = 1; i < activeEvents.size(); i++) {
            Event event = activeEvents.get(i);
            if (Boolean.TRUE.equals(event.getIsMyEventsHeader())) {
                log.debug("Сброс флага isMyEventsHeader=false для события ID={}", event.getId());
                event.setIsMyEventsHeader(false);
                eventRepository.save(event);
            }
        }
        
        // Обновляем счетчик в шапке
        myEventsCommandHandler.updateMyEventsHeaderCount(userId);
        
        log.debug("Шапка /my_events успешно обновлена для пользователя ID={}", userId);
    }
    
    /**
     * Формирует сообщение о пустом состоянии /my_events.
     * 
     * <p>Сообщение включает:</p>
     * <ul>
     *   <li>Заголовок "Мои события"</li>
     *   <li>Информацию об отсутствии событий</li>
     *   <li>Подсказку о добавлении нового события</li>
     * </ul>
     * 
     * <p>Все специальные символы MarkdownV2 корректно экранированы.</p>
     * 
     * <p><b>Требования:</b> 3.1, 3.2, 3.3</p>
     * 
     * @return отформатированное сообщение о пустом состоянии
     */
    private String buildEmptyStateMessage() {
        StringBuilder message = new StringBuilder();
        message.append("📋 ").append(bold("Мои события")).append("\n\n");
        message.append(escape("У вас пока нет созданных событий.\n\n"));
        message.append(escape("Используйте ")).append(escape("/add_event")).append(escape(" для добавления нового события."));
        return message.toString();
    }
    
    /**
     * Получает отфильтрованные события пользователя по типу фильтра.
     * 
     * <p>Метод возвращает события в зависимости от выбранного фильтра:</p>
     * <ul>
     *   <li>{@link EventFilter#ALL ALL} - все события семьи (семейные и личные события пользователя)</li>
     *   <li>{@link EventFilter#FAMILY FAMILY} - только семейные события (isPersonal = false)</li>
     *   <li>{@link EventFilter#PERSONAL PERSONAL} - только личные события пользователя (isPersonal = true)</li>
     * </ul>
     * 
     * <p>События автоматически сортируются по дате и времени в порядке возрастания.</p>
     * 
     * <p><b>Требования:</b> 2.1, 2.2, 2.3</p>
     * 
     * @param user пользователь, для которого выполняется фильтрация
     * @param filter тип фильтра событий
     * @return список отфильтрованных событий, отсортированный по дате и времени
     * @throws IllegalArgumentException если user или filter равны null
     */
    @Transactional(readOnly = true)
    public List<Event> getFilteredEvents(
            @NotNull(message = "user не может быть null") User user, 
            @NotNull(message = "filter не может быть null") EventFilter filter) {
        log.debug("Получение отфильтрованных событий для пользователя ID={}, фильтр={}", 
                  user.getId(), filter);
        
        if (user.getFamily() == null) {
            log.warn("Пользователь ID={} не принадлежит ни одной семье", user.getId());
            return List.of();
        }
        
        Long familyId = user.getFamily().getId();
        Long userId = user.getId();
        
        List<Event> events;
        
        switch (filter) {
            case ALL:
                // Все события семьи (семейные + личные события пользователя)
                events = eventRepository.findByFamilyIdAndStatusOrderByEventDateAscEventTimeAsc(
                    familyId, Event.EventStatus.ACTIVE);
                
                // Фильтруем: оставляем семейные события и личные события текущего пользователя
                events = events.stream()
                    .filter(event -> !event.getIsPersonal() || event.belongsToUser(userId))
                    .toList();
                
                log.debug("Найдено {} событий (ALL) для пользователя ID={}", events.size(), userId);
                break;
                
            case FAMILY:
                // Только семейные события (isPersonal = false)
                events = eventRepository.findByFamilyIdAndIsPersonalAndStatusOrderByEventDateAscEventTimeAsc(
                    familyId, false, Event.EventStatus.ACTIVE);
                
                log.debug("Найдено {} семейных событий для пользователя ID={}", events.size(), userId);
                break;
                
            case PERSONAL:
                // Только личные события пользователя (isPersonal = true)
                events = eventRepository.findByUserIdAndIsPersonalAndStatusOrderByEventDateAscEventTimeAsc(
                    userId, true, Event.EventStatus.ACTIVE);
                
                log.debug("Найдено {} личных событий для пользователя ID={}", events.size(), userId);
                break;
                
            default:
                log.warn("Неизвестный тип фильтра: {}, возвращаем пустой список", filter);
                events = List.of();
        }
        
        return events;
    }
    
    /**
     * Получает количество активных событий пользователя.
     * 
     * <p>Подсчитывает только события со статусом ACTIVE, исключая:</p>
     * <ul>
     *   <li>Удаленные события (DELETED)</li>
     *   <li>Черновики (DRAFT)</li>
     *   <li>Завершенные события (COMPLETED)</li>
     * </ul>
     * 
     * <p>Используется для отображения количества событий в шапке списка "Мои события".
     * Количество должно соответствовать фактическому количеству отображаемых событий.</p>
     * 
     * <p><b>Требования:</b> 1.1, 1.2, 1.3, 1.4, 1.5</p>
     * 
     * @param userId идентификатор пользователя
     * @return количество активных событий пользователя со статусом ACTIVE
     */
    public int getActiveEventsCount(Long userId) {
        int count = eventRepository.countByUserIdAndStatus(userId, Event.EventStatus.ACTIVE);
        log.debug("Подсчитано активных событий (статус ACTIVE) для пользователя ID={}: {}", userId, count);
        return count;
    }
    
    /**
     * Отправляет или обновляет сообщение о событии в Telegram.
     * 
     * <p>Метод выполняет следующие действия:</p>
     * <ol>
     *   <li>Форматирует текст сообщения о событии</li>
     *   <li>Создает inline клавиатуру для управления событием</li>
     *   <li>Если событие имеет messageId, пытается обновить существующее сообщение</li>
     *   <li>Если обновление не удалось или messageId отсутствует, отправляет новое сообщение</li>
     *   <li>Сохраняет полученный messageId в событие</li>
     *   <li>Сохраняет событие в базе данных</li>
     * </ol>
     * 
     * <p><b>Обработка ошибок:</b></p>
     * <ul>
     *   <li>Если сообщение удалено пользователем - отправляет новое сообщение</li>
     *   <li>Если сообщение слишком старое (>48 часов) - отправляет новое сообщение</li>
     *   <li>Если произошла ошибка редактирования - отправляет новое сообщение</li>
     *   <li>Все операции детально логируются</li>
     * </ul>
     * 
     * <p><b>Требования:</b> 1.3, 1.4, 2.5, 3.1, 3.2, 3.3, 3.4, 3.5, 4.3, 4.5, 5.1, 5.2, 5.3, 5.4</p>
     * 
     * @param event событие для отправки/обновления
     * @param chatId ID чата для отправки
     * @return обновленное событие с актуальным messageId
     * @throws TelegramApiException при критических ошибках отправки
     * @throws IllegalArgumentException если event или chatId равны null
     */
    public Event sendOrUpdateEventMessage(
            @NotNull(message = "event не может быть null") Event event,
            @NotNull(message = "chatId не может быть null") Long chatId) throws TelegramApiException {
        
        if (event == null) {
            log.error("Попытка отправить/обновить сообщение для null события");
            throw new IllegalArgumentException("Event не может быть null");
        }
        
        if (chatId == null) {
            log.error("Попытка отправить/обновить сообщение с null chatId для события ID={}", 
                    event.getId());
            throw new IllegalArgumentException("ChatId не может быть null");
        }
        
        log.debug("Отправка/обновление сообщения о событии: eventId={}, chatId={}, messageId={}, isMyEventsHeader={}", 
                event.getId(), chatId, event.getMessageId(), event.getIsMyEventsHeader());
        
        // Форматирование текста сообщения
        String messageText = botMessageBuilder.buildEventMessage(event);
        log.debug("Текст сообщения сформирован: eventId={}, textLength={}", 
                event.getId(), messageText.length());
        
        // Если это первое событие в списке "Мои события", добавляем шапку
        if (Boolean.TRUE.equals(event.getIsMyEventsHeader())) {
            int eventCount = getActiveEventsCount(event.getUser().getId());
            String header = botMessageBuilder.buildMyEventsHeader(eventCount);
            messageText = header + "\n" + messageText;
            log.debug("Добавлена шапка 'Мои события' к сообщению: eventId={}, eventCount={}", 
                    event.getId(), eventCount);
        }
        
        // Создание inline клавиатуры в зависимости от статуса события
        InlineKeyboardMarkup keyboard;
        if (event.getStatus() == Event.EventStatus.DRAFT) {
            // Для черновиков используем специальную клавиатуру редактирования
            keyboard = keyboardService.createEditFieldSelectionKeyboard(event.getId());
            log.debug("Клавиатура для черновика создана для события ID={}", event.getId());
        } else {
            // Для активных, завершённых и удалённых событий используем клавиатуру с учетом статуса и прав
            // Получаем ID пользователя из события
            Long userId = event.getUser().getId();
            keyboard = keyboardService.createEventActionsKeyboard(event, userId);
            log.debug("Клавиатура для события создана: eventId={}, status={}, userId={}", 
                    event.getId(), event.getStatus(), userId);
        }
        
        // Если есть messageId, пытаемся обновить существующее сообщение
        if (event.getMessageId() != null) {
            log.debug("Попытка обновления существующего сообщения: eventId={}, messageId={}", 
                    event.getId(), event.getMessageId());
            
            boolean updated = telegramMessageService.tryEditMessageText(
                    chatId, 
                    event.getMessageId().intValue(), 
                    messageText, 
                    keyboard
            );
            
            if (updated) {
                log.info("Сообщение о событии успешно обновлено: eventId={}, messageId={}", 
                        event.getId(), event.getMessageId());
                return event;
            }
            
            // Если обновление не удалось, отправляем новое сообщение
            log.info("Не удалось обновить сообщение, отправляем новое: eventId={}, oldMessageId={}", 
                    event.getId(), event.getMessageId());
        } else {
            log.debug("MessageId отсутствует, отправляем новое сообщение: eventId={}", event.getId());
        }
        
        // Отправляем новое сообщение
        Message sentMessage = telegramMessageService.sendMessageAndGet(chatId, messageText, keyboard);
        
        // Сохраняем новый messageId
        Long oldMessageId = event.getMessageId();
        event.setMessageId((long) sentMessage.getMessageId());
        Event savedEvent = eventRepository.save(event);
        
        log.info("Новое сообщение о событии отправлено и messageId сохранён: eventId={}, oldMessageId={}, newMessageId={}", 
                event.getId(), oldMessageId, sentMessage.getMessageId());
        
        return savedEvent;
    }
}
