package ru.golubyatnikov.family.calendar.bot.model.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import ru.golubyatnikov.family.calendar.bot.model.enums.ReminderType;

import java.time.LocalDateTime;

/**
 * Entity класс для напоминаний о событиях.
 *
 * @author Golubyatnikov Aleksey
 * @since 2026-01-16
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
     * Тип напоминания: EVENING_BEFORE, ONE_HOUR_BEFORE, FIFTEEN_MINUTES_BEFORE
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "reminder_type", nullable = false, length = 50)
    private ReminderType reminderType;
    
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
}
