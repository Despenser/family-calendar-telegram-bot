package ru.golubyatnikov.family.calendar.bot.model.context;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

/**
 * Класс для хранения статистики по событиям.
 *
 * @author Golubyatnikov Aleksey
 * @since 2026-02-12
 */
@Data
@Builder
@AllArgsConstructor
public class EventStatistics {
    /**
     * Идентификатор пользователя
     */
    private Long userId;
    
    /**
     * Год статистики
     */
    private int year;
    
    /**
     * Месяц статистики (1-12)
     */
    private int month;
    
    /**
     * Общее количество событий за период (активные + завершенные)
     */
    private long totalEvents;
    
    /**
     * Количество активных событий
     */
    private long activeEvents;
    
    /**
     * Количество завершенных событий
     */
    private long completedEvents;
    
    /**
     * Количество семейных событий
     */
    private long familyEvents;
    
    /**
     * Количество персональных событий
     */
    private long personalEvents;
    
    /**
     * Количество повторяющихся событий
     */
    private long recurringEvents;
    
    /**
     * Возвращает процент завершенных событий.
     * Рассчитывается как отношение завершенных событий к сумме активных и завершенных событий.
     * Исключает из расчета события со статусами DELETED и DRAFT.
     * 
     * @return процент завершенных событий (0-100)
     */
    public double getCompletionRate() {
        long totalRelevantEvents = activeEvents + completedEvents;
        if (totalRelevantEvents == 0) {
            return 0.0;
        }
        return (completedEvents * 100.0) / totalRelevantEvents;
    }
    
    /**
     * Возвращает процент семейных событий.
     * 
     * @return процент семейных событий (0-100)
     */
    public double getFamilyEventsRate() {
        if (totalEvents == 0) {
            return 0.0;
        }
        return (familyEvents * 100.0) / totalEvents;
    }
    
    /**
     * Возвращает процент персональных событий.
     * 
     * @return процент персональных событий (0-100)
     */
    public double getPersonalEventsRate() {
        if (totalEvents == 0) {
            return 0.0;
        }
        return (personalEvents * 100.0) / totalEvents;
    }
}
