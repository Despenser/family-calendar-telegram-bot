package ru.golubyatnikov.family.calendar.bot.service.event;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;
import ru.golubyatnikov.family.calendar.bot.exception.EventNotFoundException;
import ru.golubyatnikov.family.calendar.bot.exception.InvalidDateException;
import ru.golubyatnikov.family.calendar.bot.exception.UnauthorizedAccessException;
import ru.golubyatnikov.family.calendar.bot.exception.UserNotFoundException;
import ru.golubyatnikov.family.calendar.bot.model.Event;
import ru.golubyatnikov.family.calendar.bot.model.EventHistory;
import ru.golubyatnikov.family.calendar.bot.model.User;
import ru.golubyatnikov.family.calendar.bot.repository.EventRepository;
import ru.golubyatnikov.family.calendar.bot.repository.UserRepository;
import ru.golubyatnikov.family.calendar.bot.service.reminder.ReminderService;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

/**
 * Сервис для операций записи событий.
 * 
 * <p>Предоставляет методы для создания и обновления событий с автоматической
 * инвалидацией кэша и публикацией доменных событий.</p>
 * 
 * @author Family Calendar Bot Team
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
    private final ReminderService reminderService;
    private final ApplicationEventPublisher eventPublisher;
    
    /**
     * Создает новое событие в календаре.
     * 
     * @param userId идентификатор пользователя
     * @param title название события
     * @param description описание события
     * @param eventDateTime дата и время события
     * @return созданное событие
     */
    @Transactional
    @CacheEvict(value = {"upcomingEvents", "userEvents"}, allEntries = true)
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
     * @param userId идентификатор пользователя
     * @param title название события
     * @param description описание события
     * @param eventDateTime дата и время начала события
     * @param endTime время окончания события
     * @param isPersonal флаг персонального события
     * @return созданное событие
     */
    @Transactional
    @CacheEvict(value = {"upcomingEvents", "userEvents"}, allEntries = true)
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
        
        User user = userRepository.findById(userId)
            .orElseThrow(() -> {
                log.error("Пользователь с ID={} не найден при создании события", userId);
                return new UserNotFoundException(userId);
            });
        
        if (eventDateTime.toLocalDate().isBefore(user.getCurrentDate())) {
            log.warn("Попытка создать событие с датой в прошлом: {} для пользователя ID={}", 
                     eventDateTime, userId);
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
            EventHistory.ActionType.CREATED,
            null,
            null,
            String.format("Событие '%s' создано", title)
        );
        
        // Публикация доменного события
        publishEventCreated(savedEvent);
        
        return savedEvent;
    }
    
    /**
     * Обновляет существующее событие.
     * 
     * @param eventId идентификатор события
     * @param userId идентификатор пользователя
     * @param title новое название события
     * @param description новое описание события
     * @param eventDateTime новая дата и время события
     * @return обновленное событие
     */
    @Transactional
    @CacheEvict(value = {"upcomingEvents", "userEvents"}, allEntries = true)
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
     * @param eventId идентификатор события
     * @param userId идентификатор пользователя
     * @param title новое название события
     * @param description новое описание события
     * @param eventDateTime новая дата и время начала события
     * @param endTime новое время окончания события
     * @return обновленное событие
     */
    @Transactional
    @CacheEvict(value = {"upcomingEvents", "userEvents"}, allEntries = true)
    public Event updateEvent(
            @NotNull(message = "eventId не может быть null") Long eventId, 
            @NotNull(message = "userId не может быть null") Long userId, 
            @NotBlank(message = "Название события не может быть пустым") 
            @Size(max = 255, message = "Название события не может превышать 255 символов") String title, 
            @Size(max = 2000, message = "Описание события не может превышать 2000 символов") String description, 
            @NotNull(message = "Дата и время события не могут быть null") LocalDateTime eventDateTime, 
            LocalTime endTime) {
        log.debug("Обновление события ID={} пользователем ID={}, endTime={}", eventId, userId, endTime);
        
        Event event = eventRepository.findById(eventId)
            .orElseThrow(() -> {
                log.error("Событие с ID={} не найдено при попытке обновления", eventId);
                return new EventNotFoundException(eventId);
            });
        
        checkEditPermission(event, userId);
        
        if (eventDateTime.toLocalDate().isBefore(event.getUser().getCurrentDate())) {
            log.warn("Попытка обновить событие ID={} с датой в прошлом: {}", eventId, eventDateTime);
            throw new InvalidDateException("Дата события не может быть в прошлом");
        }
        
        if (endTime != null && endTime.isBefore(eventDateTime.toLocalTime())) {
            log.warn("Попытка обновить событие ID={} с временем окончания раньше времени начала: start={}, end={}", 
                     eventId, eventDateTime.toLocalTime(), endTime);
            throw new InvalidDateException("Время окончания не может быть раньше времени начала");
        }
        
        // Сохранение старых значений для истории и доменного события
        Event previousState = Event.builder()
            .id(event.getId())
            .title(event.getTitle())
            .description(event.getDescription())
            .eventDate(event.getEventDate())
            .eventTime(event.getEventTime())
            .endTime(event.getEndTime())
            .build();
        
        String oldTitle = event.getTitle();
        String oldDescription = event.getDescription();
        LocalDate oldDate = event.getEventDate();
        LocalTime oldTime = event.getEventTime();
        LocalTime oldEndTime = event.getEndTime();
        
        event.setTitle(title);
        event.setDescription(description);
        event.setEventDate(eventDateTime.toLocalDate());
        event.setEventTime(eventDateTime.toLocalTime());
        event.setEndTime(endTime);
        
        Event updatedEvent = eventRepository.save(event);
        log.info("Событие ID={} успешно обновлено пользователем ID={}", eventId, userId);
        
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
        
        // Публикация доменного события
        publishEventUpdated(updatedEvent, previousState);
        
        return updatedEvent;
    }
    
    /**
     * Обновляет название события.
     * 
     * @param eventId идентификатор события
     * @param userId идентификатор пользователя
     * @param newTitle новое название события
     * @return обновленное событие
     */
    @Transactional
    @CacheEvict(value = {"upcomingEvents", "userEvents"}, allEntries = true)
    public Event updateEventTitle(
            @NotNull(message = "eventId не может быть null") Long eventId, 
            @NotNull(message = "userId не может быть null") Long userId, 
            @NotBlank(message = "Название события не может быть пустым") 
            @Size(max = 255, message = "Название события не может превышать 255 символов") String newTitle) {
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
     * @param eventId идентификатор события
     * @param userId идентификатор пользователя
     * @param newDate новая дата события
     * @return обновленное событие
     */
    @Transactional
    @CacheEvict(value = {"upcomingEvents", "userEvents"}, allEntries = true)
    public Event updateEventDate(
            @NotNull(message = "eventId не может быть null") Long eventId, 
            @NotNull(message = "userId не может быть null") Long userId, 
            @NotNull(message = "Дата события не может быть null") LocalDate newDate) {
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
     * @param eventId идентификатор события
     * @param userId идентификатор пользователя
     * @param newTime новое время события
     * @return обновленное событие
     */
    @Transactional
    @CacheEvict(value = {"upcomingEvents", "userEvents"}, allEntries = true)
    public Event updateEventTime(
            @NotNull(message = "eventId не может быть null") Long eventId, 
            @NotNull(message = "userId не может быть null") Long userId, 
            @NotNull(message = "Время события не может быть null") LocalTime newTime) {
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
     * @param eventId идентификатор события
     * @param userId идентификатор пользователя
     * @param newDescription новое описание события
     * @return обновленное событие
     */
    @Transactional
    @CacheEvict(value = {"upcomingEvents", "userEvents"}, allEntries = true)
    public Event updateEventDescription(
            @NotNull(message = "eventId не может быть null") Long eventId, 
            @NotNull(message = "userId не может быть null") Long userId, 
            @Size(max = 2000, message = "Описание события не может превышать 2000 символов") String newDescription) {
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
            EventHistory.ActionType.UPDATED,
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
     * @return сохраненное событие
     */
    @Transactional
    @CacheEvict(value = {"upcomingEvents", "userEvents"}, allEntries = true)
    public Event saveEvent(@NotNull(message = "event не может быть null") Event event) {
        log.debug("Сохранение события ID={}", event.getId());
        Event savedEvent = eventRepository.save(event);
        log.debug("Событие ID={} успешно сохранено", savedEvent.getId());
        return savedEvent;
    }
    
    /**
     * Проверяет права пользователя на редактирование события.
     * 
     * @param event событие для проверки
     * @param userId идентификатор пользователя
     * @throws UnauthorizedAccessException если нет прав на редактирование
     */
    private void checkEditPermission(Event event, Long userId) {
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
            log.error("Ошибка при публикации EventCreatedEvent для события ID={}: {}", 
                     event.getId(), e.getMessage(), e);
        }
    }
    
    /**
     * Публикует доменное событие об обновлении события.
     * 
     * @param event обновленное событие
     * @param previousState предыдущее состояние события
     */
    private void publishEventUpdated(Event event, Event previousState) {
        try {
            eventPublisher.publishEvent(new EventUpdatedEvent(event, previousState));
            log.debug("Опубликовано доменное событие EventUpdatedEvent для события ID={}", event.getId());
        } catch (Exception e) {
            log.error("Ошибка при публикации EventUpdatedEvent для события ID={}: {}", 
                     event.getId(), e.getMessage(), e);
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
            reminderService.recalculateReminders(eventId);
            log.info("Напоминания пересчитаны для события ID={}", eventId);
        } catch (Exception e) {
            log.error("Ошибка при пересчете напоминаний для события ID={}: {}", 
                     eventId, e.getMessage(), e);
        }
    }
    
    /**
     * Доменное событие о создании события.
     */
    public record EventCreatedEvent(Event event) {}
    
    /**
     * Доменное событие об обновлении события.
     */
    public record EventUpdatedEvent(Event event, Event previousState) {}
}
