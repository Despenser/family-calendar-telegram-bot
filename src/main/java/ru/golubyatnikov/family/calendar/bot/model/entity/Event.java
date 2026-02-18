package ru.golubyatnikov.family.calendar.bot.model.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import ru.golubyatnikov.family.calendar.bot.model.enums.EventStatus;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;


/**
 * Entity класс для представления события в семейном календаре.
 *
 * @author Golubyatnikov Aleksey
 * @since 2026-01-16
 */
@Entity
@Table(name = "events", indexes = {
    @Index(name = "idx_events_family_date", columnList = "family_id, event_date"),
    @Index(name = "idx_events_user_id", columnList = "user_id"),
    @Index(name = "idx_events_datetime", columnList = "event_date, event_time"),
    @Index(name = "idx_events_status", columnList = "status"),
    @Index(name = "idx_events_user_status", columnList = "user_id, status")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Event {

    /**
     * Уникальный идентификатор события.
     * Генерируется автоматически базой данных.
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Пользователь, создавший событие.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false, foreignKey = @ForeignKey(name = "events_user_fk"))
    private User user;

    /**
     * Семья, к которой относится событие.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "family_id", nullable = false, foreignKey = @ForeignKey(name = "events_family_fk"))
    private Family family;

    /**
     * Название события.
     * Может быть NULL для черновиков в процессе создания.
     */
    @Column(name = "title")
    private String title;

    /**
     * Подробное описание события.
     * Опциональное поле, может содержать дополнительную информацию о событии.
     */
    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    /**
     * Дата события.
     * Может быть NULL для черновиков в процессе создания.
     */
    @Column(name = "event_date")
    private LocalDate eventDate;

    /**
     * Время события.
     * Может быть NULL для черновиков в процессе создания.
     */
    @Column(name = "event_time")
    private LocalTime eventTime;

    /**
     * Время окончания события (для временных интервалов).
     * Опциональное поле для событий с указанием продолжительности.
     */
    @Column(name = "end_time")
    private LocalTime endTime;

    /**
     * Статус события (черновик, активное, завершенное или удаленное).
     * По умолчанию ACTIVE. Черновики используются для многошагового создания события.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    @Builder.Default
    private EventStatus status = EventStatus.ACTIVE;

    /**
     * Флаг персонального события.
     * true - событие видно только создателю, false - видно всей семье.
     * По умолчанию false (семейное событие).
     */
    @Column(name = "is_personal", nullable = false)
    @Builder.Default
    private Boolean isPersonal = false;

    /**
     * UUID серии для повторяющихся событий.
     * NULL для обычных событий, одинаковый для всех событий одной серии.
     */
    @Column(name = "series_id")
    private String seriesId;

    /**
     * Заметка о завершении события.
     * Добавляется пользователем после завершения события.
     */
    @Column(name = "completion_note", columnDefinition = "TEXT")
    private String completionNote;

    /**
     * Дата и время перемещения события в корзину.
     * NULL для активных событий. События в корзине хранятся 30 дней.
     */
    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    /**
     * Дата и время завершения события.
     * Устанавливается автоматически планировщиком или вручную пользователем.
     */
    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    /**
     * Флаг отправки уведомления о событии.
     * По умолчанию false. Устанавливается в true после отправки уведомления всем членам семьи за 1 час до события.
     */
    @Column(name = "notified", nullable = false)
    @Builder.Default
    private Boolean notified = false;

    /**
     * Идентификатор сообщения Telegram, связанного с этим событием.
     */
    @Column(name = "message_id")
    private Long messageId;

    /**
     * Флаг, указывающий, что сообщение этого события содержит шапку списка "Мои события"
     */
    @Column(name = "is_my_events_header")
    @Builder.Default
    private Boolean isMyEventsHeader = false;

    /**
     * Флаг, указывающий, что сообщение этого события содержит шапку корзины.
     *
     */
    @Column(name = "is_trash_header")
    @Builder.Default
    private Boolean isTrashHeader = false;

    /**
     * Дата и время создания записи о событии.
     * Устанавливается автоматически при создании записи.
     */
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /**
     * Вложения события (файлы, документы, изображения).
     */
    @OneToMany(mappedBy = "event", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<Attachment> attachments = new ArrayList<>();

    /**
     * Напоминания о событии.
     */
    @OneToMany(mappedBy = "event", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<Reminder> reminders = new ArrayList<>();

    /**
     * Автоматически устанавливает дату и время создания.
     */
    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }

    /**
     * Возвращает полную дату и время события как LocalDateTime.
     * 
     * @return объединенные дата и время события, или null если дата или время не заполнены
     */
    public LocalDateTime getEventDateTime() {
        if (eventDate == null || eventTime == null) {
            return null;
        }
        return LocalDateTime.of(eventDate, eventTime);
    }



    /**
     * Проверяет, находится ли событие в прошлом.
     * 
     * @return true, если событие уже прошло, иначе false
     */
    public boolean isPast() {
        LocalDateTime dateTime = getEventDateTime();
        return dateTime != null && dateTime.isBefore(LocalDateTime.now());
    }

    /**
     * Проверяет, находится ли событие в будущем.
     * 
     * @return true, если событие еще не наступило, иначе false
     */
    public boolean isFuture() {
        LocalDateTime dateTime = getEventDateTime();
        return dateTime != null && dateTime.isAfter(LocalDateTime.now());
    }

    /**
     * Проверяет, принадлежит ли событие указанному пользователю.
     * 
     * @param userId идентификатор пользователя для проверки
     * @return true, если событие создано указанным пользователем, иначе false
     */
    public boolean belongsToUser(Long userId) {
        return user != null && user.getId().equals(userId);
    }

    /**
     * Проверяет, является ли событие черновиком.
     * 
     * @return true, если событие в статусе DRAFT, иначе false
     */
    public boolean isDraft() {
        return status == EventStatus.DRAFT;
    }

    /**
     * Проверяет, является ли событие активным.
     * 
     * @return true, если событие в статусе ACTIVE, иначе false
     */
    public boolean isActive() {
        return status == EventStatus.ACTIVE;
    }

    /**
     * Проверяет, является ли событие завершенным.
     * 
     * @return true, если событие в статусе COMPLETED, иначе false
     */
    public boolean isCompleted() {
        return status == EventStatus.COMPLETED;
    }

    /**
     * Проверяет, является ли событие удаленным (в корзине).
     * 
     * @return true, если событие в статусе DELETED, иначе false
     */
    public boolean isDeleted() {
        return status == EventStatus.DELETED;
    }

    /**
     * Проверяет, является ли событие частью серии повторяющихся событий.
     * 
     * @return true, если событие имеет series_id, иначе false
     */
    public boolean isRecurring() {
        return seriesId != null && !seriesId.isEmpty();
    }
}
