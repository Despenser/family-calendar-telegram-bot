package ru.golubyatnikov.family.calendar.bot.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import ru.golubyatnikov.family.calendar.bot.model.User;

import java.util.Optional;

/**
 * Spring Data JPA репозиторий для работы с сущностью {@link User}.
 * 
 * <p>Предоставляет методы для управления пользователями в системе, включая
 * стандартные CRUD операции и специализированные методы поиска.</p>
 * 
 * <p>Основные возможности:</p>
 * <ul>
 *   <li>Стандартные CRUD операции (save, findById, findAll, delete, count)</li>
 *   <li>Поиск пользователя по Telegram ID для авторизации</li>
 * </ul>
 * 
 * <p><b>Требования:</b> 11.1</p>
 * 
 * @see User
 * @see JpaRepository
 * @author Family Calendar Bot Team
 * @version 1.0.0
 * @since 2025-12-30
 */
@Repository
public interface UserRepository extends JpaRepository<User, Long> {
    
    /**
     * Находит пользователя по его уникальному Telegram ID.
     * 
     * <p>Этот метод используется для авторизации пользователей при обработке
     * команд от Telegram бота. Telegram ID является уникальным идентификатором
     * пользователя в системе Telegram и используется для связи между Telegram
     * аккаунтом и записью в базе данных.</p>
     * 
     * <p>Метод использует индекс {@code idx_users_telegram_id} для быстрого поиска.</p>
     * 
     * @param telegramId уникальный идентификатор пользователя в Telegram
     * @return Optional содержащий пользователя, если найден, иначе пустой Optional
     * @throws org.springframework.dao.DataAccessException если возникла ошибка доступа к БД
     */
    Optional<User> findByTelegramId(Long telegramId);
}
