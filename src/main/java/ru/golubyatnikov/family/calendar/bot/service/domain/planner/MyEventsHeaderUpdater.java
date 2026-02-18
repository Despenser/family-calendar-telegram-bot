package ru.golubyatnikov.family.calendar.bot.service.domain.planner;

/**
 * Интерфейс для обновления счетчика в шапке "Мои события".
 *
 * @author Golubyatnikov Aleksey
 * @since 2026-02-12
 */
public interface MyEventsHeaderUpdater {
    
    /**
     * Обновляет счетчик событий в шапке "Мои события".
     * 
     * @param userId идентификатор пользователя
     */
    void updateMyEventsHeaderCount(Long userId);
}
