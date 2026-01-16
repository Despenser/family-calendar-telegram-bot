package ru.golubyatnikov.family.calendar.bot.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Entity класс для представления пользователя Telegram бота.
 * 
 * <p>Пользователь идентифицируется по уникальному Telegram ID и может быть членом одной семьи.
 * Каждый пользователь может создавать события в календаре своей семьи.</p>
 * 
 * <p>Соответствует таблице {@code users} в базе данных.</p>
 * 
 * <p><b>Требования:</b> 11.2, 11.3, 11.4</p>
 * 
 * @see Family
 * @see Event
 * @author Family Calendar Bot Team
 * @version 1.0.0
 * @since 2025-12-30
 */
@Entity
@Table(name = "users", indexes = {
    @Index(name = "idx_users_telegram_id", columnList = "telegram_id"),
    @Index(name = "idx_users_family_id", columnList = "family_id")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class User {

    /**
     * Уникальный идентификатор пользователя в системе.
     * Генерируется автоматически базой данных.
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Уникальный идентификатор пользователя в Telegram.
     * Используется для авторизации и идентификации пользователя.
     * Обязательное поле, должно быть уникальным.
     */
    @Column(name = "telegram_id", nullable = false, unique = true)
    private Long telegramId;

    /**
     * Username пользователя в Telegram.
     * Опциональное поле, так как не все пользователи Telegram имеют username.
     */
    @Column(name = "username", length = 255)
    private String username;

    /**
     * Имя пользователя.
     * Обязательное поле, берется из профиля Telegram.
     */
    @Column(name = "first_name", nullable = false, length = 255)
    private String firstName;

    /**
     * Фамилия пользователя.
     * Опциональное поле, берется из профиля Telegram.
     */
    @Column(name = "last_name", length = 255)
    private String lastName;

    /**
     * Семья, к которой принадлежит пользователь.
     * Связь многие-к-одному с сущностью Family.
     * Может быть null, если пользователь еще не добавлен в семью.
     * При удалении семьи устанавливается в NULL (ON DELETE SET NULL).
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "family_id", foreignKey = @ForeignKey(name = "users_family_fk"))
    private Family family;

    /**
     * Фильтр событий, выбранный пользователем.
     * Определяет, какие события отображаются пользователю (все, семейные или личные).
     * По умолчанию установлен в ALL (все события).
     * 
     * <p><b>Требования:</b> 3.4</p>
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "event_filter", length = 20)
    @Builder.Default
    private EventFilter eventFilter = EventFilter.ALL;

    /**
     * Дата и время регистрации пользователя в системе.
     * Устанавливается автоматически при создании записи.
     */
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /**
     * Callback метод JPA, вызываемый перед сохранением новой сущности.
     * Автоматически устанавливает дату и время регистрации.
     */
    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }

    /**
     * Возвращает полное имя пользователя.
     * Комбинирует имя и фамилию, если фамилия присутствует.
     * 
     * @return полное имя пользователя
     */
    public String getFullName() {
        if (lastName != null && !lastName.isBlank()) {
            return firstName + " " + lastName;
        }
        return firstName;
    }

    /**
     * Проверяет, является ли пользователь членом какой-либо семьи.
     * 
     * @return true, если пользователь принадлежит семье, иначе false
     */
    public boolean hasFamily() {
        return family != null;
    }
}
