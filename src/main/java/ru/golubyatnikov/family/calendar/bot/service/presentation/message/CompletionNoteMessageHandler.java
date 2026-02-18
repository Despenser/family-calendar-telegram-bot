package ru.golubyatnikov.family.calendar.bot.service.presentation.message;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.message.Message;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboardMarkup;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import ru.golubyatnikov.family.calendar.bot.exception.EventNotFoundException;
import ru.golubyatnikov.family.calendar.bot.exception.UnauthorizedAccessException;
import ru.golubyatnikov.family.calendar.bot.model.context.CompletionNoteContext;
import ru.golubyatnikov.family.calendar.bot.model.entity.Event;
import ru.golubyatnikov.family.calendar.bot.model.entity.User;
import ru.golubyatnikov.family.calendar.bot.service.domain.event.EventNotificationService;
import ru.golubyatnikov.family.calendar.bot.service.domain.event.EventService;
import ru.golubyatnikov.family.calendar.bot.service.infrastructure.conversation.ConversationStateService;
import ru.golubyatnikov.family.calendar.bot.service.infrastructure.telegram.TelegramMessageService;
import ru.golubyatnikov.family.calendar.bot.service.presentation.formatting.BotMessageFormattingService;
import ru.golubyatnikov.family.calendar.bot.service.presentation.keyboard.KeyboardService;

import static ru.golubyatnikov.family.calendar.bot.util.MarkdownFormatter.formatMessage;

/**
 * Обработчик ввода заметки к завершенному событию.
 *
 * @author Golubyatnikov Aleksey
 * @since 2026-02-02
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class CompletionNoteMessageHandler {

    private final ConversationStateService conversationStateService;
    private final EventService eventService;
    private final TelegramMessageService messageService;
    private final KeyboardService keyboardService;
    private final EventNotificationService eventNotificationService;
    private final BotMessageFormattingService botMessageFormattingService;

    /**
     * Обрабатывает ввод заметки к завершенному событию.
     * 
     * @param message сообщение с текстом заметки
     * @param user пользователь, добавляющий заметку
     * @param noteText оригинальный текст заметки
     */
    public void handle(Message message, User user, String noteText) {
        try {
            Long chatId = message.getChatId();
            Long userId = user.getId();
            Integer userMessageId = message.getMessageId();
            
            CompletionNoteContext context = conversationStateService.getCompletionNoteContext(userId);
            
            if (context == null) {
                handleMissingContext(userId, chatId);
                return;
            }
            
            Long eventId = context.getEventId();
            Integer messageId = context.getMessageId();
            
            // Получаем событие ДО добавления заметки, чтобы проверить было ли оно частью списка
            Event eventBefore = eventService.getEventById(eventId);
            boolean wasPartOfMyEventsList = (eventBefore.getMessageId() != null);
            
            messageService.deleteMessageSilently(chatId, userMessageId);
            Event event = eventService.addCompletionNote(eventId, userId, noteText);
            
            updateEventCard(chatId, messageId, event);
            
            conversationStateService.clearAwaitingCompletionNote(userId);

            if (wasPartOfMyEventsList) {
                eventNotificationService.updateMyEventsHeaderAfterRemoval(userId);

            } else {
                log.info("Событие ID={} не было частью списка /my_events (только что создано), шапка не обновляется: userId={}", 
                        eventId, userId);
            }

        } catch (EventNotFoundException e) {
            handleEventNotFoundError(user, message.getChatId());

        } catch (UnauthorizedAccessException e) {
            handleUnauthorizedError(user, message.getChatId());

        } catch (IllegalStateException e) {
            handleIllegalStateError(user, message.getChatId());

        } catch (Exception e) {
            handleGeneralError(user, message.getChatId());
        }
    }

    /**
     * Обрабатывает отсутствие контекста.
     */
    private void handleMissingContext(Long userId, Long chatId) {
        conversationStateService.clearAwaitingCompletionNote(userId);
        
        try {
            String response = formatMessage("❌ Время ожидания истекло. Попробуйте завершить событие заново.");
            ReplyKeyboardMarkup keyboard = keyboardService.createAuthorizedUserKeyboard();
            messageService.sendMessage(chatId, response, keyboard);

        } catch (Exception e) {
            logMessageSendError(e);
        }
    }

    /**
     * Обновляет карточку события с заметкой.
     */
    private void updateEventCard(Long chatId, Integer messageId, Event event) {
        String eventMessage = botMessageFormattingService.buildCompletedEventMessage(event);
        
        if (messageId != null) {
            try {
                messageService.editMessageText(chatId, messageId, eventMessage, null);
                
            } catch (TelegramApiException e) {
                log.warn("Не удалось отредактировать сообщение, отправка нового (fallback): chatId={}, messageId={}, error={}", 
                        chatId, messageId, e.getMessage());

                sendEventMessage(chatId, eventMessage);
            }

        } else {
            sendEventMessage(chatId, eventMessage);
        }
    }

    /**
     * Отправляет сообщение с информацией о событии.
     */
    private void sendEventMessage(Long chatId, String eventMessage) {
        try {
            messageService.sendMessage(chatId, eventMessage);

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

    private void handleEventNotFoundError(@NonNull User user, Long chatId) {
        String errorMessage = "❌ Событие не найдено. Возможно, оно было удалено.";
        sendErrorResponse(user, chatId, errorMessage);
    }

    private void handleUnauthorizedError(@NonNull User user, Long chatId) {
        String errorMessage = "❌ У вас нет прав для добавления заметки к этому событию.";
        sendErrorResponse(user, chatId, errorMessage);
    }

    private void handleIllegalStateError(@NonNull User user, Long chatId) {
        String errorMessage = "❌ Заметку можно добавить только к завершенному событию.";
        sendErrorResponse(user, chatId, errorMessage);
    }

    private void handleGeneralError(@NonNull User user, Long chatId) {
        String errorMessage = "❌ Произошла ошибка при добавлении заметки. Попробуйте еще раз.";
        sendErrorResponse(user, chatId, errorMessage);
    }

    /**
     * Отправляет сообщение об ошибке пользователю и очищает состояние.
     */
    private void sendErrorResponse(@NonNull User user, Long chatId, String errorMessage) {
        try {
            conversationStateService.clearAwaitingCompletionNote(user.getId());
            String response = formatMessage(errorMessage);
            ReplyKeyboardMarkup keyboard = keyboardService.createAuthorizedUserKeyboard();
            messageService.sendMessage(chatId, response, keyboard);

        } catch (Exception ex) {
            log.error("Ошибка при отправке сообщения об ошибке: telegramId={}, error={}", 
                    user.getTelegramId(), ex.getMessage(), ex);
        }
    }
}
