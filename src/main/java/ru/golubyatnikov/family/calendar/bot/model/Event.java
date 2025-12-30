package ru.golubyatnikov.family.calendar.bot.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

/**
 * Entity класс для представления события в семейном календаре.
 * 
 * <p>Событие создается пользователем и принадлежит его семье. Каждое событие имеет
 * дату, время, название и описание. Система отслеживает, было ли отправлено уведомление
 * о событии через поле {@code notified}.</p>
 * 
 * <p>Соответствует таблице {@code events} в базе данных.</p>
 * 
 * <p><b>Требования:</b> 11.3, 11.4</p>
 * 
 * @see User
 * @see Family
 * @author Family Calendar Bot Team
 * @version 1.0.0
 * @since 2025-12-30
 */
@Entity
@Table(name = "events", indexes = {
    @Index(name = "idx_events_family_date", columnList = "family_id, event_date"),
    @Index(name = "idx_events_user_id", columnList = "user_id"),
    @Index(name = "idx_events_datetime", columnList = "event_date, event_time")
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
     * Связь многие-к-одному с сущностью User.
     * Обязательное поле. При удалении пользователя событие также удаляется (ON DELETE CASCADE).
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false, foreignKey = @ForeignKey(name = "events_user_fk"))
    private User user;

    /**
     * Семья, к которой относится событие.
     * Связь многие-к-одному с сущностью Family.
     * Обязательное поле. При удалении семьи событие также удаляется (ON DELETE CASCADE).
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "family_id", nullable = false, foreignKey = @ForeignKey(name = "events_family_fk"))
    private Family family;

    /**
     * Название события.
     * Обязательное поле, краткое описание события.
     */
    @Column(name = "title", nullable = false, length = 255)
    private String title;

    /**
     * Подробное описание события.
     * Опциональное поле, может содержать дополнительную информацию о событии.
     */
    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    /**
     * Дата события.
     * Обязательное поле, определяет день, когда произойдет событие.
     */
    @Column(name = "event_date", nullable = false)
    private LocalDate eventDate;

    /**
     * Время события.
     * Обязательное поле, определяет время начала события.
     */
    @Column(name = "event_time", nullable = false)
    private LocalTime eventTime;

    /**
     * Флаг отправки уведомления о событии.
     * По умолчанию false. Устанавливается в true после отправки уведомления
     * всем членам семьи за 1 час до события.
     */
    @Column(name = "notified", nullable = false)
    @Builder.Default
    private Boolean notified = false;

    /**
     * Дата и время создания записи о событии.
     * Устанавливается автоматически при создании записи.
     */
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /**
     * Callback метод JPA, вызываемый перед сохранением новой сущности.
     * Автоматически устанавливает дату и время создания.
     */
    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }

    /**
     * Возвращает полную дату и время события как LocalDateTime.
     * 
     * @return объединенные дата и время события
     */
    public LocalDateTime getEventDateTime() {
        return LocalDateTime.of(eventDate, eventTime);
    }

    /**
     * Форматирует дату события в читаемый формат.
     * 
     * @return дата в формате "dd.MM.yyyy"
     */
    public String getFormattedDate() {
        return eventDate.format(DateTimeFormatter.ofPattern("dd.MM.yyyy"));
    }

    /**
     * Форматирует время события в читаемый формат.
     * 
     * @return время в формате "HH:mm"
     */
    public String getFormattedTime() {
        return eventTime.format(DateTimeFormatter.ofPattern("HH:mm"));
    }

    /**
     * Проверяет, находится ли событие в прошлом.
     * 
     * @return true, если событие уже прошло, иначе false
     */
    public boolean isPast() {
        return getEventDateTime().isBefore(LocalDateTime.now());
    }

    /**
     * Проверяет, находится ли событие в будущем.
     * 
     * @return true, если событие еще не наступило, иначе false
     */
    public boolean isFuture() {
        return getEventDateTime().isAfter(LocalDateTime.now());
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
}
