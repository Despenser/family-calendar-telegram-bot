package ru.golubyatnikov.family.calendar.bot.service.telegram;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboardMarkup;
import ru.golubyatnikov.family.calendar.bot.model.MessageCategory;
import ru.golubyatnikov.family.calendar.bot.model.User;
import ru.golubyatnikov.family.calendar.bot.service.dispatcher.CallbackQueryDispatcher;
import ru.golubyatnikov.family.calendar.bot.service.dispatcher.CommandDispatcher;
import ru.golubyatnikov.family.calendar.bot.service.KeyboardService;
import ru.golubyatnikov.family.calendar.bot.service.user.UserService;
import ru.golubyatnikov.family.calendar.bot.service.authorization.AuthorizationService;
import ru.golubyatnikov.family.calendar.bot.service.message.MessageRouter;
import ru.golubyatnikov.family.calendar.bot.util.CorrelationIdUtil;

import java.util.Optional;

import static ru.golubyatnikov.family.calendar.bot.util.MarkdownFormatter.escape;
import static ru.golubyatnikov.family.calendar.bot.util.MarkdownFormatter.formatMessage;

/**
 * Координатор обработки обновлений от Telegram Bot API.
 * 
 * <p>Выполняет роль координатора, делегируя обработку специализированным компонентам:</p>
 * <ul>
 *   <li>Callback queries → CallbackQueryDispatcher</li>
 *   <li>Текстовые сообщения → MessageRouter</li>
 *   <li>Файловые сообщения → MessageRouter</li>
 *   <li>Команды → CommandDispatcher</li>
 * </ul>
 * 
 * @author Family Calendar Bot Team
 * @since 2026-02-02
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class UpdateProcessor {

    private final CommandDispatcher commandDispatcher;
    private final CallbackQueryDispatcher callbackQueryDispatcher;
    private final UserService userService;
    private final TelegramMessageService messageService;
    private final KeyboardService keyboardService;
    private final AuthorizationService authorizationService;
    private final MessageRouter messageRouter;

    /**
     * Асинхронно обрабатывает входящее обновление от Telegram Bot API.
     * 
     * @param update объект Update от Telegram
     */
    @Async
    public void processUpdate(Update update) {
        CorrelationIdUtil.executeWithCorrelationId(() -> {
            if (update == null) {
                log.error("Получено null обновление для обработки");
                throw new IllegalArgumentException("Update не может быть null");
            }
            
            log.debug("Начало асинхронной обработки обновления: updateId={}", update.getUpdateId());
            
            try {
                // Делегируем обработку callback query
                if (update.hasCallbackQuery()) {
                    log.debug("Обновление содержит callback query: updateId={}", update.getUpdateId());
                    callbackQueryDispatcher.dispatch(update.getCallbackQuery());
                    return;
                }
                
                // Проверяем наличие сообщения
                if (!update.hasMessage()) {
                    log.debug("Обновление не содержит сообщения: updateId={}, hasEditedMessage={}", 
                            update.getUpdateId(), update.hasEditedMessage());
                    return;
                }
                
                Message message = update.getMessage();
                
                if (message == null) {
                    log.warn("Обновление помечено как hasMessage=true, но message=null: updateId={}", 
                            update.getUpdateId());
                    return;
                }
                
                // Обрабатываем сообщение
                processMessage(message);
                
                log.debug("Обновление успешно обработано: updateId={}", update.getUpdateId());
                
            } catch (Exception e) {
                log.error("Ошибка при обработке обновления: updateId={}, error={}", 
                        update.getUpdateId(), e.getMessage(), e);
            }
        });
    }

    /**
     * Обрабатывает сообщение от пользователя.
     */
    private void processMessage(Message message) {
        String originalText = message.getText();
        Long telegramId = message.getFrom().getId();
        Optional<User> userOpt = userService.findByTelegramId(telegramId);
        
        // Обрабатываем файловые сообщения
        if (originalText == null || originalText.isBlank()) {
            log.debug("Сообщение не содержит текста: messageId={}", message.getMessageId());
            
            if (message.hasDocument() || message.hasPhoto() || message.hasVideo() || message.hasAudio()) {
                handleFileMessage(message, userOpt);
            }
            return;
        }
        
        log.debug("Извлечено сообщение: messageId={}, chatId={}, from={}", 
                message.getMessageId(), message.getChatId(), telegramId);
        
        // Преобразуем текст кнопки в команду
        String commandText = keyboardService.buttonTextToCommand(originalText);
        
        if (!originalText.equals(commandText)) {
            log.debug("Текст кнопки '{}' преобразован в команду '{}'", originalText, commandText);
            message.setText(commandText);
        }
        
        // Проверяем авторизацию
        // Если пользователь не авторизован, проверяем требует ли команда авторизации
        if (userOpt.isEmpty()) {
            // Проверяем, является ли это командой, которая не требует авторизации
            if (commandText != null && commandText.startsWith("/") && commandDispatcher.hasHandler(commandText)) {
                // Команда существует - проверяем, требует ли она авторизации
                // Если команда не требует авторизации (например, /start или /help), обрабатываем её
                handleCommand(message);
                return;
            }
            
            // Для всех остальных случаев отправляем сообщение о необходимости авторизации
            handleUnauthorizedMessage(message, commandText);
            return;
        }
        
        User user = userOpt.get();
        
        // Логируем состояния пользователя
        logUserStates(user);
        
        // Маршрутизируем сообщение
        boolean handled = messageRouter.routeMessage(message, user, originalText, commandText);
        
        if (!handled) {
            // Сообщение не обработано маршрутизатором - обрабатываем как команду
            handleCommand(message);
        }
    }


    /**
     * Обрабатывает файловое сообщение.
     */
    private void handleFileMessage(Message message, Optional<User> userOpt) {
        Long chatId = message.getChatId();
        Long telegramId = message.getFrom().getId();
        
        if (userOpt.isEmpty()) {
            log.warn("Неавторизованный пользователь пытается отправить файл: telegramId={}", telegramId);
            try {
                messageService.sendMessage(chatId, 
                    "❌ Для отправки файлов необходимо авторизоваться. Используйте 🚀 " + escape("/start"));
            } catch (Exception e) {
                log.error("Ошибка при отправке сообщения: {}", e.getMessage());
            }
            return;
        }
        
        User user = userOpt.get();
        messageRouter.routeFileMessage(message, user);
    }

    /**
     * Обрабатывает сообщение от неавторизованного пользователя.
     */
    private void handleUnauthorizedMessage(Message message, String commandText) {
        Long chatId = message.getChatId();
        Long telegramId = message.getFrom().getId();
        String username = message.getFrom().getUserName();
        
        log.debug("Сообщение от неавторизованного пользователя: telegramId={}, command={}", 
                telegramId, commandText);
        
        // Проверяем, является ли это командой
        if (commandText != null && commandText.startsWith("/")) {
            MessageCategory category = determineMessageCategory(commandText);
            authorizationService.checkAuthorizationAndNotify(telegramId, chatId, category, commandText, username);
        } else {
            // Неавторизованный пользователь отправил не команду
            try {
                ReplyKeyboardMarkup keyboard = keyboardService.createUnauthorizedUserKeyboard();
                String response = formatMessage(
                    "Для использования бота необходимо авторизоваться. Используйте 🚀 " + escape("/start"));
                messageService.sendMessage(chatId, response, keyboard);
            } catch (Exception e) {
                log.error("Ошибка при отправке сообщения: {}", e.getMessage());
            }
        }
    }

    /**
     * Логирует состояния пользователя для диагностики.
     */
    private void logUserStates(User user) {
        // Эта логика будет реализована через ConversationStateService
        log.debug("Обработка сообщения от авторизованного пользователя: userId={}, telegramId={}", 
                user.getId(), user.getTelegramId());
    }

    /**
     * Обрабатывает команду.
     */
    private void handleCommand(Message message) {
        String messageText = message.getText().trim();
        Long telegramId = message.getFrom().getId();
        Long chatId = message.getChatId();
        String username = message.getFrom().getUserName();
        
        String commandText = extractCommand(messageText);
        
        log.debug("Обработка команды: command='{}', telegramId={}, chatId={}", 
                commandText, telegramId, chatId);
        
        if (commandText == null) {
            handleInvalidCommand(chatId, telegramId);
            return;
        }
        
        log.debug("Извлечена команда: '{}' из текста: '{}'", commandText, messageText);
        
        if (!commandDispatcher.hasHandler(commandText)) {
            handleUnknownCommand(chatId, telegramId, commandText);
            return;
        }
        
        try {
            String response = commandDispatcher.dispatch(message);
            
            log.debug("Команда '{}' успешно обработана: telegramId={}, responseLength={}", 
                    commandText, telegramId, response != null ? response.length() : 0);
            
            if (response != null && !response.isBlank()) {
                sendCommandResponse(chatId, telegramId, response);
            } else {
                log.warn("Пустой ответ от обработчика команды: command={}, telegramId={}", 
                        commandText, telegramId);
            }
            
        } catch (ru.golubyatnikov.family.calendar.bot.exception.UnauthorizedAccessException e) {
            log.debug("Команда '{}' отклонена из-за отсутствия авторизации: telegramId={}", 
                    commandText, telegramId);
            
            MessageCategory category = determineMessageCategory(commandText);
            authorizationService.checkAuthorizationAndNotify(telegramId, chatId, category, commandText, username);
        }
    }

    /**
     * Обрабатывает невалидную команду.
     */
    private void handleInvalidCommand(Long chatId, Long telegramId) {
        log.warn("Не удалось извлечь команду из текста: telegramId={}", telegramId);
        
        try {
            Optional<User> userOpt = userService.findByTelegramId(telegramId);
            ReplyKeyboardMarkup keyboard = userOpt.isPresent()
                    ? keyboardService.createAuthorizedUserKeyboard()
                    : keyboardService.createUnauthorizedUserKeyboard();
            
            String response = formatMessage(
                    "Команда должна начинаться с символа '/'. Используйте 📚 " + escape("/help") + 
                    " для списка доступных команд.");
            messageService.sendMessage(chatId, response, keyboard);
        } catch (Exception e) {
            log.error("Ошибка при отправке сообщения об ошибке: {}", e.getMessage(), e);
        }
    }

    /**
     * Обрабатывает неизвестную команду.
     */
    private void handleUnknownCommand(Long chatId, Long telegramId, String commandText) {
        log.warn("Обработчик не найден для команды: '{}', telegramId={}", commandText, telegramId);
        
        try {
            Optional<User> userOpt = userService.findByTelegramId(telegramId);
            ReplyKeyboardMarkup keyboard = userOpt.isPresent()
                    ? keyboardService.createAuthorizedUserKeyboard()
                    : keyboardService.createUnauthorizedUserKeyboard();
            
            String response = formatMessage("Неизвестная команда: %s\n\nИспользуйте 📚 %s для списка доступных команд.", 
                                          commandText, escape("/help"));
            messageService.sendMessage(chatId, response, keyboard);
        } catch (Exception e) {
            log.error("Ошибка при отправке сообщения об ошибке: {}", e.getMessage(), e);
        }
    }

    /**
     * Отправляет ответ на команду.
     */
    private void sendCommandResponse(Long chatId, Long telegramId, String response) {
        try {
            Optional<User> userOpt = userService.findByTelegramId(telegramId);
            ReplyKeyboardMarkup keyboard = userOpt.isPresent()
                    ? keyboardService.createAuthorizedUserKeyboard()
                    : keyboardService.createUnauthorizedUserKeyboard();
            
            messageService.sendMessage(chatId, response, keyboard);
            log.debug("Ответ с клавиатурой успешно отправлен пользователю: chatId={}, responseLength={}", 
                    chatId, response.length());
        } catch (Exception e) {
            log.error("Ошибка при отправке ответа пользователю: telegramId={}, chatId={}, error={}", 
                    telegramId, chatId, e.getMessage(), e);
        }
    }

    /**
     * Извлекает команду из текста сообщения.
     */
    private String extractCommand(String text) {
        if (text == null || text.isEmpty()) {
            return null;
        }
        
        String trimmed = text.trim();
        if (!trimmed.startsWith("/")) {
            return null;
        }
        
        int spaceIndex = trimmed.indexOf(' ');
        if (spaceIndex > 0) {
            return trimmed.substring(0, spaceIndex).toLowerCase();
        }
        
        return trimmed.toLowerCase();
    }

    /**
     * Определяет категорию сообщения на основе команды.
     */
    private MessageCategory determineMessageCategory(String command) {
        return switch (command) {
            case "/add_event" -> MessageCategory.EVENT_CREATION;
            case "/my_events", "/month", "/today", "/week" -> MessageCategory.EVENT_VIEWING;
            case "/search", "/filter" -> MessageCategory.SEARCH_FILTER;
            case "/trash" -> MessageCategory.TRASH_MANAGEMENT;
            case "/stats" -> MessageCategory.STATISTICS;
            default -> MessageCategory.GENERAL;
        };
    }
}
