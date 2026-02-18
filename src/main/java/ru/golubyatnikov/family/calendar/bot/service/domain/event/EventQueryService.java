package ru.golubyatnikov.family.calendar.bot.service.domain.event;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.golubyatnikov.family.calendar.bot.exception.EventNotFoundException;
import ru.golubyatnikov.family.calendar.bot.model.entity.Event;
import ru.golubyatnikov.family.calendar.bot.model.entity.User;
import ru.golubyatnikov.family.calendar.bot.model.enums.EventFilter;
import ru.golubyatnikov.family.calendar.bot.model.enums.EventStatus;
import ru.golubyatnikov.family.calendar.bot.repository.EventRepository;
import java.time.LocalDate;
import java.util.List;

/**
 * Сервис для операций чтения событий.
 *
 * @author Golubyatnikov Aleksey
 * @since 2026-02-01
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class EventQueryService {
    
    private final EventRepository eventRepository;
    
    /**
     * Получает предстоящие активные события семьи на указанное количество дней.
     *
     * @param familyId идентификатор семьи
     * @param days количество дней для поиска событий
     * @param zoneId временная зона для определения текущей даты
     *
     * @return список активных предстоящих событий
     */
    @Transactional(readOnly = true)
    @Cacheable(value = "upcomingEvents", key = "#familyId + '_' + #days + '_' + #zoneId")
    public List<Event> getUpcomingEvents(Long familyId, int days, java.time.ZoneId zoneId) {
        if (days <= 0) {
            throw new IllegalArgumentException("Количество дней должно быть больше 0");
        }
        
        LocalDate startDate = LocalDate.now(zoneId);
        LocalDate endDate = startDate.plusDays(days);

        return eventRepository.findByFamilyIdAndEventDateBetweenAndStatus(
            familyId, startDate, endDate, EventStatus.ACTIVE);
    }
    
    /**
     * Получает активные события пользователя.
     *
     * @param userId идентификатор пользователя
     * @return список активных событий пользователя
     */
    @Transactional(readOnly = true)
    @Cacheable(value = "userEvents", key = "#userId + '_ACTIVE'")
    public List<Event> getUserEvents(Long userId) {
        return eventRepository.findByUserIdAndStatusOrderByEventDateAscEventTimeAsc(
            userId,
            EventStatus.ACTIVE
        );
    }
    
    /**
     * Получает событие по его идентификатору.
     * 
     * @param eventId идентификатор события
     *
     * @return событие с указанным ID
     * @throws EventNotFoundException если событие не найдено
     */
    @Transactional(readOnly = true)
    public Event getEventById(Long eventId) {
        return eventRepository.findById(eventId)
            .orElseThrow(() -> new EventNotFoundException(eventId));
    }

    
    /**
     * Получает отфильтрованные события пользователя по типу фильтра.
     * 
     * @param user пользователь
     * @param filter тип фильтра событий
     *
     * @return список отфильтрованных событий
     */
    @Transactional(readOnly = true)
    public List<Event> getFilteredEvents(@NonNull User user, @NonNull EventFilter filter) {
        if (user.getFamily() == null) {
            return List.of();
        }
        
        Long familyId = user.getFamily().getId();
        Long userId = user.getId();
        
        List<Event> events;

        switch (filter) {
            case ALL -> {
                events = eventRepository.findByFamilyIdAndStatusOrderByEventDateAscEventTimeAsc(
                        familyId, EventStatus.ACTIVE);

                events = events.stream()
                        .filter(event -> !event.getIsPersonal() || event.belongsToUser(userId))
                        .toList();
            }
            case FAMILY -> events = eventRepository.findByFamilyIdAndIsPersonalAndStatusOrderByEventDateAscEventTimeAsc(
                    familyId, false, EventStatus.ACTIVE);

            case PERSONAL -> events = eventRepository.findByUserIdAndIsPersonalAndStatusOrderByEventDateAscEventTimeAsc(
                    userId, true, EventStatus.ACTIVE);

            case COMPLETED -> {
                events = eventRepository.findByFamilyIdAndStatusOrderByEventDateAscEventTimeAsc(
                        familyId, EventStatus.COMPLETED);

                events = events.stream()
                        .filter(event -> !event.getIsPersonal() || event.belongsToUser(userId))
                        .toList();
            }

            default -> events = List.of();
        }


        return events;
    }
    
    /**
     * Получает количество активных событий пользователя.
     * 
     * @param userId идентификатор пользователя
     * @return количество активных событий
     */
    @Transactional(readOnly = true)
    public int getActiveEventsCount(Long userId) {
        return eventRepository.countByUserIdAndStatus(userId, EventStatus.ACTIVE);
    }
    
    /**
     * Получает активные события семьи на указанную дату.
     * 
     * @param familyId идентификатор семьи
     * @param date дата для поиска событий
     *
     * @return список активных событий на указанную дату, отсортированный по времени
     */
    @Transactional(readOnly = true)
    public List<Event> getEventsByDate(Long familyId, LocalDate date) {
        if (familyId == null) {
            throw new IllegalArgumentException("familyId не может быть null");
        }
        if (date == null) {
            throw new IllegalArgumentException("date не может быть null");
        }

        return eventRepository.findByFamilyIdAndEventDateAndStatusOrderByEventTimeAsc(
            familyId, date, EventStatus.ACTIVE);
    }
    
    /**
     * Получает активные и завершенные события семьи на указанную дату.
     * Используется для просмотра событий в календаре, включая завершенные события в прошлом.
     * 
     * @param familyId идентификатор семьи
     * @param date дата для поиска событий
     *
     * @return список активных и завершенных событий на указанную дату, отсортированный по времени
     */
    @Transactional(readOnly = true)
    public List<Event> getEventsByDateIncludingCompleted(Long familyId, LocalDate date) {
        if (familyId == null) {
            throw new IllegalArgumentException("familyId не может быть null");
        }
        if (date == null) {
            throw new IllegalArgumentException("date не может быть null");
        }

        return eventRepository.findByFamilyIdAndEventDateIncludingCompleted(familyId, date);
    }
}
