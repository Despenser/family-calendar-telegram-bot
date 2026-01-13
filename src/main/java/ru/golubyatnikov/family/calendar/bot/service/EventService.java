package ru.golubyatnikov.family.calendar.bot.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.golubyatnikov.family.calendar.bot.exception.EventNotFoundException;
import ru.golubyatnikov.family.calendar.bot.exception.InvalidDateException;
import ru.golubyatnikov.family.calendar.bot.exception.UnauthorizedAccessException;
import ru.golubyatnikov.family.calendar.bot.exception.UserNotFoundException;
import ru.golubyatnikov.family.calendar.bot.model.Event;
import ru.golubyatnikov.family.calendar.bot.model.EventHistory;
import ru.golubyatnikov.family.calendar.bot.model.User;
import ru.golubyatnikov.family.calendar.bot.repository.EventRepository;
import ru.golubyatnikov.family.calendar.bot.repository.UserRepository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

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
 * целостности данных. Сервис выполняет валидацию входных данных и проверку
 * прав доступа перед выполнением операций.</p>
 * 
 * <p><b>Требования:</b> 4.1, 4.2, 4.3, 5.1, 5.4, 7.1, 7.2, 7.3, 7.4, 7.5</p>
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
@RequiredArgsConstructor
@Slf4j
public class EventService {
    
    private final EventRepository eventRepository;
    private final UserRepository userRepository;
    private final EventHistoryService eventHistoryService;
    private final ReminderService reminderService;
    
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
     * @param title название события (обязательное)
     * @param description описание события (может быть null)
     * @param eventDateTime дата и время события
     * @return созданное и сохраненное событие
     * @throws UserNotFoundException если пользователь с указанным ID не найден
     * @throws InvalidDateException если дата события находится в прошлом
     * @throws IllegalArgumentException если title пустой или null
     */
    public Event createEvent(Long userId, String title, String description, LocalDateTime eventDateTime) {
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
     * @param title название события (обязательное)
     * @param description описание события (может быть null)
     * @param eventDateTime дата и время начала события
     * @param endTime время окончания события (может быть null)
     * @param isPersonal флаг персонального события (true - видно только создателю)
     * @return созданное и сохраненное событие
     * @throws UserNotFoundException если пользователь с указанным ID не найден
     * @throws InvalidDateException если дата события находится в прошлом или endTime раньше eventTime
     * @throws IllegalArgumentException если title пустой или null
     */
    public Event createEvent(Long userId, String title, String description, 
                            LocalDateTime eventDateTime, LocalTime endTime, Boolean isPersonal) {
        log.debug("Создание события для пользователя ID={}: title='{}', dateTime={}, endTime={}, isPersonal={}", 
                  userId, title, eventDateTime, endTime, isPersonal);
        
        // Валидация входных параметров
        if (title == null || title.isBlank()) {
            log.warn("Попытка создать событие с пустым названием для пользователя ID={}", userId);
            throw new IllegalArgumentException("Название события не может быть пустым");
        }
        
        // Валидация даты - событие не должно быть в прошлом
        if (eventDateTime.isBefore(LocalDateTime.now())) {
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
     * Получает предстоящие события семьи на указанное количество дней.
     * 
     * <p>Метод возвращает все события семьи, которые запланированы в диапазоне
     * от текущей даты до указанного количества дней в будущем. События
     * автоматически сортируются по дате и времени.</p>
     * 
     * <p><b>Требования:</b> 5.1, 5.4</p>
     * 
     * @param familyId идентификатор семьи
     * @param days количество дней для поиска событий (от текущей даты)
     * @return список предстоящих событий, отсортированный по дате и времени
     * @throws IllegalArgumentException если days меньше или равно 0
     */
    @Transactional(readOnly = true)
    public List<Event> getUpcomingEvents(Long familyId, int days) {
        log.debug("Получение предстоящих событий для семьи ID={} на {} дней", familyId, days);
        
        if (days <= 0) {
            throw new IllegalArgumentException("Количество дней должно быть больше 0");
        }
        
        LocalDate startDate = LocalDate.now();
        LocalDate endDate = startDate.plusDays(days);
        
        List<Event> events = eventRepository.findByFamilyIdAndEventDateBetween(
            familyId, startDate, endDate);
        
        log.debug("Найдено {} предстоящих событий для семьи ID={}", events.size(), familyId);
        return events;
    }
    
    /**
     * Получает все события пользователя, отсортированные по дате.
     * 
     * <p>Метод возвращает все события, созданные указанным пользователем,
     * отсортированные по дате в порядке возрастания (от ближайших к более поздним).</p>
     * 
     * <p><b>Требования:</b> 7.1</p>
     * 
     * @param userId идентификатор пользователя
     * @return список событий пользователя, отсортированный по дате
     */
    @Transactional(readOnly = true)
    public List<Event> getUserEvents(Long userId) {
        log.debug("Получение событий пользователя ID={}", userId);
        
        List<Event> events = eventRepository.findByUserIdOrderByEventDateAsc(userId);
        
        log.debug("Найдено {} событий для пользователя ID={}", events.size(), userId);
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
     * @param title новое название события (обязательное)
     * @param description новое описание события (может быть null)
     * @param eventDateTime новая дата и время события
     * @return обновленное событие
     * @throws EventNotFoundException если событие с указанным ID не найдено
     * @throws UnauthorizedAccessException если пользователь не является создателем события
     * @throws InvalidDateException если новая дата находится в прошлом
     * @throws IllegalArgumentException если title пустой или null
     */
    public Event updateEvent(Long eventId, Long userId, String title, 
                            String description, LocalDateTime eventDateTime) {
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
     * @param title новое название события (обязательное)
     * @param description новое описание события (может быть null)
     * @param eventDateTime новая дата и время начала события
     * @param endTime новое время окончания события (может быть null)
     * @return обновленное событие
     * @throws EventNotFoundException если событие с указанным ID не найдено
     * @throws UnauthorizedAccessException если пользователь не является создателем события
     * @throws InvalidDateException если новая дата находится в прошлом или endTime раньше eventTime
     * @throws IllegalArgumentException если title пустой или null
     */
    public Event updateEvent(Long eventId, Long userId, String title, 
                            String description, LocalDateTime eventDateTime, LocalTime endTime) {
        log.debug("Обновление события ID={} пользователем ID={}, endTime={}", eventId, userId, endTime);
        
        // Валидация входных параметров
        if (title == null || title.isBlank()) {
            log.warn("Попытка обновить событие ID={} с пустым названием", eventId);
            throw new IllegalArgumentException("Название события не может быть пустым");
        }
        
        // Валидация даты
        if (eventDateTime.isBefore(LocalDateTime.now())) {
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
     *   <li>Изменяет статус события на DELETED</li>
     *   <li>Устанавливает дату удаления</li>
     *   <li>Записывает действие в историю</li>
     * </ol>
     * 
     * <p>Событие хранится в корзине 30 дней, после чего автоматически удаляется.</p>
     * 
     * <p><b>Требования:</b> 7.3, 7.5, 19.1, 18.5</p>
     * 
     * @param eventId идентификатор события для удаления
     * @param userId идентификатор пользователя, выполняющего удаление
     * @throws EventNotFoundException если событие с указанным ID не найдено
     * @throws UnauthorizedAccessException если пользователь не является создателем события
     */
    public void deleteEvent(Long eventId, Long userId) {
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
        
        // Перемещение в корзину
        event.setStatus(Event.EventStatus.DELETED);
        event.setDeletedAt(LocalDateTime.now());
        eventRepository.save(event);
        
        log.info("Событие ID={} успешно перемещено в корзину пользователем ID={}", eventId, userId);
        
        // Запись в историю изменений
        eventHistoryService.recordDeletion(eventId, userId);
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
}
