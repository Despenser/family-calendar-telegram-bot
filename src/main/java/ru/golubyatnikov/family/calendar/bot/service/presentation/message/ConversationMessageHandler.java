package ru.golubyatnikov.family.calendar.bot.service.presentation.message;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.message.Message;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboardMarkup;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import ru.golubyatnikov.family.calendar.bot.model.entity.Event;
import ru.golubyatnikov.family.calendar.bot.model.entity.User;
import ru.golubyatnikov.family.calendar.bot.model.enums.ConversationStep;
import ru.golubyatnikov.family.calendar.bot.service.domain.event.EventService;
import ru.golubyatnikov.family.calendar.bot.service.infrastructure.conversation.ConversationService;
import ru.golubyatnikov.family.calendar.bot.service.infrastructure.telegram.TelegramMessageService;
import ru.golubyatnikov.family.calendar.bot.service.presentation.formatting.BotMessageFormattingService;
import ru.golubyatnikov.family.calendar.bot.service.presentation.formatting.DateTimeFormattingService;
import ru.golubyatnikov.family.calendar.bot.service.presentation.keyboard.KeyboardService;

import static ru.golubyatnikov.family.calendar.bot.util.EmojiConstants.Commands.ADD_EVENT;
import static ru.golubyatnikov.family.calendar.bot.util.EmojiConstants.Event.*;
import static ru.golubyatnikov.family.calendar.bot.util.EmojiConstants.Status.ERROR;
import static ru.golubyatnikov.family.calendar.bot.util.EmojiConstants.Status.SUCCESS;
import static ru.golubyatnikov.family.calendar.bot.util.MarkdownFormatter.*;

/**
 * Обработчик сообщений в контексте диалога создания события.
 *
 * @author Golubyatnikov Aleksey
 * @since 2026-02-02
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ConversationMessageHandler {

    private final ConversationService conversationService;
    private final TelegramMessageService messageService;
    private final KeyboardService keyboardService;
    private final EventService eventService;
    private final BotMessageFormattingService botMessageFormattingService;
    private final DateTimeFormattingService dateTimeFormattingService;

    /**
     * Обрабатывает сообщение в контексте активного диалога создания события.
     * 
     * @param message сообщение от пользователя
     * @param user авторизованный пользователь
     */
    public void handle(Message message, User user) {
        try {
            Event draft = conversationService.getActiveDraft(user.getId());
            ConversationStep step = conversationService.getCurrentStep(draft);

            String text = message.getText();
            Long chatId = message.getChatId();
            Integer userMessageId = message.getMessageId();
            Long creationMessageId = draft.getMessageId();
            
            switch (step) {
                case WAITING_FOR_TITLE -> handleTitleInput(user, chatId, userMessageId, text, creationMessageId);
                case WAITING_FOR_DESCRIPTION -> handleDescriptionInput(user, chatId, userMessageId, text, creationMessageId);
                default -> handleUnexpectedStep(user, chatId, step);
            }
            
        } catch (Exception e) {
            log.error("Ошибка при обработке сообщения в контексте диалога: userId={}, telegramId={}, error={}", 
                user.getId(), user.getTelegramId(), e.getMessage(), e);
            
            handleError(message.getChatId(), user);
        }
    }

    /**
     * Обрабатывает ввод названия события.
     */
    private void handleTitleInput(@NonNull User user,
                                  Long chatId,
                                  Integer userMessageId,
                                  String text,
                                  Long creationMessageId) {

        Long userId = user.getId();
        
        // 1. Удаляем сообщение пользователя
        messageService.deleteMessageSilently(chatId, userMessageId);

        // 2. Сохраняем название в черновике
        conversationService.updateEventTitle(userId, text);

        // 3. Обновляем сообщение создания
        String response = bold(ADD_EVENT + " Создание нового события") + "\n\n" +
            SUCCESS + " Название: " + escape(text) + "\n\n" +
            "Теперь отправьте описание события или нажмите кнопку 'Пропустить':";
        
        InlineKeyboardMarkup skipKeyboard = keyboardService.createSkipDescriptionKeyboard();
        
        if (creationMessageId != null) {
            try {
                messageService.editMessageText(chatId, creationMessageId.intValue(), response, skipKeyboard);

            } catch (TelegramApiException e) {
                log.warn("Не удалось обновить сообщение создания, отправка нового: chatId={}, messageId={}, error={}", 
                        chatId, creationMessageId, e.getMessage());

                sendMessageWithInlineKeyboard(chatId, response, skipKeyboard);
            }
        } else {
            sendMessageWithInlineKeyboard(chatId, response, skipKeyboard);
        }
    }

    /**
     * Обрабатывает ввод описания события.
     */
    private void handleDescriptionInput(@NonNull User user,
                                        Long chatId,
                                        Integer userMessageId,
                                        @NonNull String text,
                                        Long creationMessageId) {

        Long userId = user.getId();
        
        // 1. Удаляем сообщение пользователя
        messageService.deleteMessageSilently(chatId, userMessageId);

        // 2. Обрабатываем текст описания (включая "пропустить")
        String description = text.equalsIgnoreCase("пропустить") ? null : text;
        
        // 3. Завершаем создание события
        Event completedEvent = conversationService.completeEventCreation(userId, description);

        // 4. Обновляем сообщение создания с финальной карточкой события
        updateWithFinalCard(chatId, creationMessageId, completedEvent, userId, description);
    }

    /**
     * Обновляет сообщение с финальной карточкой события.
     */
    private void updateWithFinalCard(Long chatId,
                                     Long creationMessageId,
                                     Event event,
                                     Long userId,
                                     String description) {

        if (creationMessageId != null) {
            try {
                int eventCount = eventService.getActiveEventsCount(userId);
                String eventMessage = botMessageFormattingService.buildEventMessageWithHeader(event, eventCount);
                InlineKeyboardMarkup keyboard = keyboardService.createEventActionsKeyboard(event, userId);
                
                messageService.editMessageText(chatId, creationMessageId.intValue(), eventMessage, keyboard);

            } catch (TelegramApiException e) {
                log.warn("Не удалось обновить сообщение создания с финальной карточкой, отправка нового: chatId={}, messageId={}, error={}", 
                        chatId, creationMessageId, e.getMessage());

                sendEventMessageFallback(chatId, event, description);
            }
        } else {
            sendEventMessageFallback(chatId, event, description);
        }
    }

    /**
     * Отправляет сообщение о созданном событии (fallback).
     */
    private void sendEventMessageFallback(Long chatId, Event event, String description) {
        try {
            eventService.sendOrUpdateEventMessage(event, chatId);

        } catch (TelegramApiException ex) {
            log.error("Ошибка при отправке сообщения о созданном событии (fallback): eventId={}, error={}", 
                    event.getId(), ex.getMessage());

            sendLastFallbackMessage(chatId, event, description);
        }
    }

    /**
     * Отправляет последнее fallback сообщение о созданном событии.
     */
    private void sendLastFallbackMessage(Long chatId, Event event, String description) {
        try {
            String response = DATE + " Дата: " + escape(dateTimeFormattingService.formatDate(event.getEventDate())) + "\n" +
                TIME + " Время: " + escape(dateTimeFormattingService.formatTime(event.getEventTime())) + "\n" +
                DESCRIPTION + " Название: " + escape(event.getTitle()) + "\n" +
                (description != null ? NOTE + " Описание: " + escape(description) : "");

            ReplyKeyboardMarkup fallbackKeyboard = keyboardService.createAuthorizedUserKeyboard();
            messageService.sendMessage(chatId, response, fallbackKeyboard);
            
        } catch (Exception e) {
            logMessageSendError(e);
        }
    }

    /**
     * Обрабатывает неожиданный шаг диалога.
     */
    private void handleUnexpectedStep(@NonNull User user, Long chatId, ConversationStep step) {
        log.warn("Неожиданный шаг диалога: step={}, userId={}, telegramId={}", 
                step, user.getId(), user.getTelegramId());

        conversationService.cancelEventCreation(user.getId());
        sendErrorMessage(chatId);
    }

    /**
     * Обрабатывает ошибку.
     */
    private void handleError(Long chatId, User user) {
        try {
            sendErrorMessage(chatId);

        } catch (Exception ex) {
            log.error("Ошибка при отправке сообщения об ошибке: telegramId={}, error={}", 
                    user.getTelegramId(), ex.getMessage(), ex);
        }
    }

    /**
     * Отправляет сообщение об ошибке пользователю.
     */
    private void sendErrorMessage(Long chatId) {
        try {
            String response = ERROR + " " + bold("Произошла ошибка") + "\\. " + 
                            italic("Попробуйте начать заново с команды " + ADD_EVENT + " /add_event");

            ReplyKeyboardMarkup keyboard = keyboardService.createAuthorizedUserKeyboard();
            messageService.sendMessage(chatId, response, keyboard);

        } catch (Exception e) {
            logMessageSendError(e);
        }
    }

    /**
     * Отправляет сообщение с inline клавиатурой.
     */
    private void sendMessageWithInlineKeyboard(Long chatId, String message, InlineKeyboardMarkup keyboard) {
        try {
            messageService.sendMessageWithInlineKeyboard(chatId, message, keyboard);

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
