package ru.golubyatnikov.family.calendar.bot.service.domain.event;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;
import ru.golubyatnikov.family.calendar.bot.exception.EventNotFoundException;
import ru.golubyatnikov.family.calendar.bot.exception.InvalidDateException;
import ru.golubyatnikov.family.calendar.bot.exception.UnauthorizedAccessException;
import ru.golubyatnikov.family.calendar.bot.exception.UserNotFoundException;
import ru.golubyatnikov.family.calendar.bot.model.entity.Event;
import ru.golubyatnikov.family.calendar.bot.model.entity.User;
import ru.golubyatnikov.family.calendar.bot.model.enums.ActionType;
import ru.golubyatnikov.family.calendar.bot.repository.EventRepository;
import ru.golubyatnikov.family.calendar.bot.repository.UserRepository;
import ru.golubyatnikov.family.calendar.bot.service.domain.reminder.ReminderSchedulingService;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

/**
 * Сервис для операций записи событий.
 *
 * @author Golubyatnikov Aleksey
 * @since 2026-02-01
 */
@Service
@Validated
@Slf4j
@RequiredArgsConstructor
public class EventCommandService {
    
    private final EventRepository eventRepository;
    private final UserRepository userRepository;
    private final EventHistoryService eventHistoryService;
    private final ReminderSchedulingService reminderSchedulingService;
    private final ApplicationEventPublisher eventPublisher;

    /**
     * Доменное событие о создании события.
     */
    public record EventCreatedEvent(Event event) {}
    
    /**
     * Создает новое событие в календаре.
     * 
     * @param userId идентификатор пользователя
     * @param title название события
     * @param description описание события
     * @param eventDateTime дата и время события
     *
     * @return созданное событие
     */
    @Transactional
    @CacheEvict(value = {"upcomingEvents", "userEvents"}, allEntries = true)
    public Event createEvent(Long userId, String title, String description,LocalDateTime eventDateTime) {

        return createEvent(userId, title, description, eventDateTime, null, false);
    }
    
    /**
     * Создает новое событие в календаре с расширенными параметрами.
     * 
     * @param userId идентификатор пользователя
     * @param title название события
     * @param description описание события
     * @param eventDateTime дата и время начала события
     * @param endTime время окончания события
     * @param isPersonal флаг персонального события
     *
     * @return созданное событие
     */
    @Transactional
    @CacheEvict(value = {"upcomingEvents", "userEvents"}, allEntries = true)
    public Event createEvent(Long userId,
                             String title,
                             String description,
                             LocalDateTime eventDateTime,
                             LocalTime endTime,
                             Boolean isPersonal) {

        log.debug("Создание события для пользователя ID={}: title='{}', dateTime={}, endTime={}, isPersonal={}", 
                  userId, title, eventDateTime, endTime, isPersonal);
        
        User user = userRepository.findById(userId)
            .orElseThrow(() -> {
                log.error("Пользователь с ID={} не найден при создании события", userId);
                return new UserNotFoundException(userId);
            });
        
        if (eventDateTime.toLocalDate().isBefore(user.getCurrentDate())) {
            log.warn("Попытка создать событие с датой в прошлом: {} для пользователя ID={}", eventDateTime, userId);
            throw new InvalidDateException("Дата события не может быть в прошлом");
        }
        
        if (endTime != null && endTime.isBefore(eventDateTime.toLocalTime())) {
            log.warn("Попытка создать событие с временем окончания раньше времени начала: start={}, end={}", 
                     eventDateTime.toLocalTime(), endTime);

            throw new InvalidDateException("Время окончания не может быть раньше времени начала");
        }
        
        if (user.getFamily() == null) {
            log.error("Пользователь ID={} не принадлежит ни одной семье", userId);
            throw new IllegalStateException("Пользователь должен принадлежать семье для создания событий");
        }
        
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
        
        eventHistoryService.recordChange(
            savedEvent.getId(),
            userId,
            ActionType.CREATED,
            null,
            null,
            String.format("Событие '%s' создано", title)
        );

        publishEventCreated(savedEvent);
        return savedEvent;
    }
    
    /**
     * Обновляет название события.
     * 
     * @param eventId идентификатор события
     * @param userId идентификатор пользователя
     * @param newTitle новое название события
     *
     * @return обновленное событие
     */
    @Transactional
    @CacheEvict(value = {"upcomingEvents", "userEvents"}, allEntries = true)
    public Event updateEventTitle(Long eventId, Long userId, String newTitle) {
        log.debug("Обновление названия события ID={} пользователем ID={}", eventId, userId);
        
        Event event = eventRepository.findById(eventId)
            .orElseThrow(() -> new EventNotFoundException(eventId));

        checkEditPermission(event, userId);
        
        String oldTitle = event.getTitle();
        event.setTitle(newTitle);
        Event updated = eventRepository.save(event);
        
        log.info("Название события ID={} обновлено пользователем ID={}: '{}' → '{}'", 
                 eventId, userId, oldTitle, newTitle);
        
        eventHistoryService.recordChange(
            eventId,
            userId,
            ActionType.UPDATED,
            "title",
            oldTitle,
            newTitle
        );
        
        return updated;
    }
    
    /**
     * Обновляет дату события.
     * 
     * @param eventId идентификатор события
     * @param userId идентификатор пользователя
     * @param newDate новая дата события
     * @return обновленное событие
     */
    @Transactional
    @CacheEvict(value = {"upcomingEvents", "userEvents"}, allEntries = true)
    public Event updateEventDate(Long eventId, Long userId, @NonNull LocalDate newDate) {

        log.debug("Обновление даты события ID={} пользователем ID={}", eventId, userId);
        
        Event event = eventRepository.findById(eventId)
            .orElseThrow(() -> new EventNotFoundException(eventId));
        checkEditPermission(event, userId);
        
        if (newDate.isBefore(event.getUser().getCurrentDate())) {
            log.warn("Попытка установить дату в прошлом для события ID={}: {}", eventId, newDate);
            throw new InvalidDateException("Дата события не может быть в прошлом");
        }
        
        LocalDate oldDate = event.getEventDate();
        event.setEventDate(newDate);
        Event updated = eventRepository.save(event);
        
        log.info("Дата события ID={} обновлена пользователем ID={}: {} → {}", 
                 eventId, userId, oldDate, newDate);
        
        eventHistoryService.recordChange(
            eventId,
            userId,
            ActionType.UPDATED,
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
     * @param eventId идентификатор события
     * @param userId идентификатор пользователя
     * @param newTime новое время события
     *
     * @return обновленное событие
     */
    @Transactional
    @CacheEvict(value = {"upcomingEvents", "userEvents"}, allEntries = true)
    public Event updateEventTime(Long eventId, Long userId, LocalTime newTime) {

        log.debug("Обновление времени события ID={} пользователем ID={}", eventId, userId);
        
        Event event = eventRepository.findById(eventId)
            .orElseThrow(() -> new EventNotFoundException(eventId));

        checkEditPermission(event, userId);
        
        LocalTime oldTime = event.getEventTime();
        event.setEventTime(newTime);
        Event updated = eventRepository.save(event);
        
        log.info("Время события ID={} обновлено пользователем ID={}: {} → {}", 
                 eventId, userId, oldTime, newTime);
        
        eventHistoryService.recordChange(
            eventId,
            userId,
            ActionType.UPDATED,
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
     * @param eventId идентификатор события
     * @param userId идентификатор пользователя
     * @param newDescription новое описание события
     *
     * @return обновленное событие
     */
    @Transactional
    @CacheEvict(value = {"upcomingEvents", "userEvents"}, allEntries = true)
    public Event updateEventDescription(Long eventId, Long userId, String newDescription) {

        log.debug("Обновление описания события ID={} пользователем ID={}", eventId, userId);
        
        Event event = eventRepository.findById(eventId)
            .orElseThrow(() -> new EventNotFoundException(eventId));

        checkEditPermission(event, userId);
        
        String oldDescription = event.getDescription();
        event.setDescription(newDescription);
        Event updated = eventRepository.save(event);
        
        log.info("Описание события ID={} обновлено пользователем ID={}", eventId, userId);
        
        eventHistoryService.recordChange(
            eventId,
            userId,
            ActionType.UPDATED,
            "description",
            oldDescription,
            newDescription
        );
        
        return updated;
    }
    
    /**
     * Сохраняет событие в базе данных.
     *
     * @param event событие для сохранения
     */
    @Transactional
    @CacheEvict(value = {"upcomingEvents", "userEvents"}, allEntries = true)
    public void saveEvent(@NonNull Event event) {
        log.debug("Сохранение события ID={}", event.getId());
        Event savedEvent = eventRepository.save(event);
        log.debug("Событие ID={} успешно сохранено", savedEvent.getId());
    }
    
    /**
     * Проверяет права пользователя на редактирование события.
     * 
     * @param event событие для проверки
     * @param userId идентификатор пользователя
     *
     * @throws UnauthorizedAccessException если нет прав на редактирование
     */
    private void checkEditPermission(@NonNull Event event, Long userId) {
        if (event.getUser().getId().equals(userId)) {
            return;
        }
        
        if (!event.getIsPersonal() && event.getFamily() != null) {
            boolean isFromSameFamily = event.getFamily().getMembers().stream()
                .anyMatch(u -> u.getId().equals(userId));
            
            if (isFromSameFamily) {
                return;
            }
        }
        
        log.warn("Пользователь ID={} попытался отредактировать событие ID={} без прав доступа", 
                 userId, event.getId());
        throw new UnauthorizedAccessException(
            "У вас нет прав для редактирования этого события");
    }
    
    /**
     * Публикует доменное событие о создании события.
     * 
     * @param event созданное событие
     */
    private void publishEventCreated(Event event) {
        try {
            eventPublisher.publishEvent(new EventCreatedEvent(event));
            log.debug("Опубликовано доменное событие EventCreatedEvent для события ID={}", event.getId());

        } catch (Exception e) {
            log.error("Ошибка при публикации EventCreatedEvent для события ID={}: {}", event.getId(), e.getMessage(), e);
        }
    }
    
    /**
     * Обрабатывает изменение даты/времени события.
     * Пересчитывает напоминания для события.
     * 
     * @param eventId идентификатор события
     */
    @Transactional
    public void handleEventDateTimeChange(Long eventId) {
        log.info("Обработка изменения даты/времени события ID={}", eventId);
        
        try {
            reminderSchedulingService.recalculateReminders(eventId);
            log.info("Напоминания пересчитаны для события ID={}", eventId);

        } catch (Exception e) {
            log.error("Ошибка при пересчете напоминаний для события ID={}: {}", eventId, e.getMessage(), e);
        }
    }
}
