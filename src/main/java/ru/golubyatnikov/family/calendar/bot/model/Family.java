package ru.golubyatnikov.family.calendar.bot.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Entity класс для представления семьи в системе.
 * 
 * <p>Семья представляет собой группу пользователей, имеющих доступ к общему календарю.
 * Каждая семья может содержать несколько пользователей (members) и иметь множество событий.</p>
 * 
 * <p>Соответствует таблице {@code families} в базе данных.</p>
 * 
 * <p><b>Требования:</b> 11.2, 11.4</p>
 * 
 * @see User
 * @see Event
 * @author Family Calendar Bot Team
 * @version 1.0.0
 * @since 2025-12-30
 */
@Entity
@Table(name = "families")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Family {

    /**
     * Уникальный идентификатор семьи.
     * Генерируется автоматически базой данных.
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Название семьи.
     * Обязательное поле, не может быть пустым.
     */
    @Column(name = "name", nullable = false, length = 255)
    private String name;

    /**
     * Список членов семьи.
     * Связь один-ко-многим с сущностью User.
     * При удалении семьи связь с пользователями устанавливается в NULL (ON DELETE SET NULL).
     */
    @OneToMany(mappedBy = "family", cascade = CascadeType.ALL, orphanRemoval = false)
    @Builder.Default
    private List<User> members = new ArrayList<>();

    /**
     * Дата и время создания семьи.
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
     * Добавляет пользователя в семью.
     * Устанавливает двустороннюю связь между семьей и пользователем.
     * 
     * @param user пользователь для добавления в семью
     */
    public void addMember(User user) {
        members.add(user);
        user.setFamily(this);
    }

    /**
     * Удаляет пользователя из семьи.
     * Разрывает двустороннюю связь между семьей и пользователем.
     * 
     * @param user пользователь для удаления из семьи
     */
    public void removeMember(User user) {
        members.remove(user);
        user.setFamily(null);
    }
}
