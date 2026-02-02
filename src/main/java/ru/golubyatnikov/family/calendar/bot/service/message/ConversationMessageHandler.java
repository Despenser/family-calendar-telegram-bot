package ru.golubyatnikov.family.calendar.bot.service.message;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboardMarkup;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import ru.golubyatnikov.family.calendar.bot.model.User;
import ru.golubyatnikov.family.calendar.bot.service.ConversationService;
import ru.golubyatnikov.family.calendar.bot.service.EventService;
import ru.golubyatnikov.family.calendar.bot.service.KeyboardService;
import ru.golubyatnikov.family.calendar.bot.service.TelegramMessageService;
import ru.golubyatnikov.family.calendar.bot.util.BotMessageBuilder;

import static ru.golubyatnikov.family.calendar.bot.util.MarkdownFormatter.*;

/**
 * Обработчик сообщений в контексте диалога создания события.
 * 
 * <p>Реализует улучшенный процесс создания события, где весь диалог
 * происходит в одном сообщении бота, а сообщения пользователя удаляются из чата.</p>
 * 
 * <p>Шаги диалога:</p>
 * <ul>
 *   <li>WAITING_FOR_TITLE: Пользователь вводит название события</li>
 *   <li>WAITING_FOR_DESCRIPTION: Пользователь вводит описание или пропускает</li>
 * </ul>
 * 
 * @author Family Calendar Bot Team
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
    private final BotMessageBuilder botMessageBuilder;

    /**
     * Обрабатывает сообщение в контексте активного диалога создания события.
     * 
     * @param message сообщение от пользователя
     * @param user авторизованный пользователь
     */
    public void handle(Message message, User user) {
        try {
            ru.golubyatnikov.family.calendar.bot.model.Event draft = 
                conversationService.getActiveDraft(user.getId());
            
            ConversationService.ConversationStep step = conversationService.getCurrentStep(draft);
            String text = message.getText();
            Long chatId = message.getChatId();
            Long telegramId = user.getTelegramId();
            Integer userMessageId = message.getMessageId();
            Long creationMessageId = draft.getMessageId();
            
            log.debug("Обработка сообщения в контексте диалога: userId={}, telegramId={}, step={}, creationMessageId={}", 
                user.getId(), telegramId, step, creationMessageId);
            
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
    private void handleTitleInput(User user, Long chatId, Integer userMessageId, String text, Long creationMessageId) {
        Long userId = user.getId();
        
        // 1. Удаляем сообщение пользователя
        messageService.deleteMessageSilently(chatId, userMessageId);
        log.debug("Сообщение пользователя с названием удалено: chatId={}, messageId={}, userId={}", 
                chatId, userMessageId, userId);
        
        // 2. Сохраняем название в черновике
        conversationService.updateEventTitle(userId, text);
        log.debug("Название события сохранено: userId={}, title='{}'", userId, text);
        
        // 3. Обновляем сообщение создания
        String response = bold("📋 Создание нового события") + "\n\n" +
            "✅ Название: " + escape(text) + "\n\n" +
            "Теперь отправьте описание события или нажмите кнопку 'Пропустить':";
        
        InlineKeyboardMarkup skipKeyboard = keyboardService.createSkipDescriptionKeyboard();
        
        if (creationMessageId != null) {
            try {
                messageService.editMessageText(chatId, creationMessageId.intValue(), response, skipKeyboard);
                log.debug("Сообщение создания обновлено с названием: chatId={}, messageId={}, userId={}", 
                        chatId, creationMessageId, userId);
            } catch (TelegramApiException e) {
                log.warn("Не удалось обновить сообщение создания, отправка нового: chatId={}, messageId={}, error={}", 
                        chatId, creationMessageId, e.getMessage());
                try {
                    messageService.sendMessageWithInlineKeyboard(chatId, response, skipKeyboard);
                } catch (Exception ex) {
                    log.error("Ошибка при отправке сообщения: {}", ex.getMessage());
                }
            }
        } else {
            log.warn("creationMessageId отсутствует в черновике, отправка нового сообщения: userId={}", userId);
            try {
                messageService.sendMessageWithInlineKeyboard(chatId, response, skipKeyboard);
            } catch (Exception e) {
                log.error("Ошибка при отправке сообщения: {}", e.getMessage());
            }
        }
    }

    /**
     * Обрабатывает ввод описания события.
     */
    private void handleDescriptionInput(User user, Long chatId, Integer userMessageId, String text, Long creationMessageId) {
        Long userId = user.getId();
        
        // 1. Удаляем сообщение пользователя
        messageService.deleteMessageSilently(chatId, userMessageId);
        log.debug("Сообщение пользователя с описанием удалено: chatId={}, messageId={}, userId={}", 
                chatId, userMessageId, userId);
        
        // 2. Обрабатываем текст описания (включая "пропустить")
        String description = text.equalsIgnoreCase("пропустить") ? null : text;
        
        // 3. Завершаем создание события
        ru.golubyatnikov.family.calendar.bot.model.Event completedEvent = 
            conversationService.completeEventCreation(userId, description);
        log.debug("Создание события завершено: eventId={}, userId={}", 
                completedEvent.getId(), userId);
        
        // 4. Обновляем сообщение создания с финальной карточкой события
        updateWithFinalCard(chatId, creationMessageId, completedEvent, userId, description);
        
        log.debug("Событие успешно создано: eventId={}, userId={}, telegramId={}", 
            completedEvent.getId(), userId, user.getTelegramId());
    }

    /**
     * Обновляет сообщение с финальной карточкой события.
     */
    private void updateWithFinalCard(Long chatId, Long creationMessageId, 
                                     ru.golubyatnikov.family.calendar.bot.model.Event event, 
                                     Long userId, String description) {
        if (creationMessageId != null) {
            try {
                int eventCount = eventService.getActiveEventsCount(userId);
                String eventMessage = botMessageBuilder.buildEventMessageWithHeader(event, eventCount);
                InlineKeyboardMarkup keyboard = keyboardService.createEventActionsKeyboard(event, userId);
                
                messageService.editMessageText(chatId, creationMessageId.intValue(), eventMessage, keyboard);
                log.debug("Сообщение создания обновлено с финальной карточкой: chatId={}, messageId={}, eventId={}", 
                        chatId, creationMessageId, event.getId());
                
            } catch (TelegramApiException e) {
                log.warn("Не удалось обновить сообщение создания с финальной карточкой, отправка нового: chatId={}, messageId={}, error={}", 
                        chatId, creationMessageId, e.getMessage());
                sendEventMessageFallback(chatId, event, description);
            }
        } else {
            log.warn("creationMessageId отсутствует в черновике, отправка нового сообщения: userId={}", userId);
            sendEventMessageFallback(chatId, event, description);
        }
    }

    /**
     * Отправляет сообщение о созданном событии (fallback).
     */
    private void sendEventMessageFallback(Long chatId, ru.golubyatnikov.family.calendar.bot.model.Event event, String description) {
        try {
            eventService.sendOrUpdateEventMessage(event, chatId);
            log.debug("Сообщение о созданном событии отправлено (fallback): eventId={}", event.getId());
        } catch (org.telegram.telegrambots.meta.exceptions.TelegramApiException ex) {
            log.error("Ошибка при отправке сообщения о созданном событии (fallback): eventId={}, error={}", 
                    event.getId(), ex.getMessage());
            
            // Последний fallback: простое подтверждающее сообщение
            try {
                String response = bold("✅ Событие успешно создано!") + "\n\n" +
                    "📅 Дата: " + escape(event.getFormattedDate()) + "\n" +
                    "🕐 Время: " + escape(event.getFormattedTime()) + "\n" +
                    "📝 Название: " + escape(event.getTitle()) + "\n" +
                    (description != null ? "📄 Описание: " + escape(description) : "");
                ReplyKeyboardMarkup fallbackKeyboard = keyboardService.createAuthorizedUserKeyboard();
                messageService.sendMessage(chatId, response, fallbackKeyboard);
            } catch (Exception e) {
                log.error("Ошибка при отправке последнего fallback сообщения: {}", e.getMessage());
            }
        }
    }

    /**
     * Обрабатывает неожиданный шаг диалога.
     */
    private void handleUnexpectedStep(User user, Long chatId, ConversationService.ConversationStep step) {
        log.warn("Неожиданный шаг диалога: step={}, userId={}, telegramId={}", 
                step, user.getId(), user.getTelegramId());
        conversationService.cancelEventCreation(user.getId());
        
        try {
            String response = "❌ " + bold("Произошла ошибка") + "\\. " + 
                            italic("Попробуйте начать заново с команды ➕ /add_event");
            ReplyKeyboardMarkup keyboard = keyboardService.createAuthorizedUserKeyboard();
            messageService.sendMessage(chatId, response, keyboard);
        } catch (Exception e) {
            log.error("Ошибка при отправке сообщения: {}", e.getMessage());
        }
    }

    /**
     * Обрабатывает ошибку.
     */
    private void handleError(Long chatId, User user) {
        try {
            String response = "❌ " + bold("Произошла ошибка") + "\\. " + 
                            italic("Попробуйте начать заново с команды ➕ /add_event");
            ReplyKeyboardMarkup keyboard = keyboardService.createAuthorizedUserKeyboard();
            messageService.sendMessage(chatId, response, keyboard);
        } catch (Exception ex) {
            log.error("Ошибка при отправке сообщения об ошибке: telegramId={}, error={}", 
                    user.getTelegramId(), ex.getMessage(), ex);
        }
    }
}
