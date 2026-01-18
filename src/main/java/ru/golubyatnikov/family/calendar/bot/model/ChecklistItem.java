package ru.golubyatnikov.family.calendar.bot.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

/**
 * Entity класс для пунктов чек-листа события.
 * Позволяет создавать списки задач внутри событий с возможностью отметки выполнения.
 *
 * @author Golubyatnikov Aleksey
 * @version 1.0.0
 * @since 2026-01-16
 * @see Event
 * @see User
 */
@Entity
@Table(name = "checklist_items")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChecklistItem {
    
    /**
     * Уникальный идентификатор пункта чек-листа
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    /**
     * Событие, к которому относится чек-лист
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "event_id", nullable = false)
    private Event event;
    
    /**
     * Текст пункта чек-листа
     */
    @Column(name = "text", nullable = false)
    private String text;
    
    /**
     * Флаг выполнения пункта: true - выполнен, false - не выполнен
     */
    @Column(name = "completed", nullable = false)
    @Builder.Default
    private Boolean completed = false;
    
    /**
     * Порядковый номер пункта в чек-листе (для сортировки)
     */
    @Column(name = "position", nullable = false)
    private Integer position;
    
    /**
     * Дата и время отметки пункта как выполненного
     */
    @Column(name = "completed_at")
    private LocalDateTime completedAt;
    
    /**
     * Пользователь, отметивший пункт как выполненный
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "completed_by")
    private User completedBy;
}
