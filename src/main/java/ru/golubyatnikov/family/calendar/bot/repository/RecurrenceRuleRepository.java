package ru.golubyatnikov.family.calendar.bot.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import ru.golubyatnikov.family.calendar.bot.model.RecurrenceRule;
import java.util.Optional;

/**
 * Repository интерфейс для работы с правилами повторения событий.
 * Предоставляет методы для CRUD операций и поиска правил повторения.
 *
 * @author Golubyatnikov Aleksey
 * @version 1.0.0
 * @since 2026-01-16
 * @see RecurrenceRule
 */
@Repository
public interface RecurrenceRuleRepository extends JpaRepository<RecurrenceRule, Long> {
    
    /**
     * Находит правило повторения по идентификатору серии событий.
     * 
     * @param seriesId UUID серии повторяющихся событий
     * @return Optional с правилом повторения, если найдено
     */
    Optional<RecurrenceRule> findBySeriesId(String seriesId);
}
