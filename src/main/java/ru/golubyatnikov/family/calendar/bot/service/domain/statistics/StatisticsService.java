package ru.golubyatnikov.family.calendar.bot.service.domain.statistics;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.golubyatnikov.family.calendar.bot.model.dto.EventStatistics;
import ru.golubyatnikov.family.calendar.bot.model.enums.EventStatus;
import ru.golubyatnikov.family.calendar.bot.repository.EventRepository;
import java.time.LocalDate;
import java.time.YearMonth;

/**
 * Сервис для получения статистики по событиям.
 * Предоставляет функциональность для анализа и подсчета
 * различных метрик по событиям пользователя.
 *
 * @author Golubyatnikov Aleksey
 * @since 2026-01-16
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
     * @param familyId идентификатор семьи
     * @param userId идентификатор пользователя
     * @param year год
     * @param month месяц (1-12)
     *
     * @return статистика за месяц
     * @throws IllegalArgumentException если месяц некорректен
     */
    public EventStatistics getMonthlyStatistics(Long familyId, Long userId, int year, int month) {
        if (month < 1 || month > 12) {
            throw new IllegalArgumentException("Месяц должен быть от 1 до 12");
        }
        
        YearMonth yearMonth = YearMonth.of(year, month);
        LocalDate startDate = yearMonth.atDay(1);
        LocalDate endDate = yearMonth.atEndOfMonth();
        
        // Подсчет активных событий за месяц (исключая COMPLETED, DELETED, DRAFT)
        long activeEvents = eventRepository.countByFamilyIdAndEventDateBetweenAndStatus(
            familyId, startDate, endDate, EventStatus.ACTIVE
        );
        
        // Подсчет завершенных событий
        long completedEvents = eventRepository.countByFamilyIdAndEventDateBetweenAndStatus(
            familyId, startDate, endDate, EventStatus.COMPLETED
        );
        
        // Общее количество событий = активные + завершенные
        long totalEvents = activeEvents + completedEvents;
        
        // Подсчет семейных активных событий
        long familyEvents = eventRepository.countByUserIdAndEventDateBetweenAndIsPersonalAndStatus(
            userId, startDate, endDate, false, EventStatus.ACTIVE
        );
        
        // Подсчет персональных активных событий
        long personalEvents = eventRepository.countByUserIdAndEventDateBetweenAndIsPersonalAndStatus(
            userId, startDate, endDate, true, EventStatus.ACTIVE
        );

        return EventStatistics.builder()
            .userId(userId)
            .year(year)
            .month(month)
            .totalEvents(totalEvents)
            .activeEvents(activeEvents)
            .completedEvents(completedEvents)
            .familyEvents(familyEvents)
            .personalEvents(personalEvents)
            .build();
    }
}
