package ru.golubyatnikov.family.calendar.bot.repository;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import ru.golubyatnikov.family.calendar.bot.model.entity.User;
import java.util.Optional;

/**
 * Repository интерфейс для работы с пользователем
 *
 * @author Golubyatnikov Aleksey
 * @since 2026-01-16
 */
@Repository
public interface UserRepository extends JpaRepository<User, Long> {
    
    /**
     * Находит пользователя по его уникальному Telegram ID.
     * 
     * @param telegramId уникальный идентификатор пользователя в Telegram
     *
     * @return Optional содержащий пользователя, если найден, иначе пустой Optional
     * @throws org.springframework.dao.DataAccessException если возникла ошибка доступа к БД
     */
    @EntityGraph(attributePaths = {"family"})
    Optional<User> findByTelegramId(Long telegramId);
}
