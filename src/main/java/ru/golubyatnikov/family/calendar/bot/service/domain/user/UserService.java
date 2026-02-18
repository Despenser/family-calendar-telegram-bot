package ru.golubyatnikov.family.calendar.bot.service.domain.user;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.golubyatnikov.family.calendar.bot.exception.UserNotFoundException;
import ru.golubyatnikov.family.calendar.bot.model.entity.Family;
import ru.golubyatnikov.family.calendar.bot.model.entity.User;
import ru.golubyatnikov.family.calendar.bot.model.enums.EventFilter;
import ru.golubyatnikov.family.calendar.bot.repository.UserRepository;

import java.util.Optional;

/**
 * Сервис для управления пользователями Telegram бота.
 *
 * @author Golubyatnikov Aleksey
 * @since 2026-01-16
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserService {
    
    private final UserRepository userRepository;

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

    /**
     * Устанавливает фильтр событий для пользователя.
     *
     * @param userId внутренний идентификатор пользователя
     * @param filter тип фильтра событий для установки
     *
     * @throws UserNotFoundException если пользователь с указанным ID не найден
     * @throws IllegalArgumentException если userId или filter равны null
     * @throws org.springframework.dao.DataAccessException если возникла ошибка доступа к БД
     */
    @Transactional
    public void setEventFilter(Long userId, EventFilter filter) {
        log.info("Установка фильтра событий: userId={}, filter={}", userId, filter);
        
        if (userId == null) {
            log.error("Попытка установить фильтр с null userId");
            throw new IllegalArgumentException("User ID не может быть null");
        }
        
        if (filter == null) {
            log.error("Попытка установить null фильтр для пользователя: userId={}", userId);
            throw new IllegalArgumentException("Фильтр событий не может быть null");
        }
        
        User user = userRepository.findById(userId)
                .orElseThrow(() -> {
                    log.error("Пользователь не найден при установке фильтра: userId={}", userId);
                    return new UserNotFoundException("Пользователь не найден: userId=" + userId);
                });
        
        user.setEventFilter(filter);
        userRepository.save(user);
        
        log.info("Фильтр событий успешно установлен: userId={}, filter={}", userId, filter);
    }
}
