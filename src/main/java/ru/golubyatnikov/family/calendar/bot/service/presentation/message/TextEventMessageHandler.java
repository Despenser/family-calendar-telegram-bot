package ru.golubyatnikov.family.calendar.bot.service.presentation.message;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.message.Message;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardRow;
import ru.golubyatnikov.family.calendar.bot.model.dto.EventParsingResponse;
import ru.golubyatnikov.family.calendar.bot.model.dto.EventParsingSession;
import ru.golubyatnikov.family.calendar.bot.model.dto.ParsedEvent;
import ru.golubyatnikov.family.calendar.bot.model.entity.User;
import ru.golubyatnikov.family.calendar.bot.model.enums.CallbackPrefix;
import ru.golubyatnikov.family.calendar.bot.model.enums.EventParsingState;
import ru.golubyatnikov.family.calendar.bot.service.infrastructure.ai.EventParsingSessionService;
import ru.golubyatnikov.family.calendar.bot.service.infrastructure.ai.GigaChatEventParsingService;
import ru.golubyatnikov.family.calendar.bot.service.infrastructure.telegram.TelegramMessageService;
import ru.golubyatnikov.family.calendar.bot.service.presentation.formatting.DateTimeFormattingService;
import ru.golubyatnikov.family.calendar.bot.service.presentation.keyboard.KeyboardService;

import static ru.golubyatnikov.family.calendar.bot.util.EmojiConstants.Actions.CANCEL;
import static ru.golubyatnikov.family.calendar.bot.util.EmojiConstants.Commands.ADD_EVENT;
import static ru.golubyatnikov.family.calendar.bot.util.EmojiConstants.Event.*;
import static ru.golubyatnikov.family.calendar.bot.util.EmojiConstants.Misc.AI;
import static ru.golubyatnikov.family.calendar.bot.util.EmojiConstants.Status.ERROR;
import static ru.golubyatnikov.family.calendar.bot.util.EmojiConstants.Status.SUCCESS;
import static ru.golubyatnikov.family.calendar.bot.util.MarkdownFormatter.*;

/**
 * Обработчик распознавания событий из текстовых сообщений с использованием GigaChat AI.
 *
 * @author Golubyatnikov Aleksey
 * @since 2026-02-02
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class TextEventMessageHandler {

    private final GigaChatEventParsingService gigaChatEventParsingService;
    private final EventParsingSessionService sessionService;
    private final TelegramMessageService messageService;
    private final KeyboardService keyboardService;
    private final DateTimeFormattingService dateTimeFormattingService;

    /**
     * Обрабатывает распознавание события из текстового сообщения с использованием AI.
     * 
     * @param message исходное сообщение от пользователя
     * @param user авторизованный пользователь
     * @param text текст сообщения для парсинга
     */
    public void handle(Message message, User user, String text) {
        try {
            Long chatId = message.getChatId();
            Long userId = user.getId();
            
            // Получаем или создаем сессию парсинга
            EventParsingSession session = sessionService.getOrCreateSession(userId);
            
            // Используем userId как conversationId для сохранения контекста
            String conversationId = String.valueOf(userId);
            
            // Парсим текст через GigaChat с использованием Agent-as-a-Judge
            EventParsingResponse response = gigaChatEventParsingService.parseEventFromText(
                    text, 
                    conversationId
            );
            
            if (response.success() && response.parsedEvent() != null) {
                // Событие успешно распознано
                handleSuccessfulParsing(chatId, session, response.parsedEvent());
                
            } else if (response.clarificationQuestion() != null) {
                // Нужно уточнение от пользователя
                handleClarificationNeeded(chatId, session, response.clarificationQuestion());
                
            } else {
                // Ошибка парсинга
                handleParsingError(chatId, user, response.errorMessage());
            }
            
            sessionService.updateSession(session);
            
        } catch (Exception e) {
            log.error("Ошибка при обработке распознавания события из текста: userId={}, telegramId={}, error={}", 
                     user.getId(), user.getTelegramId(), e.getMessage(), e);
            
            handleError(message.getChatId(), user);
        }
    }

    /**
     * Обрабатывает успешное распознавание события.
     */
    private void handleSuccessfulParsing(Long chatId, @NonNull EventParsingSession session, 
                                        @NonNull ParsedEvent parsedEvent) {
        session.setParsedEvent(parsedEvent);
        session.updateState(EventParsingState.AWAITING_CONFIRMATION);
        
        sendEventPreview(chatId, parsedEvent);
    }

    /**
     * Обрабатывает необходимость уточнения данных.
     */
    private void handleClarificationNeeded(Long chatId, @NonNull EventParsingSession session, 
                                          @NonNull String question) {
        session.updateState(EventParsingState.AWAITING_CLARIFICATION);
        
        String message = AI + " " + escape(question);
        ReplyKeyboardMarkup keyboard = keyboardService.createAuthorizedUserKeyboard();
        sendMessage(chatId, message, keyboard);
    }

    /**
     * Обрабатывает ошибку парсинга.
     */
    private void handleParsingError(Long chatId, @NonNull User user, String errorMessage) {
        sessionService.cancelSession(user.getId());
        
        String message = ERROR + " " + bold("Не удалось распознать событие") + "\n\n";
        if (errorMessage != null) {
            message += escape(errorMessage) + "\n\n";
        }
        message += italic("Попробуйте переформулировать запрос или используйте команду " + 
                         ADD_EVENT + " /add_event для пошагового создания.");
        
        ReplyKeyboardMarkup keyboard = keyboardService.createAuthorizedUserKeyboard();
        sendMessage(chatId, message, keyboard);
    }

    /**
     * Отправляет предпросмотр распознанного события.
     */
    private void sendEventPreview(Long chatId, @NonNull ParsedEvent parsedEvent) {
        String preview = bold(SUCCESS + " Распознано событие:") + "\n\n" +
            DESCRIPTION + " Название: " + escape(parsedEvent.title()) + "\n" +
            DATE + " Дата: " + escape(dateTimeFormattingService.formatDate(parsedEvent.date())) + "\n" +
            TIME + " Время: " + escape(dateTimeFormattingService.formatTime(parsedEvent.time())) + "\n\n" +
            "Подтвердите создание события:";
        
        sendMessageWithKeyboard(chatId, preview, createEventConfirmationKeyboard(parsedEvent));
    }

    /**
     * Создает inline-клавиатуру для подтверждения создания события из текста.
     */
    private InlineKeyboardMarkup createEventConfirmationKeyboard(@NonNull ParsedEvent parsedEvent) {
        // Не передаем данные в callback_data, так как они уже сохранены в сессии
        InlineKeyboardButton confirmButton = InlineKeyboardButton.builder()
                .text(SUCCESS + " Создать событие")
                .callbackData(CallbackPrefix.CONFIRM_TEXT_EVENT.withPayload(""))
                .build();
        
        InlineKeyboardButton cancelButton = InlineKeyboardButton.builder()
                .text(CANCEL + " Отменить")
                .callbackData(CallbackPrefix.CANCEL_TEXT_EVENT.withPayload(""))
                .build();
        
        return InlineKeyboardMarkup.builder()
                .keyboardRow(new InlineKeyboardRow(confirmButton, cancelButton))
                .build();
    }

    /**
     * Обрабатывает ошибку.
     */
    private void handleError(Long chatId, User user) {
        try {
            String response = bold(ERROR + " Произошла ошибка при распознавании события") + ".\n\n" +
                    italic("Используйте команду " + ADD_EVENT + " /add_event для пошагового создания.");

            ReplyKeyboardMarkup keyboard = keyboardService.createAuthorizedUserKeyboard();
            messageService.sendMessage(chatId, response, keyboard);

        } catch (Exception ex) {
            log.error("Ошибка при отправке сообщения об ошибке: telegramId={}, error={}", 
                    user.getTelegramId(), ex.getMessage(), ex);
        }
    }

    /**
     * Отправляет сообщение с inline клавиатурой.
     */
    private void sendMessageWithKeyboard(Long chatId, String message, InlineKeyboardMarkup keyboard) {
        try {
            messageService.sendMessageWithInlineKeyboard(chatId, message, keyboard);
            
        } catch (Exception e) {
            logMessageSendError(e);
        }
    }

    /**
     * Отправляет сообщение с reply клавиатурой.
     */
    private void sendMessage(Long chatId, String message, ReplyKeyboardMarkup keyboard) {
        try {
            messageService.sendMessage(chatId, message, keyboard);

        } catch (Exception e) {
            logMessageSendError(e);
        }
    }

    /**
     * Логирует ошибку при отправке сообщения.
     */
    private void logMessageSendError(@NonNull Exception e) {
        log.error("Ошибка при отправке сообщения: {}", e.getMessage());
    }
}
