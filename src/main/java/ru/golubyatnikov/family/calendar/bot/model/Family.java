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
 * @author Golubyatnikov Aleksey
 * @since 2026-01-16
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
    @Column(name = "name", nullable = false)
    private String name;

    /**
     * Список членов семьи.
     */
    @OneToMany(mappedBy = "family", cascade = CascadeType.ALL)
    @Builder.Default
    private List<User> members = new ArrayList<>();

    /**
     * Дата и время создания семьи.
     * Устанавливается автоматически при создании записи.
     */
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /**
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
