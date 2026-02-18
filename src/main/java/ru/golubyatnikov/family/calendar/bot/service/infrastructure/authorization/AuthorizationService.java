package ru.golubyatnikov.family.calendar.bot.service.infrastructure.authorization;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import ru.golubyatnikov.family.calendar.bot.model.enums.MessageCategory;
import ru.golubyatnikov.family.calendar.bot.model.entity.User;
import ru.golubyatnikov.family.calendar.bot.service.infrastructure.telegram.TelegramMessageService;
import ru.golubyatnikov.family.calendar.bot.service.infrastructure.telegram.UnauthorizedMessageService;
import ru.golubyatnikov.family.calendar.bot.service.domain.user.UserService;
import java.time.Instant;
import java.util.Optional;

/**
 * Сервис для централизованной проверки авторизации пользователей.
 *
 * @author Golubyatnikov Aleksey
 * @since 2026-01-12
 */
@RequiredArgsConstructor
@Service
@Slf4j
public class AuthorizationService {
    
    private final UserService userService;
    private final UnauthorizedMessageService messageService;
    private final TelegramMessageService telegramMessageService;
    
    /**
     * Проверяет авторизацию пользователя и отправляет сообщение при отсутствии доступа.
     *
     * @param telegramId Telegram ID пользователя для проверки авторизации
     * @param chatId ID чата для отправки сообщения об ограничении доступа
     * @param category категория команды для формирования специфичного сообщения
     * @param commandName имя команды для логирования (например, "/add_event")
     * @param username имя пользователя в Telegram (может быть null)
     *
     * @throws IllegalArgumentException если telegramId, chatId, category или commandName равны null
     * @throws org.springframework.dao.DataAccessException если возникла ошибка доступа к БД
     */
    public void checkAuthorizationAndNotify(Long telegramId,
                                            Long chatId,
                                            MessageCategory category,
                                            String commandName,
                                            String username) {
        
        validateParams(telegramId, chatId, category, commandName);
        
        log.debug("Проверка авторизации: telegramId={}, command={}", telegramId, commandName);

        Optional<User> userOpt;
        try {
            userOpt = userService.findByTelegramId(telegramId);

        } catch (Exception e) {
            log.error("Ошибка доступа к БД при проверке авторизации: telegramId={}, command={}, error={}", 
                    telegramId, commandName, e.getMessage(), e);

            sendTemporaryUnavailableMessage(chatId);
            return;
        }
        
        if (userOpt.isEmpty()) {
            logUnauthorizedAccess(telegramId, username, commandName);
            sendUnauthorizedMessage(chatId, category);

        } else {
            log.debug("Пользователь авторизован: telegramId={}, userId={}", 
                    telegramId, userOpt.get().getId());
        }

    }
    
    /**
     * Логирует попытку доступа неавторизованного пользователя.
     *
     * @param telegramId Telegram ID пользователя
     * @param username имя пользователя в Telegram (может быть null)
     * @param commandName имя команды
     */
    private void logUnauthorizedAccess(Long telegramId, String username, String commandName) {
        try {
            if (username != null && !username.isBlank()) {
                log.info("Неавторизованная попытка доступа: telegramId={}, username={}, command={}, timestamp={}",
                        telegramId, username, commandName, Instant.now());
            } else {
                log.info("Неавторизованная попытка доступа: telegramId={}, username=<not_provided>, command={}, timestamp={}",
                        telegramId, commandName, Instant.now());
            }
        } catch (Exception e) {
            log.error("Ошибка при логировании попытки доступа: telegramId={}, command={}, error={}", 
                    telegramId, commandName, e.getMessage());
        }
    }
    
    /**
     * Отправляет сообщение об ограничении доступа пользователю.
     *
     * @param chatId ID чата для отправки сообщения
     * @param category категория команды для формирования специфичного сообщения
     */
    private void sendUnauthorizedMessage(Long chatId, MessageCategory category) {
        try {
            String message = messageService.getMessage(category);
            telegramMessageService.sendMessage(chatId, message);
            
            log.debug("Сообщение об ограничении доступа отправлено: chatId={}, category={}", chatId, category);
            
        } catch (TelegramApiException e) {
            log.error("Ошибка при отправке сообщения об ограничении доступа: chatId={}, category={}, error={}", 
                    chatId, category, e.getMessage());

        } catch (Exception e) {
            log.error("Неожиданная ошибка при отправке сообщения об ограничении доступа: chatId={}, category={}, error={}", 
                    chatId, category, e.getMessage(), e);
        }
    }
    
    /**
     * Отправляет сообщение о временной недоступности сервиса.
     *
     * @param chatId ID чата для отправки сообщения
     */
    private void sendTemporaryUnavailableMessage(Long chatId) {
        try {
            String message = "⚠️ Временные технические проблемы\\. Пожалуйста, попробуйте позже\\.";
            telegramMessageService.sendMessage(chatId, message);
            
            log.debug("Сообщение о временной недоступности отправлено: chatId={}", chatId);
            
        } catch (Exception e) {
            log.error("Ошибка при отправке сообщения о временной недоступности: chatId={}, error={}",
                    chatId, e.getMessage());
        }
    }
    
    /**
     * Валидирует параметры метода checkAuthorizationAndNotify.
     * 
     * @param telegramId Telegram ID пользователя
     * @param chatId ID чата
     * @param category категория сообщения
     * @param commandName имя команды
     *
     * @throws IllegalArgumentException если какой-либо параметр некорректен
     */
    private void validateParams(Long telegramId,
                                Long chatId,
                                MessageCategory category,
                                String commandName) {

        if (telegramId == null) {
            log.error("Попытка проверить авторизацию с null telegramId");
            throw new IllegalArgumentException("TelegramId не может быть null");
        }
        
        if (chatId == null) {
            log.error("Попытка проверить авторизацию с null chatId: telegramId={}", telegramId);
            throw new IllegalArgumentException("ChatId не может быть null");
        }
        
        if (category == null) {
            log.error("Попытка проверить авторизацию с null category: telegramId={}", telegramId);
            throw new IllegalArgumentException("Категория сообщения не может быть null");
        }
        
        if (commandName == null || commandName.isBlank()) {
            log.error("Попытка проверить авторизацию с пустым commandName: telegramId={}", telegramId);
            throw new IllegalArgumentException("Имя команды не может быть пустым");
        }
    }
}
