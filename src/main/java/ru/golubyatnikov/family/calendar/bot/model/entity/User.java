package ru.golubyatnikov.family.calendar.bot.model.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import ru.golubyatnikov.family.calendar.bot.model.enums.EventFilter;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;

/**
 * Entity класс для представления пользователя Telegram бота.
 *
 * @author Golubyatnikov Aleksey
 * @since 2026-01-16
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
    @Column(name = "username")
    private String username;

    /**
     * Имя пользователя.
     * Обязательное поле, берется из профиля Telegram.
     */
    @Column(name = "first_name", nullable = false)
    private String firstName;

    /**
     * Фамилия пользователя.
     * Опциональное поле, берется из профиля Telegram.
     */
    @Column(name = "last_name")
    private String lastName;

    /**
     * Семья, к которой принадлежит пользователь.
     * Может быть null, если пользователь еще не добавлен в семью.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "family_id", foreignKey = @ForeignKey(name = "users_family_fk"))
    private Family family;

    /**
     * Фильтр событий, выбранный пользователем.
     * Определяет, какие события отображаются пользователю (все, семейные или личные).
     * По умолчанию установлен в ALL (все события).
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "event_filter", length = 20)
    @Builder.Default
    private EventFilter eventFilter = EventFilter.ALL;

    /**
     * Часовой пояс пользователя в формате IANA (например, "Europe/Moscow").
     * Используется для корректной валидации и отображения дат и времени.
     * По умолчанию установлен в "Europe/Moscow" (UTC+3).
     */
    @Column(name = "timezone", length = 50)
    @Builder.Default
    private String timezone = "Europe/Moscow";

    /**
     * Дата и время регистрации пользователя в системе.
     * Устанавливается автоматически при создании записи.
     */
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /**
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

    /**
     * Получает ZoneId пользователя для работы с датами и временем.
     * 
     * @return ZoneId пользователя
     */
    public ZoneId getZoneId() {
        return ZoneId.of(timezone);
    }

    /**
     * Получает текущую дату в таймзоне пользователя.
     * 
     * @return текущая дата в таймзоне пользователя
     */
    public LocalDate getCurrentDate() {
        return LocalDate.now(getZoneId());
    }

    /**
     * Получает текущее время в таймзоне пользователя.
     * 
     * @return текущее время в таймзоне пользователя
     */
    public LocalDateTime getCurrentDateTime() {
        return LocalDateTime.now(getZoneId());
    }
}
