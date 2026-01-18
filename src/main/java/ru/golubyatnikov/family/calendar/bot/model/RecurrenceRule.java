package ru.golubyatnikov.family.calendar.bot.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDate;

/**
 * Entity класс для правил повторения событий.
 * Хранит настройки повторяющихся событий (ежедневно, еженедельно, ежемесячно).
 *
 * @author Golubyatnikov Aleksey
 * @version 1.0.0
 * @since 2026-01-16
 * @see Event
 */
@Entity
@Table(name = "recurrence_rules")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RecurrenceRule {
    
    /**
     * Уникальный идентификатор правила повторения
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    /**
     * UUID серии событий (связь с events.series_id)
     */
    @Column(name = "series_id", nullable = false, unique = true)
    private String seriesId;
    
    /**
     * Частота повторения: DAILY, WEEKLY, MONTHLY
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "frequency", nullable = false)
    private Frequency frequency;
    
    /**
     * Интервал повторения: каждые N дней/недель/месяцев (по умолчанию 1)
     */
    @Column(name = "interval", nullable = false)
    @Builder.Default
    private Integer interval = 1;
    
    /**
     * Дни недели для еженедельного повторения в формате "1,3,5" (1=Пн, 7=Вс)
     */
    @Column(name = "days_of_week")
    private String daysOfWeek;
    
    /**
     * Дата окончания повторений (опционально, если не указано occurrences)
     */
    @Column(name = "end_date")
    private LocalDate endDate;
    
    /**
     * Количество повторений (опционально, если не указано end_date)
     */
    @Column(name = "occurrences")
    private Integer occurrences;
    
    /**
     * Исключенные даты в формате "2025-01-15,2025-02-20" (даты, когда событие не создается)
     */
    @Column(name = "exceptions")
    private String exceptions;
    
    /**
     * ENUM для частоты повторения событий
     */
    public enum Frequency {
        /**
         * Ежедневное повторение
         */
        DAILY,
        
        /**
         * Еженедельное повторение
         */
        WEEKLY,
        
        /**
         * Ежемесячное повторение
         */
        MONTHLY
    }
}
