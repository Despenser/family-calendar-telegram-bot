package ru.golubyatnikov.family.calendar.bot.service.myevents;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.golubyatnikov.family.calendar.bot.model.Event;
import ru.golubyatnikov.family.calendar.bot.service.EventService;

import java.util.List;
import java.util.Optional;

/**
 * Сервис для получения данных о событиях пользователя.
 * 
 * <p>Отвечает за запросы к базе данных для получения списка событий
 * и поиска событий с определенными флагами.</p>
 * 
 * @author Family Calendar Bot Team
 * @since 2026-02-03
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class MyEventsQueryService {

    private final EventService eventService;

    /**
     * Получает список всех активных событий пользователя.
     * 
     * @param userId идентификатор пользователя
     * @return список событий пользователя
     */
    @Transactional(readOnly = true)
    public List<Event> getUserEvents(Long userId) {
        log.debug("Получение событий для пользователя ID={}", userId);
        return eventService.getUserEvents(userId);
    }

    /**
     * Находит событие с флагом isMyEventsHeader для пользователя.
     * 
     * @param userEvents список событий пользователя
     * @return событие с флагом шапки или null
     */
    public Event findHeaderEvent(List<Event> userEvents) {
        return userEvents.stream()
                .filter(e -> Boolean.TRUE.equals(e.getIsMyEventsHeader()))
                .findFirst()
                .orElse(null);
    }

    /**
     * Получает первое событие из списка.
     * 
     * @param userEvents список событий
     * @return первое событие или null если список пуст
     */
    public Event getFirstEvent(List<Event> userEvents) {
        return userEvents.isEmpty() ? null : userEvents.get(0);
    }

    /**
     * Получает количество событий пользователя.
     * 
     * @param userEvents список событий
     * @return количество событий
     */
    public int getEventCount(List<Event> userEvents) {
        return userEvents.size();
    }

    /**
     * Проверяет, пуст ли список событий.
     * 
     * @param userEvents список событий
     * @return true если список пуст
     */
    public boolean isEmpty(List<Event> userEvents) {
        return userEvents.isEmpty();
    }

    /**
     * Получает событие по идентификатору.
     * 
     * @param eventId идентификатор события
     * @return событие
     */
    @Transactional(readOnly = true)
    public Event getEventById(Long eventId) {
        log.debug("Получение события ID={}", eventId);
        return eventService.getEventById(eventId);
    }

    /**
     * Проверяет, может ли пользователь редактировать событие.
     * 
     * <p>Пользователь может редактировать событие, если:</p>
     * <ul>
     *   <li>Он создатель события</li>
     *   <li>Событие семейное и пользователь из той же семьи</li>
     * </ul>
     * 
     * @param event событие для проверки
     * @param userId идентификатор пользователя
     * @return true если пользователь может редактировать событие
     */
    public boolean canUserEditEvent(Event event, Long userId) {
        // Пользователь может редактировать событие, если:
        // 1. Он создатель события
        if (event.getUser().getId().equals(userId)) {
            return true;
        }
        
        // 2. Событие семейное и пользователь из той же семьи
        if (!event.getIsPersonal() && event.getFamily() != null) {
            return event.getFamily().getMembers().stream()
                    .anyMatch(u -> u.getId().equals(userId));
        }
        
        return false;
    }

    /**
     * Проверяет, может ли пользователь просматривать событие.
     * 
     * @param event событие для проверки
     * @param userId идентификатор пользователя
     * @return true если пользователь может просматривать событие
     */
    public boolean canUserViewEvent(Event event, Long userId) {
        // Проверяем права доступа
        if (event.getIsPersonal() && !event.getUser().getId().equals(userId)) {
            return false;
        }
        return true;
    }
}
