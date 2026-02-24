package ru.golubyatnikov.family.calendar.bot.service.infrastructure.authorization;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import ru.golubyatnikov.family.calendar.bot.model.entity.User;
import ru.golubyatnikov.family.calendar.bot.model.enums.MessageCategory;
import ru.golubyatnikov.family.calendar.bot.service.domain.user.UserService;
import ru.golubyatnikov.family.calendar.bot.service.infrastructure.telegram.TelegramMessageService;
import ru.golubyatnikov.family.calendar.bot.service.infrastructure.telegram.UnauthorizedMessageService;

import java.util.Optional;

import static ru.golubyatnikov.family.calendar.bot.util.EmojiConstants.Status.WARNING;

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
            sendUnauthorizedMessage(chatId, category);
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
            String message = WARNING + " Временные технические проблемы\\. Пожалуйста, попробуйте позже\\.";
            telegramMessageService.sendMessage(chatId, message);
            
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
            throw new IllegalArgumentException("TelegramId не может быть null");
        }
        
        if (chatId == null) {
            throw new IllegalArgumentException("ChatId не может быть null");
        }
        
        if (category == null) {
            throw new IllegalArgumentException("Категория сообщения не может быть null");
        }
        
        if (commandName == null || commandName.isBlank()) {
            throw new IllegalArgumentException("Имя команды не может быть пустым");
        }
    }
}
