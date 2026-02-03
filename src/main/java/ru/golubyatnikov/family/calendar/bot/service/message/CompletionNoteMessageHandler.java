package ru.golubyatnikov.family.calendar.bot.service.message;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboardMarkup;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import ru.golubyatnikov.family.calendar.bot.model.User;
import ru.golubyatnikov.family.calendar.bot.service.conversation.ConversationStateService;
import ru.golubyatnikov.family.calendar.bot.service.KeyboardService;
import ru.golubyatnikov.family.calendar.bot.service.telegram.TelegramMessageService;
import ru.golubyatnikov.family.calendar.bot.service.event.EventNotificationService;
import ru.golubyatnikov.family.calendar.bot.service.event.EventService;
import ru.golubyatnikov.family.calendar.bot.util.BotMessageBuilder;

import static ru.golubyatnikov.family.calendar.bot.util.MarkdownFormatter.formatMessage;

/**
 * Обработчик ввода заметки к завершенному событию.
 * 
 * <p>Обрабатывает текстовое сообщение пользователя как заметку к завершенному событию.
 * После добавления заметки обновляет сообщение с карточкой события и шапку /my_events.</p>
 * 
 * @author Family Calendar Bot Team
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
    private final BotMessageBuilder botMessageBuilder;

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
            Long telegramId = user.getTelegramId();
            Integer userMessageId = message.getMessageId();
            
            log.debug("Обработка заметки к завершенному событию от пользователя: userId={}, telegramId={}", 
                    userId, telegramId);
            
            ConversationStateService.CompletionNoteContext context = 
                conversationStateService.getCompletionNoteContext(userId);
            
            if (context == null) {
                handleMissingContext(userId, chatId);
                return;
            }
            
            Long eventId = context.getEventId();
            Integer messageId = context.getMessageId();
            
            messageService.deleteMessageSilently(chatId, userMessageId);
            log.debug("Сообщение пользователя с заметкой удалено: chatId={}, messageId={}, userId={}", 
                    chatId, userMessageId, userId);
            
            ru.golubyatnikov.family.calendar.bot.model.Event event = 
                eventService.addCompletionNote(eventId, userId, noteText);
            
            log.info("Заметка успешно добавлена к завершенному событию ID={} пользователем ID={}: noteLength={}", 
                    eventId, userId, noteText != null ? noteText.length() : 0);
            
            updateEventCard(chatId, messageId, event);
            
            conversationStateService.clearAwaitingCompletionNote(userId);
            log.debug("Контекст добавления заметки очищен: userId={}", userId);
            
            eventNotificationService.updateMyEventsHeaderAfterRemoval(userId);
            log.info("Шапка /my_events обновлена после добавления заметки: userId={}", userId);
            
        } catch (ru.golubyatnikov.family.calendar.bot.exception.EventNotFoundException e) {
            handleEventNotFoundError(user, message.getChatId(), e);
        } catch (ru.golubyatnikov.family.calendar.bot.exception.UnauthorizedAccessException e) {
            handleUnauthorizedError(user, message.getChatId(), e);
        } catch (IllegalStateException e) {
            handleIllegalStateError(user, message.getChatId(), e);
        } catch (Exception e) {
            handleGeneralError(user, message.getChatId(), e);
        }
    }

    /**
     * Обрабатывает отсутствие контекста.
     */
    private void handleMissingContext(Long userId, Long chatId) {
        log.warn("Контекст добавления заметки не найден для пользователя: userId={}", userId);
        conversationStateService.clearAwaitingCompletionNote(userId);
        
        try {
            String response = formatMessage("❌ Время ожидания истекло. Попробуйте завершить событие заново.");
            ReplyKeyboardMarkup keyboard = keyboardService.createAuthorizedUserKeyboard();
            messageService.sendMessage(chatId, response, keyboard);
        } catch (Exception e) {
            log.error("Ошибка при отправке сообщения: {}", e.getMessage());
        }
    }

    /**
     * Обновляет карточку события с заметкой.
     */
    private void updateEventCard(Long chatId, Integer messageId, ru.golubyatnikov.family.calendar.bot.model.Event event) {
        if (messageId != null) {
            try {
                String eventMessage = botMessageBuilder.buildCompletedEventMessage(event);
                messageService.editMessageText(chatId, messageId, eventMessage, null);
                
                log.debug("Сообщение отредактировано с финальной карточкой события: chatId={}, messageId={}, eventId={}", 
                        chatId, messageId, event.getId());
                
            } catch (TelegramApiException e) {
                log.warn("Не удалось отредактировать сообщение, отправка нового (fallback): chatId={}, messageId={}, error={}", 
                        chatId, messageId, e.getMessage());
                
                try {
                    String eventMessage = botMessageBuilder.buildCompletedEventMessage(event);
                    messageService.sendMessage(chatId, eventMessage);
                } catch (Exception ex) {
                    log.error("Ошибка при отправке сообщения: {}", ex.getMessage());
                }
            }
        } else {
            log.warn("messageId отсутствует в контексте, отправка нового сообщения: eventId={}", event.getId());
            
            try {
                String eventMessage = botMessageBuilder.buildCompletedEventMessage(event);
                messageService.sendMessage(chatId, eventMessage);
            } catch (Exception e) {
                log.error("Ошибка при отправке сообщения: {}", e.getMessage());
            }
        }
    }

    private void handleEventNotFoundError(User user, Long chatId, 
                                         ru.golubyatnikov.family.calendar.bot.exception.EventNotFoundException e) {
        log.error("Событие не найдено при добавлении заметки: userId={}, error={}", user.getId(), e.getMessage());
        
        try {
            conversationStateService.clearAwaitingCompletionNote(user.getId());
            String response = formatMessage("❌ Событие не найдено. Возможно, оно было удалено.");
            ReplyKeyboardMarkup keyboard = keyboardService.createAuthorizedUserKeyboard();
            messageService.sendMessage(chatId, response, keyboard);
        } catch (Exception ex) {
            log.error("Ошибка при отправке сообщения об ошибке: telegramId={}, error={}", 
                    user.getTelegramId(), ex.getMessage(), ex);
        }
    }

    private void handleUnauthorizedError(User user, Long chatId, 
                                        ru.golubyatnikov.family.calendar.bot.exception.UnauthorizedAccessException e) {
        log.error("Нет прав для добавления заметки: userId={}, error={}", user.getId(), e.getMessage());
        
        try {
            conversationStateService.clearAwaitingCompletionNote(user.getId());
            String response = formatMessage("❌ У вас нет прав для добавления заметки к этому событию.");
            ReplyKeyboardMarkup keyboard = keyboardService.createAuthorizedUserKeyboard();
            messageService.sendMessage(chatId, response, keyboard);
        } catch (Exception ex) {
            log.error("Ошибка при отправке сообщения об ошибке: telegramId={}, error={}", 
                    user.getTelegramId(), ex.getMessage(), ex);
        }
    }

    private void handleIllegalStateError(User user, Long chatId, IllegalStateException e) {
        log.error("Событие не завершено при добавлении заметки: userId={}, error={}", user.getId(), e.getMessage());
        
        try {
            conversationStateService.clearAwaitingCompletionNote(user.getId());
            String response = formatMessage("❌ Заметку можно добавить только к завершенному событию.");
            ReplyKeyboardMarkup keyboard = keyboardService.createAuthorizedUserKeyboard();
            messageService.sendMessage(chatId, response, keyboard);
        } catch (Exception ex) {
            log.error("Ошибка при отправке сообщения об ошибке: telegramId={}, error={}", 
                    user.getTelegramId(), ex.getMessage(), ex);
        }
    }

    private void handleGeneralError(User user, Long chatId, Exception e) {
        log.error("Ошибка при обработке заметки к событию: userId={}, telegramId={}, error={}", 
                 user.getId(), user.getTelegramId(), e.getMessage(), e);
        
        try {
            conversationStateService.clearAwaitingCompletionNote(user.getId());
            String response = formatMessage("❌ Произошла ошибка при добавлении заметки. Попробуйте еще раз.");
            ReplyKeyboardMarkup keyboard = keyboardService.createAuthorizedUserKeyboard();
            messageService.sendMessage(chatId, response, keyboard);
        } catch (Exception ex) {
            log.error("Ошибка при отправке сообщения об ошибке: telegramId={}, error={}", 
                    user.getTelegramId(), ex.getMessage(), ex);
        }
    }
}
