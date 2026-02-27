package ru.golubyatnikov.family.calendar.bot.service.domain.myevents;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.golubyatnikov.family.calendar.bot.config.MyEventsConfig;
import ru.golubyatnikov.family.calendar.bot.model.entity.Event;
import ru.golubyatnikov.family.calendar.bot.model.enums.EventStatus;
import ru.golubyatnikov.family.calendar.bot.repository.EventRepository;

/**
 * Сервис для работы с постраничным списком событий пользователя.
 *
 * @author Golubyatnikov Aleksey
 * @since 2026-02-26
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class MyEventsPageService {
    
    private final EventRepository eventRepository;
    private final MyEventsConfig config;
    
    /**
     * Получает страницу активных событий пользователя.
     * 
     * @param userId идентификатор пользователя
     * @param page номер страницы (начиная с 0)
     * @param size размер страницы
     *
     * @return страница событий
     */
    @Transactional(readOnly = true)
    @Cacheable(value = "myEventsPage", key = "#userId + '_' + #page + '_' + #size")
    public Page<Event> getEventsPage(Long userId, int page, int size) {
        if (userId == null) {
            throw new IllegalArgumentException("userId не может быть null");
        }
        
        if (page < 0) {
            throw new IllegalArgumentException("Номер страницы не может быть отрицательным");
        }
        
        if (size <= 0) {
            throw new IllegalArgumentException("Размер страницы должен быть больше 0");
        }
        
        Pageable pageable = PageRequest.of(page, size);
        
        log.debug("Получение страницы {} событий пользователя ID={}, размер страницы: {}", 
                page, userId, size);
        
        return eventRepository.findByUserIdAndStatusOrderByEventDateAscEventTimeAsc(
                userId, EventStatus.ACTIVE, pageable);
    }
    
    /**
     * Получает страницу активных событий пользователя с размером по умолчанию.
     * 
     * @param userId идентификатор пользователя
     * @param page номер страницы (начиная с 0)
     *
     * @return страница событий
     */
    @Transactional(readOnly = true)
    public Page<Event> getEventsPage(Long userId, int page) {
        return getEventsPage(userId, page, config.getPageSize());
    }
}
