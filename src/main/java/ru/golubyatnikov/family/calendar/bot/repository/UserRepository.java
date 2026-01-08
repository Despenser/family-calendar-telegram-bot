package ru.golubyatnikov.family.calendar.bot.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import ru.golubyatnikov.family.calendar.bot.model.User;

import java.util.Optional;

/**
 * Spring Data JPA репозиторий для работы с сущностью {@link User}.
 *
 * @author Family Calendar Bot Team
 * @version 1.0.0
 * @since 2025-12-30
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
    Optional<User> findByTelegramId(Long telegramId);
}
