package ru.golubyatnikov.family.calendar.bot.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

/**
 * Entity класс для истории изменений событий.
 * Отслеживает все действия пользователей с событиями для аудита и отображения истории.
 *
 * @author Golubyatnikov Aleksey
 * @since 2026-01-16
 */
@Entity
@Table(name = "event_history")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EventHistory {
    
    /**
     * Уникальный идентификатор записи истории
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    /**
     * Идентификатор события (может быть удалено, поэтому без FK)
     */
    @Column(name = "event_id", nullable = false)
    private Long eventId;
    
    /**
     * Пользователь, выполнивший действие
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;
    
    /**
     * Тип действия: CREATED, UPDATED, DELETED, RESTORED
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "action_type", nullable = false, length = 20)
    private ActionType actionType;
    
    /**
     * Название измененного поля (только для action_type = UPDATED)
     */
    @Column(name = "field_name")
    private String fieldName;
    
    /**
     * Старое значение поля (для UPDATED)
     */
    @Column(name = "old_value", columnDefinition = "TEXT")
    private String oldValue;
    
    /**
     * Новое значение поля (для UPDATED)
     */
    @Column(name = "new_value", columnDefinition = "TEXT")
    private String newValue;
    
    /**
     * Дата и время выполнения действия
     */
    @Column(name = "changed_at", nullable = false)
    private LocalDateTime changedAt;
    
    /**
     * Автоматически устанавливает дату изменения при создании записи
     */
    @PrePersist
    protected void onCreate() {
        changedAt = LocalDateTime.now();
    }
}
