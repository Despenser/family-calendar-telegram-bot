package ru.golubyatnikov.family.calendar.bot.service.infrastructure.telegram;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.NonNull;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.message.Message;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboardMarkup;
import ru.golubyatnikov.family.calendar.bot.exception.UnauthorizedAccessException;
import ru.golubyatnikov.family.calendar.bot.model.entity.User;
import ru.golubyatnikov.family.calendar.bot.model.enums.MessageCategory;
import ru.golubyatnikov.family.calendar.bot.service.domain.user.UserService;
import ru.golubyatnikov.family.calendar.bot.service.infrastructure.authorization.AuthorizationService;
import ru.golubyatnikov.family.calendar.bot.service.infrastructure.dispatcher.CallbackQueryDispatcher;
import ru.golubyatnikov.family.calendar.bot.service.infrastructure.dispatcher.CommandDispatcher;
import ru.golubyatnikov.family.calendar.bot.service.presentation.keyboard.KeyboardService;
import ru.golubyatnikov.family.calendar.bot.service.presentation.message.MessageRouter;
import ru.golubyatnikov.family.calendar.bot.util.CommandUtil;
import ru.golubyatnikov.family.calendar.bot.util.CorrelationIdUtil;

import java.util.Optional;

import static ru.golubyatnikov.family.calendar.bot.util.EmojiConstants.Commands.HELP;
import static ru.golubyatnikov.family.calendar.bot.util.EmojiConstants.Commands.START;
import static ru.golubyatnikov.family.calendar.bot.util.EmojiConstants.Status.ERROR;
import static ru.golubyatnikov.family.calendar.bot.util.MarkdownFormatter.escape;
import static ru.golubyatnikov.family.calendar.bot.util.MarkdownFormatter.formatMessage;

/**
 * Координатор обработки обновлений от Telegram Bot API.
 *
 * @author Golubyatnikov Aleksey
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
                throw new IllegalArgumentException("Update не может быть null");
            }
            
            try {
                if (update.hasCallbackQuery()) {
                    callbackQueryDispatcher.dispatch(update.getCallbackQuery());
                    return;
                }

                if (!update.hasMessage()) {
                    return;
                }
                
                Message message = update.getMessage();
                
                if (message == null) {
                    log.warn("Обновление помечено как hasMessage=true, но message=null: updateId={}", 
                            update.getUpdateId());
                    return;
                }

                processMessage(message);
                
            } catch (Exception e) {
                log.error("Ошибка при обработке обновления: updateId={}, error={}", update.getUpdateId(), e.getMessage(), e);
            }
        });
    }

    /**
     * Обрабатывает сообщение от пользователя.
     */
    private void processMessage(@NonNull Message message) {
        String originalText = message.getText();
        Long telegramId = message.getFrom().getId();
        Optional<User> userOpt = userService.findByTelegramId(telegramId);

        if (originalText == null || originalText.isBlank()) {
            if (message.hasDocument() || message.hasPhoto() || message.hasVideo() || message.hasAudio()) {
                handleFileMessage(message, telegramId);
            }
            return;
        }
        
        // Преобразуем текст кнопки в команду
        String commandText = keyboardService.buttonTextToCommand(originalText);
        
        if (!originalText.equals(commandText)) {
            message.setText(commandText);
        }

        if (userOpt.isEmpty()) {
            // Проверяем, является ли это командой, которая не требует авторизации
            if (commandText != null && commandText.startsWith("/") && commandDispatcher.hasHandler(commandText)) {
                handleCommand(message);
                return;
            }

            handleUnauthorizedMessage(message, commandText);
            return;
        }
        
        User user = userOpt.get();
        boolean handled = messageRouter.routeMessage(message, user, originalText, commandText);
        
        if (!handled) {
            // Сообщение не обработано маршрутизатором - обрабатываем как команду
            handleCommand(message);
        }
    }


    /**
     * Обрабатывает файловое сообщение.
     */
    private void handleFileMessage(@NonNull Message message, @NonNull Long telegramId) {
        Long chatId = message.getChatId();
        Optional<User> userOpt = userService.findByTelegramId(telegramId);
        
        if (userOpt.isEmpty()) {
            log.warn("Неавторизованный пользователь пытается отправить файл: telegramId={}", telegramId);
            sendErrorMessage(chatId, 
                ERROR + " Для отправки файлов необходимо авторизоваться. Используйте " + START + " " + escape("/start"));
            return;
        }
        
        messageRouter.routeFileMessage(message, userOpt.get());
    }

    /**
     * Обрабатывает сообщение от неавторизованного пользователя.
     */
    private void handleUnauthorizedMessage(@NonNull Message message, String commandText) {
        Long chatId = message.getChatId();
        Long telegramId = message.getFrom().getId();
        String username = message.getFrom().getUserName();
        
        // Проверяем, является ли это командой
        if (commandText != null && commandText.startsWith("/")) {
            MessageCategory category = determineMessageCategory(commandText);
            authorizationService.checkAuthorizationAndNotify(telegramId, chatId, category, commandText, username);

        } else {
            // Неавторизованный пользователь отправил не команду
            ReplyKeyboardMarkup keyboard = keyboardService.createUnauthorizedUserKeyboard();
            String response = formatMessage(
                "Для использования бота необходимо авторизоваться. Используйте " + START + " " + escape("/start"));

            sendMessageWithKeyboard(chatId, response, keyboard);
        }
    }

    /**
     * Обрабатывает команду.
     */
    private void handleCommand(@NonNull Message message) {
        String messageText = message.getText().trim();
        Long telegramId = message.getFrom().getId();
        Long chatId = message.getChatId();
        String username = message.getFrom().getUserName();
        
        String commandText = CommandUtil.extractCommand(messageText);
        
        if (commandText == null) {
            handleInvalidCommand(chatId, telegramId);
            return;
        }
        
        if (!commandDispatcher.hasHandler(commandText)) {
            handleUnknownCommand(chatId, telegramId, commandText);
            return;
        }
        
        try {
            String response = commandDispatcher.dispatch(message);
            
            if (response != null && !response.isBlank()) {
                sendCommandResponse(chatId, telegramId, response);

            } else {
                log.warn("Пустой ответ от обработчика команды: command={}, telegramId={}", 
                        commandText, telegramId);
            }
            
        } catch (UnauthorizedAccessException e) {
            MessageCategory category = determineMessageCategory(commandText);
            authorizationService.checkAuthorizationAndNotify(telegramId, chatId, category, commandText, username);
        }
    }

    /**
     * Обрабатывает невалидную команду.
     */
    private void handleInvalidCommand(Long chatId, Long telegramId) {
        ReplyKeyboardMarkup keyboard = getUserKeyboard(telegramId);
        String response = formatMessage(
                "Команда должна начинаться с символа '/'. Используйте " + HELP + " " + escape("/help") + 
                " для списка доступных команд.");

        sendMessageWithKeyboard(chatId, response, keyboard);
    }

    /**
     * Обрабатывает неизвестную команду.
     */
    private void handleUnknownCommand(Long chatId, Long telegramId, String commandText) {
        ReplyKeyboardMarkup keyboard = getUserKeyboard(telegramId);
        String response = formatMessage("Неизвестная команда: %s\n\nИспользуйте " + HELP + " %s для списка доступных команд.", 
                                      commandText, escape("/help"));

        sendMessageWithKeyboard(chatId, response, keyboard);
    }

    /**
     * Отправляет ответ на команду.
     */
    private void sendCommandResponse(Long chatId, Long telegramId, String response) {
        ReplyKeyboardMarkup keyboard = getUserKeyboard(telegramId);
        sendMessageWithKeyboard(chatId, response, keyboard);
    }
    
    /**
     * Получает клавиатуру для пользователя в зависимости от его статуса авторизации.
     */
    private ReplyKeyboardMarkup getUserKeyboard(Long telegramId) {
        return userService.findByTelegramId(telegramId)
                .map(user -> keyboardService.createAuthorizedUserKeyboard())
                .orElseGet(keyboardService::createUnauthorizedUserKeyboard);
    }
    
    /**
     * Отправляет сообщение с клавиатурой, обрабатывая возможные ошибки.
     */
    private void sendMessageWithKeyboard(Long chatId, String message, ReplyKeyboardMarkup keyboard) {
        try {
            messageService.sendMessage(chatId, message, keyboard);

        } catch (Exception e) {
            log.error("Ошибка при отправке сообщения: chatId={}, error={}", chatId, e.getMessage(), e);
        }
    }
    
    /**
     * Отправляет сообщение об ошибке, обрабатывая возможные исключения.
     */
    private void sendErrorMessage(Long chatId, String message) {
        try {
            messageService.sendMessage(chatId, message);

        } catch (Exception e) {
            log.error("Ошибка при отправке сообщения об ошибке: chatId={}, error={}", chatId, e.getMessage(), e);
        }
    }

    /**
     * Определяет категорию сообщения на основе команды.
     */
    private MessageCategory determineMessageCategory(@NonNull String command) {
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
