package ru.golubyatnikov.family.calendar.bot.service.domain.event;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.golubyatnikov.family.calendar.bot.exception.EventNotFoundException;
import ru.golubyatnikov.family.calendar.bot.exception.InvalidDateException;
import ru.golubyatnikov.family.calendar.bot.exception.UnauthorizedAccessException;
import ru.golubyatnikov.family.calendar.bot.model.entity.Event;
import ru.golubyatnikov.family.calendar.bot.model.enums.ActionType;
import ru.golubyatnikov.family.calendar.bot.repository.EventRepository;
import ru.golubyatnikov.family.calendar.bot.service.domain.reminder.ReminderSchedulingService;

import java.time.LocalDate;
import java.time.LocalTime;

/**
 * Сервис для операций записи событий.
 *
 * @author Golubyatnikov Aleksey
 * @since 2026-02-01
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class EventCommandService {
    
    private final EventRepository eventRepository;
    private final EventHistoryService eventHistoryService;
    private final ReminderSchedulingService reminderSchedulingService;

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
    @CacheEvict(value = {"upcomingEvents", "userEvents", "myEventsPage"}, allEntries = true)
    public Event updateEventTitle(Long eventId, Long userId, String newTitle) {
        Event event = getEventAndCheckEditPermission(eventId, userId);

        String oldTitle = event.getTitle();
        event.setTitle(newTitle);
        Event updated = eventRepository.save(event);

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
     *
     * @return обновленное событие
     */
    @Transactional
    @CacheEvict(value = {"upcomingEvents", "userEvents", "myEventsPage"}, allEntries = true)
    public Event updateEventDate(Long eventId, Long userId, @NonNull LocalDate newDate) {
        Event event = getEventAndCheckEditPermission(eventId, userId);

        if (newDate.isBefore(event.getUser().getCurrentDate())) {
            throw new InvalidDateException("Дата события не может быть в прошлом");
        }

        LocalDate oldDate = event.getEventDate();
        event.setEventDate(newDate);
        Event updated = eventRepository.save(event);

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
    @CacheEvict(value = {"upcomingEvents", "userEvents", "myEventsPage"}, allEntries = true)
    public Event updateEventTime(Long eventId, Long userId, LocalTime newTime) {
        Event event = getEventAndCheckEditPermission(eventId, userId);

        LocalTime oldTime = event.getEventTime();
        event.setEventTime(newTime);
        Event updated = eventRepository.save(event);

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
    @CacheEvict(value = {"upcomingEvents", "userEvents", "myEventsPage"}, allEntries = true)
    public Event updateEventDescription(Long eventId, Long userId, String newDescription) {
        Event event = getEventAndCheckEditPermission(eventId, userId);

        String oldDescription = event.getDescription();
        event.setDescription(newDescription);
        Event updated = eventRepository.save(event);

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
    @CacheEvict(value = {"upcomingEvents", "userEvents", "myEventsPage"}, allEntries = true)
    public void saveEvent(@NonNull Event event) {
        eventRepository.save(event);
    }
    
    /**
     * Получает событие и проверяет права на его редактирование.
     * 
     * @param eventId идентификатор события
     * @param userId идентификатор пользователя
     * @return событие, готовое к редактированию
     *
     * @throws EventNotFoundException если событие не найдено
     * @throws UnauthorizedAccessException если нет прав на редактирование
     */
    private Event getEventAndCheckEditPermission(Long eventId, Long userId) {
        Event event = eventRepository.findById(eventId)
            .orElseThrow(() -> new EventNotFoundException(eventId));

        checkEditPermission(event, userId);
        return event;
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

        throw new UnauthorizedAccessException("У пользователя нет прав для редактирования этого события");
    }
    
    /**
     * Обрабатывает изменение даты/времени события.
     * Пересчитывает напоминания для события.
     * 
     * @param eventId идентификатор события
     */
    @Transactional
    public void handleEventDateTimeChange(Long eventId) {
        try {
            reminderSchedulingService.recalculateReminders(eventId);

        } catch (Exception e) {
            log.error("Ошибка при пересчете напоминаний для события ID={}: {}", eventId, e.getMessage(), e);
        }
    }
}
