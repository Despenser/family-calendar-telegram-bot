package ru.golubyatnikov.family.calendar.bot.service.event;

import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;
import ru.golubyatnikov.family.calendar.bot.exception.EventNotFoundException;
import ru.golubyatnikov.family.calendar.bot.model.Event;
import ru.golubyatnikov.family.calendar.bot.model.EventFilter;
import ru.golubyatnikov.family.calendar.bot.model.User;
import ru.golubyatnikov.family.calendar.bot.repository.EventRepository;

import java.time.LocalDate;
import java.util.List;

/**
 * Сервис для операций чтения событий.
 * 
 * <p>Предоставляет методы только для чтения данных о событиях с поддержкой кэширования
 * для часто запрашиваемых данных. Все методы выполняются в read-only транзакциях
 * для оптимизации производительности.</p>
 * 
 * @author Family Calendar Bot Team
 * @since 2026-02-01
 */
@Service
@Validated
@Slf4j
@RequiredArgsConstructor
public class EventQueryService {
    
    private final EventRepository eventRepository;
    
    /**
     * Получает предстоящие активные события семьи на указанное количество дней.
     * 
     * <p>Результат кэшируется на 5 минут для оптимизации производительности.</p>
     * 
     * @param familyId идентификатор семьи
     * @param days количество дней для поиска событий
     * @param zoneId временная зона для определения текущей даты
     * @return список активных предстоящих событий
     */
    @Transactional(readOnly = true)
    @Cacheable(value = "upcomingEvents", key = "#familyId + '_' + #days + '_' + #zoneId")
    public List<Event> getUpcomingEvents(Long familyId, int days, java.time.ZoneId zoneId) {
        log.debug("Получение активных предстоящих событий для семьи ID={} на {} дней, timezone={}", 
                  familyId, days, zoneId);
        
        if (days <= 0) {
            throw new IllegalArgumentException("Количество дней должно быть больше 0");
        }
        
        LocalDate startDate = LocalDate.now(zoneId);
        LocalDate endDate = startDate.plusDays(days);
        
        List<Event> events = eventRepository.findByFamilyIdAndEventDateBetweenAndStatus(
            familyId, startDate, endDate, Event.EventStatus.ACTIVE);
        
        log.debug("Найдено {} активных предстоящих событий для семьи ID={}", events.size(), familyId);
        return events;
    }
    
    /**
     * Получает активные события пользователя.
     * 
     * <p>Результат кэшируется для оптимизации производительности.</p>
     * 
     * @param userId идентификатор пользователя
     * @return список активных событий пользователя
     */
    @Transactional(readOnly = true)
    @Cacheable(value = "userEvents", key = "#userId + '_ACTIVE'")
    public List<Event> getUserEvents(Long userId) {
        log.info("Получение активных событий пользователя ID={}", userId);
        
        List<Event> events = eventRepository.findByUserIdAndStatusOrderByEventDateAscEventTimeAsc(
            userId, 
            Event.EventStatus.ACTIVE
        );
        
        log.info("Найдено {} активных событий для пользователя ID={}", events.size(), userId);
        
        if (log.isDebugEnabled() && !events.isEmpty()) {
            log.debug("Список активных событий пользователя ID={}:", userId);
            events.forEach(event -> 
                log.debug("  - Событие ID={}, title='{}', status={}, date={}, time={}", 
                    event.getId(), event.getTitle(), event.getStatus(), 
                    event.getEventDate(), event.getEventTime())
            );
        }
        
        if (events.isEmpty()) {
            log.info("У пользователя ID={} нет активных событий", userId);
        }
        
        return events;
    }
    
    /**
     * Получает событие по его идентификатору.
     * 
     * @param eventId идентификатор события
     * @return событие с указанным ID
     * @throws EventNotFoundException если событие не найдено
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
     * Получает событие по его идентификатору с загрузкой напоминаний.
     * 
     * @param eventId идентификатор события
     * @return событие с указанным ID и загруженными напоминаниями
     * @throws EventNotFoundException если событие не найдено
     */
    @Transactional(readOnly = true)
    public Event getEventByIdWithReminders(Long eventId) {
        log.debug("Получение события по ID={} с напоминаниями", eventId);
        
        Event event = eventRepository.findByIdWithReminders(eventId)
            .orElseThrow(() -> {
                log.error("Событие с ID={} не найдено", eventId);
                return new EventNotFoundException(eventId);
            });
        
        log.debug("Событие ID={} успешно получено с {} напоминаниями: title='{}'", 
                 eventId, event.getReminders().size(), event.getTitle());
        return event;
    }
    
    /**
     * Получает отфильтрованные события пользователя по типу фильтра.
     * 
     * @param user пользователь
     * @param filter тип фильтра событий
     * @return список отфильтрованных событий
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
                events = eventRepository.findByFamilyIdAndStatusOrderByEventDateAscEventTimeAsc(
                    familyId, Event.EventStatus.ACTIVE);
                
                events = events.stream()
                    .filter(event -> !event.getIsPersonal() || event.belongsToUser(userId))
                    .toList();
                
                log.debug("Найдено {} событий (ALL) для пользователя ID={}", events.size(), userId);
                break;
                
            case FAMILY:
                events = eventRepository.findByFamilyIdAndIsPersonalAndStatusOrderByEventDateAscEventTimeAsc(
                    familyId, false, Event.EventStatus.ACTIVE);
                
                log.debug("Найдено {} семейных событий для пользователя ID={}", events.size(), userId);
                break;
                
            case PERSONAL:
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
     * @param userId идентификатор пользователя
     * @return количество активных событий
     */
    @Transactional(readOnly = true)
    public int getActiveEventsCount(Long userId) {
        int count = eventRepository.countByUserIdAndStatus(userId, Event.EventStatus.ACTIVE);
        log.debug("Подсчитано активных событий для пользователя ID={}: {}", userId, count);
        return count;
    }
    
    /**
     * Определяет, является ли дата события "сегодня" относительно timezone пользователя.
     * 
     * @param eventDate дата события
     * @param user пользователь для определения timezone
     * @return true если событие сегодня
     */
    public boolean isToday(LocalDate eventDate, User user) {
        LocalDate userToday = user.getCurrentDate();
        boolean result = eventDate.equals(userToday);
        
        log.debug("Проверка isToday: eventDate={}, userToday={}, timezone={}, result={}", 
                 eventDate, userToday, user.getTimezone(), result);
        
        return result;
    }
    
    /**
     * Определяет, является ли дата события "завтра" относительно timezone пользователя.
     * 
     * @param eventDate дата события
     * @param user пользователь для определения timezone
     * @return true если событие завтра
     */
    public boolean isTomorrow(LocalDate eventDate, User user) {
        LocalDate userTomorrow = user.getCurrentDate().plusDays(1);
        boolean result = eventDate.equals(userTomorrow);
        
        log.debug("Проверка isTomorrow: eventDate={}, userTomorrow={}, timezone={}, result={}", 
                 eventDate, userTomorrow, user.getTimezone(), result);
        
        return result;
    }
    
    /**
     * Получает активные события семьи на указанную дату.
     * 
     * @param familyId идентификатор семьи
     * @param date дата для поиска событий
     * @return список активных событий на указанную дату, отсортированный по времени
     */
    @Transactional(readOnly = true)
    public List<Event> getEventsByDate(Long familyId, LocalDate date) {
        log.debug("Получение активных событий для семьи ID={} на дату {}", familyId, date);
        
        if (familyId == null) {
            throw new IllegalArgumentException("familyId не может быть null");
        }
        if (date == null) {
            throw new IllegalArgumentException("date не может быть null");
        }
        
        List<Event> events = eventRepository.findByFamilyIdAndEventDateAndStatusOrderByEventTimeAsc(
            familyId, date, Event.EventStatus.ACTIVE);
        
        log.debug("Найдено {} активных событий для семьи ID={} на дату {}", 
                 events.size(), familyId, date);
        
        return events;
    }
    
    /**
     * Получает активные и завершенные события семьи на указанную дату.
     * Используется для просмотра событий в календаре, включая завершенные события в прошлом.
     * 
     * @param familyId идентификатор семьи
     * @param date дата для поиска событий
     * @return список активных и завершенных событий на указанную дату, отсортированный по времени
     */
    @Transactional(readOnly = true)
    public List<Event> getEventsByDateIncludingCompleted(Long familyId, LocalDate date) {
        log.debug("Получение активных и завершенных событий для семьи ID={} на дату {}", familyId, date);
        
        if (familyId == null) {
            throw new IllegalArgumentException("familyId не может быть null");
        }
        if (date == null) {
            throw new IllegalArgumentException("date не может быть null");
        }
        
        List<Event> events = eventRepository.findByFamilyIdAndEventDateIncludingCompleted(familyId, date);
        
        log.debug("Найдено {} активных и завершенных событий для семьи ID={} на дату {}", 
                 events.size(), familyId, date);
        
        return events;
    }
}
