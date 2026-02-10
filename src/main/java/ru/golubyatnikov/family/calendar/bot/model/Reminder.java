package ru.golubyatnikov.family.calendar.bot.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

/**
 * Entity класс для напоминаний о событиях.
 * Хранит настройки гибких напоминаний с различными типами времени отправки.
 *
 * @author Golubyatnikov Aleksey
 * @version 1.0.0
 * @see Event
 */
@Entity
@Table(name = "reminders")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Reminder {
    
    /**
     * Уникальный идентификатор напоминания
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    /**
     * Событие, для которого настроено напоминание
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "event_id", nullable = false)
    private Event event;
    
    /**
     * Тип напоминания: MORNING_OF_DAY, EVENING_BEFORE, ONE_HOUR_BEFORE, TEN_MINUTES_BEFORE, CUSTOM
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "reminder_type", nullable = false, length = 50)
    private ReminderType reminderType;
    
    /**
     * Количество минут до события для CUSTOM типа (обязательно для custom, NULL для остальных)
     */
    @Column(name = "custom_minutes")
    private Integer customMinutes;
    
    /**
     * Рассчитанное время отправки напоминания в UTC.
     */
    @Column(name = "reminder_time", nullable = false)
    private LocalDateTime reminderTime;
    
    /**
     * Флаг отправки напоминания: true - отправлено, false - ожидает отправки
     */
    @Column(name = "sent", nullable = false)
    @Builder.Default
    private Boolean sent = false;
    
    /**
     * Дата и время фактической отправки напоминания в UTC
     */
    @Column(name = "sent_at")
    private LocalDateTime sentAt;
    
    /**
     * ENUM для типов напоминаний
     */
    public enum ReminderType {
        /**
         * Утром в день события (9:00)
         */
        MORNING_OF_DAY,
        
        /**
         * Вечером накануне (20:00)
         */
        EVENING_BEFORE,
        
        /**
         * За 1 час до события
         */
        ONE_HOUR_BEFORE,
        
        /**
         * За 15 минут до события
         */
        FIFTEEN_MINUTES_BEFORE,
        
        /**
         * Свое время (указывается в customMinutes)
         */
        CUSTOM
    }
}
