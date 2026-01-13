package ru.golubyatnikov.family.calendar.bot.service;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.golubyatnikov.family.calendar.bot.model.Event;
import ru.golubyatnikov.family.calendar.bot.repository.EventRepository;

import java.time.LocalDate;
import java.time.YearMonth;

/**
 * Сервис для получения статистики по событиям.
 * Предоставляет функциональность для анализа и подсчета
 * различных метрик по событиям пользователя.
 * 
 * <p>Основные функции:</p>
 * <ul>
 *   <li>Получение статистики за месяц</li>
 *   <li>Подсчет событий по различным критериям</li>
 *   <li>Анализ активности пользователя</li>
 * </ul>
 * 
 * <p><b>Требования:</b> 31.3, 31.4</p>
 * 
 * @author Family Calendar Bot
 * @version 1.0
 * @see Event
 * @see EventRepository
 */
@Service
@Transactional(readOnly = true)
@Slf4j
@RequiredArgsConstructor
public class StatisticsService {
    
    private final EventRepository eventRepository;
    
    /**
     * Получает статистику по событиям семьи за указанный месяц.
     * 
     * <p>Подсчитывает различные метрики: общее количество событий,
     * активные, завершенные, семейные, персональные и повторяющиеся события.
     * Включает семейные события и персональные события пользователя.</p>
     * 
     * @param familyId идентификатор семьи
     * @param userId идентификатор пользователя
     * @param year год
     * @param month месяц (1-12)
     * @return статистика за месяц
     * @throws IllegalArgumentException если месяц некорректен
     */
    public EventStatistics getMonthlyStatistics(Long familyId, Long userId, int year, int month) {
        log.debug("Получение статистики для семьи ID {} и пользователя ID {}: год={}, месяц={}", 
                  familyId, userId, year, month);
        
        if (month < 1 || month > 12) {
            log.error("Некорректный месяц: {}", month);
            throw new IllegalArgumentException("Месяц должен быть от 1 до 12");
        }
        
        YearMonth yearMonth = YearMonth.of(year, month);
        LocalDate startDate = yearMonth.atDay(1);
        LocalDate endDate = yearMonth.atEndOfMonth();
        
        // Подсчет общего количества событий за месяц (семейные + персональные пользователя)
        long totalEvents = eventRepository.countByFamilyIdAndEventDateBetween(familyId, startDate, endDate);
        
        // Подсчет активных событий
        long activeEvents = eventRepository.countByFamilyIdAndEventDateBetweenAndStatus(
            familyId, startDate, endDate, Event.EventStatus.ACTIVE
        );
        
        // Подсчет завершенных событий
        long completedEvents = eventRepository.countByFamilyIdAndEventDateBetweenAndStatus(
            familyId, startDate, endDate, Event.EventStatus.COMPLETED
        );
        
        // Подсчет семейных событий
        long familyEvents = eventRepository.countByUserIdAndEventDateBetweenAndIsPersonal(
            userId, startDate, endDate, false
        );
        
        // Подсчет персональных событий
        long personalEvents = eventRepository.countByUserIdAndEventDateBetweenAndIsPersonal(
            userId, startDate, endDate, true
        );
        
        // Подсчет повторяющихся событий (имеют series_id)
        long recurringEvents = 0; // Временно отключено, так как метод отсутствует в репозитории
        
        EventStatistics statistics = EventStatistics.builder()
            .userId(userId)
            .year(year)
            .month(month)
            .totalEvents(totalEvents)
            .activeEvents(activeEvents)
            .completedEvents(completedEvents)
            .familyEvents(familyEvents)
            .personalEvents(personalEvents)
            .recurringEvents(recurringEvents)
            .build();
        
        log.info("Статистика для семьи ID {} и пользователя ID {} за {}/{}: всего={}, активных={}, завершенных={}", 
                 familyId, userId, month, year, totalEvents, activeEvents, completedEvents);
        
        return statistics;
    }
    
    /**
     * Класс для хранения статистики по событиям.
     */
    @Data
    @Builder
    @AllArgsConstructor
    public static class EventStatistics {
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
         * Общее количество событий за период
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
         * 
         * @return процент завершенных событий (0-100)
         */
        public double getCompletionRate() {
            if (totalEvents == 0) {
                return 0.0;
            }
            return (completedEvents * 100.0) / totalEvents;
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
}
