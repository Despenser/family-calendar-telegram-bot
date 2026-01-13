package ru.golubyatnikov.family.calendar.bot.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.golubyatnikov.family.calendar.bot.model.Family;
import ru.golubyatnikov.family.calendar.bot.model.User;
import ru.golubyatnikov.family.calendar.bot.repository.UserRepository;
import java.util.Optional;

/**
 * Сервис для управления пользователями Telegram бота.
 *
 * @author Family Calendar Bot Team
 * @version 1.0.0
 * @since 2025-12-30
 */
@Service
@Transactional(readOnly = true)
@Slf4j
public class UserService {

    private final UserRepository userRepository;

    /**
     * Конструктор для внедрения зависимостей.
     * 
     * @param userRepository репозиторий для работы с пользователями
     */
    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    /**
     * Находит пользователя по его Telegram ID.
     * 
     * @param telegramId уникальный идентификатор пользователя в Telegram
     *
     * @return Optional содержащий пользователя, если найден, иначе пустой Optional
     * @throws org.springframework.dao.DataAccessException если возникла ошибка доступа к БД
     */
    public Optional<User> findByTelegramId(Long telegramId) {
        log.debug("Поиск пользователя по Telegram ID: {}", telegramId);
        
        Optional<User> user = userRepository.findByTelegramId(telegramId);
        
        if (user.isPresent()) {
            User foundUser = user.get();
            // Явно проверяем наличие семьи для отладки
            boolean hasFamily = foundUser.hasFamily();
            Family family = foundUser.getFamily();
            log.info("Пользователь найден: telegramId={}, userId={}, username={}, hasFamily={}, familyId={}", 
                    telegramId, foundUser.getId(), foundUser.getUsername(), hasFamily, 
                    family != null ? family.getId() : null);
        } else {
            log.info("Пользователь не найден: telegramId={}", telegramId);
        }
        
        return user;
    }

    /**
     * Находит пользователя по его внутреннему ID.
     * 
     * @param userId внутренний идентификатор пользователя
     *
     * @return Optional содержащий пользователя, если найден, иначе пустой Optional
     * @throws org.springframework.dao.DataAccessException если возникла ошибка доступа к БД
     */
    public Optional<User> findById(Long userId) {
        log.debug("Поиск пользователя по ID: {}", userId);
        
        Optional<User> user = userRepository.findById(userId);
        
        if (user.isPresent()) {
            log.info("Пользователь найден: userId={}, telegramId={}, username={}", 
                    userId, user.get().getTelegramId(), user.get().getUsername());
        } else {
            log.info("Пользователь не найден: userId={}", userId);
        }
        
        return user;
    }

    /**
     * Создает нового пользователя в системе.
     *
     * @param telegramId уникальный идентификатор пользователя в Telegram
     * @param username username пользователя в Telegram (может быть null)
     * @param firstName имя пользователя (обязательное поле)
     * @param family семья, к которой принадлежит пользователь (может быть null)
     *
     * @return созданный и сохраненный пользователь
     * @throws IllegalArgumentException если telegramId или firstName равны null
     * @throws org.springframework.dao.DataAccessException если возникла ошибка доступа к БД
     */
    @Transactional
    public User createUser(Long telegramId, String username, String firstName, Family family) {
        log.info("Создание нового пользователя: telegramId={}, username={}, firstName={}, familyId={}", 
                telegramId, username, firstName, family != null ? family.getId() : null);
        
        if (telegramId == null) {
            log.error("Попытка создать пользователя с null telegramId");
            throw new IllegalArgumentException("Telegram ID не может быть null");
        }
        
        if (firstName == null || firstName.isBlank()) {
            log.error("Попытка создать пользователя с пустым firstName: telegramId={}", telegramId);
            throw new IllegalArgumentException("Имя пользователя не может быть пустым");
        }
        
        User user = User.builder()
                .telegramId(telegramId)
                .username(username)
                .firstName(firstName)
                .family(family)
                .build();
        
        User savedUser = userRepository.save(user);
        
        log.info("Пользователь успешно создан: userId={}, telegramId={}, username={}", 
                savedUser.getId(), savedUser.getTelegramId(), savedUser.getUsername());
        
        return savedUser;
    }

    /**
     * Проверяет, авторизован ли пользователь в системе.
     * 
     * @param telegramId уникальный идентификатор пользователя в Telegram
     *
     * @return true, если пользователь авторизован (найден в БД), иначе false
     * @throws org.springframework.dao.DataAccessException если возникла ошибка доступа к БД
     */
    public boolean isUserAuthorized(Long telegramId) {
        log.debug("Проверка авторизации пользователя: telegramId={}", telegramId);
        
        boolean isAuthorized = userRepository.findByTelegramId(telegramId).isPresent();
        
        log.info("Результат проверки авторизации: telegramId={}, authorized={}", 
                telegramId, isAuthorized);
        
        return isAuthorized;
    }
}
