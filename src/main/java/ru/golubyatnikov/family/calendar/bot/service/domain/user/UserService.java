package ru.golubyatnikov.family.calendar.bot.service.domain.user;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.golubyatnikov.family.calendar.bot.exception.UserNotFoundException;
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
        return userRepository.findByTelegramId(telegramId);
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
        return userRepository.findById(userId);
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
        return userRepository.findByTelegramId(telegramId).isPresent();
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
        if (userId == null) {
            throw new IllegalArgumentException("User ID не может быть null");
        }
        
        if (filter == null) {
            throw new IllegalArgumentException("Фильтр событий не может быть null");
        }
        
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException("Пользователь не найден: userId=" + userId));
        
        user.setEventFilter(filter);
        userRepository.save(user);
    }
}
