package ru.golubyatnikov.family.calendar.bot.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Entity класс для комментариев к событиям.
 * Позволяет членам семьи обсуждать детали событий через комментарии.
 * 
 * @author Family Calendar Bot
 * @version 1.0
 * @see Event
 * @see User
 */
@Entity
@Table(name = "comments")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Comment {
    
    /**
     * Уникальный идентификатор комментария
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    /**
     * Событие, к которому относится комментарий
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "event_id", nullable = false)
    private Event event;
    
    /**
     * Автор комментария
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;
    
    /**
     * Текст комментария
     */
    @Column(name = "text", columnDefinition = "TEXT", nullable = false)
    private String text;
    
    /**
     * Дата и время создания комментария
     */
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
    
    /**
     * Автоматически устанавливает дату создания при создании записи
     */
    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
