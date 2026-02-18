package ru.golubyatnikov.family.calendar.bot.service.domain.planner;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.golubyatnikov.family.calendar.bot.model.entity.Event;
import ru.golubyatnikov.family.calendar.bot.service.domain.event.EventService;
import java.util.List;

/**
 * Сервис для получения данных планировщика событий.
 *
 * @author Golubyatnikov Aleksey
 * @since 2026-02-12
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PlannerQueryService {

    private final EventService eventService;

    /**
     * Получает список всех активных событий пользователя.
     * 
     * @param userId идентификатор пользователя
     * @return список событий пользователя
     */
    @Transactional(readOnly = true)
    public List<Event> getUserEvents(Long userId) {
        return eventService.getUserEvents(userId);
    }

    /**
     * Получает первое событие из списка.
     * 
     * @param userEvents список событий
     * @return первое событие или null, если список пуст
     */
    public Event getFirstEvent(@NonNull List<Event> userEvents) {
        return userEvents.isEmpty() ? null : userEvents.getFirst();
    }

    /**
     * Проверяет, пуст ли список событий.
     * 
     * @param userEvents список событий
     * @return true, если список пуст
     */
    public boolean isEmpty(@NonNull List<Event> userEvents) {
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
        return eventService.getEventById(eventId);
    }
}
