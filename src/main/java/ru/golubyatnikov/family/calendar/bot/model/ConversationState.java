package ru.golubyatnikov.family.calendar.bot.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.time.LocalDateTime;

/**
 * Entity класс для хранения состояния диалога пользователя с ботом.
 * 
 * <p>Используется для сохранения контекста между операциями, в частности
 * для хранения информации о сообщениях с вложениями, которые нужно редактировать
 * вместо создания новых сообщений.</p>
 * 
 * <p>Каждый пользователь имеет только одно состояние диалога (UNIQUE constraint на user_id).</p>
 * 
 * @author Family Calendar Bot Team
 * @version 1.0.0
 * @since 2026-01-21
 * @see User
 * @see Event
 */
@Entity
@Table(name = "conversation_states", indexes = {
    @Index(name = "idx_conversation_states_user_id", columnList = "user_id", unique = true),
    @Index(name = "idx_conversation_states_event_id", columnList = "attachment_event_id")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConversationState {
    
    /**
     * Уникальный идентификатор состояния диалога.
     * Генерируется автоматически базой данных.
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    /**
     * Пользователь, которому принадлежит это состояние диалога.
     * Обязательное поле, должно быть уникальным (один пользователь = одно состояние).
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false, 
                foreignKey = @ForeignKey(name = "fk_conversation_states_user"))
    private User user;
    
    /**
     * Идентификатор события для контекста вложений.
     * Используется для связи контекста сообщения с конкретным событием.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "attachment_event_id", 
                foreignKey = @ForeignKey(name = "fk_conversation_states_event"))
    private Event attachmentEvent;
    
    /**
     * Идентификатор чата для контекста вложений.
     * Необходим для редактирования сообщения в правильном чате.
     */
    @Column(name = "attachment_chat_id")
    private Long attachmentChatId;
    
    /**
     * Идентификатор сообщения для редактирования при работе с вложениями.
     * Позволяет системе редактировать одно и то же сообщение вместо создания новых.
     */
    @Column(name = "attachment_message_id")
    private Integer attachmentMessageId;
    
    /**
     * Время создания контекста вложений.
     * Используется для проверки истечения контекста (Telegram API позволяет
     * редактировать сообщения только в течение 48 часов).
     */
    @Column(name = "attachment_context_created_at")
    private Instant attachmentContextCreatedAt;
    
    /**
     * Флаг, указывающий что событие было первым в списке "Мои события".
     * Используется для восстановления шапки "📋 Мои события" при возврате к событию.
     */
    @Column(name = "event_has_my_events_header")
    private Boolean eventHasMyEventsHeader;
    
    /**
     * Количество событий пользователя на момент открытия события.
     * Используется для формирования корректной шапки "Мои события (X событий)".
     */
    @Column(name = "event_count_for_header")
    private Integer eventCountForHeader;
    
    /**
     * Дата и время создания записи состояния диалога.
     * Устанавливается автоматически при создании записи.
     */
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
    
    /**
     * Дата и время последнего обновления записи состояния диалога.
     * Обновляется автоматически при каждом изменении записи.
     */
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
    
    /**
     * Автоматически устанавливает дату и время создания.
     */
    @PrePersist
    protected void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        this.createdAt = now;
        this.updatedAt = now;
    }
    
    /**
     * Автоматически обновляет дату и время последнего изменения.
     */
    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
    
    /**
     * Проверяет, есть ли сохраненный контекст сообщения с вложениями.
     * 
     * @return true, если все поля контекста заполнены, false в противном случае
     */
    public boolean hasAttachmentMessageContext() {
        return attachmentEvent != null 
            && attachmentChatId != null 
            && attachmentMessageId != null 
            && attachmentContextCreatedAt != null;
    }
    
    /**
     * Очищает контекст сообщения с вложениями.
     * Устанавливает все поля контекста в null.
     */
    public void clearAttachmentMessageContext() {
        this.attachmentEvent = null;
        this.attachmentChatId = null;
        this.attachmentMessageId = null;
        this.attachmentContextCreatedAt = null;
    }
    
    /**
     * Проверяет, есть ли сохраненный контекст шапки события.
     * 
     * @return true, если контекст шапки сохранен, false в противном случае
     */
    public boolean hasEventHeaderContext() {
        return eventHasMyEventsHeader != null && eventCountForHeader != null;
    }
    
    /**
     * Очищает контекст шапки события.
     * Устанавливает поля контекста шапки в null.
     */
    public void clearEventHeaderContext() {
        this.eventHasMyEventsHeader = null;
        this.eventCountForHeader = null;
    }
}
